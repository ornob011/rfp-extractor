package com.dsi.rfp.adapter.extraction.parser;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.SneakyThrows;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
public class FontSizeHeadingLexicon {

    private static final String LEXICON_PATH = "lexicon/font-size-levels-v1.yml";

    private final LexiconDocument document;

    public FontSizeHeadingLexicon() {
        document = loadDocument();
    }

    public float defaultMedianFontSize() {
        return document.defaultMedianFontSize();
    }

    public float headingFontDelta() {
        return document.headingFontDelta();
    }

    public int maxHeadingLength() {
        return document.maxHeadingLength();
    }

    public List<FontSizeRule> levels() {
        return document.levels();
    }

    @SneakyThrows
    private LexiconDocument loadDocument() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        try (InputStream input = new ClassPathResource(LEXICON_PATH).getInputStream()) {
            return mapper.readValue(input, LexiconDocument.class);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LexiconDocument(
        float defaultMedianFontSize,
        float headingFontDelta,
        int maxHeadingLength,
        List<FontSizeRule> levels
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FontSizeRule(
        Float minExclusive,
        Float maxInclusive,
        int level
    ) {
    }
}
