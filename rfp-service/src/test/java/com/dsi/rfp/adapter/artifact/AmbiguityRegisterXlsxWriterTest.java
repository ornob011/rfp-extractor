package com.dsi.rfp.adapter.artifact;

import com.dsi.rfp.domain.model.AmbiguityCategory;
import com.dsi.rfp.domain.model.AmbiguityItem;
import com.dsi.rfp.domain.model.RuleSeverity;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AmbiguityRegisterXlsxWriterTest {

    private AmbiguityRegisterXlsxWriter writer;

    @BeforeEach
    void setUp() {
        writer = new AmbiguityRegisterXlsxWriter();
    }

    @Test
    void shouldReturnNonEmptyBytesWhenItemsProvided() {
        List<AmbiguityItem> items = List.of(
            ambiguityItem("1", RuleSeverity.HIGH, AmbiguityCategory.MISSING_FIELD),
            ambiguityItem("2", RuleSeverity.FATAL, AmbiguityCategory.VAGUE_REQUIREMENT)
        );

        byte[] bytes = writer.write(items);

        assertThat(bytes).isNotEmpty();
    }

    @Test
    void shouldSortBySeverityWithFatalFirst() throws IOException {
        List<AmbiguityItem> items = List.of(
            ambiguityItem("1", RuleSeverity.MEDIUM, AmbiguityCategory.MINOR_GAP),
            ambiguityItem("2", RuleSeverity.FATAL, AmbiguityCategory.MISSING_FIELD),
            ambiguityItem("3", RuleSeverity.HIGH, AmbiguityCategory.VAGUE_REQUIREMENT)
        );

        byte[] bytes = writer.write(items);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue())
                .isEqualTo("FATAL");
            assertThat(sheet.getRow(2).getCell(1).getStringCellValue())
                .isEqualTo("HIGH");
            assertThat(sheet.getRow(3).getCell(1).getStringCellValue())
                .isEqualTo("MEDIUM");
        }
    }

    private AmbiguityItem ambiguityItem(
        String id,
        RuleSeverity severity,
        AmbiguityCategory category
    ) {
        return AmbiguityItem.builder()
                            .id(id)
                            .severity(severity)
                            .category(category)
                            .description("Test description")
                            .sourceClauseId("1.1")
                            .page(5)
                            .recommendedAction("Review")
                            .build();
    }
}
