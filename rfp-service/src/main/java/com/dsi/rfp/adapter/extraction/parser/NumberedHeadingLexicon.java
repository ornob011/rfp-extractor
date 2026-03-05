package com.dsi.rfp.adapter.extraction.parser;

import com.dsi.rfp.domain.exception.SystemIoException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

@Component
public class NumberedHeadingLexicon {

    private static final String LEXICON_PATH = "lexicon/numbered-heading-keywords-v1.yml";

    private final Set<String> keywordHeadings;
    private final Set<String> chapterKeywords;

    public NumberedHeadingLexicon() {
        LexiconDocument document = loadDocument();
        keywordHeadings = Set.copyOf(document.keywordHeadings());
        chapterKeywords = Set.copyOf(document.chapterKeywords());
    }

    public Set<String> keywordHeadings() {
        return keywordHeadings;
    }

    public Set<String> chapterKeywords() {
        return chapterKeywords;
    }

    private LexiconDocument loadDocument() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        try (InputStream input = new ClassPathResource(LEXICON_PATH).getInputStream()) {
            return mapper.readValue(input, LexiconDocument.class);
        } catch (IOException exception) {
            throw new SystemIoException(
                String.format("Failed to load lexicon from path: %s", LEXICON_PATH),
                exception
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LexiconDocument(
        List<String> keywordHeadings,
        List<String> chapterKeywords
    ) {
    }
}
