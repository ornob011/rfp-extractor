package com.dsi.rfp.adapter.rulepack;

import com.dsi.rfp.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RulePackWorksTest extends RulePackFixtureSupport {

    private static final String PACK_ID = "bd-govt-works-v1";

    @ParameterizedTest
    @ValueSource(strings = {
        "BD-W-001", "BD-W-002", "BD-W-003", "BD-W-004", "BD-W-005", "BD-W-006", "BD-W-007", "BD-W-008",
        "BD-W-009", "BD-W-010", "BD-W-011", "BD-W-012", "BD-W-013", "BD-W-014", "BD-W-015", "BD-W-016",
        "BD-W-017", "BD-W-018", "BD-W-019", "BD-W-020", "BD-W-021", "BD-W-022", "BD-W-023", "BD-W-024",
        "BD-W-025", "BD-W-026", "BD-W-027", "BD-W-028", "BD-W-029", "BD-W-030", "BD-W-031", "BD-W-032",
        "BD-W-033"
    })
    void shouldContainDeclaredWorksRule(String ruleId) {
        assertRuleExists(
            loadPack(PACK_ID),
            ruleId
        );
    }

    @Test
    void shouldLoadWorksPackWithThirtyThreeRules() {
        RulePackDefinition pack = loadPack(PACK_ID);

        assertThat(pack.getRules()).hasSize(33);
    }

    @Test
    void shouldFailTitleRuleWhenSectionTitleIsMissing() throws Exception {
        RulePackDefinition pack = loadPack(PACK_ID);
        RfpDocument document = RfpDocument.builder()
                                          .sections(List.of())
                                          .entities(RfpEntities.builder().build())
                                          .build();

        assertThat(
            findingById(
                runPack(pack, document),
                "BD-W-001"
            ).getStatus()
        ).isEqualTo(RuleStatus.FAIL);
    }

    @Test
    void shouldPassScopeRuleWhenScopeOfWorkExists() throws Exception {
        RulePackDefinition pack = loadPack(PACK_ID);
        RfpDocument document = RfpDocument.builder()
                                          .sections(List.of(section("Works Tender")))
                                          .entities(RfpEntities.builder()
                                                               .scopeOfWork("Construction of a reinforced concrete bridge")
                                                               .build())
                                          .build();

        assertThat(
            findingById(
                runPack(pack, document),
                "BD-W-006"
            ).getStatus()
        ).isEqualTo(RuleStatus.PASS);
    }

    private Section section(String title) {
        return Section.builder()
                      .title(title)
                      .build();
    }
}
