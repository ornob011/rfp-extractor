package com.dsi.rfp.adapter.artifact;

import com.dsi.rfp.domain.model.ComplianceItem;
import com.dsi.rfp.domain.model.RfpDocument;
import com.dsi.rfp.domain.model.RfpEntities;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class ComplianceItemProjector {

    private final ArtifactGenerationConfig config;

    public List<ComplianceItem> project(RfpDocument document) {
        RfpEntities entities = document.getEntities();
        List<ArtifactGenerationConfig.ChecklistItemDef> items = config.complianceChecklistItems();

        return IntStream.range(0, items.size())
                        .mapToObj(index -> project(
                            index + 1,
                            items.get(index),
                            entities
                        ))
                        .toList();
    }

    private ComplianceItem project(
        int serialNumber,
        ArtifactGenerationConfig.ChecklistItemDef itemDef,
        RfpEntities entities
    ) {
        String answer = resolveEntityValue(
            itemDef.entityField(),
            entities
        );

        return ComplianceItem.builder()
                             .serialNumber(serialNumber)
                             .title(itemDef.title())
                             .answer(answer)
                             .build();
    }

    private String resolveEntityValue(
        String fieldName,
        RfpEntities entities
    ) {
        return Optional.ofNullable(entities)
                       .flatMap(e -> readField(e, fieldName))
                       .orElse(config.complianceEmptyAnswerLabel());
    }

    private Optional<String> readField(
        RfpEntities entities,
        String fieldName
    ) {
        String getterName = String.format(
            "get%s%s",
            fieldName.substring(0, 1).toUpperCase(),
            fieldName.substring(1)
        );

        try {
            Method getter = RfpEntities.class.getMethod(getterName);
            Object value = getter.invoke(entities);

            return Optional.ofNullable(value)
                           .map(this::formatValue);
        } catch (NoSuchMethodException exception) {
            return tryBooleanGetter(entities, fieldName);
        } catch (ReflectiveOperationException exception) {
            log.warn(
                "Failed to read entity field: {}",
                fieldName,
                exception
            );
            return Optional.empty();
        }
    }

    private Optional<String> tryBooleanGetter(
        RfpEntities entities,
        String fieldName
    ) {
        String isGetterName = String.format(
            "is%s%s",
            fieldName.substring(0, 1).toUpperCase(),
            fieldName.substring(1)
        );

        try {
            Method getter = RfpEntities.class.getMethod(isGetterName);
            Object value = getter.invoke(entities);

            return Optional.ofNullable(value)
                           .map(this::formatValue);
        } catch (ReflectiveOperationException exception) {
            log.warn(
                "No getter found for entity field: {}",
                fieldName
            );
            return Optional.empty();
        }
    }

    private String formatValue(Object value) {
        return switch (value) {
            case Boolean b -> b ? "Yes" : "No";
            default -> value.toString();
        };
    }
}
