package com.dsi.rfp.adapter.artifact;

import com.dsi.rfp.domain.exception.SystemIoException;
import com.dsi.rfp.domain.model.RfpDocument;
import com.dsi.rfp.domain.model.RiskItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class RiskLogXlsxWriter {

    private static final List<RiskLogColumn> COLUMNS = List.of(
        RiskLogColumn.INDEX,
        RiskLogColumn.RISK_ASSUMPTION,
        RiskLogColumn.SOURCE,
        RiskLogColumn.IMPACT,
        RiskLogColumn.MITIGATION_SUGGESTION,
        RiskLogColumn.OWNER
    );

    private final RiskMitigationEnricher riskMitigationEnricher;
    private final ArtifactGenerationConfig config;

    public byte[] write(
        List<RiskItem> items,
        RfpDocument document
    ) {
        List<RiskItem> enrichedItems = riskMitigationEnricher.enrich(
            items,
            document
        );

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet(
                config.riskLogSheetName()
            );

            addHeaderRow(
                workbook,
                sheet
            );
            IntStream.range(
                         0,
                         enrichedItems.size()
                     )
                     .forEach(index -> addDataRow(
                         sheet,
                         index + 1,
                         enrichedItems.get(index)
                     ));
            autoSizeColumns(sheet);

            log.debug("Risk Log XLSX: {} rows", enrichedItems.size());
            return toBytes(workbook);
        } catch (IOException exception) {
            throw new SystemIoException(
                "Failed to generate risk log XLSX",
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

        IntStream.range(
                     0,
                     COLUMNS.size()
                 )
                 .forEach(index -> {
                     XSSFCell cell = headerRow.createCell(index);
                     cell.setCellValue(COLUMNS.get(index).header());
                     cell.setCellStyle(headerStyle);
                 });
    }

    private void addDataRow(
        XSSFSheet sheet,
        int rowIndex,
        RiskItem item
    ) {
        XSSFRow row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(rowIndex);
        row.createCell(1).setCellValue(item.getRiskDescription());
        row.createCell(2).setCellValue(resolveSource(item));
        row.createCell(3).setCellValue(item.getImpact().name());
        row.createCell(4).setCellValue(resolveMitigation(item));
        row.createCell(5).setCellValue(resolveOwner(item));
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

    private String resolveSource(RiskItem item) {
        return Optional.ofNullable(item.getSource())
                       .orElse(config.riskLogEmptySourceLabel());
    }

    private String resolveMitigation(RiskItem item) {
        return Optional.ofNullable(item.getMitigationSuggestion())
                       .orElse(config.riskLogEmptyMitigationLabel());
    }

    private String resolveOwner(RiskItem item) {
        return Optional.ofNullable(item.getOwner())
                       .orElse(config.defaultRiskOwner());
    }
}
