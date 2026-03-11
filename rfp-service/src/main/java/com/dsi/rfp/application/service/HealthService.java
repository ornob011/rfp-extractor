package com.dsi.rfp.application.service;

import com.dsi.rfp.adapter.api.HealthResponse;
import com.dsi.rfp.domain.model.HealthStatus;
import com.dsi.rfp.domain.model.LlmProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HealthService {

    private final LlmProvider provider;
    private final ChatModel chatModel;

    public HealthService(
        @Value("${spring.ai.model.chat}") String providerName,
        ChatModel chatModel
    ) {
        this.provider = LlmProvider.fromString(providerName);
        this.chatModel = chatModel;
    }

    public HealthResponse check() {
        String model = chatModel.getDefaultOptions().getModel();

        log.info(
            "event=health.check component=HealthService"
            + " status=INFO provider={} model={}",
            provider.jsonValue(),
            model
        );

        return HealthResponse.builder()
                             .status(HealthStatus.UP)
                             .provider(provider)
                             .model(model)
                             .build();
    }
}
