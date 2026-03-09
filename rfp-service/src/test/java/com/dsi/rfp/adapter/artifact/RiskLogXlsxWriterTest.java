package com.dsi.rfp.adapter.artifact;

import com.dsi.rfp.adapter.llm.LlmAdapter;
import com.dsi.rfp.adapter.llm.PromptTemplateRenderer;
import com.dsi.rfp.domain.exception.LlmUnavailableException;
import com.dsi.rfp.domain.model.RfpDocument;
import com.dsi.rfp.domain.model.RfpEntities;
import com.dsi.rfp.domain.model.RiskImpact;
import com.dsi.rfp.domain.model.RiskItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiskLogXlsxWriterTest {

    @Mock
    private LlmAdapter llmAdapter;

    private RiskLogXlsxWriter writer;

    @BeforeEach
    void setUp() {
        ArtifactGenerationConfig config = new ArtifactGenerationConfig();
        writer = new RiskLogXlsxWriter(
            new RiskMitigationEnricher(
                llmAdapter,
                config,
                new PromptTemplateRenderer(),
                new ArtifactDocumentContextResolver(config)
            ),
            config
        );
    }

    @Test
    void shouldCallLlmForMitigationPerRiskItem() {
        List<RiskItem> items = List.of(
            riskItem("Risk A"),
            riskItem("Risk B")
        );

        when(llmAdapter.extractStructured(any(), any(), any()))
            .thenReturn(Optional.of(
                new RiskMitigationResponse("Suggested action")
            ));

        byte[] bytes = writer.write(items, minimalDoc());

        assertThat(bytes).isNotEmpty();
        verify(llmAdapter, times(2)).extractStructured(
            any(),
            any(),
            eq(RiskMitigationResponse.class)
        );
    }

    @Test
    void shouldPropagateWhenLlmFails() {
        List<RiskItem> items = List.of(riskItem("Risk A"));

        when(llmAdapter.extractStructured(any(), any(), any()))
            .thenThrow(new LlmUnavailableException("LLM down"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> writer.write(
            items,
            minimalDoc()
        )).isInstanceOf(LlmUnavailableException.class);
    }

    private RiskItem riskItem(String description) {
        return RiskItem.builder()
                       .id("r-1")
                       .riskDescription(description)
                       .source("rule:BD-ICT-001")
                       .impact(RiskImpact.HIGH)
                       .owner("Bid Team")
                       .build();
    }

    private RfpDocument minimalDoc() {
        return RfpDocument.builder()
                          .sections(List.of())
                          .entities(RfpEntities.builder().build())
                          .build();
    }
}
