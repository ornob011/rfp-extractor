package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.domain.model.Clause;
import com.dsi.rfp.domain.model.DocumentChunk;
import com.dsi.rfp.domain.model.Section;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentByCharacterSplitter;
import dev.langchain4j.data.document.splitter.HierarchicalDocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DocumentChunkingService {

    private static final int MAX_CHARS_PER_CHUNK = 14_000;
    private static final int OVERLAP_CHARS = 800;

    private final HierarchicalDocumentSplitter splitter;

    public DocumentChunkingService() {
        splitter = new DocumentByCharacterSplitter(
            MAX_CHARS_PER_CHUNK,
            OVERLAP_CHARS
        );
    }

    public List<DocumentChunk> chunkDocument(
        List<Section> sections,
        List<Clause> clauses
    ) {
        String fullText = clauses.stream()
                                 .map(Clause::getText)
                                 .collect(Collectors.joining("\n\n"));

        if (fullText.isBlank()) {
            return List.of();
        }

        String contextHeader = buildContextHeader(sections);
        List<TextSegment> segments = splitter.split(Document.from(fullText));
        List<DocumentChunk> chunks = new ArrayList<>();

        for (int i = 0; i < segments.size(); i++) {
            String segment = segments.get(i).text();

            chunks.add(
                DocumentChunk.builder()
                             .chunkIndex(i)
                             .sections(sections)
                             .rawText(segment)
                             .tokenEstimate(estimateTokens(segment))
                             .contextHeader(contextHeader)
                             .build()
            );
        }

        log.info(
            "event=chunking.complete component=DocumentChunkingService chunkCount={} totalChars={}",
            chunks.size(),
            fullText.length()
        );

        return chunks;
    }

    private String buildContextHeader(List<Section> sections) {
        if (sections.isEmpty()) {
            return "# Document Context\n---\n";
        }

        String title = sections.stream()
                               .filter(s -> s.getLevel() == 0)
                               .map(Section::getTitle)
                               .findFirst()
                               .orElse(sections.getFirst().getTitle());

        return String.format("# Document Context%nTitle: %s%n---%n", title);
    }

    private int estimateTokens(String text) {
        return (int) Math.ceil(text.length() / 4.0);
    }
}
