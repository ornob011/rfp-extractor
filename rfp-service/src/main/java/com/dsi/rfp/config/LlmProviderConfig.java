package com.dsi.rfp.config;

import com.dsi.rfp.domain.model.LlmProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class LlmProviderConfig {

    private final LlmProviderProperties props;

    @PostConstruct
    void validateConfiguration() {
        String model = switch (props.getProvider()) {
            case OPENROUTER -> {
                validateOpenRouterConfig();
                yield props.getOpenrouter().getModel();
            }
            case OLLAMA -> props.getOllama().getModel();
        };
        log.info(
            "event=llm.config component=LlmProviderConfig status=INFO"
            + " provider={} model={}",
            props.getProvider().jsonValue(),
            model);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(
        name = "app.llm.provider",
        havingValue = "openrouter",
        matchIfMissing = true)
    public ChatClient openRouterChatClient() {
        LlmProviderProperties.OpenRouterProps or = props.getOpenrouter();
        OpenAiApi api = OpenAiApi.builder().baseUrl(or.getBaseUrl()).apiKey(or.getApiKey()).build();
        OpenAiChatModel chatModel =
            OpenAiChatModel.builder()
                           .openAiApi(api)
                           .defaultOptions(
                               OpenAiChatOptions.builder()
                                                .model(or.getModel())
                                                .temperature(props.getTemperature())
                                                .maxTokens(props.getMaxTokens())
                                                .build())
                           .build();
        return ChatClient.builder(chatModel).build();
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.llm.provider", havingValue = "ollama")
    public ChatClient ollamaChatClient() {
        LlmProviderProperties.OllamaProps ol = props.getOllama();
        OllamaApi api = OllamaApi.builder().baseUrl(ol.getBaseUrl()).build();
        OllamaChatModel chatModel =
            OllamaChatModel.builder()
                           .ollamaApi(api)
                           .defaultOptions(
                               OllamaChatOptions.builder()
                                                .model(ol.getModel())
                                                .temperature(props.getTemperature())
                                                .build())
                           .build();
        return ChatClient.builder(chatModel).build();
    }

    private void validateOpenRouterConfig() {
        if (!StringUtils.hasText(props.getOpenrouter().getApiKey())) {
            throw new IllegalStateException(
                "OpenRouter API key must not be blank when provider=openrouter. "
                    + "Set app.llm.openrouter.api-key or OPENROUTER_API_KEY env var.");
        }
    }
}
