package com.dsi.rfp.agent.node;

import com.dsi.rfp.adapter.table.TableContinuationDetector;
import com.dsi.rfp.adapter.table.TableExtractor;
import com.dsi.rfp.adapter.table.TableSectionLinker;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.ExtractionConfidence;
import com.dsi.rfp.domain.model.TableExtractionResult;
import com.dsi.rfp.domain.model.TableProvenance;
import com.dsi.rfp.domain.model.TableType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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

    @InjectMocks
    private ExtractTablesNode node;

    @Test
    void shouldExtractTablesFromDocument() {

        ExtractionState state = buildState();

        TableExtractionResult table = sampleTable();
        when(tableExtractor.extractFromDocument(any(String.class), anyList()))
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
    void shouldCallContinuationDetector() {

        ExtractionState state = buildState();

        when(tableExtractor.extractFromDocument(any(String.class), anyList()))
            .thenReturn(List.of(sampleTable()));
        when(continuationDetector.detect(anyList()))
            .thenReturn(List.of(sampleTable()));

        node.apply(state);

        verify(continuationDetector).detect(anyList());
    }

    @Test
    void shouldCallSectionLinker() {

        ExtractionState state = buildState();

        when(tableExtractor.extractFromDocument(any(String.class), anyList()))
            .thenReturn(List.of(sampleTable()));
        when(continuationDetector.detect(anyList()))
            .thenReturn(List.of(sampleTable()));

        node.apply(state);

        verify(sectionLinker).link(anyList(), anyList(), anyList());
    }

    @Test
    void shouldPropagateError() {
        ExtractionState state = buildState();

        when(tableExtractor.extractFromDocument(any(String.class), anyList()))
            .thenThrow(new RuntimeException("table failed"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> node.apply(state))
                                       .isInstanceOf(RuntimeException.class);
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
