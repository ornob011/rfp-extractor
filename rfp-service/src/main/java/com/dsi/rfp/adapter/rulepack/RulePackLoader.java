package com.dsi.rfp.adapter.rulepack;

import com.dsi.rfp.domain.exception.RulePackLoadException;
import com.dsi.rfp.domain.model.RulePackDefinition;
import com.dsi.rfp.domain.port.out.RulePackManagementPort;
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
import java.util.*;
import java.util.stream.Stream;

@Slf4j
@Component
public class RulePackLoader implements RulePackManagementPort {

    private final RulePackConfig config;
    private final ResourcePatternResolver resourcePatternResolver;
    private final ObjectMapper yamlMapper = Jackson2ObjectMapperBuilder.yaml().build();
    private final JsonSchema rulePackSchema;
    private volatile Map<String, RulePackDefinition> cache = Map.of();

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
        Map<String, RulePackDefinition> loadedPacks = loadPackIndex(scanResources());

        cache = loadedPacks;

        return loadedPacks;
    }

    public Optional<RulePackDefinition> load(String packId) {
        return Optional.ofNullable(cache.get(packId));
    }

    @Override
    public List<RulePackDefinition> listPacks() {
        return List.copyOf(cache.values());
    }

    @Override
    public List<String> reloadAll() {
        return List.copyOf(loadAll().keySet());
    }

    public void reload(String packId) {
        RulePackDefinition reloadedPack = loadPackFromResources(
            packId,
            scanResources()
        );

        Map<String, RulePackDefinition> reloadedCache = new LinkedHashMap<>(cache);
        reloadedCache.put(packId, reloadedPack);
        cache = Collections.unmodifiableMap(reloadedCache);
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

    private Map<String, RulePackDefinition> loadPackIndex(Resource[] resources) {
        Map<String, RulePackDefinition> loadedPacks = new LinkedHashMap<>();

        resourcesToPacks(resources).forEach(pack -> putUniquePack(
            loadedPacks,
            pack
        ));

        return Collections.unmodifiableMap(loadedPacks);
    }

    private List<RulePackDefinition> resourcesToPacks(Resource[] resources) {
        return Stream.of(resources)
                     .map(this::parseAndValidate)
                     .sorted(Comparator.comparing(RulePackDefinition::getPackId))
                     .peek(this::logPackLoaded)
                     .toList();
    }

    private RulePackDefinition loadPackFromResources(
        String packId,
        Resource[] resources
    ) {
        return resourcesToPacks(resources).stream()
                                          .filter(pack -> pack.getPackId().equals(packId))
                                          .findFirst()
                                          .map(this::logPackReloaded)
                                          .orElseThrow(() -> new RulePackLoadException(
                                              String.format("Rule pack not found for reload: %s", packId)
                                          ));
    }

    private void logPackLoaded(RulePackDefinition pack) {
        log.info(
            "Rule pack loaded: packId={}, version={}, ruleCount={}",
            pack.getPackId(),
            pack.getPackVersion(),
            pack.getRules().size()
        );
    }

    private RulePackDefinition logPackReloaded(RulePackDefinition pack) {
        log.info(
            "Rule pack reloaded: packId={}, version={}, ruleCount={}",
            pack.getPackId(),
            pack.getPackVersion(),
            pack.getRules().size()
        );

        return pack;
    }

    private void putUniquePack(
        Map<String, RulePackDefinition> loadedPacks,
        RulePackDefinition pack
    ) {
        if (loadedPacks.containsKey(pack.getPackId())) {
            throw new RulePackLoadException(
                String.format("Duplicate rule pack id detected: %s", pack.getPackId())
            );
        }

        loadedPacks.put(
            pack.getPackId(),
            pack
        );
    }
}
