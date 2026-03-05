package com.dsi.rfp.adapter.entity.contract;

import com.dsi.rfp.adapter.entity.PromptKey;
import com.dsi.rfp.domain.exception.EntityMetadataContractException;
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
import java.util.stream.Collectors;

@Component
public class EntityContractScopeConfig {

    private static final String SCOPE_PATH = "metadata/entity-contract-scope-v1.yml";

    private final ScopeDocument scopeDocument;

    public EntityContractScopeConfig() {
        scopeDocument = loadDocument();
    }

    public Set<PromptKey> includedPromptKeys() {
        return scopeDocument.includedPromptKeys()
                            .stream()
                            .map(this::parsePromptKey)
                            .collect(Collectors.toUnmodifiableSet());
    }

    public Set<String> includedSchemaDomains() {
        return includedPromptKeys().stream()
                                   .map(key -> key.name().toLowerCase())
                                   .collect(Collectors.toUnmodifiableSet());
    }

    public Set<String> excludedSchemaDomains() {
        return Set.copyOf(scopeDocument.excludedSchemaDomains());
    }

    private PromptKey parsePromptKey(String value) {
        return PromptKey.valueOf(value);
    }

    private ScopeDocument loadDocument() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        try (InputStream input = new ClassPathResource(SCOPE_PATH).getInputStream()) {
            return mapper.readValue(input, ScopeDocument.class);
        } catch (IOException exception) {
            throw new SystemIoException(
                String.format("Failed to load entity contract scope: %s", SCOPE_PATH),
                exception
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ScopeDocument(
        Integer version,
        List<String> includedPromptKeys,
        List<String> excludedSchemaDomains
    ) {

        private ScopeDocument {
            if (version == null) {
                throw new EntityMetadataContractException("Entity contract scope must define version");
            }

            if (includedPromptKeys == null) {
                throw new EntityMetadataContractException("Entity contract scope must define includedPromptKeys");
            }

            if (excludedSchemaDomains == null) {
                throw new EntityMetadataContractException("Entity contract scope must define excludedSchemaDomains");
            }
        }
    }
}
