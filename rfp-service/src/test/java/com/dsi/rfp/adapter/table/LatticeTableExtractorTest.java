package com.dsi.rfp.adapter.table;

import com.dsi.rfp.domain.model.TableEngineCell;
import com.dsi.rfp.domain.model.TableEngineTable;
import com.dsi.rfp.domain.model.TableExtractionResult;
import com.dsi.rfp.domain.model.TableExtractionStrategy;
import com.dsi.rfp.domain.model.TableType;
import com.dsi.rfp.domain.port.out.TableEnginePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LatticeTableExtractorTest {

    @Mock
    private TableEnginePort tableEngineClient;

    @Mock
    private TableTypeClassifier classifier;

    @Mock
    private TableExtractionConfig config;

    @InjectMocks
    private LatticeTableExtractor extractor;

    @Test
    void shouldReturnEmptyWhenSidecarReturnsEmpty() {
        when(config.latticeStrategy()).thenReturn(TableExtractionStrategy.LATTICE);
        when(tableEngineClient.extractTables(
            eq("/tmp/sample.pdf"),
            eq(1),
            eq(TableExtractionStrategy.LATTICE)
        )).thenReturn(List.of());

        List<TableExtractionResult> result = extractor.extractFromPage(
            "/tmp/sample.pdf",
            1
        );

        assertThat(result).isEmpty();
    }

    @Test
    void shouldMapSidecarTableToDomain() {
        when(config.latticeMethod()).thenReturn("lattice");
        when(config.latticeStrategy()).thenReturn(TableExtractionStrategy.LATTICE);
        when(classifier.classify(anyList(), eq("Table A")))
            .thenReturn(TableType.DELIVERABLES);

        TableEngineTable engineTable = new TableEngineTable(
            "Table A",
            List.of("Milestone", "Due"),
            List.of(
                List.of(
                    new TableEngineCell(0, 0, "Milestone", 1, 1, true),
                    new TableEngineCell(0, 1, "Due", 1, 1, true)
                ),
                List.of(
                    new TableEngineCell(1, 0, "Kickoff", 1, 1, false),
                    new TableEngineCell(1, 1, "Week 1", 1, 1, false)
                )
            ),
            0.91,
            "lattice"
        );

        when(tableEngineClient.extractTables(
            eq("/tmp/sample.pdf"),
            eq(1),
            eq(TableExtractionStrategy.LATTICE)
        )).thenReturn(List.of(engineTable));

        List<TableExtractionResult> result = extractor.extractFromPage(
            "/tmp/sample.pdf",
            1
        );

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getType()).isEqualTo(TableType.DELIVERABLES);
        assertThat(result.getFirst().getHeaders()).containsExactly("Milestone", "Due");
        assertThat(result.getFirst().getConfidence().getMethod()).isEqualTo("lattice");
    }
}
