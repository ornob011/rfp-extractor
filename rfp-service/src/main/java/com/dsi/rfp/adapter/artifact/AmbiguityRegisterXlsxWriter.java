package com.dsi.rfp.adapter.artifact;

import com.dsi.rfp.domain.exception.SystemIoException;
import com.dsi.rfp.domain.model.AmbiguityItem;
import com.dsi.rfp.domain.model.RuleSeverity;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
public class AmbiguityRegisterXlsxWriter {

    private static final List<AmbiguityRegisterColumn> COLUMNS = List.of(
        AmbiguityRegisterColumn.ISSUE_ID,
        AmbiguityRegisterColumn.SEVERITY,
        AmbiguityRegisterColumn.CATEGORY,
        AmbiguityRegisterColumn.DESCRIPTION,
        AmbiguityRegisterColumn.SOURCE_CLAUSE,
        AmbiguityRegisterColumn.PAGE,
        AmbiguityRegisterColumn.RECOMMENDED_ACTION
    );

    public byte[] write(List<AmbiguityItem> items) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("Ambiguity Register");

            addHeaderRow(wb, sheet);

            List<AmbiguityItem> sorted = items.stream()
                                              .sorted(Comparator.comparingInt(
                                                  item -> item.getSeverity().ordinal()
                                              ))
                                              .toList();

            for (int i = 0; i < sorted.size(); i++) {
                addDataRow(wb, sheet, i + 1, sorted.get(i));
            }

            autoSizeColumns(sheet);

            log.debug("Ambiguity Register XLSX: {} rows", sorted.size());

            return toBytes(wb);
        } catch (IOException ex) {
            throw new SystemIoException(
                "Failed to generate ambiguity register XLSX",
                ex
            );
        }
    }

    private void addHeaderRow(XSSFWorkbook wb, XSSFSheet sheet) {
        XSSFCellStyle headerStyle = wb.createCellStyle();
        XSSFFont headerFont = wb.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        XSSFRow headerRow = sheet.createRow(0);

        for (int i = 0; i < COLUMNS.size(); i++) {
            XSSFCell cell = headerRow.createCell(i);
            cell.setCellValue(COLUMNS.get(i).header());
            cell.setCellStyle(headerStyle);
        }

        sheet.createFreezePane(0, 1);
    }

    private void addDataRow(
        XSSFWorkbook wb,
        XSSFSheet sheet,
        int rowIndex,
        AmbiguityItem item
    ) {
        XSSFRow row = sheet.createRow(rowIndex);

        row.createCell(0).setCellValue(item.getId());
        row.createCell(1).setCellValue(item.getSeverity().name());
        row.createCell(2).setCellValue(item.getCategory().label());
        row.createCell(3).setCellValue(item.getDescription());
        row.createCell(4).setCellValue(item.getSourceClauseId());
        row.createCell(5).setCellValue(item.getPage());
        row.createCell(6).setCellValue(item.getRecommendedAction());

        applyRowColor(
            row,
            item.getSeverity(),
            wb
        );
    }

    private void applyRowColor(
        XSSFRow row,
        RuleSeverity severity,
        XSSFWorkbook wb
    ) {
        IndexedColors color = severityColor(severity);

        if (color == null) {
            return;
        }

        XSSFCellStyle style = createColorStyle(wb, color);

        for (int i = 0; i < COLUMNS.size(); i++) {
            row.getCell(i).setCellStyle(style);
        }
    }

    private IndexedColors severityColor(RuleSeverity severity) {
        return switch (severity) {
            case FATAL -> IndexedColors.CORAL;
            case HIGH -> IndexedColors.LIGHT_ORANGE;
            case MEDIUM -> IndexedColors.LIGHT_YELLOW;
            case LOW -> IndexedColors.LIGHT_CORNFLOWER_BLUE;
            case INFO -> null;
        };
    }

    private XSSFCellStyle createColorStyle(
        XSSFWorkbook wb,
        IndexedColors color
    ) {
        XSSFCellStyle style = wb.createCellStyle();

        style.setFillForegroundColor(color.getIndex());
        style.setFillPattern(
            org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND
        );

        return style;
    }

    private void autoSizeColumns(XSSFSheet sheet) {
        for (int i = 0; i < COLUMNS.size(); i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private byte[] toBytes(XSSFWorkbook wb) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);

        return out.toByteArray();
    }
}
