package com.dsi.rfp.application.service;

import com.dsi.rfp.adapter.api.HealthResponse;
import com.dsi.rfp.config.LlmProviderProperties;
import com.dsi.rfp.domain.model.HealthStatus;
import com.dsi.rfp.domain.model.SidecarReachability;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class HealthService {

    private final LlmProviderProperties props;
    private final RestClient restClient;

    public HealthService(
        LlmProviderProperties props,
        @Qualifier("sidecarRestClient") RestClient restClient
    ) {
        this.props = props;
        this.restClient = restClient;
    }

    public HealthResponse check() {
        SidecarReachability ocrStatus = pingOcrSidecar();
        String model = switch (props.getProvider()) {
            case OPENROUTER -> props.getOpenrouter().getModel();
            case OLLAMA -> props.getOllama().getModel();
        };

        log.info(
            "event=health.check component=HealthService status=INFO provider={} model={} ocr={}",
            props.getProvider().jsonValue(),
            model,
            ocrStatus
        );

        return HealthResponse.builder()
                             .status(HealthStatus.UP)
                             .provider(props.getProvider())
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
