package com.mongle.backend.auth;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongle.backend.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class AuthControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 유효한_deviceId로_익명_인증하면_토큰을_받는다() throws Exception {
        String body = objectMapper.writeValueAsString(new DeviceIdRequest(UUID.randomUUID().toString()));

        mockMvc.perform(post("/auth/anonymous").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(notNullValue()))
                .andExpect(jsonPath("$.accessToken").value(notNullValue()))
                .andExpect(jsonPath("$.expiresAt").value(notNullValue()));
    }

    @Test
    void 같은_deviceId로_두번_호출하면_같은_userId를_돌려준다() throws Exception {
        String deviceId = UUID.randomUUID().toString();
        String body = objectMapper.writeValueAsString(new DeviceIdRequest(deviceId));

        String firstUserId = extractUserId(body);
        String secondUserId = extractUserId(body);

        org.junit.jupiter.api.Assertions.assertEquals(firstUserId, secondUserId);
    }

    @Test
    void deviceId_형식이_잘못되면_400_VALIDATION_ERROR() throws Exception {
        String body = objectMapper.writeValueAsString(new DeviceIdRequest("not-a-uuid"));

        mockMvc.perform(post("/auth/anonymous").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.field").value("deviceId"));
    }

    @Test
    void deviceId가_없으면_400() throws Exception {
        mockMvc.perform(post("/auth/anonymous").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    private String extractUserId(String requestBody) throws Exception {
        String response = mockMvc.perform(post("/auth/anonymous").contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("userId").asText();
    }

    private record DeviceIdRequest(String deviceId) {}
}
