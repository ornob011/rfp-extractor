package com.dsi.rfp.adapter.rulepack;

import com.dsi.rfp.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RulePackGoodsTest extends RulePackFixtureSupport {

    private static final String PACK_ID = "bd-govt-goods-v1";

    @ParameterizedTest
    @ValueSource(strings = {
        "BD-G-001", "BD-G-002", "BD-G-003", "BD-G-004", "BD-G-005", "BD-G-006", "BD-G-007", "BD-G-008",
        "BD-G-009", "BD-G-010", "BD-G-011", "BD-G-012", "BD-G-013", "BD-G-014", "BD-G-015", "BD-G-016",
        "BD-G-017", "BD-G-018", "BD-G-019", "BD-G-020", "BD-G-021", "BD-G-022"
    })
    void shouldContainDeclaredGoodsRule(String ruleId) {
        assertRuleExists(
            loadPack(PACK_ID),
            ruleId
        );
    }

    @Test
    void shouldLoadGoodsPackWithTwentyTwoRules() {
        RulePackDefinition pack = loadPack(PACK_ID);

        assertThat(pack.getRules()).hasSize(22);
    }

    @Test
    void shouldFailPaymentTermsRuleWhenPaymentTermsAreMissing() throws Exception {
        RulePackDefinition pack = loadPack(PACK_ID);
        RfpDocument document = RfpDocument.builder()
                                          .sections(List.of(section("Goods Procurement")))
                                          .entities(RfpEntities.builder()
                                                               .scopeOfWork("Supply and delivery of laptops")
                                                               .build())
                                          .build();

        assertThat(
            findingById(
                runPack(pack, document),
                "BD-G-006"
            ).getStatus()
        ).isEqualTo(RuleStatus.FAIL);
    }

    @Test
    void shouldPassWarrantyRuleWhenWarrantyPeriodExists() throws Exception {
        RulePackDefinition pack = loadPack(PACK_ID);
        RfpDocument document = RfpDocument.builder()
                                          .sections(List.of(section("Supply of Equipment")))
                                          .entities(RfpEntities.builder()
                                                               .warrantyPeriod("24 months")
                                                               .build())
                                          .build();

        assertThat(
            findingById(
                runPack(pack, document),
                "BD-G-008"
            ).getStatus()
        ).isEqualTo(RuleStatus.PASS);
    }

    private Section section(String title) {
        return Section.builder()
                      .title(title)
                      .build();
    }
}
