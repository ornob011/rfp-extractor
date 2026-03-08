package com.dsi.rfp.agent.node;

import com.dsi.rfp.adapter.extraction.MixedPageContent;
import com.dsi.rfp.adapter.extraction.MixedPageExtractor;
import com.dsi.rfp.adapter.extraction.PageImageRenderer;
import com.dsi.rfp.adapter.ocr.OcrPageWithLayoutResultDto;
import com.dsi.rfp.adapter.ocr.OcrSidecarClient;
import com.dsi.rfp.adapter.ocr.ScannedPageExtractionResult;
import com.dsi.rfp.adapter.ocr.ScannedPageExtractor;
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExtractTextNode implements NodeAction<ExtractionState> {

    private final ScannedPageExtractor scannedExtractor;
    private final MixedPageExtractor mixedExtractor;
    private final OcrSidecarClient ocrClient;
    private final PageImageRenderer pageImageRenderer;

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
            ExtractionState.Key.PAGE_TEXTS.value(), acc.pageTexts(),
            ExtractionState.Key.PAGE_CONFIDENCES.value(), acc.pageConfidences(),
            ExtractionState.Key.PAGE_EXTRACTION_METHODS.value(), acc.pageMethods()
        );
    }

    private ExtractionAccumulator extractAllPages(
        ExtractionState state
    ) throws IOException {
        Path docPath = Path.of(state.documentPath());
        List<Clause> clauses = new ArrayList<>();
        Map<Integer, String> pageTexts = new HashMap<>();
        Map<Integer, Double> pageConfidences = new HashMap<>();
        Map<Integer, PageExtractionMethod> pageMethods = new HashMap<>();

        for (PageSummary page : state.pageClassifications()) {
            PageResult result = extractForPage(
                state.documentPath(),
                docPath,
                page
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
        Path docPath,
        PageSummary page
    ) throws IOException {
        return switch (page.getClassification()) {
            case DIGITAL -> extractDigital(docPath, page);
            case SCANNED -> extractScanned(documentPath, page);
            case MIXED -> extractMixed(documentPath, page);
        };
    }

    private PageResult extractDigital(
        Path docPath,
        PageSummary page
    ) throws IOException {
        String text = extractOrderedText(
            docPath.toString(),
            page.getPageNumber()
        );

        return new PageResult(
            buildClause(page, text),
            text,
            1.0,
            PageExtractionMethod.TEXT_LAYER
        );
    }

    private String extractOrderedText(
        String documentPath,
        int pageNumber
    ) throws IOException {
        byte[] pngBytes = pageImageRenderer.renderPage(
            documentPath,
            pageNumber
        );
        OcrPageWithLayoutResultDto result = ocrClient.extractPageWithLayout(
            pngBytes,
            documentPath,
            pageNumber
        );

        return result.readingOrder().orderedText();
    }

    private PageResult extractScanned(
        String documentPath,
        PageSummary page
    ) throws IOException {
        ScannedPageExtractionResult result = scannedExtractor.extractPage(
            documentPath,
            page.getPageNumber()
        );

        return new PageResult(
            buildClause(page, result.text()),
            result.text(),
            result.confidence(),
            result.extractionMethod()
        );
    }

    private PageResult extractMixed(
        String documentPath,
        PageSummary page
    ) throws IOException {
        MixedPageContent content = mixedExtractor.extractPage(
            documentPath,
            page.getPageNumber()
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
