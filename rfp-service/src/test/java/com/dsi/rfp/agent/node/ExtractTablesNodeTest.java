package com.dsi.rfp.agent.node;

import com.dsi.rfp.adapter.table.ScannedTableReconstructor;
import com.dsi.rfp.adapter.table.TableContinuationDetector;
import com.dsi.rfp.adapter.table.TableExtractor;
import com.dsi.rfp.adapter.table.TableSectionLinker;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExtractTablesNodeTest {

    @Mock
    private TableExtractor tableExtractor;

    @Mock
    private TableContinuationDetector continuationDetector;

    @Mock
    private TableSectionLinker sectionLinker;

    @Mock
    private ScannedTableReconstructor scannedTableReconstructor;

    @InjectMocks
    private ExtractTablesNode node;

    @Test
    void shouldExtractTablesFromDocument() throws Exception {
        ExtractionState state = buildState();

        TableExtractionResult table = sampleTable();
        when(tableExtractor.extractFromDocument(anyString(), anyList()))
            .thenReturn(List.of(table));
        when(continuationDetector.detect(anyList()))
            .thenReturn(List.of(table));

        Map<String, Object> result = node.apply(state);

        List<?> tables = (List<?>) result.get(
            ExtractionState.Key.TABLES.value()
        );
        assertThat(tables).hasSize(1);
    }

    @Test
    void shouldCallContinuationDetector() throws Exception {
        ExtractionState state = buildState();

        when(tableExtractor.extractFromDocument(anyString(), anyList()))
            .thenReturn(List.of(sampleTable()));
        when(continuationDetector.detect(anyList()))
            .thenReturn(List.of(sampleTable()));

        node.apply(state);

        verify(continuationDetector).detect(anyList());
    }

    @Test
    void shouldCallSectionLinker() throws Exception {
        ExtractionState state = buildState();

        when(tableExtractor.extractFromDocument(anyString(), anyList()))
            .thenReturn(List.of(sampleTable()));
        when(continuationDetector.detect(anyList()))
            .thenReturn(List.of(sampleTable()));

        node.apply(state);

        verify(sectionLinker).link(anyList(), anyList(), anyList());
    }

    @Test
    void shouldPropagateError() {
        ExtractionState state = buildState();

        when(tableExtractor.extractFromDocument(anyString(), anyList()))
            .thenThrow(new RuntimeException("table failed"));

        assertThatThrownBy(() -> node.apply(state))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldReconstructTablesForScannedPages() throws Exception {
        ExtractionState state = buildStateWithScannedPage();

        TableExtractionResult digitalTable = sampleTable();
        TableExtractionResult scannedTable = TableExtractionResult.builder()
                                                                  .pageStart(2)
                                                                  .pageEnd(2)
                                                                  .provenance(TableProvenance.SCANNED)
                                                                  .type(TableType.OTHER)
                                                                  .headers(List.of("X", "Y"))
                                                                  .confidence(ExtractionConfidence.builder()
                                                                                                  .score(0.6)
                                                                                                  .method("lattice")
                                                                                                  .build())
                                                                  .build();

        when(tableExtractor.extractFromDocument(anyString(), anyList()))
            .thenReturn(List.of(digitalTable));
        when(scannedTableReconstructor.reconstructTables(
            anyString(),
            anyString(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyDouble()
        )).thenReturn(List.of(scannedTable));
        when(continuationDetector.detect(anyList()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = node.apply(state);

        List<?> tables = (List<?>) result.get(
            ExtractionState.Key.TABLES.value()
        );
        assertThat(tables).hasSize(2);
    }

    private ExtractionState buildStateWithScannedPage() {
        Map<String, Object> data = new HashMap<>(
            ExtractionState.initial(42L, "/tmp/sample.pdf")
        );

        PageSummary scannedPage = PageSummary.builder()
                                             .pageNumber(2)
                                             .classification(PageClassification.SCANNED)
                                             .build();
        data.put(
            ExtractionState.Key.PAGE_CLASSIFICATIONS.value(),
            List.of(scannedPage)
        );
        data.put(
            ExtractionState.Key.PAGE_TEXTS.value(),
            Map.of(2, "Col1\tCol2\nA\tB\nC\tD\nE\tF")
        );
        data.put(
            ExtractionState.Key.PAGE_CONFIDENCES.value(),
            Map.of(2, 0.75)
        );

        return new ExtractionState(data);
    }

    private ExtractionState buildState() {
        Map<String, Object> data = ExtractionState.initial(
            42L,
            "/tmp/sample.pdf"
        );
        return new ExtractionState(data);
    }

    private TableExtractionResult sampleTable() {
        return TableExtractionResult.builder()
                                    .pageStart(1)
                                    .pageEnd(1)
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
