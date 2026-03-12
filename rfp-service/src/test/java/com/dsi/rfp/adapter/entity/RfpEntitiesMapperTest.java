package com.dsi.rfp.adapter.entity;

import com.dsi.rfp.domain.model.RfpEntities;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RfpEntitiesMapperTest {

    private final RfpEntitiesMapper mapper = new RfpEntitiesMapper(new ObjectMapper(), new RfpEntitiesMapperConfigRegistry());

    @Test
    void shouldMapStringFields() {
        Map<String, Object> data = new HashMap<>();
        data.put("client_name", "Test Corporation");
        data.put("submission_deadline", "2025-12-31");
        data.put("database", "PostgreSQL");

        RfpEntities entities = mapper.fromMap(data);

        assertThat(entities.getClientName()).isEqualTo("Test Corporation");
        assertThat(entities.getSubmissionDeadline()).isEqualTo("2025-12-31");
        assertThat(entities.getDatabase()).isEqualTo("PostgreSQL");
    }

    @Test
    void shouldMapBooleanFields() {
        Map<String, Object> data = new HashMap<>();
        data.put("data_migration_required", true);
        data.put("mobile_app_required", "true");

        RfpEntities entities = mapper.fromMap(data);

        assertThat(entities.getDataMigrationRequired()).isTrue();
        assertThat(entities.getMobileAppRequired()).isTrue();
    }

    @Test
    void shouldReturnNullForMissingKeys() {
        RfpEntities entities = mapper.fromMap(Map.of());

        assertThat(entities.getClientName()).isNull();
        assertThat(entities.getDataMigrationRequired()).isNull();
        assertThat(entities.getCriteria()).isNull();
    }

    @Test
    void shouldMapCriteriaAsObject() {
        Map<String, Object> data = new HashMap<>();
        data.put("criteria", Map.of("criterion", "Technical", "weight", 80));

        RfpEntities entities = mapper.fromMap(data);

        assertThat(entities.getCriteria()).isNotNull();
    }

    @Test
    void shouldExtractSourceFieldsIntoFieldSourcesMap() {
        Map<String, Object> data = new HashMap<>();
        data.put("client_name", "Test Corporation");
        data.put("client_name_source", "ITC 12.1 (pdf page 10)");
        data.put("submission_deadline", "2025-12-31");
        data.put("submission_deadline_source", "ITC 33.3(d) (pdf page 31)");

        RfpEntities entities = mapper.fromMap(data);

        assertThat(entities.getFieldSources())
            .containsEntry("clientName", "ITC 12.1 (pdf page 10)")
            .containsEntry("submissionDeadline", "ITC 33.3(d) (pdf page 31)");
    }

    @Test
    void shouldIgnoreSourceKeysWithNullValues() {
        Map<String, Object> data = new HashMap<>();
        data.put("client_name", "Test Corporation");
        data.put("client_name_source", null);

        RfpEntities entities = mapper.fromMap(data);

        assertThat(entities.getFieldSources()).doesNotContainKey("clientName");
    }

    @Test
    void shouldIgnoreUndeclaredSourceSuffixKeys() {
        Map<String, Object> data = new HashMap<>();
        data.put("client_name", "Test Corporation");
        data.put("unknown_field_source", "Should not be mapped");

        RfpEntities entities = mapper.fromMap(data);

        assertThat(entities.getFieldSources()).doesNotContainValue("Should not be mapped");
    }
}
