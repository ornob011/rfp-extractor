package com.dsi.rfp.adapter.artifact;

import com.dsi.rfp.domain.exception.SystemIoException;
import com.dsi.rfp.domain.model.ComplianceItem;
import com.dsi.rfp.domain.model.RfpDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class ComplianceChecklistXlsxWriter {

    private static final List<ComplianceChecklistColumn> COLUMNS = List.of(
        ComplianceChecklistColumn.SERIAL_NUMBER,
        ComplianceChecklistColumn.TITLE,
        ComplianceChecklistColumn.ANSWER
    );

    private final ComplianceItemProjector complianceItemProjector;
    private final ArtifactGenerationConfig config;

    public byte[] write(RfpDocument document) {
        List<ComplianceItem> items = complianceItemProjector.project(document);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet(
                config.complianceChecklistSheetName()
            );

            addHeaderRow(workbook, sheet);

            IntStream.range(0, items.size())
                     .forEach(index -> addDataRow(
                         sheet,
                         index + 1,
                         items.get(index)
                     ));

            autoSizeColumns(sheet);

            log.debug(
                "Compliance Checklist XLSX: {} rows",
                items.size()
            );

            return toBytes(workbook);
        } catch (IOException exception) {
            throw new SystemIoException(
                "Failed to generate compliance checklist XLSX",
                exception
            );
        }
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

        IntStream.range(0, COLUMNS.size())
                 .forEach(index -> {
                     XSSFCell cell = headerRow.createCell(index);
                     cell.setCellValue(COLUMNS.get(index).header());
                     cell.setCellStyle(headerStyle);
                 });

        sheet.createFreezePane(0, 1);
    }

    private void addDataRow(
        XSSFSheet sheet,
        int rowIndex,
        ComplianceItem item
    ) {
        XSSFRow row = sheet.createRow(rowIndex);

        row.createCell(0).setCellValue(
            String.format("%d.", item.getSerialNumber())
        );
        row.createCell(1).setCellValue(item.getTitle());
        row.createCell(2).setCellValue(item.getAnswer());
    }

    private void autoSizeColumns(XSSFSheet sheet) {
        IntStream.range(0, COLUMNS.size())
                 .forEach(sheet::autoSizeColumn);
    }

    private byte[] toBytes(XSSFWorkbook workbook) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        return output.toByteArray();
    }
}
