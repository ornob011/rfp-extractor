package com.dsi.rfp.application.service;

import com.dsi.rfp.adapter.api.HealthResponse;
import com.dsi.rfp.config.LlmProviderProperties;
import com.dsi.rfp.domain.model.HealthStatus;
import com.dsi.rfp.domain.model.LlmProvider;
import com.dsi.rfp.domain.model.SidecarReachability;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Service
public class HealthService {

    private final LlmProviderProperties props;
    private final RestClient restClient;
    private final String ocrSidecarUrl;

    public HealthService(
        LlmProviderProperties props,
        RestClient restClient,
        @Value("${app.ocr.sidecar-url}") String ocrSidecarUrl
    ) {
        this.props = props;
        this.restClient = restClient;
        this.ocrSidecarUrl = ocrSidecarUrl;
    }

    public HealthResponse check() {
        SidecarReachability ocrStatus = pingOcrSidecar();
        String model =
            props.getProvider() == LlmProvider.OPENROUTER
                ? props.getOpenrouter().getModel()
                : props.getOllama().getModel();

        log.info(
            "event=health.check component=HealthService status=INFO"
            + " provider={} model={} ocr={}",
            props.getProvider().jsonValue(),
            model,
            ocrStatus);

        return HealthResponse.builder()
                             .status(HealthStatus.UP)
                             .provider(props.getProvider())
                             .model(model)
                             .ocrSidecar(ocrStatus)
                             .build();
    }

    private SidecarReachability pingOcrSidecar() {
        try {
            restClient.get().uri(ocrSidecarUrl + "/health").retrieve().body(String.class);
            return SidecarReachability.REACHABLE;
        } catch (RestClientException e) {
            log.warn(
                "event=ocr.ping component=HealthService status=WARN"
                + " sidecarUrl={} error={}",
                ocrSidecarUrl,
                e.getMessage());

            return SidecarReachability.UNREACHABLE;
        }
    }
}
