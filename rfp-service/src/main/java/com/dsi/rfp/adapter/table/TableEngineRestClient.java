package com.dsi.rfp.adapter.table;

import com.dsi.rfp.domain.exception.TableExtractionUnavailableException;
import com.dsi.rfp.domain.model.TableExtractionStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

@Component
public class TableEngineRestClient implements TableEngineClient {

    private final RestClient restClient;
    private final String tableSidecarUrl;

    public TableEngineRestClient(
        RestClient restClient,
        @Value("${app.sidecar.url}") String tableSidecarUrl
    ) {
        this.restClient = restClient;
        this.tableSidecarUrl = tableSidecarUrl;
    }

    @Override
    public List<TableEngineTable> extractTables(
        String documentPath,
        int pageNumber,
        TableExtractionStrategy strategy
    ) {
        TableEngineResponse response = restClient.post()
                                                 .uri(String.format("%s/v1/table/extract", tableSidecarUrl))
                                                 .body(new TableEngineRequest(
                                                     documentPath,
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
}
