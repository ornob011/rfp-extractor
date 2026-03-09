package com.dsi.rfp.config;

import com.dsi.rfp.domain.model.LlmProvider;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.llm")
public class LlmProviderProperties {

    private LlmProvider provider = LlmProvider.OPENROUTER;
    private OpenRouterProps openrouter = new OpenRouterProps();
    private OllamaProps ollama = new OllamaProps();
    private double temperature = 0.0;
    private int maxTokens = 4096;
    private int chunkSizeTokens = 3500;
    private int timeoutSeconds = 30;
    private int rateLimitPerMinute = 60;

    @Data
    public static class OpenRouterProps {
        private String baseUrl = "https://openrouter.ai/api/v1";
        private String apiKey;
        private String model = "google/gemini-2.0-flash-001";
        private String modelJudge = "google/gemini-2.5-pro-preview-06-05";
    }

    @Data
    public static class OllamaProps {
        private String baseUrl = "http://localhost:11434";
        private String model = "llama3.1:8b";
        private String modelJudge = "llama3.1:70b";
    }
}
