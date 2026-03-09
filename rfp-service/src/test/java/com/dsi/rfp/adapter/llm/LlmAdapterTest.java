package com.dsi.rfp.adapter.llm;

import com.dsi.rfp.domain.exception.LlmUnavailableException;
import com.dsi.rfp.domain.model.LlmJudgmentResult;
import com.dsi.rfp.domain.model.RuleStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

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
        llmAdapter = new LlmAdapter(caller, new ObjectMapper());
    }

    @Test
    void shouldReturnParsedDtoWhenLlmReturnsValidJson() {
        stubCallerToReturn("{\"name\":\"test\"}");
        Optional<TestDto> result = llmAdapter.extractStructured("system prompt", "user content", TestDto.class);
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("test");
    }

    @Test
    void shouldThrowRuntimeExceptionWhenLlmReturnsInvalidJson() {
        stubCallerToReturn("this is not json");
        assertThatThrownBy(() -> llmAdapter.extractStructured("system", "user", TestDto.class))
            .isInstanceOf(RuntimeException.class);
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
    void shouldThrowCompletionExceptionWhenCallerFails() {
        when(caller.call(anyString(), anyString())).thenReturn(CompletableFuture.failedFuture(
            new LlmUnavailableException("LLM unavailable: Connection refused",
                new RuntimeException("Connection refused"))));
        assertThatThrownBy(() -> llmAdapter.extractStructured("system", "user", TestDto.class))
            .isInstanceOf(CompletionException.class)
            .hasCauseInstanceOf(LlmUnavailableException.class);
    }

    @Test
    void shouldThrowCompletionExceptionFromJudgeSnippetOnFailure() {
        when(caller.callJudge(anyString())).thenReturn(CompletableFuture.failedFuture(
            new LlmUnavailableException("LLM unavailable: timeout",
                new RuntimeException("timeout"))));
        assertThatThrownBy(() -> llmAdapter.judgeSnippet("judge prompt", "snippet"))
            .isInstanceOf(CompletionException.class)
            .hasCauseInstanceOf(LlmUnavailableException.class);
    }

    @Test
    void shouldReturnFailJudgmentWhenLlmFindingIsTrue() {
        String json = "{\"finding\":true,\"explanation\":\"vague scope\",\"confidence\":0.9}";
        when(caller.callJudge(anyString()))
            .thenReturn(CompletableFuture.completedFuture(json));

        LlmJudgmentResult result = llmAdapter.judgeSnippet("judge prompt", "snippet");

        assertThat(result.isFinding()).isTrue();
        assertThat(result.getStatus()).isEqualTo(RuleStatus.FAIL);
        assertThat(result.getExplanation()).isEqualTo("vague scope");
        assertThat(result.getConfidence()).isEqualTo(0.9);
    }

    @Test
    void shouldReturnPassJudgmentWhenLlmFindingIsFalse() {
        String json = "{\"finding\":false,\"explanation\":\"clear\",\"confidence\":0.95}";
        when(caller.callJudge(anyString()))
            .thenReturn(CompletableFuture.completedFuture(json));

        LlmJudgmentResult result = llmAdapter.judgeSnippet("judge prompt", "snippet");

        assertThat(result.isFinding()).isFalse();
        assertThat(result.getStatus()).isEqualTo(RuleStatus.PASS);
    }

    @Test
    void shouldReturnSkippedWhenLlmReturnsBlankJudgment() {
        when(caller.callJudge(anyString()))
            .thenReturn(CompletableFuture.completedFuture("   "));

        LlmJudgmentResult result = llmAdapter.judgeSnippet("judge prompt", "snippet");

        assertThat(result.getStatus()).isEqualTo(RuleStatus.SKIPPED);
    }

    private void stubCallerToReturn(String response) {
        when(caller.call(anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(response));
    }

    @Setter
    @Getter
    static class TestDto {
        private String name;
    }
}
