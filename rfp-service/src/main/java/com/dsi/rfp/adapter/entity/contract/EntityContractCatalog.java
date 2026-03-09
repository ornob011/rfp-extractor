package com.dsi.rfp.adapter.entity.contract;

import com.dsi.rfp.adapter.entity.EntityExtractorMetadataRegistry;
import com.dsi.rfp.adapter.entity.PromptKey;
import com.dsi.rfp.adapter.entity.RfpEntitiesMapper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class EntityContractCatalog {

    private final EntityExtractorMetadataRegistry metadataRegistry;
    private final EntityContractScopeConfig scopeConfig;
    private final PromptContractParser promptContractParser;
    private final SchemaContractParser schemaContractParser;
    private final RfpEntitiesMapper entitiesMapper;

    public EntityContractCatalog(
        EntityExtractorMetadataRegistry metadataRegistry,
        EntityContractScopeConfig scopeConfig,
        PromptContractParser promptContractParser,
        SchemaContractParser schemaContractParser,
        RfpEntitiesMapper entitiesMapper
    ) {
        this.metadataRegistry = metadataRegistry;
        this.scopeConfig = scopeConfig;
        this.promptContractParser = promptContractParser;
        this.schemaContractParser = schemaContractParser;
        this.entitiesMapper = entitiesMapper;
    }

    public Set<PromptKey> includedPromptKeys() {
        return scopeConfig.includedPromptKeys();
    }

    public Set<String> includedSchemaDomains() {
        return scopeConfig.includedSchemaDomains();
    }

    public Set<String> excludedSchemaDomains() {
        return scopeConfig.excludedSchemaDomains();
    }

    public Map<PromptKey, Set<String>> metadataRequiredFields() {
        return includedPromptKeys().stream()
                                   .collect(Collectors.toUnmodifiableMap(
                                       key -> key,
                                       key -> Set.copyOf(metadataRegistry.requiredFields(key))
                                   ));
    }

    public Map<PromptKey, Set<String>> promptFieldKeys() {
        return includedPromptKeys().stream()
                                   .collect(Collectors.toUnmodifiableMap(
                                       key -> key,
                                       key -> promptContractParser.parseFieldKeys(metadataRegistry.promptResource(key))
                                   ));
    }

    public Map<String, Set<String>> schemaFieldKeys() {
        return schemaContractParser.parseEntityFieldKeys(includedSchemaDomains());
    }

    public Set<String> allSchemaDomains() {
        return schemaContractParser.parseAllEntityDomains();
    }

    public Map<String, RfpEntitiesMapper.ValueKind> mapperKeys() {
        return entitiesMapper.supportedPayloadKeys();
    }
}
