package com.dsi.rfp.adapter.artifact;

import com.dsi.rfp.adapter.llm.LlmAdapter;
import com.dsi.rfp.adapter.llm.PromptTemplateRenderer;
import com.dsi.rfp.domain.exception.LlmUnavailableException;
import com.dsi.rfp.domain.model.RfpDocument;
import com.dsi.rfp.domain.model.RiskItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
class RiskMitigationEnricher {

    private final LlmAdapter llmAdapter;
    private final ArtifactGenerationConfig config;
    private final PromptTemplateRenderer promptTemplateRenderer;
    private final ArtifactDocumentContextResolver contextResolver;

    List<RiskItem> enrich(
        List<RiskItem> items,
        RfpDocument document
    ) {
        String promptTemplate = config.riskMitigationPromptTemplate();

        return IntStream.range(
                            0,
                            items.size()
                        )
                        .mapToObj(index -> enrichItem(
                            items.get(index),
                            document,
                            promptTemplate,
                            index
                        ))
                        .toList();
    }

    private RiskItem enrichItem(
        RiskItem item,
        RfpDocument document,
        String promptTemplate,
        int index
    ) {
        return item.toBuilder()
                   .mitigationSuggestion(resolveMitigation(
                       item,
                       document,
                       promptTemplate,
                       index
                   ))
                   .owner(resolveOwner(item))
                   .build();
    }

    private String resolveMitigation(
        RiskItem item,
        RfpDocument document,
        String promptTemplate,
        int index
    ) {
        return Optional.ofNullable(item.getMitigationSuggestion())
                       .orElseGet(() -> generatedMitigation(
                           item,
                           document,
                           promptTemplate,
                           index
                       ));
    }

    private String generatedMitigation(
        RiskItem item,
        RfpDocument document,
        String promptTemplate,
        int index
    ) {
        return Optional.of(index)
                       .filter(position -> position < config.maxLlmMitigations())
                       .map(position -> generateMitigation(
                           item,
                           document,
                           promptTemplate
                       ))
                       .orElse(null);
    }

    private String generateMitigation(
        RiskItem item,
        RfpDocument document,
        String promptTemplate
    ) {
        String prompt = promptTemplateRenderer.render(
            promptTemplate,
            Map.of(
                "riskDescription", item.getRiskDescription(),
                "source", resolveSource(item),
                "documentTitle", contextResolver.title(document),
                "procurementRef", contextResolver.procurementReference(document)
            )
        );

        return llmAdapter.extractStructured(
                             "",
                             prompt,
                             RiskMitigationResponse.class
                         )
                         .map(RiskMitigationResponse::mitigation)
                         .orElseThrow(() -> new LlmUnavailableException(
                             String.format(
                                 "Risk mitigation generation returned no structured response for risk: %s",
                                 item.getId()
                             )
                         ));
    }

    private String resolveSource(RiskItem item) {
        return Optional.ofNullable(item.getSource())
                       .orElse(config.riskLogEmptySourceLabel());
    }

    private String resolveOwner(RiskItem item) {
        return Optional.ofNullable(item.getOwner())
                       .orElse(config.defaultRiskOwner());
    }
}
