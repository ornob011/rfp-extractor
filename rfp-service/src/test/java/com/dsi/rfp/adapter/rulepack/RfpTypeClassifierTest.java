package com.dsi.rfp.adapter.rulepack;

import com.dsi.rfp.domain.model.RfpDocument;
import com.dsi.rfp.domain.model.RfpEntities;
import com.dsi.rfp.domain.model.RfpType;
import com.dsi.rfp.domain.model.Section;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RfpTypeClassifierTest {

    private final EntitySignalReader entitySignalReader = new EntitySignalReader();
    private final RulePackConfig config = new RulePackConfig(entitySignalReader);
    private final PackSignalScorer packSignalScorer = new PackSignalScorer(entitySignalReader);
    private final CandidatePackResolver candidatePackResolver = new CandidatePackResolver();
    private final RfpTypeClassifier classifier = new RfpTypeClassifier(
        config,
        packSignalScorer,
        candidatePackResolver
    );

    @Test
    void shouldClassifyAsIctWhenEntitySignalsExist() {
        RfpDocument doc = RfpDocument.builder()
                                     .sections(List.of(section("Project Overview")))
                                     .entities(RfpEntities.builder()
                                                          .scopeOfWork("Integrated digital platform with APIs")
                                                          .database("PostgreSQL")
                                                          .hosting("Government cloud hosting")
                                                          .integrations("SSO and payment gateway integration")
                                                          .build())
                                     .build();

        RulePackClassification result = classifier.classify(doc);

        assertThat(result.resolvedType()).isEqualTo(RfpType.ICT);
        assertThat(result.candidatePackIds()).containsExactly("bd-govt-ict-v1");
        assertThat(result.packScores()).containsEntry("bd-govt-ict-v1", result.packScores().get("bd-govt-ict-v1"));
    }

    @Test
    void shouldReturnUnknownWhenNoSignalsMatch() {
        RfpDocument doc = RfpDocument.builder()
                                     .sections(List.of(section("Introduction")))
                                     .entities(RfpEntities.builder().build())
                                     .build();

        RulePackClassification result = classifier.classify(doc);

        assertThat(result.resolvedType()).isEqualTo(RfpType.UNKNOWN);
        assertThat(result.candidatePackIds()).isEmpty();
        assertThat(result.confidence()).isZero();
    }

    @Test
    void shouldUseTitleAndScopeSignalsWhenEntitySignalsAreWeak() {
        RfpDocument doc = RfpDocument.builder()
                                     .sections(List.of(
                                         section("Software Development"),
                                         section("System Integration")
                                     ))
                                     .entities(RfpEntities.builder()
                                                          .scopeSummary("Digital platform and database integration")
                                                          .build())
                                     .build();

        RulePackClassification result = classifier.classify(doc);

        assertThat(result.resolvedType()).isEqualTo(RfpType.ICT);
        assertThat(result.candidatePackIds()).contains("bd-govt-ict-v1");
        assertThat(result.confidence()).isGreaterThan(0.0d);
    }

    @Test
    void shouldClassifyAsWorksWhenConstructionSignalsExist() {
        RfpDocument doc = RfpDocument.builder()
                                     .sections(List.of(
                                         section("Civil Works"),
                                         section("Bill of Quantities")
                                     ))
                                     .entities(RfpEntities.builder()
                                                          .scopeOfWork("Construction of bridge and road infrastructure")
                                                          .scopeSummary("Construction of road and bridge site foundation")
                                                          .build())
                                     .build();

        RulePackClassification result = classifier.classify(doc);

        assertThat(result.resolvedType()).isEqualTo(RfpType.WORKS);
        assertThat(result.candidatePackIds()).contains("bd-govt-works-v1");
    }

    @Test
    void shouldClassifyAsConsultancyWhenAdvisorySignalsExist() {
        RfpDocument doc = RfpDocument.builder()
                                     .sections(List.of(
                                         section("Terms of Reference"),
                                         section("Consulting Methodology")
                                     ))
                                     .entities(RfpEntities.builder()
                                                          .scopeOfWork("Feasibility study and advisory technical assistance")
                                                          .scopeSummary("Consultant advisory feasibility study methodology")
                                                          .staffMonths("36 person-months")
                                                          .methodOfSelection("QCBS")
                                                          .cvRequirements("International format CV required")
                                                          .build())
                                     .build();

        RulePackClassification result = classifier.classify(doc);

        assertThat(result.resolvedType()).isEqualTo(RfpType.CONSULTANCY);
        assertThat(result.candidatePackIds()).contains("bd-govt-consultancy-v1");
    }

    @Test
    void shouldClassifyAsGoodsWhenSupplySignalsExist() {
        RfpDocument doc = RfpDocument.builder()
                                     .sections(List.of(
                                         section("Procurement of Goods"),
                                         section("Supply of Equipment")
                                     ))
                                     .entities(RfpEntities.builder()
                                                          .scopeOfWork("Supply and delivery of goods equipment purchase")
                                                          .scopeSummary("Supply goods equipment delivery procurement purchase")
                                                          .build())
                                     .build();

        RulePackClassification result = classifier.classify(doc);

        assertThat(result.resolvedType()).isEqualTo(RfpType.GOODS);
        assertThat(result.candidatePackIds()).contains("bd-govt-goods-v1");
    }

    private Section section(String title) {
        return Section.builder()
                      .title(title)
                      .build();
    }
}
