package com.dsi.rfp.adapter.table;

import com.dsi.rfp.adapter.vision.VisionExtractionAdapter;
import com.dsi.rfp.adapter.vision.VisionExtractionConfig;
import com.dsi.rfp.adapter.vision.VisionPageResult;
import com.dsi.rfp.adapter.vision.VisionTableResult;
import com.dsi.rfp.domain.model.TableExtractionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisionTableEngineClientTest {

    @Mock
    private VisionExtractionAdapter visionAdapter;

    @Spy
    private VisionExtractionConfig config = new VisionExtractionConfig();

    private VisionTableEngineClient client;

    @BeforeEach
    void setUp() {
        client = new VisionTableEngineClient(
            visionAdapter,
            config
        );
    }

    @Test
    void shouldExtractTablesFromSinglePage() throws IOException {
        when(visionAdapter.extractTablesOnly("/tmp/sample.pdf", 1))
            .thenReturn(new VisionPageResult(
                "",
                0.80,
                List.of(
                    new VisionTableResult(
                        "Budget",
                        List.of("Item", "Cost"),
                        List.of(List.of("Server", "50000")),
                        0.80
                    )
                ),
                true
            ));

        List<TableEngineTable> tables = client.extractTables(
            "/tmp/sample.pdf",
            1,
            TableExtractionStrategy.LATTICE
        );

        assertThat(tables).hasSize(1);
        assertThat(tables.getFirst().headers())
            .containsExactly("Item", "Cost");
        assertThat(tables.getFirst().method()).isEqualTo(
            config.tableMethod()
        );
    }

    @Test
    void shouldReturnEmptyWhenNoTables() throws IOException {
        when(visionAdapter.extractTablesOnly("/tmp/sample.pdf", 1))
            .thenReturn(new VisionPageResult(
                "text only",
                0.85,
                List.of(),
                false
            ));

        List<TableEngineTable> tables = client.extractTables(
            "/tmp/sample.pdf",
            1,
            TableExtractionStrategy.LATTICE
        );

        assertThat(tables).isEmpty();
    }

    @Test
    void shouldExtractBatchAcrossPages() throws IOException {
        when(visionAdapter.extractTablesOnly("/tmp/sample.pdf", 1))
            .thenReturn(new VisionPageResult(
                "",
                0.80,
                List.of(
                    new VisionTableResult(
                        "",
                        List.of("A"),
                        List.of(List.of("1")),
                        0.80
                    )
                ),
                true
            ));
        when(visionAdapter.extractTablesOnly("/tmp/sample.pdf", 3))
            .thenReturn(new VisionPageResult(
                "",
                0.80,
                List.of(),
                false
            ));

        Map<Integer, List<TableEngineTable>> results =
            client.extractTablesBatch(
                "/tmp/sample.pdf",
                List.of(1, 3)
            );

        assertThat(results).hasSize(2);
        assertThat(results.get(1)).hasSize(1);
        assertThat(results.get(3)).isEmpty();
    }
}
