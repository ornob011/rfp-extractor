package com.dsi.rfp.adapter.table;

import com.dsi.rfp.adapter.ocr.OcrScannedTableDto;
import com.dsi.rfp.domain.model.ExtractionConfidence;
import com.dsi.rfp.domain.model.TableCell;
import com.dsi.rfp.domain.model.TableExtractionResult;
import com.dsi.rfp.domain.model.TableProvenance;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
public class ScannedTableResultMapper {

    private final TableTypeClassifier typeClassifier;
    private final TableExtractionConfig config;

    public TableExtractionResult fromSidecar(
        OcrScannedTableDto table,
        int pageNum
    ) {
        return buildResult(
            table.headers(),
            table.rows(),
            pageNum,
            table.confidence(),
            table.method()
        );
    }

    TableExtractionResult fromLlm(
        ScannedTableResponse response,
        int pageNum,
        double ocrPageConfidence
    ) {
        return buildResult(
            response.headers(),
            response.rows(),
            pageNum,
            ocrPageConfidence * config.scannedConfidenceFactor(),
            config.scannedLlmMethod()
        );
    }

    private TableExtractionResult buildResult(
        List<String> headers,
        List<List<String>> rows,
        int pageNum,
        double confidenceScore,
        String method
    ) {
        return TableExtractionResult.builder()
                                    .pageStart(pageNum)
                                    .pageEnd(pageNum)
                                    .provenance(TableProvenance.SCANNED)
                                    .caption(StringUtils.EMPTY)
                                    .type(typeClassifier.classify(headers, StringUtils.EMPTY))
                                    .headers(headers)
                                    .grid(buildGrid(headers, rows))
                                    .confidence(ExtractionConfidence.builder()
                                                                    .score(confidenceScore)
                                                                    .method(method)
                                                                    .build())
                                    .build();
    }

    private List<List<TableCell>> buildGrid(
        List<String> headers,
        List<List<String>> rows
    ) {
        return IntStream.rangeClosed(0, rows.size())
                        .mapToObj(index -> buildRow(
                            headers,
                            rows,
                            index
                        ))
                        .toList();
    }

    private List<TableCell> buildRow(
        List<String> headers,
        List<List<String>> rows,
        int rowIndex
    ) {
        List<String> values = switch (rowIndex) {
            case 0 -> headers;
            default -> rows.get(rowIndex - 1);
        };

        return IntStream.range(0, values.size())
                        .mapToObj(columnIndex -> TableCell.builder()
                                                          .row(rowIndex)
                                                          .col(columnIndex)
                                                          .value(values.get(columnIndex))
                                                          .rowspan(1)
                                                          .colspan(1)
                                                          .isHeader(rowIndex == 0)
                                                          .build())
                        .toList();
    }
}
