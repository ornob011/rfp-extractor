package com.dsi.rfp.adapter.entity;

import com.dsi.rfp.adapter.extraction.DocumentChunkingService;
import com.dsi.rfp.domain.model.Clause;
import com.dsi.rfp.domain.model.DocumentChunk;
import com.dsi.rfp.domain.model.Section;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentChunkingServiceTest {

    private final DocumentChunkingService service = new DocumentChunkingService();

    @Test
    void shouldReturnEmptyListForNoClauses() {
        List<DocumentChunk> chunks = service.chunkDocument(List.of(), List.of());

        assertThat(chunks).isEmpty();
    }

    @Test
    void shouldCreateSingleChunkForShortText() {
        Clause clause = Clause.builder()
                              .clauseId("C-1")
                              .sectionId("S-1")
                              .text("Short text content")
                              .pageNumber(1)
                              .build();

        Section section = Section.builder()
                                 .title("Introduction")
                                 .level(0)
                                 .pageStart(1)
                                 .pageEnd(1)
                                 .build();

        List<DocumentChunk> chunks = service.chunkDocument(
            List.of(section),
            List.of(clause)
        );

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().getChunkIndex()).isZero();
        assertThat(chunks.getFirst().getRawText()).isEqualTo("Short text content");
        assertThat(chunks.getFirst().getContextHeader()).contains("Introduction");
    }

    @Test
    void shouldSplitLongTextIntoMultipleChunks() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 150; i++) {
            sb.append("Section content line number ")
              .append(i)
              .append(" with enough text to fill the chunk. ");
        }
        String longText = sb.toString().repeat(5);

        Clause clause = Clause.builder()
                              .clauseId("C-1")
                              .sectionId("S-1")
                              .text(longText)
                              .pageNumber(1)
                              .build();

        List<DocumentChunk> chunks = service.chunkDocument(
            List.of(),
            List.of(clause)
        );

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks.getFirst().getChunkIndex()).isZero();
        assertThat(chunks.get(1).getChunkIndex()).isEqualTo(1);
    }

    @Test
    void shouldEstimateTokens() {
        Clause clause = Clause.builder()
                              .clauseId("C-1")
                              .sectionId("S-1")
                              .text("ABCDEFGH")
                              .pageNumber(1)
                              .build();

        List<DocumentChunk> chunks = service.chunkDocument(
            List.of(),
            List.of(clause)
        );

        assertThat(chunks.getFirst().getTokenEstimate()).isEqualTo(2);
    }
}
