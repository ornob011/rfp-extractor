package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.domain.exception.EntityMetadataContractException;
import com.dsi.rfp.domain.exception.SystemIoException;
import com.dsi.rfp.domain.model.ConfidenceSource;
import com.dsi.rfp.domain.model.RepairComponentType;
import com.dsi.rfp.domain.model.RepairStrategy;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

@Component
public class RepairPolicyConfig {

    private static final String CONFIG_PATH = "metadata/repair-policy-v1.yml";

    private final ConfigDocument config;

    public RepairPolicyConfig() {
        config = loadConfig();
    }

    public RepairStrategy strategyFor(
        RepairComponentType componentType,
        ConfidenceSource confidenceSource
    ) {
        StrategyPolicy policy = Optional.ofNullable(config.strategies().get(componentType))
                                        .orElseThrow(() -> new EntityMetadataContractException(
                                            String.format(
                                                "Repair policy is missing component type: %s",
                                                componentType
                                            )
                                        ));

        return Optional.ofNullable(policy.byConfidenceSource())
                       .map(sourceMap -> sourceMap.get(confidenceSource))
                       .orElse(policy.defaultStrategy());
    }

    public int scannedTableHigherDpi() {
        return config.scannedTable().higherDpi();
    }

    private ConfigDocument loadConfig() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        try (InputStream input = new ClassPathResource(CONFIG_PATH).getInputStream()) {
            return mapper.readValue(
                input,
                ConfigDocument.class
            );
        } catch (IOException exception) {
            throw new SystemIoException(
                String.format(
                    "Failed to load repair policy config: %s",
                    CONFIG_PATH
                ),
                exception
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ConfigDocument(
        Integer version,
        Map<RepairComponentType, StrategyPolicy> strategies,
        ScannedTablePolicy scannedTable
    ) {

        private ConfigDocument {
            if (version == null) {
                throw new EntityMetadataContractException("Repair policy config must define version");
            }

            if (strategies == null || strategies.isEmpty()) {
                throw new EntityMetadataContractException("Repair policy config must define strategies");
            }

            if (scannedTable == null) {
                throw new EntityMetadataContractException("Repair policy config must define scannedTable");
            }
        }
    }

    private record StrategyPolicy(
        RepairStrategy defaultStrategy,
        Map<ConfidenceSource, RepairStrategy> byConfidenceSource
    ) {

        private StrategyPolicy {
            if (defaultStrategy == null) {
                throw new EntityMetadataContractException("Repair strategy policy must define defaultStrategy");
            }
        }
    }

    private record ScannedTablePolicy(
        Integer higherDpi
    ) {

        private ScannedTablePolicy {
            if (higherDpi == null) {
                throw new EntityMetadataContractException("Repair policy scannedTable section must define higherDpi");
            }
        }
    }
}
