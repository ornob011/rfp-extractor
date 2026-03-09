package com.dsi.rfp.adapter.artifact;

import com.dsi.rfp.domain.model.*;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ComplianceChecklistXlsxWriterTest {

    private ComplianceChecklistXlsxWriter writer;

    @BeforeEach
    void setUp() {
        ArtifactGenerationConfig config = new ArtifactGenerationConfig();
        writer = new ComplianceChecklistXlsxWriter(
            new ComplianceItemProjector(
                new ClauseReferenceLocator(config)
            ),
            config
        );
    }

    @Test
    void shouldIncludeAllFatalAndHighFindingsAsRows() throws IOException {
        RulePackResults results = RulePackResults.builder()
                                                 .packId("test")
                                                 .findings(List.of(
                                                     finding("R1", RuleSeverity.FATAL, RuleStatus.FAIL),
                                                     finding("R2", RuleSeverity.HIGH, RuleStatus.FAIL),
                                                     finding("R3", RuleSeverity.MEDIUM, RuleStatus.FAIL),
                                                     finding("R4", RuleSeverity.FATAL, RuleStatus.PASS)
                                                 ))
                                                 .build();

        byte[] bytes = writer.write(results, minimalDoc());

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(3);
        }
    }

    @Test
    void shouldLeaveStatusColumnBlank() throws IOException {
        RulePackResults results = RulePackResults.builder()
                                                 .packId("test")
                                                 .findings(List.of(
                                                     finding("R1", RuleSeverity.FATAL, RuleStatus.FAIL)
                                                 ))
                                                 .build();

        byte[] bytes = writer.write(results, minimalDoc());

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = wb.getSheetAt(0);
            String statusValue = sheet.getRow(1).getCell(5).getStringCellValue();
            assertThat(statusValue).isEmpty();
        }
    }

    @Test
    void shouldPopulateSourceClauseAndPageWhenEvidenceMatchesClause() throws IOException {
        RulePackResults results = RulePackResults.builder()
                                                 .packId("test")
                                                 .findings(List.of(
                                                     RuleFinding.builder()
                                                                .ruleId("R1")
                                                                .severity(RuleSeverity.FATAL)
                                                                .status(RuleStatus.FAIL)
                                                                .message("Submission requirement")
                                                                .evidence("Bid security shall be submitted with the proposal")
                                                                .checkedAt(Instant.now())
                                                                .build()
                                                 ))
                                                 .build();

        byte[] bytes = writer.write(results, docWithClause());

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("proc-1:2.1");
            assertThat(sheet.getRow(1).getCell(3).getNumericCellValue()).isEqualTo(4);
        }
    }

    private RuleFinding finding(
        String ruleId,
        RuleSeverity severity,
        RuleStatus status
    ) {
        return RuleFinding.builder()
                          .ruleId(ruleId)
                          .severity(severity)
                          .status(status)
                          .message(String.format("Check %s", ruleId))
                          .checkedAt(Instant.now())
                          .build();
    }

    private RfpDocument minimalDoc() {
        return RfpDocument.builder()
                          .sections(List.of())
                          .entities(RfpEntities.builder().build())
                          .build();
    }

    private RfpDocument docWithClause() {
        return RfpDocument.builder()
                          .sections(List.of())
                          .entities(RfpEntities.builder().build())
                          .clauses(List.of(
                              Clause.builder()
                                    .clauseId("proc-1:2.1")
                                    .text("Bid security shall be submitted with the proposal")
                                    .pageNumber(4)
                                    .build()
                          ))
                          .build();
    }
}
