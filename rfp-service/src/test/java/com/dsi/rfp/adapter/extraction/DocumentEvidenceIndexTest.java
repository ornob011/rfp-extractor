package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.domain.model.DocumentChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentEvidenceIndexTest {

    private final DocumentEvidenceIndex evidenceIndex = new DocumentEvidenceIndex();

    @Test
    void shouldKeepBestScoreWhenThresholdFiltersOutMatches() {
        DocumentChunk chunk = DocumentChunk.builder()
                                           .chunkIndex(0)
                                           .rawText("This request for proposal covers software development and system integration.")
                                           .tokenEstimate(16)
                                           .contextHeader("ICT procurement")
                                           .build();

        DocumentEvidenceIndex.RetrievalResult<DocumentChunk> result = evidenceIndex.retrieveChunks(
            List.of(chunk),
            List.of("software development"),
            3,
            100.0
        );

        assertThat(result.items()).isEmpty();
        assertThat(result.rankedItems()).containsExactly(chunk);
        assertThat(result.bestScore()).isGreaterThan(0.0);
    }
}
