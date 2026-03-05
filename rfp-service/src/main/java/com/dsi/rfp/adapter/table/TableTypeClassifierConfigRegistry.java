package com.dsi.rfp.adapter.table;

import com.dsi.rfp.domain.exception.EntityMetadataContractException;
import com.dsi.rfp.domain.exception.SystemIoException;
import com.dsi.rfp.domain.model.TableType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class TableTypeClassifierConfigRegistry {

    private static final String CONFIG_PATH = "metadata/table-type-classifier-v1.yml";

    private final Map<TableType, Map<String, Double>> weightedKeywords;
    private final List<TableType> tieBreakOrder;

    public TableTypeClassifierConfigRegistry() {
        ConfigDocument config = loadConfig();
        weightedKeywords = toWeightedKeywords(config.keywords());
        tieBreakOrder = toTieBreakOrder(config.tieBreakOrder());
    }

    public Map<TableType, Map<String, Double>> weightedKeywords() {
        return weightedKeywords;
    }

    public List<TableType> tieBreakOrder() {
        return tieBreakOrder;
    }

    private Map<TableType, Map<String, Double>> toWeightedKeywords(
        Map<String, Map<String, Double>> source
    ) {
        EnumMap<TableType, Map<String, Double>> result = new EnumMap<>(TableType.class);

        source.forEach((name, weights) ->
            result.put(
                parseTableType(name),
                Map.copyOf(weights)
            )
        );

        return Map.copyOf(result);
    }

    private List<TableType> toTieBreakOrder(
        List<String> source
    ) {
        return source.stream()
                     .map(this::parseTableType)
                     .toList();
    }

    private TableType parseTableType(String name) {
        return Arrays.stream(TableType.values())
                     .filter(type -> type.name().equalsIgnoreCase(name))
                     .findFirst()
                     .orElseThrow(() -> new EntityMetadataContractException(
                         String.format("Unknown table type in classifier config: %s", name)
                     ));
    }

    private ConfigDocument loadConfig() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        try (InputStream input = new ClassPathResource(CONFIG_PATH).getInputStream()) {
            return mapper.readValue(input, ConfigDocument.class);
        } catch (IOException exception) {
            throw new SystemIoException(
                String.format("Failed to load table type classifier config: %s", CONFIG_PATH),
                exception
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ConfigDocument(
        Integer version,
        List<String> tieBreakOrder,
        Map<String, Map<String, Double>> keywords
    ) {

        private ConfigDocument {
            if (version == null) {
                throw new EntityMetadataContractException("Table type classifier config must define version");
            }

            if (tieBreakOrder == null || tieBreakOrder.isEmpty()) {
                throw new EntityMetadataContractException("Table type classifier config must define tieBreakOrder");
            }

            if (keywords == null || keywords.isEmpty()) {
                throw new EntityMetadataContractException("Table type classifier config must define keywords");
            }

            Set<String> blankKeys = keywords.keySet()
                                            .stream()
                                            .filter(name -> name == null || name.isBlank())
                                            .collect(Collectors.toSet());

            if (!blankKeys.isEmpty()) {
                throw new EntityMetadataContractException("Table type classifier keywords contain blank key");
            }
        }
    }
}

