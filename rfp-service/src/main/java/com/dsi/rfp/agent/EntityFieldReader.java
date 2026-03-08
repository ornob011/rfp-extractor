package com.dsi.rfp.agent;

import com.dsi.rfp.domain.model.RfpEntities;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class EntityFieldReader {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public Map<String, Object> readAllFields(RfpEntities entities) {
        return objectMapper.convertValue(
                               entities,
                               MAP_TYPE
                           ).entrySet()
                           .stream()
                           .collect(LinkedHashMap::new,
                               (map, entry) -> map.put(entry.getKey(), entry.getValue()),
                               Map::putAll
                           );
    }
}
