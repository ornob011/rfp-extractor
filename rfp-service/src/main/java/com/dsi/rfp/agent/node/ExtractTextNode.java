package com.dsi.rfp.agent.node;

import com.dsi.rfp.adapter.extraction.MixedPageContent;
import com.dsi.rfp.adapter.extraction.MixedPageExtractor;
import com.dsi.rfp.adapter.ocr.OcrBatchPageResult;
import com.dsi.rfp.adapter.ocr.OcrSidecarClient;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.Clause;
import com.dsi.rfp.domain.model.PageClassification;
import com.dsi.rfp.domain.model.PageExtractionMethod;
import com.dsi.rfp.domain.model.PageSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExtractTextNode implements NodeAction<ExtractionState> {

    private final MixedPageExtractor mixedExtractor;
    private final OcrSidecarClient ocrClient;

    @Override
    public Map<String, Object> apply(
        ExtractionState state
    ) throws Exception {
        ExtractionAccumulator acc = extractAllPages(state);

        log.info(
            "event=text.extracted component=ExtractTextNode"
            + " jobId={} clauses={} scannedPages={} mixedPages={}",
            state.jobId(),
            acc.clauses().size(),
            countByClassification(
                state.pageClassifications(),
                PageClassification.SCANNED
            ),
            countByClassification(
                state.pageClassifications(),
                PageClassification.MIXED
            )
        );

        return Map.of(
            ExtractionState.Key.CLAUSES.value(), acc.clauses(),
            ExtractionState.Key.PAGE_TEXTS.value(), stringifyPageKeys(acc.pageTexts()),
            ExtractionState.Key.PAGE_CONFIDENCES.value(), stringifyPageKeys(acc.pageConfidences()),
            ExtractionState.Key.PAGE_EXTRACTION_METHODS.value(), stringifyPageKeys(acc.pageMethods())
        );
    }

    private ExtractionAccumulator extractAllPages(
        ExtractionState state
    ) throws IOException {
        List<Integer> pageNumbers = state.pageClassifications()
                                         .stream()
                                         .map(PageSummary::getPageNumber)
                                         .toList();

        Map<Integer, OcrBatchPageResult> batchResults = ocrClient.extractBatch(
            state.documentPath(),
            pageNumbers
        );

        List<Clause> clauses = new ArrayList<>();
        Map<Integer, String> pageTexts = new HashMap<>();
        Map<Integer, Double> pageConfidences = new HashMap<>();
        Map<Integer, PageExtractionMethod> pageMethods = new HashMap<>();

        for (PageSummary page : state.pageClassifications()) {
            OcrBatchPageResult batchResult = batchResults.get(
                page.getPageNumber()
            );
            PageResult result = extractForPage(
                state.documentPath(),
                page,
                batchResult
            );
            collectResult(
                result,
                page,
                clauses,
                pageTexts,
                pageConfidences,
                pageMethods
            );
        }

        return new ExtractionAccumulator(
            clauses,
            pageTexts,
            pageConfidences,
            pageMethods
        );
    }

    private void collectResult(
        PageResult result,
        PageSummary page,
        List<Clause> clauses,
        Map<Integer, String> pageTexts,
        Map<Integer, Double> pageConfidences,
        Map<Integer, PageExtractionMethod> pageMethods
    ) {
        clauses.add(result.clause());
        pageTexts.put(page.getPageNumber(), result.text());
        pageConfidences.put(page.getPageNumber(), result.confidence());
        pageMethods.put(page.getPageNumber(), result.method());
    }

    private PageResult extractForPage(
        String documentPath,
        PageSummary page,
        OcrBatchPageResult batchResult
    ) throws IOException {
        return switch (page.getClassification()) {
            case DIGITAL -> extractDigital(page, batchResult);
            case SCANNED -> extractScanned(page, batchResult);
            case MIXED -> extractMixed(
                documentPath, page, batchResult
            );
        };
    }

    private PageResult extractDigital(
        PageSummary page,
        OcrBatchPageResult batchResult
    ) {
        String text = batchResult.readingOrder().orderedText();

        return new PageResult(
            buildClause(page, text),
            text,
            1.0,
            PageExtractionMethod.TEXT_LAYER
        );
    }

    private PageResult extractScanned(
        PageSummary page,
        OcrBatchPageResult batchResult
    ) {
        String text = batchResult.ocrResult().text();
        double confidence = batchResult.ocrResult().pageConfidence();

        return new PageResult(
            buildClause(page, text),
            text,
            confidence,
            PageExtractionMethod.OCR
        );
    }

    private PageResult extractMixed(
        String documentPath,
        PageSummary page,
        OcrBatchPageResult batchResult
    ) throws IOException {
        MixedPageContent content = mixedExtractor.extractPageWithResult(
            documentPath,
            page.getPageNumber(),
            batchResult
        );

        return new PageResult(
            buildClause(page, content.text()),
            content.text(),
            content.confidence(),
            content.method()
        );
    }

    private Clause buildClause(
        PageSummary page,
        String text
    ) {
        return Clause.builder()
                     .clauseId(String.format("P%d", page.getPageNumber()))
                     .pageNumber(page.getPageNumber())
                     .text(text)
                     .build();
    }

    private long countByClassification(
        List<PageSummary> pages,
        PageClassification classification
    ) {
        return pages.stream()
                    .filter(p -> p.getClassification() == classification)
                    .count();
    }

    private <T> Map<String, T> stringifyPageKeys(
        Map<Integer, T> values
    ) {
        return values.entrySet()
                     .stream()
                     .collect(Collectors.toMap(
                         entry -> String.valueOf(entry.getKey()),
                         Map.Entry::getValue
                     ));
    }

    private record PageResult(
        Clause clause,
        String text,
        double confidence,
        PageExtractionMethod method
    ) {
    }

    private record ExtractionAccumulator(
        List<Clause> clauses,
        Map<Integer, String> pageTexts,
        Map<Integer, Double> pageConfidences,
        Map<Integer, PageExtractionMethod> pageMethods
    ) {
    }
}
