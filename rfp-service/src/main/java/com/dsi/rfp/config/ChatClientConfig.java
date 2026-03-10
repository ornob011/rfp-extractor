package com.dsi.rfp.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
class ChatClientConfig {

    @Bean
    @Primary
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    ChatClient judgeChatClient(
        ChatModel chatModel,
        @Value("${app.llm.judge-model}") String judgeModel
    ) {
        return ChatClient.builder(chatModel)
                         .defaultOptions(
                             ChatOptions.builder()
                                        .model(judgeModel)
                                        .build()
                         )
                         .build();
    }
}
