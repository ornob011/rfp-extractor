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

@Component
public class FontHeadingLexicon {

    private static final String LEXICON_PATH = "lexicon/font-heading-markers-v1.yml";

    private final List<String> markers;

    public FontHeadingLexicon() {
        markers = loadMarkers();
    }

    public List<String> markers() {
        return markers;
    }

    private List<String> loadMarkers() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        try (InputStream input = new ClassPathResource(LEXICON_PATH).getInputStream()) {
            LexiconDocument doc = mapper.readValue(input, LexiconDocument.class);
            return doc.markers();
        } catch (IOException exception) {
            throw new SystemIoException(
                String.format("Failed to load lexicon: %s", LEXICON_PATH),
                exception
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LexiconDocument(List<String> markers) {
    }
}
