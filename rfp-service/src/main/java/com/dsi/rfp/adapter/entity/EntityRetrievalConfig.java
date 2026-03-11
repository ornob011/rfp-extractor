package com.dsi.rfp.adapter.entity;

import com.dsi.rfp.config.YamlConfigLoader;
import com.dsi.rfp.domain.exception.EntityMetadataContractException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class EntityRetrievalConfig {

    private static final String CONFIG_PATH = "metadata/entity-retrieval-v1.yml";

    private final ConfigDocument config;

    public EntityRetrievalConfig(YamlConfigLoader yamlConfigLoader) {
        config = yamlConfigLoader.load(
            CONFIG_PATH,
            ConfigDocument.class,
            "entity retrieval config"
        );
    }

    public List<PromptKey> extractionOrder() {
        return config.extractionOrder().stream().map(PromptKey::valueOf).toList();
    }

    public DomainConfig domain(PromptKey promptKey) {
        DomainConfig domainConfig = config.domains().get(promptKey.name());

        if (domainConfig != null) {
            return domainConfig;
        }

        throw new EntityMetadataContractException(
            String.format("Missing retrieval config for prompt key: %s", promptKey)
        );
    }

    public record ConfigDocument(
        Integer version,
        List<String> extractionOrder,
        Map<String, DomainConfig> domains
    ) {

        public ConfigDocument {
            if (version == null) {
                throw new EntityMetadataContractException("Entity retrieval config must define version");
            }

            if (extractionOrder == null || extractionOrder.isEmpty()) {
                throw new EntityMetadataContractException("Entity retrieval config must define extractionOrder");
            }

            if (domains == null || domains.isEmpty()) {
                throw new EntityMetadataContractException("Entity retrieval config must define domains");
            }
        }
    }

    public record DomainConfig(
        boolean alwaysRun,
        int topK,
        double minimumScore,
        List<String> queries
    ) {

        public DomainConfig {
            if (queries == null || queries.isEmpty()) {
                throw new EntityMetadataContractException("Entity retrieval domain config must define queries");
            }
        }
    }
}
