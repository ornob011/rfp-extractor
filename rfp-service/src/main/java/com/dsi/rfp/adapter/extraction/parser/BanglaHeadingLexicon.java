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
public class BanglaHeadingLexicon {

    private static final String LEXICON_PATH = "lexicon/bangla-heading-prefixes-v1.yml";

    private final List<HeadingRule> rules;

    public BanglaHeadingLexicon() {
        this.rules = loadRules();
    }

    public List<HeadingRule> rules() {
        return rules;
    }

    private List<HeadingRule> loadRules() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        try (InputStream input = new ClassPathResource(LEXICON_PATH).getInputStream()) {
            LexiconDocument doc = mapper.readValue(input, LexiconDocument.class);

            return doc.rules();
        } catch (IOException exception) {
            throw new SystemIoException(
                String.format("Failed to load lexicon from path: %s", LEXICON_PATH),
                exception
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LexiconDocument(
        List<HeadingRule> rules
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HeadingRule(
        String prefix,
        int level,
        boolean numberRequired
    ) {
    }
}
