package com.dsi.rfp.adapter.entity;

import com.dsi.rfp.domain.exception.EntityMetadataContractException;
import com.dsi.rfp.domain.exception.SystemIoException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class EntityExtractorMetadataRegistry {

    private static final String METADATA_PATH = "metadata/entity-extractor-metadata-v1.yml";

    private final Map<PromptKey, ExtractorMetadata> metadataByPromptKey;
    private final Resource systemPromptResource;

    public EntityExtractorMetadataRegistry() {
        metadataByPromptKey = new EnumMap<>(PromptKey.class);

        MetadataDocument document = loadDocument();

        document.extractors().forEach(this::putEntry);
        validatePromptKeyCoverage();
        validatePromptResources();
        systemPromptResource = new ClassPathResource(document.systemPromptResourcePath());
    }

    public Resource promptResource(PromptKey key) {
        String path = metadata(key).promptResourcePath();

        return new ClassPathResource(path);
    }

    public Resource systemPromptResource() {
        return systemPromptResource;
    }

    public List<String> requiredFields(PromptKey key) {
        return List.copyOf(metadata(key).requiredFields());
    }

    public Set<PromptKey> availablePromptKeys() {
        return Set.copyOf(metadataByPromptKey.keySet());
    }

    public Map<PromptKey, List<String>> requiredFieldsByPromptKey() {
        return metadataByPromptKey.entrySet()
                                  .stream()
                                  .collect(Collectors.toUnmodifiableMap(
                                      Map.Entry::getKey,
                                      entry -> List.copyOf(entry.getValue().requiredFields())
                                  ));
    }

    public PromptKey promptKeyForField(String fieldName) {
        return metadataByPromptKey.entrySet()
                                  .stream()
                                  .filter(entry -> entry.getValue().requiredFields().contains(fieldName))
                                  .map(Map.Entry::getKey)
                                  .findFirst()
                                  .orElse(null);
    }

    private ExtractorMetadata metadata(PromptKey key) {
        ExtractorMetadata metadata = metadataByPromptKey.get(key);

        if (metadata == null) {
            throw new SystemIoException(
                String.format("No extractor metadata found for key: %s", key),
                new IllegalStateException("Missing extractor metadata")
            );
        }

        return metadata;
    }

    private void putEntry(
        String rawKey,
        ExtractorMetadata metadata
    ) {
        PromptKey key = parsePromptKey(rawKey);
        metadataByPromptKey.put(key, metadata);
    }

    private PromptKey parsePromptKey(String rawKey) {
        return PromptKey.valueOf(rawKey);
    }

    private void validatePromptKeyCoverage() {
        Set<PromptKey> expected = Set.copyOf(Arrays.asList(PromptKey.values()));
        Set<PromptKey> actual = Set.copyOf(metadataByPromptKey.keySet());

        Set<PromptKey> missing = expected.stream()
                                         .filter(key -> !actual.contains(key))
                                         .collect(Collectors.toUnmodifiableSet());

        if (missing.isEmpty()) {
            return;
        }

        throw new EntityMetadataContractException(
            String.format("Missing metadata entries for prompt keys: %s", missing)
        );
    }

    private void validatePromptResources() {
        metadataByPromptKey.forEach((key, value) ->
            validatePromptResource(key, value.promptResourcePath())
        );
    }

    private void validatePromptResource(
        PromptKey key,
        String path
    ) {
        boolean exists = new ClassPathResource(path).exists();

        if (exists) {
            return;
        }

        throw new EntityMetadataContractException(
            String.format("Prompt resource does not exist for key %s: %s", key, path)
        );
    }

    private MetadataDocument loadDocument() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        try (InputStream input = new ClassPathResource(METADATA_PATH).getInputStream()) {
            return mapper.readValue(input, MetadataDocument.class);
        } catch (IOException exception) {
            throw new SystemIoException(
                String.format("Failed to load extractor metadata: %s", METADATA_PATH),
                exception
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MetadataDocument(
        String systemPromptResourcePath,
        Map<String, ExtractorMetadata> extractors
    ) {

        private MetadataDocument {
            if (systemPromptResourcePath == null) {
                throw new EntityMetadataContractException("Metadata must include 'systemPromptResourcePath'");
            }

            if (extractors == null) {
                throw new EntityMetadataContractException("Metadata must include 'extractors' map");
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ExtractorMetadata(
        String promptResourcePath,
        List<String> requiredFields
    ) {

        private ExtractorMetadata {
            if (promptResourcePath == null) {
                throw new EntityMetadataContractException("Extractor metadata must include promptResourcePath");
            }

            if (requiredFields == null) {
                throw new EntityMetadataContractException("Extractor metadata must include requiredFields");
            }
        }
    }
}
