package com.dsi.rfp.adapter.rulepack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleDslValidationTest {

    private static final ObjectMapper YAML_MAPPER = Jackson2ObjectMapperBuilder.yaml().build();

    private static JsonSchema ruleSchema;

    @BeforeAll
    static void loadSchema() throws IOException {
        try (InputStream input = new ClassPathResource("schema/rule-pack-schema-v1.json").getInputStream()) {
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
            ruleSchema = factory.getSchema(input);
        }
    }

    @Test
    void shouldValidateAllRulesAgainstSchemaWithZeroErrors() throws IOException {
        Resource[] ruleResources = new PathMatchingResourcePatternResolver().getResources("classpath:rules/*.yaml");
        List<String> errors = new ArrayList<>();
        int totalRuleCount = 0;

        for (Resource ruleResource : ruleResources) {
            JsonNode root = readYaml(ruleResource);
            JsonNode rules = root.get("rules");

            assertThat(rules)
                .as("Rules array in %s", ruleResource.getFilename())
                .isNotNull();

            totalRuleCount += rules.size();

            ruleSchema.validate(root)
                      .stream()
                      .map(message -> String.format(
                          "%s %s",
                          ruleResource.getFilename(),
                          message.getMessage()
                      ))
                      .forEach(errors::add);
        }

        assertThat(errors)
            .as("Rule validation errors")
            .isEmpty();

        assertThat(totalRuleCount)
            .as("Total rule count across all packs")
            .isEqualTo(80);
    }

    private JsonNode readYaml(Resource ruleResource) throws IOException {
        try (InputStream input = ruleResource.getInputStream()) {
            return YAML_MAPPER.readTree(input);
        }
    }
}
