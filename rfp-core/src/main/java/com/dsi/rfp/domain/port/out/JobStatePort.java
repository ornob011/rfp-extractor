package com.dsi.rfp.domain.port.out;

import com.dsi.rfp.domain.model.AnalysisStatus;
import com.dsi.rfp.domain.model.ExtractionJob;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Optional;

public interface JobStatePort {

    ExtractionJob save(ExtractionJob job);

    Optional<ExtractionJob> findById(Long jobId);

    void updateStatus(Long jobId, AnalysisStatus status);

    void updateProgress(Long jobId, int progress);

    void updateSectionsJson(Long jobId, JsonNode sectionsJson);

    List<ExtractionJob> findAll();
}
