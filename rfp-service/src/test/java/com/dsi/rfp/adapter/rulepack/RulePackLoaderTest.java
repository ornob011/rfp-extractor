package com.dsi.rfp.adapter.rulepack;

import com.dsi.rfp.domain.exception.RulePackLoadException;
import com.dsi.rfp.domain.model.RulePackDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RulePackLoaderTest {

    private static final String VALID_YAML = """
        pack_id: bd-govt-test-v1
        pack_version: "1.0.0"
        rfp_type: ICT
        rules:
            - id: BD-ICT-001
              name: Test Rule
              pack: bd-govt-test-v1
              version: "1.0.0"
              severity: FATAL
              check_type: structural
              condition: "entities.clientName != null"
              evidence_path: "entities.clientName"
              message: "Client name is missing"
        """;

    private static final String VALID_YAML_SECOND = """
        pack_id: bd-govt-another-v1
        pack_version: "1.0.0"
        rfp_type: GOODS
        rules:
            - id: BD-G-001
              name: Second Rule
              pack: bd-govt-another-v1
              version: "1.0.0"
              severity: HIGH
              check_type: structural
              condition: "entities.scopeOfWork != null"
              evidence_path: "entities.scopeOfWork"
              message: "Scope is missing"
        """;

    private static final String INVALID_YAML = """
        pack_id: bd-govt-bad-v1
        pack_version: "1.0.0"
        rfp_type: ICT
        rules:
            - name: Missing ID
              pack: bd-govt-bad-v1
              version: "1.0.0"
              severity: FATAL
              check_type: structural
              condition: "entities.x != null"
              evidence_path: "entities.x"
              message: "Missing field"
        """;

    @Mock
    private RulePackConfig config;

    @Mock
    private ResourcePatternResolver resolver;

    @Test
    void shouldLoadAllPacksWhenValidYamlFilesExist() throws IOException {
        stubConfig();
        when(resolver.getResources("classpath:rules/*.yaml"))
            .thenReturn(new Resource[]{yamlResource("test.yaml", VALID_YAML)});

        RulePackLoader loader = new RulePackLoader(
            config,
            resolver
        );
        loader.init();

        Map<String, RulePackDefinition> packs = loader.loadAll();

        assertThat(packs).containsKey("bd-govt-test-v1");
        assertThat(packs.get("bd-govt-test-v1").getRules()).hasSize(1);
    }

    @Test
    void shouldReturnPackByIdWhenLoadCalled() throws IOException {
        stubConfig();
        when(resolver.getResources("classpath:rules/*.yaml"))
            .thenReturn(new Resource[]{yamlResource("test.yaml", VALID_YAML)});

        RulePackLoader loader = new RulePackLoader(
            config,
            resolver
        );
        loader.init();

        Optional<RulePackDefinition> pack = loader.load("bd-govt-test-v1");

        assertThat(pack).isPresent();
        assertThat(pack.get().getPackId()).isEqualTo("bd-govt-test-v1");
    }

    @Test
    void shouldThrowWhenYamlViolatesSchema() throws IOException {
        stubConfig();
        when(resolver.getResources("classpath:rules/*.yaml"))
            .thenReturn(new Resource[]{yamlResource("bad.yaml", INVALID_YAML)});

        RulePackLoader loader = new RulePackLoader(
            config,
            resolver
        );

        assertThatThrownBy(loader::init)
            .isInstanceOf(RulePackLoadException.class)
            .hasMessageContaining("Validation errors");
    }

    @Test
    void shouldKeepExistingCacheWhenReloadAllFails() throws IOException {
        stubConfig();
        when(resolver.getResources("classpath:rules/*.yaml"))
            .thenReturn(new Resource[]{yamlResource("test.yaml", VALID_YAML)})
            .thenReturn(new Resource[]{
                yamlResource("test.yaml", VALID_YAML),
                yamlResource("bad.yaml", INVALID_YAML)
            });

        RulePackLoader loader = new RulePackLoader(
            config,
            resolver
        );
        loader.loadAll();

        assertThatThrownBy(loader::reloadAll)
            .isInstanceOf(RulePackLoadException.class);

        assertThat(loader.listPacks())
            .extracting(RulePackDefinition::getPackId)
            .containsExactly("bd-govt-test-v1");
    }

    @Test
    void shouldListPacksInDeterministicPackIdOrder() throws IOException {
        stubConfig();
        when(resolver.getResources("classpath:rules/*.yaml"))
            .thenReturn(new Resource[]{
                yamlResource("z-second.yaml", VALID_YAML_SECOND),
                yamlResource("a-first.yaml", VALID_YAML)
            });

        RulePackLoader loader = new RulePackLoader(
            config,
            resolver
        );

        List<RulePackDefinition> packs = loader.loadAll()
                                               .values()
                                               .stream()
                                               .toList();

        assertThat(packs)
            .extracting(RulePackDefinition::getPackId)
            .containsExactly("bd-govt-another-v1", "bd-govt-test-v1");
        assertThat(loader.reloadAll())
            .containsExactly("bd-govt-another-v1", "bd-govt-test-v1");
    }

    @Test
    void shouldThrowWhenDuplicatePackIdsExist() throws IOException {
        stubConfig();
        when(resolver.getResources("classpath:rules/*.yaml"))
            .thenReturn(new Resource[]{
                yamlResource("first.yaml", VALID_YAML),
                yamlResource("second.yaml", VALID_YAML)
            });

        RulePackLoader loader = new RulePackLoader(
            config,
            resolver
        );

        assertThatThrownBy(loader::loadAll)
            .isInstanceOf(RulePackLoadException.class)
            .hasMessageContaining("Duplicate rule pack id detected");
    }

    private void stubConfig() {
        when(config.rulesPattern()).thenReturn("classpath:rules/*.yaml");
        when(config.schemaResource()).thenReturn(new ClassPathResource("schema/rule-pack-schema-v1.json"));
    }

    private Resource yamlResource(
        String filename,
        String content
    ) {
        return new ByteArrayResource(content.getBytes()) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }
}
