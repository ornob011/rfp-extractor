package com.dsi.rfp.agent.node;

import com.dsi.rfp.adapter.extraction.PdfDocumentLoader;
import com.dsi.rfp.adapter.extraction.SectionSegmenter;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.Section;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SegmentSectionsNode implements NodeAction<ExtractionState> {

    private final SectionSegmenter sectionSegmenter;
    private final PdfDocumentLoader pdfLoader;

    @Override
    public Map<String, Object> apply(ExtractionState state) throws Exception {
        Path docPath = Path.of(state.documentPath());
        int pageCount = pdfLoader.getPageCount(docPath);

        List<Section> sections = sectionSegmenter.segment(
            docPath,
            pdfLoader,
            pageCount
        );

        log.info(
            "event=sections.segmented component=SegmentSectionsNode jobId={} sections={}",
            state.jobId(),
            sections.size()
        );

        return Map.of(
            ExtractionState.Key.SECTIONS.value(),
            sections
        );
    }
}
