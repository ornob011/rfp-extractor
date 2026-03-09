package com.dsi.rfp.adapter.artifact;

import com.dsi.rfp.domain.exception.SystemIoException;
import com.dsi.rfp.domain.model.ComplianceItem;
import com.dsi.rfp.domain.model.RfpDocument;
import com.dsi.rfp.domain.model.RuleFinding;
import com.dsi.rfp.domain.model.RulePackResults;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class ComplianceChecklistXlsxWriter {

    private static final List<ComplianceChecklistColumn> COLUMNS = List.of(
        ComplianceChecklistColumn.INDEX,
        ComplianceChecklistColumn.REQUIREMENT,
        ComplianceChecklistColumn.SOURCE_CLAUSE,
        ComplianceChecklistColumn.PAGE,
        ComplianceChecklistColumn.MANDATORY,
        ComplianceChecklistColumn.COMPLIANCE_STATUS
    );

    private final ComplianceItemProjector complianceItemProjector;
    private final ArtifactGenerationConfig config;

    public byte[] write(
        RulePackResults results,
        RfpDocument document
    ) {
        List<ComplianceRow> rows = projectRows(
            results,
            document
        );

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet(
                config.complianceChecklistSheetName()
            );

            addHeaderRow(
                workbook,
                sheet
            );
            IntStream.range(
                         0,
                         rows.size()
                     )
                     .forEach(index -> addDataRow(
                         workbook,
                         sheet,
                         index + 1,
                         rows.get(index)
                     ));
            autoSizeColumns(sheet);

            log.debug("Compliance Checklist XLSX: {} rows", rows.size());
            return toBytes(workbook);
        } catch (IOException exception) {
            throw new SystemIoException(
                "Failed to generate compliance checklist XLSX",
                exception
            );
        }
    }

    private List<ComplianceRow> projectRows(
        RulePackResults results,
        RfpDocument document
    ) {
        List<ComplianceItem> items = complianceItemProjector.project(
            results,
            document
        );
        Map<String, RuleFinding> findingsById = results.getFindings().stream()
                                                       .collect(java.util.stream.Collectors.toMap(
                                                           RuleFinding::getRuleId,
                                                           finding -> finding
                                                       ));

        return items.stream()
                    .map(item -> new ComplianceRow(
                        item,
                        ComplianceChecklistRowStyle.from(
                            findingsById.get(item.getId()).getStatus()
                        )
                    ))
                    .toList();
    }

    private void addHeaderRow(
        XSSFWorkbook workbook,
        XSSFSheet sheet
    ) {
        XSSFCellStyle headerStyle = workbook.createCellStyle();
        XSSFFont headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        XSSFRow headerRow = sheet.createRow(0);

        IntStream.range(
                     0,
                     COLUMNS.size()
                 )
                 .forEach(index -> {
                     XSSFCell cell = headerRow.createCell(index);
                     cell.setCellValue(COLUMNS.get(index).header());
                     cell.setCellStyle(headerStyle);
                 });

        sheet.createFreezePane(0, 1);
    }

    private void addDataRow(
        XSSFWorkbook workbook,
        XSSFSheet sheet,
        int rowIndex,
        ComplianceRow rowView
    ) {
        ComplianceItem item = rowView.item();
        XSSFRow row = sheet.createRow(rowIndex);

        row.createCell(0).setCellValue(rowIndex);
        row.createCell(1).setCellValue(item.getRequirement());
        row.createCell(2).setCellValue(sourceClause(item));
        row.createCell(3).setCellValue(item.getPage());
        row.createCell(4).setCellValue(mandatoryText(item));
        row.createCell(5).setCellValue(config.complianceEmptyStatusLabel());

        applyRowStyle(
            workbook,
            row,
            rowView.style()
        );
    }

    private void applyRowStyle(
        XSSFWorkbook workbook,
        XSSFRow row,
        ComplianceChecklistRowStyle style
    ) {
        switch (style) {
            case FAIL -> IntStream.range(
                                      0,
                                      COLUMNS.size()
                                  )
                                  .forEach(index -> row.getCell(index).setCellStyle(
                                      failStyle(workbook)
                                  ));
            case DEFAULT -> {
            }
        }
    }

    private XSSFCellStyle failStyle(XSSFWorkbook workbook) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(failTint().getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private IndexedColors failTint() {
        return IndexedColors.valueOf(config.complianceFailTintColor());
    }

    private void autoSizeColumns(XSSFSheet sheet) {
        IntStream.range(
                     0,
                     COLUMNS.size()
                 )
                 .forEach(sheet::autoSizeColumn);
    }

    private byte[] toBytes(XSSFWorkbook workbook) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        return output.toByteArray();
    }

    private String sourceClause(ComplianceItem item) {
        return java.util.Optional.ofNullable(item.getSourceClauseId())
                                 .orElse(config.auditMissingValueLabel());
    }

    private String mandatoryText(ComplianceItem item) {
        return Map.of(
                      Boolean.TRUE,
                      config.complianceMandatoryTrueLabel(),
                      Boolean.FALSE,
                      config.complianceMandatoryFalseLabel()
                  )
                  .get(item.isMandatory());
    }

    private record ComplianceRow(
        ComplianceItem item,
        ComplianceChecklistRowStyle style
    ) {
    }
}
