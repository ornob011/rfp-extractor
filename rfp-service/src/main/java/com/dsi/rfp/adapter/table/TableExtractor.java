package com.dsi.rfp.adapter.table;

import com.dsi.rfp.domain.model.PageClassification;
import com.dsi.rfp.domain.model.PageSummary;
import com.dsi.rfp.domain.model.TableExtractionResult;
import com.dsi.rfp.domain.model.TableExtractionStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Component
public class TableExtractor {

    private final TableExtractionConfig config;
    private final Map<TableExtractionStrategy, BiFunction<String, Integer, List<TableExtractionResult>>> strategyExtractors;

    public TableExtractor(
        LatticeTableExtractor latticeExtractor,
        StreamTableExtractor streamExtractor,
        TableExtractionConfig config
    ) {
        this.config = config;

        EnumMap<TableExtractionStrategy, BiFunction<String, Integer, List<TableExtractionResult>>> extractors = new EnumMap<>(TableExtractionStrategy.class);

        extractors.put(
            TableExtractionStrategy.LATTICE,
            latticeExtractor::extractFromPage
        );

        extractors.put(
            TableExtractionStrategy.STREAM,
            streamExtractor::extractFromPage
        );

        strategyExtractors = Map.copyOf(extractors);
    }

    public List<TableExtractionResult> extractFromDocument(
        String documentPath,
        List<PageSummary> pageClassifications
    ) {
        Map<Integer, PageClassification> classMap = pageClassifications.stream()
                                                                       .collect(Collectors.toMap(
                                                                           PageSummary::getPageNumber,
                                                                           PageSummary::getClassification
                                                                       ));

        int totalPages = pageClassifications.stream()
                                            .map(PageSummary::getPageNumber)
                                            .max(Integer::compareTo)
                                            .orElse(0);

        return IntStream.rangeClosed(1, totalPages)
                        .mapToObj(page -> extractForPage(
                            documentPath,
                            page,
                            classMap.getOrDefault(page, PageClassification.DIGITAL)
                        ))
                        .flatMap(List::stream)
                        .toList();
    }

    private List<TableExtractionResult> extractForPage(
        String documentPath,
        int page,
        PageClassification classification
    ) {
        return switch (classification) {
            case SCANNED -> {
                log.debug("event=table.skipScanned page={}", page);
                yield List.of();
            }
            default -> extractWithConfiguredOrder(documentPath, page);
        };
    }

    private List<TableExtractionResult> extractWithConfiguredOrder(
        String documentPath,
        int page
    ) {
        return config.extractionOrder()
                     .stream()
                     .map(strategy -> extractByStrategy(
                         strategy,
                         documentPath,
                         page
                     ))
                     .filter(results -> !results.isEmpty())
                     .findFirst()
                     .orElse(List.of());
    }

    private List<TableExtractionResult> extractByStrategy(
        TableExtractionStrategy strategy,
        String documentPath,
        int page
    ) {
        List<TableExtractionResult> results = strategyExtractors.getOrDefault(
            strategy,
            (path, pageNumber) -> List.of()
        ).apply(documentPath, page);

        log.debug(
            "event=table.strategy page={} strategy={} count={}",
            page,
            strategy.jsonValue(),
            results.size()
        );

        return results;
    }
}

