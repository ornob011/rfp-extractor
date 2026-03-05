package com.dsi.rfp.adapter.persistence;

import com.dsi.rfp.adapter.persistence.entity.AnalysisJobEntity;
import com.dsi.rfp.adapter.persistence.entity.DocumentEntity;
import com.dsi.rfp.domain.model.ExtractionJob;
import org.springframework.stereotype.Component;

@Component
public class AnalysisJobMapper {

    public ExtractionJob toDomain(AnalysisJobEntity entity) {
        DocumentEntity doc = entity.getDocument();

        return ExtractionJob.builder()
                            .jobId(entity.getId())
                            .status(entity.getStatus())
                            .submittedAt(entity.getCreatedAt())
                            .completedAt(entity.getCompletedAt())
                            .documentId(doc.getId())
                            .progress(entity.getProgressPercent())
                            .errorMessage(entity.getErrorMessage())
                            .pageCount(entity.getPageCount())
                            .originalFilename(doc.getOriginalFilename())
                            .sectionsJson(entity.getSectionsJson())
                            .build();
    }

    public AnalysisJobEntity toEntity(
        ExtractionJob job,
        DocumentEntity document
    ) {
        return AnalysisJobEntity.builder()
                                .document(document)
                                .status(job.getStatus())
                                .progressPercent(job.getProgress())
                                .errorMessage(job.getErrorMessage())
                                .completedAt(job.getCompletedAt())
                                .pageCount(job.getPageCount())
                                .sectionsJson(job.getSectionsJson())
                                .build();
    }
}
