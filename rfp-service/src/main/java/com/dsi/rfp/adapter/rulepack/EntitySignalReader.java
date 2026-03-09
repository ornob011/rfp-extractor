package com.dsi.rfp.adapter.rulepack;

import com.dsi.rfp.domain.model.RfpEntities;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
class EntitySignalReader {

    private static final ObjectMapper JSON_FIELD_MAPPER = JsonMapper.builder()
                                                                    .visibility(
                                                                        PropertyAccessor.ALL,
                                                                        JsonAutoDetect.Visibility.NONE
                                                                    )
                                                                    .visibility(
                                                                        PropertyAccessor.FIELD,
                                                                        JsonAutoDetect.Visibility.ANY
                                                                    )
                                                                    .build();

    Set<String> fieldNames() {
        Iterator<String> fieldNames = JSON_FIELD_MAPPER.valueToTree(new RfpEntities()).fieldNames();

        return StreamSupport.stream(
                                java.util.Spliterators.spliteratorUnknownSize(
                                    fieldNames,
                                    java.util.Spliterator.ORDERED
                                ),
                                false
                            )
                            .collect(Collectors.toUnmodifiableSet());
    }

    Object read(
        RfpEntities entities,
        String field
    ) {
        JsonNode entityNode = Optional.ofNullable(entities)
                                      .<JsonNode>map(JSON_FIELD_MAPPER::valueToTree)
                                      .orElse(null);

        return Optional.ofNullable(entityNode)
                       .map(node -> node.get(field))
                       .filter(node -> !node.isMissingNode() && !node.isNull())
                       .map(this::renderValue)
                       .orElse(null);
    }

    private Object renderValue(JsonNode node) {
        return switch (node.getNodeType()) {
            case STRING -> node.asText();
            case NUMBER -> node.numberValue();
            case BOOLEAN -> node.asBoolean();
            default -> node.toString();
        };
    }
}
