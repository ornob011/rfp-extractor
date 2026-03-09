package com.dsi.rfp.adapter.rulepack;

import com.dsi.rfp.domain.exception.RulePackEvaluationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.burt.jmespath.Expression;
import io.burt.jmespath.JmesPathType;
import io.burt.jmespath.jackson.JacksonRuntime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JmesPathEvaluator {

    private final JacksonRuntime jmespath = new JacksonRuntime();
    private final ObjectMapper objectMapper;

    public boolean evaluateAsBoolean(
        String condition,
        String rfpJson
    ) {
        return isTruthy(
            evaluateNode(
                condition,
                rfpJson
            )
        );
    }

    public Optional<String> extractEvidence(
        String evidencePath,
        String rfpJson
    ) {
        return Optional.of(
                           evaluateNode(
                               evidencePath,
                               rfpJson
                           )
                       )
                       .filter(result -> !isEmptyResult(result))
                       .map(this::renderEvidence);
    }

    public Object evaluate(
        String expression,
        String rfpJson
    ) {
        return evaluateNode(expression, rfpJson);
    }

    private JsonNode evaluateNode(
        String expression,
        String rfpJson
    ) {
        JsonNode document = parseDocument(rfpJson);
        Expression<JsonNode> compiled = compile(expression);
        return compiled.search(document);
    }

    private JsonNode parseDocument(String rfpJson) {
        try {
            return objectMapper.readTree(rfpJson);
        } catch (JsonProcessingException exception) {
            throw new RulePackEvaluationException(
                "Failed to parse RFP JSON for rule evaluation",
                exception
            );
        }
    }

    private Expression<JsonNode> compile(String expression) {
        try {
            return jmespath.compile(expression);
        } catch (RuntimeException exception) {
            throw new RulePackEvaluationException(
                String.format("Invalid JMESPath expression: %s", expression),
                exception
            );
        }
    }

    private boolean isEmptyResult(JsonNode node) {
        return node.isNull() || node.isMissingNode();
    }

    private String renderEvidence(JsonNode result) {
        return Optional.of(result)
                       .filter(JsonNode::isTextual)
                       .map(JsonNode::asText)
                       .orElseGet(result::toString);
    }

    private boolean isTruthy(JsonNode node) {
        JmesPathType type = jmespath.typeOf(node);

        return switch (type) {
            case BOOLEAN -> node.asBoolean();
            case STRING -> !node.asText().isEmpty();
            case NUMBER -> true;
            case ARRAY, OBJECT -> !node.isEmpty();
            case NULL -> false;
        };
    }
}
