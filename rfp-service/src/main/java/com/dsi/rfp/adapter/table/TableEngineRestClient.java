package com.dsi.rfp.adapter.table;

import com.dsi.rfp.domain.exception.TableExtractionUnavailableException;
import com.dsi.rfp.domain.model.TableExtractionStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TableEngineRestClient implements TableEngineClient {

    private final RestClient restClient;

    public TableEngineRestClient(
        @Qualifier("sidecarRestClient") RestClient restClient
    ) {
        this.restClient = restClient;
    }

    @Override
    public List<TableEngineTable> extractTables(
        String documentPath,
        int pageNumber,
        TableExtractionStrategy strategy
    ) {
        String documentBase64 = readDocumentBase64(documentPath);

        TableEngineResponse response = restClient.post()
                                                 .uri("/v1/table/extract")
                                                 .body(new TableEngineRequest(
                                                     documentPath,
                                                     documentBase64,
                                                     pageNumber,
                                                     strategy
                                                 ))
                                                 .retrieve()
                                                 .onStatus(
                                                     HttpStatusCode::isError,
                                                     (request, result) -> {
                                                         throw new TableExtractionUnavailableException(
                                                             String.format(
                                                                 "Table sidecar request failed: status=%s strategy=%s page=%d",
                                                                 result.getStatusCode(),
                                                                 strategy.jsonValue(),
                                                                 pageNumber
                                                             )
                                                         );
                                                     }
                                                 )
                                                 .body(TableEngineResponse.class);

        return Optional.ofNullable(response)
                       .map(TableEngineResponse::tables)
                       .orElse(List.of());
    }

    @Override
    public Map<Integer, List<TableEngineTable>> extractTablesBatch(
        String documentPath,
        List<Integer> pageNumbers
    ) {
        String documentBase64 = readDocumentBase64(documentPath);

        TableEngineBatchResponse response = restClient.post()
                                                      .uri("/v1/table/batch")
                                                      .body(new TableEngineBatchRequest(
                                                          documentBase64,
                                                          pageNumbers
                                                      ))
                                                      .retrieve()
                                                      .onStatus(
                                                          HttpStatusCode::isError,
                                                          (request, result) -> {
                                                              throw new TableExtractionUnavailableException(
                                                                  String.format(
                                                                      "Table batch sidecar request failed: status=%s pages=%d",
                                                                      result.getStatusCode(),
                                                                      pageNumbers.size()
                                                                  )
                                                              );
                                                          }
                                                      )
                                                      .body(TableEngineBatchResponse.class);

        TableEngineBatchResponse resolved = Optional.ofNullable(response)
                                                    .orElseThrow(() -> new TableExtractionUnavailableException(
                                                        "Table batch sidecar returned an empty response body"
                                                    ));

        log.info(
            "event=table.batch component=TableEngineRestClient"
            + " pages={}",
            resolved.results().size()
        );

        return resolved.results()
                        .stream()
                        .collect(Collectors.toMap(
                            TableEngineBatchPageResult::pageNumber,
                            TableEngineBatchPageResult::tables
                        ));
    }

    private String readDocumentBase64(String documentPath) {
        try {
            byte[] pdfBytes = Files.readAllBytes(Path.of(documentPath));
            return Base64.getEncoder().encodeToString(pdfBytes);
        } catch (IOException e) {
            throw new TableExtractionUnavailableException(
                String.format(
                    "Failed to read document for base64 encoding: %s",
                    documentPath
                )
            );
        }
    }
}
