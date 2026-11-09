package com.mongle.backend.reflection;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongle.backend.support.AbstractIntegrationTest;
import com.mongle.backend.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GEMINI_API_KEY가 없는 테스트 프로파일에서 돈다 — 즉 모든 호출이 GeminiCallException →
 * 폴백 경로를 탄다. 그래도 검증할 수 있는 게 있다: 폴백 상황에서도 위험 키워드 판정
 * (riskDetected)은 죽지 않아야 한다는, 이 서비스에서 제일 중요한 안전 규칙.
 */
@Import(AuthTestSupport.class)
class ReflectionControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthTestSupport authTestSupport;

    @Test
    void Gemini_키가_없어도_폴백_문구로_200을_반환한다() throws Exception {
        String token = authTestSupport.newUserToken();
        String sessionId = startSession(token);

        mockMvc.perform(post("/sessions/" + sessionId + "/reflections")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"친구가 내 말을 안 들어줘서 서운했어\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").exists())
                .andExpect(jsonPath("$.riskDetected").value(false));
    }

    @Test
    void Gemini_실패여도_위험_키워드가_있으면_riskDetected는_true로_유지된다() throws Exception {
        String token = authTestSupport.newUserToken();
        String sessionId = startSession(token);

        mockMvc.perform(post("/sessions/" + sessionId + "/reflections")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"요즘 자꾸 죽고싶다는 생각이 들어\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskDetected").value(true))
                .andExpect(jsonPath("$.riskResources.length()").value(2))
                .andExpect(jsonPath("$.riskResources[0].phone").value("0199"));
    }

    @Test
    void 끝난_세션에_reflection을_요청하면_409() throws Exception {
        String token = authTestSupport.newUserToken();
        String sessionId = startSession(token);
        mockMvc.perform(post("/sessions/" + sessionId + "/finish").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/sessions/" + sessionId + "/reflections")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"안녕\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void 한_세션에서_스무번_넘게_요청하면_429() throws Exception {
        String token = authTestSupport.newUserToken();
        String sessionId = startSession(token);

        for (int i = 0; i < 20; i++) {
            mockMvc.perform(post("/sessions/" + sessionId + "/reflections")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"text\":\"안녕 " + i + "\"}"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/sessions/" + sessionId + "/reflections")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"한번만 더\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("RATE_LIMITED"));
    }

    private String startSession(String token) throws Exception {
        String response = mockMvc.perform(post("/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryType\":\"TALK\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("sessionId").asText();
    }
}
