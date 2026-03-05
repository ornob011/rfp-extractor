package com.dsi.rfp.adapter.entity;

import com.dsi.rfp.domain.model.RfpEntities;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.CaseFormat;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class RfpEntitiesMapper {

    private final ObjectMapper mapper;
    private final Map<String, ValueKind> fieldKinds;

    public RfpEntitiesMapper(
        ObjectMapper objectMapper,
        RfpEntitiesMapperConfigRegistry configRegistry
    ) {
        mapper = objectMapper.copy()
                             .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        fieldKinds = Map.copyOf(configRegistry.fields());
    }

    public RfpEntities fromMap(Map<String, Object> merged) {
        Map<String, Object> normalized = new LinkedHashMap<>();

        fieldKinds.forEach((payloadKey, kind) ->
            normalized.put(
                toTargetProperty(payloadKey),
                normalizeValue(kind, merged.get(payloadKey))
            )
        );

        return mapper.convertValue(normalized, RfpEntities.class);
    }

    public Map<String, ValueKind> supportedPayloadKeys() {
        return fieldKinds;
    }

    private Object normalizeValue(
        ValueKind kind,
        Object value
    ) {
        return switch (kind) {
            case STRING -> normalizeString(value);
            case BOOLEAN -> normalizeBoolean(value);
            case RAW -> value;
        };
    }

    private String toTargetProperty(String payloadKey) {
        return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, payloadKey);
    }

    private Object normalizeString(Object value) {
        return switch (value) {
            case null -> null;
            default -> value.toString();
        };
    }

    private Object normalizeBoolean(Object value) {
        return switch (value) {
            case Boolean b -> b;
            case String s -> Boolean.parseBoolean(s);
            case null, default -> null;
        };
    }

    public enum ValueKind {
        STRING,
        BOOLEAN,
        RAW
    }
}
