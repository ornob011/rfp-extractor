package com.dsi.rfp.adapter.entity;

import com.dsi.rfp.adapter.entity.contract.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class EntityContractCompletenessTest {

    private EntityContractCatalog catalog;

    @BeforeEach
    void setUp() {
        EntityExtractorMetadataRegistry metadataRegistry = new EntityExtractorMetadataRegistry();
        EntityContractScopeConfig scopeConfig = new EntityContractScopeConfig();
        PromptContractParser promptParser = new PromptContractParser(
            new PromptContractParserConfig()
        );
        SchemaContractParser schemaParser = new SchemaContractParser(
            new ObjectMapper(),
            new org.springframework.core.io.DefaultResourceLoader(),
            new SchemaContractParserConfig()
        );
        RfpEntitiesMapper mapper = new RfpEntitiesMapper(new ObjectMapper(), new RfpEntitiesMapperConfigRegistry());

        catalog = new EntityContractCatalog(
            metadataRegistry,
            scopeConfig,
            promptParser,
            schemaParser,
            mapper
        );
    }

    @Test
    void shouldCoverAllInScopePromptKeysInMetadata() {
        Set<PromptKey> included = catalog.includedPromptKeys();
        Set<PromptKey> available = new EntityExtractorMetadataRegistry().availablePromptKeys();

        assertThat(available).containsAll(included);
    }

    @Test
    void shouldMatchMetadataFieldsToPromptFieldsPerDomain() {
        Map<PromptKey, Set<String>> metadata = catalog.metadataRequiredFields();
        Map<PromptKey, Set<String>> prompts = catalog.promptFieldKeys();

        metadata.forEach((key, metadataFields) ->
            assertThat(prompts.get(key))
                .as(buildDiffMessage(
                    key.name(),
                    metadataFields,
                    prompts.get(key)
                ))
                .containsExactlyInAnyOrderElementsOf(metadataFields)
        );
    }

    @Test
    void shouldMatchMetadataFieldsToSchemaFieldsPerDomain() {
        Map<PromptKey, Set<String>> metadata = catalog.metadataRequiredFields();
        Map<String, Set<String>> schema = catalog.schemaFieldKeys();

        metadata.forEach((key, metadataFields) ->
            assertThat(metadataFields)
                .as(buildDiffMessage(
                    key.name().toLowerCase(),
                    schema.get(key.name().toLowerCase()),
                    metadataFields
                ))
                .containsExactlyInAnyOrderElementsOf(schema.get(key.name().toLowerCase()))
        );
    }

    @Test
    void shouldMatchMapperKeysToMetadataUnion() {
        Set<String> mapperKeys = catalog.mapperKeys().keySet();
        Set<String> metadataKeys = catalog.metadataRequiredFields().values().stream()
                                          .flatMap(Set::stream)
                                          .collect(Collectors.toUnmodifiableSet());

        assertThat(mapperKeys)
            .as(buildDiffMessage("mapper", metadataKeys, mapperKeys))
            .containsExactlyInAnyOrderElementsOf(metadataKeys);
    }

    @Test
    void shouldDeclareExcludedDomainsFromSchema() {
        Set<String> allSchemaDomains = catalog.allSchemaDomains();
        Set<String> excluded = catalog.excludedSchemaDomains();
        Set<String> included = catalog.includedSchemaDomains();

        assertThat(allSchemaDomains).containsAll(excluded);
        assertThat(included).doesNotContainAnyElementsOf(excluded);
    }

    private String buildDiffMessage(
        String label,
        Set<String> expected,
        Set<String> actual
    ) {
        Set<String> missing = expected.stream()
                                      .filter(field -> !actual.contains(field))
                                      .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        Set<String> unknown = actual.stream()
                                    .filter(field -> !expected.contains(field))
                                    .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        return String.format(
            "Contract mismatch for %s; missing=%s unknown=%s",
            label,
            missing,
            unknown
        );
    }
}
