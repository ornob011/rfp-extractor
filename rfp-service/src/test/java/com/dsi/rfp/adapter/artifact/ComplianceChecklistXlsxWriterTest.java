package com.dsi.rfp.adapter.artifact;

import com.dsi.rfp.domain.model.RfpDocument;
import com.dsi.rfp.domain.model.RfpEntities;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ComplianceChecklistXlsxWriterTest {

    private ComplianceChecklistXlsxWriter writer;

    @BeforeEach
    void setUp() {
        ArtifactGenerationConfig config = new ArtifactGenerationConfig();
        writer = new ComplianceChecklistXlsxWriter(
            new ComplianceItemProjector(config),
            config
        );
    }

    @Test
    void shouldGenerateRowForEachChecklistItem() throws IOException {
        RfpDocument doc = RfpDocument.builder()
                                     .entities(RfpEntities.builder()
                                                          .rfpTitle("Test RFP")
                                                          .clientName("Test Client")
                                                          .build())
                                     .build();

        byte[] bytes = writer.write(doc);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("Checklist");
            assertThat(sheet.getLastRowNum()).isGreaterThanOrEqualTo(42);
        }
    }

    @Test
    void shouldPopulateAnswerFromEntityField() throws IOException {
        RfpDocument doc = RfpDocument.builder()
                                     .entities(RfpEntities.builder()
                                                          .rfpTitle("Design and Development of XYZ")
                                                          .build())
                                     .build();

        byte[] bytes = writer.write(doc);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = wb.getSheetAt(0);
            String title = sheet.getRow(1).getCell(1).getStringCellValue();
            String answer = sheet.getRow(1).getCell(2).getStringCellValue();
            assertThat(title).isEqualTo("RFP Title");
            assertThat(answer).isEqualTo("Design and Development of XYZ");
        }
    }

    @Test
    void shouldLeaveAnswerEmptyWhenEntityFieldIsNull() throws IOException {
        RfpDocument doc = RfpDocument.builder()
                                     .entities(RfpEntities.builder().build())
                                     .build();

        byte[] bytes = writer.write(doc);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = wb.getSheetAt(0);
            String answer = sheet.getRow(1).getCell(2).getStringCellValue();
            assertThat(answer).isEmpty();
        }
    }

    @Test
    void shouldFormatSerialNumberWithDot() throws IOException {
        RfpDocument doc = RfpDocument.builder()
                                     .entities(RfpEntities.builder().build())
                                     .build();

        byte[] bytes = writer.write(doc);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = wb.getSheetAt(0);
            String serial = sheet.getRow(1).getCell(0).getStringCellValue();
            assertThat(serial).isEqualTo("1.");
        }
    }

    @Test
    void shouldHaveFourColumns() throws IOException {
        RfpDocument doc = RfpDocument.builder()
                                     .entities(RfpEntities.builder().build())
                                     .build();

        byte[] bytes = writer.write(doc);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) 4);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Sl.");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("Title");
            assertThat(sheet.getRow(0).getCell(2).getStringCellValue()).isEqualTo("Answer");
            assertThat(sheet.getRow(0).getCell(3).getStringCellValue()).isEqualTo("Source");
        }
    }

    @Test
    void shouldPopulateSourceColumn() throws IOException {
        RfpEntities entities = RfpEntities.builder()
                                          .rfpTitle("Test RFP")
                                          .fieldSources(Map.of(
                                              "rfpTitle", "Cover page (pdf page 1)"
                                          ))
                                          .build();

        RfpDocument doc = RfpDocument.builder()
                                     .entities(entities)
                                     .build();

        byte[] bytes = writer.write(doc);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            XSSFSheet sheet = wb.getSheetAt(0);
            String source = sheet.getRow(1).getCell(3).getStringCellValue();
            assertThat(source).isEqualTo("Cover page (pdf page 1)");
        }
    }
}
