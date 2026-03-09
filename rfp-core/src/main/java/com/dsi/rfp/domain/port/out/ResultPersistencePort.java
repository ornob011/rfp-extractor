package com.dsi.rfp.domain.port.out;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

public interface ResultPersistencePort {

    void saveResult(
        Long jobId,
        JsonNode resultJson
    );

    Optional<JsonNode> findResult(Long jobId);
}
