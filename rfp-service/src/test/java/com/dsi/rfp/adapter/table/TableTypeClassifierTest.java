package com.dsi.rfp.adapter.table;

import com.dsi.rfp.domain.model.TableType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TableTypeClassifierTest {

    private final TableTypeClassifier classifier = new TableTypeClassifier(
        new TableTypeClassifierConfigRegistry()
    );

    @Test
    void shouldClassifyEvaluationTable() {
        List<String> headers = List.of("Criteria", "Weight", "Score");

        TableType result = classifier.classify(headers, "Evaluation Criteria");

        assertThat(result).isEqualTo(TableType.EVALUATION);
    }

    @Test
    void shouldClassifyPaymentTable() {
        List<String> headers = List.of("Installment", "Amount", "Invoice Date");

        TableType result = classifier.classify(headers, "Payment Schedule");

        assertThat(result).isEqualTo(TableType.PAYMENT);
    }

    @Test
    void shouldClassifyStaffingTable() {
        List<String> headers = List.of("Position", "Qualification", "Experience");

        TableType result = classifier.classify(headers, "Key Personnel");

        assertThat(result).isEqualTo(TableType.STAFFING);
    }

    @Test
    void shouldReturnOtherWhenNoKeywordsMatch() {
        List<String> headers = List.of("Column A", "Column B");

        TableType result = classifier.classify(headers, null);

        assertThat(result).isEqualTo(TableType.OTHER);
    }

    @Test
    void shouldResolveTieUsingConfiguredOrder() {
        List<String> headers = List.of("Staff", "Schedule");

        TableType result = classifier.classify(headers, null);

        assertThat(result).isEqualTo(TableType.STAFFING);
    }
}
