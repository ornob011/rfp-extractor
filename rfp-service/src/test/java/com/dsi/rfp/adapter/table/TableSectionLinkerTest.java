package com.dsi.rfp.adapter.table;

import com.dsi.rfp.domain.model.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TableSectionLinkerTest {

    private final TableSectionLinker linker = new TableSectionLinker();

    @Test
    void shouldLinkTableToSection() {
        UUID sectionId = UUID.randomUUID();
        Section section = Section.builder()
                                 .id(sectionId)
                                 .title("Technical Requirements")
                                 .level(1)
                                 .pageStart(1)
                                 .pageEnd(5)
                                 .build();

        TableExtractionResult table = mutableTable(3);
        List<TableExtractionResult> tables = new ArrayList<>(List.of(table));

        linker.link(tables, List.of(section), List.of());

        assertThat(table.getSectionId()).isEqualTo(sectionId);
    }

    @Test
    void shouldLinkTableToNearestClause() {
        Clause clause = Clause.builder()
                              .clauseId("3.1")
                              .sectionId("sec1")
                              .text("Requirements")
                              .pageNumber(2)
                              .build();

        TableExtractionResult table = mutableTable(3);
        List<TableExtractionResult> tables = new ArrayList<>(List.of(table));

        linker.link(tables, List.of(), List.of(clause));

        assertThat(table.getClauseId()).isEqualTo("3.1");
    }

    @Test
    void shouldLinkToInnermostSection() {
        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();

        Section child = Section.builder()
                               .id(childId)
                               .title("Subsection")
                               .level(2)
                               .pageStart(2)
                               .pageEnd(4)
                               .build();

        Section parent = Section.builder()
                                .id(parentId)
                                .title("Parent")
                                .level(1)
                                .pageStart(1)
                                .pageEnd(5)
                                .children(new ArrayList<>(List.of(child)))
                                .build();

        TableExtractionResult table = mutableTable(3);

        linker.link(
            new ArrayList<>(List.of(table)),
            List.of(parent),
            List.of()
        );

        assertThat(table.getSectionId()).isEqualTo(childId);
    }

    @Test
    void shouldNotLinkWhenNoSectionsOrClauses() {
        TableExtractionResult table = mutableTable(3);

        linker.link(
            new ArrayList<>(List.of(table)),
            List.of(),
            List.of()
        );

        assertThat(table.getSectionId()).isNull();
        assertThat(table.getClauseId()).isNull();
    }

    private TableExtractionResult mutableTable(int page) {
        return TableExtractionResult.builder()
                                    .pageStart(page)
                                    .pageEnd(page)
                                    .provenance(TableProvenance.DIGITAL)
                                    .type(TableType.OTHER)
                                    .headers(List.of("A", "B"))
                                    .confidence(ExtractionConfidence.builder()
                                                                    .score(0.8)
                                                                    .method("lattice")
                                                                    .build())
                                    .build();
    }
}
