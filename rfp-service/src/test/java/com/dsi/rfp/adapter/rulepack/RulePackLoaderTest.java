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
