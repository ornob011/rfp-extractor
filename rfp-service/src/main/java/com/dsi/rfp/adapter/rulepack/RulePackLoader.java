package com.dsi.rfp.adapter.rulepack;

import com.dsi.rfp.domain.exception.RulePackLoadException;
import com.dsi.rfp.domain.model.RulePackDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RulePackLoader {

    private final RulePackConfig config;
    private final ResourcePatternResolver resourcePatternResolver;
    private final ObjectMapper yamlMapper = Jackson2ObjectMapperBuilder.yaml().build();
    private final ConcurrentHashMap<String, RulePackDefinition> cache = new ConcurrentHashMap<>();
    private final JsonSchema rulePackSchema;

    public RulePackLoader(
        RulePackConfig config,
        ResourcePatternResolver resourcePatternResolver
    ) {
        this.config = config;
        this.resourcePatternResolver = resourcePatternResolver;
        rulePackSchema = loadSchema();
    }

    @PostConstruct
    void init() {
        loadAll();
    }

    public Map<String, RulePackDefinition> loadAll() {
        Resource[] resources = scanResources();

        cache.clear();

        for (Resource resource : resources) {
            loadResource(resource);
        }

        return Collections.unmodifiableMap(cache);
    }

    public Optional<RulePackDefinition> load(String packId) {
        return Optional.ofNullable(cache.get(packId));
    }

    public List<RulePackDefinition> listPacks() {
        return List.copyOf(cache.values());
    }

    public void reload(String packId) {
        for (Resource resource : scanResources()) {
            RulePackDefinition pack = parseAndValidate(resource);

            if (pack.getPackId().equals(packId)) {
                cache.put(packId, pack);
                log.info(
                    "Rule pack reloaded: packId={}, version={}, ruleCount={}",
                    pack.getPackId(),
                    pack.getPackVersion(),
                    pack.getRules().size()
                );
                return;
            }
        }

        throw new RulePackLoadException(
            String.format("Rule pack not found for reload: %s", packId)
        );
    }

    private void loadResource(Resource resource) {
        RulePackDefinition pack = parseAndValidate(resource);
        cache.put(pack.getPackId(), pack);

        log.info(
            "Rule pack loaded: packId={}, version={}, ruleCount={}",
            pack.getPackId(),
            pack.getPackVersion(),
            pack.getRules().size()
        );
    }

    private RulePackDefinition parseAndValidate(Resource resource) {
        try (InputStream is = resource.getInputStream()) {
            JsonNode yamlTree = yamlMapper.readTree(is);
            validatePack(yamlTree, resource.getFilename());

            RulePackDefinition pack = yamlMapper.treeToValue(yamlTree, RulePackDefinition.class);
            pack.setLoadedAt(Instant.now());
            return pack;
        } catch (IOException exception) {
            throw new RulePackLoadException(
                String.format("Failed to parse rule pack: %s", resource.getFilename()),
                exception
            );
        }
    }

    private void validatePack(
        JsonNode yamlTree,
        String filename
    ) {
        List<String> errors = rulePackSchema.validate(yamlTree)
                                            .stream()
                                            .map(ValidationMessage::getMessage)
                                            .toList();

        if (!errors.isEmpty()) {
            log.warn(
                "Rule pack validation failed: file={}, errors={}",
                filename,
                errors
            );
            throw new RulePackLoadException(
                String.format("Validation errors in %s: %s", filename, errors)
            );
        }
    }

    private Resource[] scanResources() {
        try {
            return resourcePatternResolver.getResources(config.rulesPattern());
        } catch (IOException exception) {
            throw new RulePackLoadException("Failed to scan rules directory", exception);
        }
    }

    private JsonSchema loadSchema() {
        Resource schemaResource = config.schemaResource();

        try (InputStream input = schemaResource.getInputStream()) {
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
            return factory.getSchema(input);
        } catch (IOException exception) {
            throw new RulePackLoadException("Failed to load rule schema", exception);
        }
    }
}
