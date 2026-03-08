package com.dsi.rfp.adapter.rulepack;

import com.dsi.rfp.domain.exception.EntityMetadataContractException;
import com.dsi.rfp.domain.exception.SystemIoException;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RulePackConfig {

    private static final String CONFIG_PATH = "metadata/rule-pack-v1.yml";

    private final EntitySignalReader entitySignalReader;

    private final RulePackMetadata metadata;
    private final String promptTemplate;

    RulePackConfig(EntitySignalReader entitySignalReader) {
        this.entitySignalReader = entitySignalReader;
        metadata = loadMetadata();
        promptTemplate = loadPromptTemplate();
        validatePackSignals();
    }

    public String rulesPattern() {
        return metadata.resources().rulesPattern();
    }

    public Resource schemaResource() {
        return new ClassPathResource(metadata.resources().schemaResourcePath());
    }

    public String promptTemplate() {
        return promptTemplate;
    }

    public int minimumLeadMargin() {
        return metadata.routing().minimumLeadMargin();
    }

    public boolean runAllWhenNoCandidate() {
        return metadata.routing().runAllWhenNoCandidate();
    }

    public double minimumConfidenceForExclusiveRouting() {
        return metadata.routing().minimumConfidenceForExclusiveRouting();
    }

    public String mergedPackId() {
        return metadata.routing().merge().packId();
    }

    public String mergedPackVersion() {
        return metadata.routing().merge().packVersion();
    }

    public List<RulePackMetadata.PackPolicy> enabledPacks() {
        return metadata.packs()
                       .stream()
                       .filter(RulePackMetadata.PackPolicy::enabled)
                       .toList();
    }

    public Set<String> enabledPackIds() {
        return enabledPacks().stream()
                             .map(RulePackMetadata.PackPolicy::packId)
                             .collect(Collectors.toUnmodifiableSet());
    }

    private RulePackMetadata loadMetadata() {
        YamlPropertiesFactoryBean factoryBean = new YamlPropertiesFactoryBean();
        factoryBean.setResources(new ClassPathResource(CONFIG_PATH));

        Properties properties = java.util.Optional.ofNullable(factoryBean.getObject())
                                                  .orElseThrow(() -> new SystemIoException(
                                                      String.format("Failed to load rule pack config: %s", CONFIG_PATH),
                                                      new IllegalStateException("Missing YAML properties")
                                                  ));

        return bindMetadata(properties);
    }

    private RulePackMetadata bindMetadata(Properties properties) {
        Binder binder = new Binder(new MapConfigurationPropertySource(asMap(properties)));

        return binder.bind(
                         "",
                         Bindable.of(RulePackMetadata.class)
                     )
                     .orElseThrow(() -> new SystemIoException(
                         String.format("Failed to bind rule pack config: %s", CONFIG_PATH),
                         new IllegalStateException("Binder returned empty result")
                     ));
    }

    private String loadPromptTemplate() {
        Resource resource = new ClassPathResource(metadata.resources().promptResourcePath());

        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new SystemIoException(
                String.format(
                    "Failed to load rule judgment prompt resource: %s",
                    resource.getDescription()
                ),
                exception
            );
        }
    }

    private void validatePackSignals() {
        enabledPacks().forEach(this::validatePackPolicy);
    }

    private void validatePackPolicy(RulePackMetadata.PackPolicy packPolicy) {
        packPolicy.signals()
                  .entityPresence()
                  .fields()
                  .forEach(this::validateField);

        packPolicy.signals()
                  .entityValueKeywords()
                  .fields()
                  .keySet()
                  .forEach(this::validateField);
    }

    private void validateField(String field) {
        if (!entitySignalReader.fieldNames().contains(field)) {
            throw new EntityMetadataContractException(
                String.format("Rule pack metadata references unknown entity field: %s", field)
            );
        }
    }

    private Map<String, Object> asMap(Properties properties) {
        return properties.entrySet()
                         .stream()
                         .collect(Collectors.toUnmodifiableMap(
                             entry -> entry.getKey().toString(),
                             Map.Entry::getValue
                         ));
    }
}
