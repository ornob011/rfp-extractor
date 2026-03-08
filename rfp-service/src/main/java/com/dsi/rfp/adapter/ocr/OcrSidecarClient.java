package com.dsi.rfp.adapter.ocr;

import com.dsi.rfp.domain.exception.OcrUnavailableException;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.Optional;

@Slf4j
@Component
public class OcrSidecarClient {

    private static final String RESILIENCE_INSTANCE = "ocr";
    private static final int DEFAULT_DPI = 300;

    private final RestClient restClient;
    private final String sidecarUrl;

    public OcrSidecarClient(
        RestClient restClient,
        @Value("${app.sidecar.url}") String sidecarUrl
    ) {
        this.restClient = restClient;
        this.sidecarUrl = sidecarUrl;
    }

    @Retry(name = RESILIENCE_INSTANCE)
    public OcrResultDto extractPage(
        byte[] imageBytes,
        String lang
    ) {
        String base64 = Base64.getEncoder().encodeToString(imageBytes);

        OcrPageRequest request = new OcrPageRequest(
            base64,
            lang,
            DEFAULT_DPI
        );

        OcrResultDto result = restClient.post()
                                        .uri(String.format("%s/ocr/page", sidecarUrl))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(request)
                                        .retrieve()
                                        .onStatus(
                                            HttpStatusCode::isError,
                                            (req, res) -> {
                                                throw new OcrUnavailableException(
                                                    String.format(
                                                        "OCR sidecar returned status=%s",
                                                        res.getStatusCode()
                                                    )
                                                );
                                            }
                                        )
                                        .body(OcrResultDto.class);

        OcrResultDto resolvedResult = Optional.ofNullable(result)
                                              .orElseThrow(() -> new OcrUnavailableException(
                                                  "OCR sidecar returned an empty response body"
                                              ));

        log.info(
            "event=ocr.page component=OcrSidecarClient status=INFO"
            + " lang={} confidence={} method={} words={}",
            lang,
            resolvedResult.pageConfidence(),
            resolvedResult.extractionMethod(),
            resolvedResult.wordCount()
        );

        return resolvedResult;
    }

    @Retry(name = RESILIENCE_INSTANCE)
    public OcrPageWithLayoutResultDto extractPageWithLayout(
        byte[] imageBytes,
        String documentPath,
        int pageNumber
    ) {
        String base64 = Base64.getEncoder().encodeToString(imageBytes);

        OcrPageRequest request = new OcrPageRequest(
            base64,
            "eng+ben",
            DEFAULT_DPI,
            documentPath,
            pageNumber
        );

        OcrPageWithLayoutResultDto result = restClient.post()
                                                      .uri(String.format("%s/ocr/page-with-layout", sidecarUrl))
                                                      .contentType(MediaType.APPLICATION_JSON)
                                                      .body(request)
                                                      .retrieve()
                                                      .onStatus(
                                                          HttpStatusCode::isError,
                                                          (req, res) -> {
                                                              throw new OcrUnavailableException(
                                                                  String.format(
                                                                      "OCR sidecar returned status=%s",
                                                                      res.getStatusCode()
                                                                  )
                                                              );
                                                          }
                                                      )
                                                      .body(OcrPageWithLayoutResultDto.class);

        return Optional.ofNullable(result)
                       .orElseThrow(() -> new OcrUnavailableException(
                           "OCR sidecar returned an empty page-with-layout response body"
                       ));
    }
}
