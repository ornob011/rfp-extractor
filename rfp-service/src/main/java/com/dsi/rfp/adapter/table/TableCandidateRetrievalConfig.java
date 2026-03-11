package com.dsi.rfp.adapter.table;

import com.dsi.rfp.config.YamlConfigLoader;
import com.dsi.rfp.domain.exception.EntityMetadataContractException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TableCandidateRetrievalConfig {

    private static final String CONFIG_PATH = "metadata/table-candidate-retrieval-v1.yml";

    private final ConfigDocument config;

    public TableCandidateRetrievalConfig(YamlConfigLoader yamlConfigLoader) {
        config = yamlConfigLoader.load(
            CONFIG_PATH,
            ConfigDocument.class,
            "table candidate retrieval config"
        );
    }

    public int topK() {
        return config.topK();
    }

    public double minimumScore() {
        return config.minimumScore();
    }

    public List<String> queries() {
        return config.queries();
    }

    public record ConfigDocument(
        Integer version,
        int topK,
        double minimumScore,
        List<String> queries
    ) {

        public ConfigDocument {
            if (version == null) {
                throw new EntityMetadataContractException("Table candidate retrieval config must define version");
            }

            if (queries == null || queries.isEmpty()) {
                throw new EntityMetadataContractException("Table candidate retrieval config must define queries");
            }
        }
    }
}
