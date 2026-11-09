package com.mongle.backend.memory;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class MemoryControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthTestSupport authTestSupport;

    @Test
    void 세션이_없어도_기억_미리보기를_만들고_승인할_수_있다() throws Exception {
        String token = authTestSupport.newUserToken();
        String sessionId = java.util.UUID.randomUUID().toString(); // reflections 없이도 preview 자체는 동작해야 함

        String previewResponse = mockMvc.perform(post("/memories/preview")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"" + sessionId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.previewId").exists())
                .andExpect(jsonPath("$.draftSummary").exists())
                .andReturn().getResponse().getContentAsString();
        String previewId = objectMapper.readTree(previewResponse).get("previewId").asText();

        mockMvc.perform(post("/memories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"previewId\":\"" + previewId + "\",\"finalSummary\":\"친구와 다퉈서 서운했다\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memoryId").exists())
                .andExpect(jsonPath("$.summary").value("친구와 다퉈서 서운했다"));

        mockMvc.perform(get("/memories").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memories.length()").value(1));
    }

    @Test
    void 만료된_미리보기로_승인하면_404() throws Exception {
        String token = authTestSupport.newUserToken();
        mockMvc.perform(post("/memories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"previewId\":\"does-not-exist\",\"finalSummary\":\"아무 요약\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void 기억_수정과_삭제() throws Exception {
        String token = authTestSupport.newUserToken();
        String memoryId = approveMemory(token, "원래 요약");

        mockMvc.perform(patch("/memories/" + memoryId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"고친 요약\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("고친 요약"));

        mockMvc.perform(delete("/memories/" + memoryId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/memories").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memories.length()").value(0));
    }

    @Test
    void 다른_유저의_기억을_수정하려하면_403() throws Exception {
        String ownerToken = authTestSupport.newUserToken();
        String memoryId = approveMemory(ownerToken, "원래 요약");

        String strangerToken = authTestSupport.newUserToken();
        mockMvc.perform(patch("/memories/" + memoryId)
                        .header("Authorization", "Bearer " + strangerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"침입\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    private String approveMemory(String token, String summary) throws Exception {
        String previewResponse = mockMvc.perform(post("/memories/preview")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"" + java.util.UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String previewId = objectMapper.readTree(previewResponse).get("previewId").asText();

        String approveResponse = mockMvc.perform(post("/memories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"previewId\":\"" + previewId + "\",\"finalSummary\":\"" + summary + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(approveResponse).get("memoryId").asText();
    }
}
