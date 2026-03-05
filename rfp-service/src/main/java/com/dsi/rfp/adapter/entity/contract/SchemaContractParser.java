package com.dsi.rfp.adapter.entity.contract;

import com.dsi.rfp.domain.exception.EntityMetadataContractException;
import com.dsi.rfp.domain.exception.SystemIoException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SchemaContractParser {

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final SchemaContractParserConfig config;

    public SchemaContractParser(
        ObjectMapper objectMapper,
        ResourceLoader resourceLoader,
        SchemaContractParserConfig config
    ) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.config = config;
    }

    public Map<String, Set<String>> parseEntityFieldKeys(Set<String> includedDomains) {
        JsonNode definitions = definitionsNode(loadSchema());

        return includedDomains.stream()
                              .collect(Collectors.toUnmodifiableMap(
                                  domain -> domain,
                                  domain -> parseDomainFields(definitions, domain)
                              ));
    }

    public Set<String> parseAllEntityDomains() {
        JsonNode definitions = definitionsNode(loadSchema());
        String prefix = config.entitiesPrefix();

        return streamFieldNames(definitions).filter(name -> name.startsWith(prefix))
                                            .map(name -> name.substring(prefix.length()))
                                            .collect(Collectors.toUnmodifiableSet());
    }

    private Set<String> parseDomainFields(
        JsonNode definitions,
        String domain
    ) {
        JsonNode propertiesNode = requiredNode(
            requiredNode(
                definitions,
                String.format("%s%s", config.entitiesPrefix(), domain)
            ),
            config.propertiesNode()
        );

        return streamFieldNames(propertiesNode)
            .collect(Collectors.toUnmodifiableSet());
    }

    private JsonNode loadSchema() {
        Resource resource = resourceLoader.getResource(config.schemaResource());

        try (InputStream input = resource.getInputStream()) {
            return objectMapper.readTree(input);
        } catch (IOException exception) {
            throw new SystemIoException(
                String.format("Failed to load schema resource: %s", config.schemaResource()),
                exception
            );
        }
    }

    private JsonNode definitionsNode(JsonNode root) {
        return requiredNode(root, config.definitionsNode());
    }

    private JsonNode requiredNode(
        JsonNode node,
        String field
    ) {
        JsonNode value = node.get(field);

        if (value != null) {
            return value;
        }

        throw new EntityMetadataContractException(
            String.format("Missing schema node: %s", field)
        );
    }

    private java.util.stream.Stream<String> streamFieldNames(JsonNode node) {
        return java.util.stream.StreamSupport.stream(
            java.util.Spliterators.spliteratorUnknownSize(node.fieldNames(), 0),
            false
        );
    }
}
