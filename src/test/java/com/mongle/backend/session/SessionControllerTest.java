package com.mongle.backend.session;

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

@Import(AuthTestSupport.class)
class SessionControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthTestSupport authTestSupport;

    @Test
    void 토큰_없이_세션을_시작하면_401() throws Exception {
        mockMvc.perform(post("/sessions").contentType(MediaType.APPLICATION_JSON).content("{\"entryType\":\"TALK\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void 세션을_시작하고_종료하면_FINISHED가_된다() throws Exception {
        String token = authTestSupport.newUserToken();

        String startResponse = mockMvc.perform(post("/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryType\":\"TALK\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").exists())
                .andReturn().getResponse().getContentAsString();

        String sessionId = objectMapper.readTree(startResponse).get("sessionId").asText();

        mockMvc.perform(post("/sessions/" + sessionId + "/finish").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"));
    }

    @Test
    void finish를_두번_불러도_에러가_아니다() throws Exception {
        String token = authTestSupport.newUserToken();
        String sessionId = startSession(token);

        mockMvc.perform(post("/sessions/" + sessionId + "/finish").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        // 두 번째 호출 — idempotent, 500이 아니라 여전히 200 FINISHED여야 한다 (명세서 4.1)
        mockMvc.perform(post("/sessions/" + sessionId + "/finish").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"));
    }

    @Test
    void 다른_유저의_세션을_종료하려하면_403() throws Exception {
        String ownerToken = authTestSupport.newUserToken();
        String sessionId = startSession(ownerToken);

        String strangerToken = authTestSupport.newUserToken();
        mockMvc.perform(post("/sessions/" + sessionId + "/finish").header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void 존재하지_않는_세션이면_404() throws Exception {
        String token = authTestSupport.newUserToken();
        mockMvc.perform(post("/sessions/does-not-exist/finish").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    private String startSession(String token) throws Exception {
        String response = mockMvc.perform(post("/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryType\":\"SILENT\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("sessionId").asText();
    }
}
