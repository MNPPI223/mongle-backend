package com.mongle.backend.reflection.gemini;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** application.yml의 mongle.gemini.* — 키는 .env의 GEMINI_API_KEY로만 채운다 (리포에 값 금지). */
@Component
@ConfigurationProperties(prefix = "mongle.gemini")
public class GeminiProperties {

    private String apiKey = "";
    private String model = "gemini-3.5-flash";
    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
    private int timeoutMs = 8000; // 실측 근거는 application.yml의 mongle.gemini.timeout-ms 주석 참고

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
