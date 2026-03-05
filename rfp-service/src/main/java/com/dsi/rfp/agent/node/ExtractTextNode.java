package com.dsi.rfp.agent.node;

import com.dsi.rfp.adapter.extraction.PdfDocumentLoader;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.Clause;
import com.dsi.rfp.domain.model.PageClassification;
import com.dsi.rfp.domain.model.PageSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExtractTextNode implements NodeAction<ExtractionState> {

    private final PdfDocumentLoader pdfLoader;

    @Override
    public Map<String, Object> apply(ExtractionState state) throws Exception {
        Path docPath = Path.of(state.documentPath());
        List<Clause> clauses = extractDigitalPageClauses(state, docPath);

        log.info(
            "event=text.extracted component=ExtractTextNode jobId={} clauses={}",
            state.jobId(),
            clauses.size()
        );

        return Map.of(ExtractionState.Key.CLAUSES.value(), clauses);
    }

    private List<Clause> extractDigitalPageClauses(
        ExtractionState state,
        Path docPath
    ) throws Exception {
        List<Clause> clauses = new ArrayList<>();

        for (PageSummary page : state.pageClassifications()) {
            if (page.getClassification() != PageClassification.DIGITAL) {
                continue;
            }

            clauses.add(buildClause(docPath, page));
        }

        return clauses;
    }

    private Clause buildClause(
        Path docPath,
        PageSummary page
    ) throws IOException {
        String text = pdfLoader.loadPageText(docPath, page.getPageNumber());

        return Clause.builder()
                     .clauseId(String.format("P%d", page.getPageNumber()))
                     .pageNumber(page.getPageNumber())
                     .text(text)
                     .build();
    }
}
