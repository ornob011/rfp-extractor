package com.dsi.rfp.application.service;

import com.dsi.rfp.adapter.api.HealthResponse;
import com.dsi.rfp.domain.model.HealthStatus;
import com.dsi.rfp.domain.model.LlmProvider;
import com.dsi.rfp.domain.model.SidecarReachability;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class HealthService {

    private final LlmProvider provider;
    private final ChatModel chatModel;
    private final RestClient restClient;

    public HealthService(
        @Value("${spring.ai.model.chat}") String providerName,
        ChatModel chatModel,
        @Qualifier("sidecarRestClient") RestClient restClient
    ) {
        this.provider = LlmProvider.fromString(providerName);
        this.chatModel = chatModel;
        this.restClient = restClient;
    }

    public HealthResponse check() {
        SidecarReachability ocrStatus = pingOcrSidecar();
        String model = chatModel.getDefaultOptions().getModel();

        log.info(
            "event=health.check component=HealthService status=INFO provider={} model={} ocr={}",
            provider.jsonValue(),
            model,
            ocrStatus
        );

        return HealthResponse.builder()
                             .status(HealthStatus.UP)
                             .provider(provider)
                             .model(model)
                             .ocrSidecar(ocrStatus)
                             .build();
    }

    private SidecarReachability pingOcrSidecar() {
        return CompletableFuture.supplyAsync(
                                    () -> {
                                        restClient.get()
                                                  .uri("/health")
                                                  .retrieve()
                                                  .body(String.class);
                                        return SidecarReachability.REACHABLE;
                                    }
                                )
                                .exceptionally(this::handleOcrPingFailure)
                                .join();
    }

    private SidecarReachability handleOcrPingFailure(Throwable throwable) {
        log.warn(
            "event=ocr.ping component=HealthService status=WARN error={}",
            throwable.getMessage(),
            throwable
        );

        return SidecarReachability.UNREACHABLE;
    }
}
