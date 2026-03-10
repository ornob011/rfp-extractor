package com.dsi.rfp.adapter.ocr;

import com.dsi.rfp.domain.exception.OcrUnavailableException;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;

@Slf4j
@Component
public class OcrSidecarClient {

    private static final String RESILIENCE_INSTANCE = "ocr";
    private static final int DEFAULT_DPI = 300;
    private static final String DEFAULT_LANGUAGE = "eng+ben";

    private final RestClient restClient;

    public OcrSidecarClient(
        @Qualifier("sidecarRestClient") RestClient restClient
    ) {
        this.restClient = restClient;
    }

    @Retry(name = RESILIENCE_INSTANCE)
    public OcrResultDto extractPage(
        byte[] imageBytes,
        String lang
    ) {
        OcrPageRequest request = new OcrPageRequest(
            Base64.getEncoder().encodeToString(imageBytes),
            lang,
            DEFAULT_DPI
        );

        OcrResultDto result = restClient.post()
                                        .uri("/ocr/page")
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
        return extractPageWithLayout(
            imageBytes,
            documentPath,
            pageNumber,
            DEFAULT_DPI
        );
    }

    @Retry(name = RESILIENCE_INSTANCE)
    public OcrPageWithLayoutResultDto extractPageWithLayout(
        byte[] imageBytes,
        String documentPath,
        int pageNumber,
        int dpi
    ) {
        String documentBase64 = readDocumentBase64(documentPath);

        OcrPageRequest request = new OcrPageRequest(
            Base64.getEncoder().encodeToString(imageBytes),
            DEFAULT_LANGUAGE,
            dpi,
            documentBase64,
            pageNumber
        );

        OcrPageWithLayoutResultDto result = restClient.post()
                                                      .uri("/ocr/page-with-layout")
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

    private String readDocumentBase64(String documentPath) {
        try {
            byte[] pdfBytes = Files.readAllBytes(Path.of(documentPath));
            return Base64.getEncoder().encodeToString(pdfBytes);
        } catch (IOException e) {
            throw new OcrUnavailableException(
                String.format("Failed to read PDF file: %s", documentPath),
                e
            );
        }
    }
}
