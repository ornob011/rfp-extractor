package com.dsi.rfp.agent.node;

import com.dsi.rfp.adapter.extraction.MixedPageContent;
import com.dsi.rfp.adapter.extraction.MixedPageExtractor;
import com.dsi.rfp.adapter.extraction.PdfDocumentLoader;
import com.dsi.rfp.adapter.ocr.ScannedPageExtractionResult;
import com.dsi.rfp.adapter.ocr.ScannedPageExtractor;
import com.dsi.rfp.adapter.table.ScannedTableResponse;
import com.dsi.rfp.adapter.table.ScannedTableResultMapper;
import com.dsi.rfp.adapter.vision.VisionTableResult;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.*;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ExtractTextNode implements NodeAction<ExtractionState> {

    private final PdfDocumentLoader pdfDocumentLoader;
    private final ScannedPageExtractor scannedPageExtractor;
    private final MixedPageExtractor mixedExtractor;
    private final ScannedTableResultMapper tableResultMapper;
    private final Executor pageExecutor;

    public ExtractTextNode(
        PdfDocumentLoader pdfDocumentLoader,
        ScannedPageExtractor scannedPageExtractor,
        MixedPageExtractor mixedExtractor,
        ScannedTableResultMapper tableResultMapper,
        @Qualifier("pageExtractionExecutor") Executor pageExecutor
    ) {
        this.pdfDocumentLoader = pdfDocumentLoader;
        this.scannedPageExtractor = scannedPageExtractor;
        this.mixedExtractor = mixedExtractor;
        this.tableResultMapper = tableResultMapper;
        this.pageExecutor = pageExecutor;
    }

    @Override
    public Map<String, Object> apply(
        ExtractionState state
    ) throws Exception {
        List<CompletableFuture<IndexedPageResult>> futures =
            state.pageClassifications()
                 .stream()
                 .map(page -> CompletableFuture.supplyAsync(
                     () -> extractPageSafe(
                         state.documentPath(),
                         page
                     ),
                     pageExecutor
                 ))
                 .toList();

        List<IndexedPageResult> results = collectResults(futures);

        List<Clause> clauses = new ArrayList<>();
        Map<Integer, String> pageTexts = new HashMap<>();
        Map<Integer, Double> pageConfidences = new HashMap<>();
        Map<Integer, PageExtractionMethod> pageMethods = new HashMap<>();
        Map<Integer, List<TableExtractionResult>> vlmTables = new HashMap<>();

        for (IndexedPageResult indexed : results) {
            PageResult result = indexed.result();
            int pageNum = indexed.pageNumber();

            clauses.add(result.clause());
            pageTexts.put(pageNum, result.text());
            pageConfidences.put(pageNum, result.confidence());
            pageMethods.put(pageNum, result.method());

            List<TableExtractionResult> tables = convertVlmTables(
                result.vlmTables(),
                pageNum
            );

            vlmTables.put(pageNum, tables);
        }

        log.info(
            "event=text.extracted component=ExtractTextNode"
            + " jobId={} clauses={} scannedPages={} mixedPages={}"
            + " cachedVlmTablePages={}",
            state.jobId(),
            clauses.size(),
            countByClassification(
                state.pageClassifications(),
                PageClassification.SCANNED
            ),
            countByClassification(
                state.pageClassifications(),
                PageClassification.MIXED
            ),
            vlmTables.values()
                     .stream()
                     .filter(list -> !list.isEmpty())
                     .count()
        );

        return Map.of(
            ExtractionState.Key.CLAUSES.value(), clauses,
            ExtractionState.Key.PAGE_TEXTS.value(), stringifyPageKeys(pageTexts),
            ExtractionState.Key.PAGE_CONFIDENCES.value(), stringifyPageKeys(pageConfidences),
            ExtractionState.Key.PAGE_EXTRACTION_METHODS.value(), stringifyPageKeys(pageMethods),
            ExtractionState.Key.VLM_TABLES.value(), stringifyPageKeys(vlmTables)
        );
    }

    private PageResult extractForPage(
        String documentPath,
        PageSummary page
    ) throws IOException {
        return switch (page.getClassification()) {
            case DIGITAL -> extractDigital(documentPath, page);
            case SCANNED -> extractScanned(documentPath, page);
            case MIXED -> extractMixed(documentPath, page);
        };
    }

    private PageResult extractDigital(
        String documentPath,
        PageSummary page
    ) throws IOException {
        String text = pdfDocumentLoader.loadPageText(
            Path.of(documentPath),
            page.getPageNumber()
        );

        return new PageResult(
            buildClause(page, text),
            text,
            1.0,
            PageExtractionMethod.TEXT_LAYER,
            List.of()
        );
    }

    private PageResult extractScanned(
        String documentPath,
        PageSummary page
    ) throws IOException {
        ScannedPageExtractionResult result = scannedPageExtractor.extractPage(
            documentPath,
            page.getPageNumber()
        );

        return new PageResult(
            buildClause(page, result.text()),
            result.text(),
            result.confidence(),
            PageExtractionMethod.VLM,
            result.tables()
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
            content.method(),
            content.tables()
        );
    }

    private List<TableExtractionResult> convertVlmTables(
        List<VisionTableResult> vlmTables,
        int pageNum
    ) {
        return vlmTables.stream()
                        .filter(table -> !table.headers().isEmpty())
                        .map(table -> tableResultMapper.fromLlm(
                            new ScannedTableResponse(
                                table.headers(),
                                table.grid()
                            ),
                            pageNum,
                            table.confidence()
                        ))
                        .toList();
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

    private IndexedPageResult extractPageSafe(
        String documentPath,
        PageSummary page
    ) {
        try {
            PageResult result = extractForPage(documentPath, page);
            return new IndexedPageResult(
                page.getPageNumber(),
                result
            );
        } catch (IOException exception) {
            throw new com.dsi.rfp.domain.exception.SystemIoException(
                String.format(
                    "Page extraction failed for page %d",
                    page.getPageNumber()
                ),
                exception
            );
        }
    }

    private List<IndexedPageResult> collectResults(
        List<CompletableFuture<IndexedPageResult>> futures
    ) throws ExecutionException, InterruptedException {
        CompletableFuture.allOf(
            futures.toArray(CompletableFuture[]::new)
        ).join();

        List<IndexedPageResult> results = new ArrayList<>();

        for (CompletableFuture<IndexedPageResult> future : futures) {
            results.add(future.get());
        }

        return results;
    }

    private record PageResult(
        Clause clause,
        String text,
        double confidence,
        PageExtractionMethod method,
        List<VisionTableResult> vlmTables
    ) {
    }

    private record IndexedPageResult(
        int pageNumber,
        PageResult result
    ) {
    }
}
