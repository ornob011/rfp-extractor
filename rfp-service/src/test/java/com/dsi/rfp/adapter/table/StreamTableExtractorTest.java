package com.dsi.rfp.adapter.table;

import com.dsi.rfp.domain.model.TableExtractionResult;
import com.dsi.rfp.domain.model.TableExtractionStrategy;
import com.dsi.rfp.domain.model.TableType;
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
class StreamTableExtractorTest {

    @Mock
    private TableEngineClient tableEngineClient;

    @Mock
    private TableTypeClassifier classifier;

    @Mock
    private TableExtractionConfig config;

    @InjectMocks
    private StreamTableExtractor extractor;

    @Test
    void shouldReturnEmptyWhenSidecarReturnsEmpty() {
        when(config.streamStrategy()).thenReturn(TableExtractionStrategy.STREAM);
        when(tableEngineClient.extractTables(
            eq("/tmp/sample.pdf"),
            eq(2),
            eq(TableExtractionStrategy.STREAM)
        )).thenReturn(List.of());

        List<TableExtractionResult> result = extractor.extractFromPage(
            "/tmp/sample.pdf",
            2
        );

        assertThat(result).isEmpty();
    }

    @Test
    void shouldMapStreamTableToDomain() {
        when(config.streamMethod()).thenReturn("stream");
        when(config.streamStrategy()).thenReturn(TableExtractionStrategy.STREAM);
        when(config.streamConfidence()).thenReturn(0.6);
        when(classifier.classify(anyList(), eq("Cost Summary")))
            .thenReturn(TableType.PAYMENT);

        TableEngineTable engineTable = new TableEngineTable(
            "Cost Summary",
            List.of("Item", "Amount"),
            List.of(
                List.of(
                    new TableEngineCell(0, 0, "Item", 1, 1, true),
                    new TableEngineCell(0, 1, "Amount", 1, 1, true)
                ),
                List.of(
                    new TableEngineCell(1, 0, "Dev", 1, 1, false),
                    new TableEngineCell(1, 1, "1000", 1, 1, false)
                )
            ),
            0.0,
            ""
        );

        when(tableEngineClient.extractTables(
            eq("/tmp/sample.pdf"),
            eq(2),
            eq(TableExtractionStrategy.STREAM)
        )).thenReturn(List.of(engineTable));

        List<TableExtractionResult> result = extractor.extractFromPage(
            "/tmp/sample.pdf",
            2
        );

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getType()).isEqualTo(TableType.PAYMENT);
        assertThat(result.getFirst().getConfidence().getScore()).isEqualTo(0.6);
        assertThat(result.getFirst().getConfidence().getMethod()).isEqualTo("stream");
    }
}
