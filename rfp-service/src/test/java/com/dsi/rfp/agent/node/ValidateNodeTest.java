package com.dsi.rfp.agent.node;

import com.dsi.rfp.agent.ExtractionState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidateNodeTest {

    private final ValidateNode node = new ValidateNode();

    @Test
    void shouldPassForExistingFile(@TempDir Path tempDir) throws IOException {
        Path file = Files.createFile(tempDir.resolve("test.pdf"));
        Map<String, Object> data = ExtractionState.initial(1L, file.toString());
        ExtractionState state = new ExtractionState(data);

        Map<String, Object> result = node.apply(state);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldThrowForMissingFile() {
        Map<String, Object> data = ExtractionState.initial(
            1L,
            "/nonexistent/path/doc.pdf"
        );
        ExtractionState state = new ExtractionState(data);

        assertThatThrownBy(() -> node.apply(state))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Document not found");
    }
}
