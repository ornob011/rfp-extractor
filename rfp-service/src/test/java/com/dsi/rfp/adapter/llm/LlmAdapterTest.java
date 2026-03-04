package com.dsi.rfp.adapter.llm;

import com.dsi.rfp.config.LlmProviderProperties;
import com.dsi.rfp.domain.exception.LlmResponseParseException;
import com.dsi.rfp.domain.exception.LlmUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmAdapterTest {

    @Mock
    private LlmResilientCaller caller;

    private LlmAdapter llmAdapter;

    @BeforeEach
    void setUp() {
        LlmProviderProperties props = new LlmProviderProperties();
        props.getOpenrouter().setApiKey("test-key");
        llmAdapter = new LlmAdapter(caller, new ObjectMapper(), props);
    }

    @Test
    void shouldReturnParsedDtoWhenLlmReturnsValidJson() {
        stubCallerToReturn("{\"name\":\"test\"}");
        Optional<TestDto> result =
            llmAdapter.extractStructured("system prompt", "user content", TestDto.class);
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("test");
    }

    @Test
    void shouldThrowLlmResponseParseExceptionWhenLlmReturnsInvalidJson() {
        stubCallerToReturn("this is not json");
        assertThatThrownBy(() -> llmAdapter.extractStructured("system", "user", TestDto.class))
            .isInstanceOf(LlmResponseParseException.class);
    }

    @Test
    void shouldReturnEmptyWhenLlmReturnsBlankResponse() {
        stubCallerToReturn("   ");
        Optional<TestDto> result = llmAdapter.extractStructured("system", "user", TestDto.class);
        assertThat(result).isEmpty();
    }

    @Test
    void shouldStripMarkdownJsonCodeFencesBeforeParsing() {
        stubCallerToReturn("```json\n{\"name\":\"fenced\"}\n```");
        Optional<TestDto> result = llmAdapter.extractStructured("system", "user", TestDto.class);
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("fenced");
    }

    @Test
    void shouldStripMarkdownCodeFencesBeforeParsing() {
        stubCallerToReturn("```\n{\"name\":\"plain-fence\"}\n```");
        Optional<TestDto> result = llmAdapter.extractStructured("system", "user", TestDto.class);
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("plain-fence");
    }

    @Test
    void shouldThrowLlmUnavailableExceptionWhenCallerFails() {
        when(caller.call(anyString(), anyString()))
            .thenReturn(CompletableFuture.failedFuture(
                new LlmUnavailableException("LLM unavailable: Connection refused",
                    new RuntimeException("Connection refused"))));
        assertThatThrownBy(() -> llmAdapter.extractStructured("system", "user", TestDto.class))
            .isInstanceOf(LlmUnavailableException.class);
    }

    @Test
    void shouldThrowLlmUnavailableExceptionFromJudgeSnippetOnFailure() {
        when(caller.callJudge(anyString()))
            .thenReturn(CompletableFuture.failedFuture(
                new LlmUnavailableException("LLM unavailable: timeout",
                    new RuntimeException("timeout"))));
        assertThatThrownBy(() -> llmAdapter.judgeSnippet("judge prompt", "snippet"))
            .isInstanceOf(LlmUnavailableException.class);
    }

    @Test
    void shouldReturnJudgeResponseWhenCallerResponds() {
        when(caller.callJudge(anyString()))
            .thenReturn(CompletableFuture.completedFuture("PASS"));
        Optional<String> result = llmAdapter.judgeSnippet("judge prompt", "snippet");
        assertThat(result).isPresent().contains("PASS");
    }

    private void stubCallerToReturn(String response) {
        when(caller.call(anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(response));
    }

    static class TestDto {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
