package com.dsi.rfp.adapter.rulepack;

import com.dsi.rfp.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RulePackConsultancyTest extends RulePackFixtureSupport {

    private static final String PACK_ID = "bd-govt-consultancy-v1";

    @ParameterizedTest
    @ValueSource(strings = {
        "BD-C-001", "BD-C-002", "BD-C-003", "BD-C-004", "BD-C-005", "BD-C-006", "BD-C-007", "BD-C-008",
        "BD-C-009", "BD-C-010", "BD-C-011", "BD-C-012", "BD-C-013", "BD-C-014", "BD-C-015", "BD-C-016",
        "BD-C-017", "BD-C-018", "BD-C-019", "BD-C-020", "BD-C-021", "BD-C-022", "BD-C-023", "BD-C-024",
        "BD-C-025", "BD-C-026", "BD-C-027", "BD-C-028", "BD-C-029", "BD-C-030", "BD-C-031", "BD-C-032",
        "BD-C-033"
    })
    void shouldContainDeclaredConsultancyRule(String ruleId) {
        assertRuleExists(
            loadPack(PACK_ID),
            ruleId
        );
    }

    @Test
    void shouldLoadConsultancyPackWithThirtyThreeRules() {
        RulePackDefinition pack = loadPack(PACK_ID);

        assertThat(pack.getRules()).hasSize(33);
    }

    @Test
    void shouldFailMethodOfSelectionRuleWhenSelectionMethodIsMissing() throws Exception {
        RulePackDefinition pack = loadPack(PACK_ID);
        RfpDocument document = RfpDocument.builder()
                                          .sections(List.of(section("Terms of Reference")))
                                          .entities(RfpEntities.builder()
                                                               .scopeOfWork("Feasibility study and advisory services")
                                                               .build())
                                          .build();

        assertThat(
            findingById(
                runPack(pack, document),
                "BD-C-006"
            ).getStatus()
        ).isEqualTo(RuleStatus.FAIL);
    }

    @Test
    void shouldPassStaffMonthsRuleWhenStaffMonthsExist() throws Exception {
        RulePackDefinition pack = loadPack(PACK_ID);
        RfpDocument document = RfpDocument.builder()
                                          .sections(List.of(section("Consultancy Assignment")))
                                          .entities(RfpEntities.builder()
                                                               .staffMonths("36 person-months")
                                                               .build())
                                          .build();

        assertThat(
            findingById(
                runPack(pack, document),
                "BD-C-006"
            ).getStatus()
        ).isEqualTo(RuleStatus.PASS);
    }

    private Section section(String title) {
        return Section.builder()
                      .title(title)
                      .build();
    }
}
