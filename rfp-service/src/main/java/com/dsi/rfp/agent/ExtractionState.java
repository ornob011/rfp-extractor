package com.dsi.rfp.agent;

import com.dsi.rfp.domain.model.*;
import org.apache.commons.lang3.StringUtils;
import org.bsc.langgraph4j.state.AgentState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class ExtractionState extends AgentState {

    public ExtractionState(Map<String, Object> data) {
        super(data);
    }

    public static Map<String, Object> initial(
        Long jobId,
        String documentPath
    ) {
        Map<String, Object> data = new HashMap<>();

        data.put(Key.JOB_ID.value(), jobId);
        data.put(Key.DOCUMENT_PATH.value(), documentPath);
        data.put(Key.PAGE_CLASSIFICATIONS.value(), List.of());
        data.put(Key.SECTIONS.value(), List.of());
        data.put(Key.TABLES.value(), List.of());
        data.put(Key.CLAUSES.value(), List.of());
        data.put(Key.CONFIDENCE_MAP.value(), Map.of());
        data.put(Key.PAGE_TEXTS.value(), Map.of());
        data.put(Key.PAGE_CONFIDENCES.value(), Map.of());
        data.put(Key.PAGE_EXTRACTION_METHODS.value(), Map.of());
        data.put(Key.REPAIR_LOG.value(), List.of());
        data.put(Key.LOW_CONFIDENCE_QUEUE.value(), List.of());
        data.put(Key.MANUAL_REVIEW_REQUIRED.value(), List.of());
        data.put(Key.TOTAL_REPAIR_ITERATIONS.value(), 0);
        data.put(Key.REPAIR_EXHAUSTED.value(), false);
        data.put(Key.REPAIRABLE_COMPONENTS.value(), Map.of());

        return data;
    }

    public Long jobId() {
        return readOrDefault(
            Key.JOB_ID,
            0L,
            Long.class
        );
    }

    public String documentPath() {
        return readOrDefault(
            Key.DOCUMENT_PATH,
            StringUtils.EMPTY,
            String.class
        );
    }

    public List<PageSummary> pageClassifications() {
        return listValue(
            Key.PAGE_CLASSIFICATIONS,
            PageSummary.class
        );
    }

    public List<Section> sections() {
        return listValue(
            Key.SECTIONS,
            Section.class
        );
    }

    public List<TableExtractionResult> tables() {
        return listValue(
            Key.TABLES,
            TableExtractionResult.class
        );
    }

    public List<Clause> clauses() {
        return listValue(
            Key.CLAUSES,
            Clause.class
        );
    }

    public RfpEntities entities() {
        return this.<RfpEntities>value(Key.ENTITIES.value())
                   .orElse(null);
    }

    public Map<String, Double> confidenceMap() {
        Map<?, ?> value = readMapOrDefault(Key.CONFIDENCE_MAP, Map.of());

        return value.entrySet()
                    .stream()
                    .filter(entry -> entry.getKey() instanceof String)
                    .filter(entry -> entry.getValue() instanceof Number)
                    .collect(Collectors.toMap(
                        entry -> (String) entry.getKey(),
                        entry -> ((Number) entry.getValue()).doubleValue()
                    ));
    }

    public Map<Integer, String> pageTexts() {
        return integerKeyedMap(
            Key.PAGE_TEXTS,
            String.class
        );
    }

    public Map<Integer, Double> pageConfidences() {
        Map<?, ?> value = readMapOrDefault(Key.PAGE_CONFIDENCES, Map.of());

        return value.entrySet()
                    .stream()
                    .filter(this::hasIntegerStringKey)
                    .filter(entry -> entry.getValue() instanceof Number)
                    .collect(Collectors.toMap(
                        entry -> Integer.parseInt((String) entry.getKey()),
                        entry -> ((Number) entry.getValue()).doubleValue()
                    ));
    }

    public Map<Integer, PageExtractionMethod> pageExtractionMethods() {
        return integerKeyedMap(
            Key.PAGE_EXTRACTION_METHODS,
            PageExtractionMethod.class
        );
    }

    public List<RepairLogEntry> repairLog() {
        return listValue(
            Key.REPAIR_LOG,
            RepairLogEntry.class
        );
    }

    public List<String> lowConfidenceQueue() {
        return listValue(
            Key.LOW_CONFIDENCE_QUEUE,
            String.class
        );
    }

    public List<String> manualReviewRequired() {
        return listValue(
            Key.MANUAL_REVIEW_REQUIRED,
            String.class
        );
    }

    public int totalRepairIterations() {
        return readOrDefault(
            Key.TOTAL_REPAIR_ITERATIONS,
            0,
            Integer.class
        );
    }

    public boolean repairExhausted() {
        return readOrDefault(
            Key.REPAIR_EXHAUSTED,
            false,
            Boolean.class
        );
    }

    public Map<String, RepairableComponent> repairableComponents() {
        Map<?, ?> value = readMapOrDefault(
            Key.REPAIRABLE_COMPONENTS,
            Map.of()
        );

        return value.entrySet()
                    .stream()
                    .filter(entry -> entry.getKey() instanceof String)
                    .filter(entry -> entry.getValue() instanceof RepairableComponent)
                    .collect(Collectors.toMap(
                        entry -> (String) entry.getKey(),
                        entry -> (RepairableComponent) entry.getValue()
                    ));
    }

    public RulePackResults rulePackResults() {
        return this.<RulePackResults>value(Key.RULE_PACK_RESULTS.value())
                   .orElse(null);
    }

    private <T> List<T> listValue(
        Key stateKey,
        Class<T> type
    ) {
        List<?> value = readListOrDefault(stateKey, List.of());

        return value.stream()
                    .filter(type::isInstance)
                    .map(type::cast)
                    .toList();
    }

    private <T> T readOrDefault(
        Key stateKey,
        T defaultValue,
        Class<T> type
    ) {
        return value(stateKey.value()).filter(type::isInstance)
                                      .map(type::cast)
                                      .orElse(defaultValue);
    }

    private List<?> readListOrDefault(
        Key stateKey,
        List<?> defaultValue
    ) {
        return value(stateKey.value()).flatMap(this::asList)
                                      .orElse(defaultValue);
    }

    private Map<?, ?> readMapOrDefault(
        Key stateKey,
        Map<?, ?> defaultValue
    ) {
        return value(stateKey.value()).flatMap(this::asMap)
                                      .orElse(defaultValue);
    }

    private <T> Map<Integer, T> integerKeyedMap(
        Key stateKey,
        Class<T> valueType
    ) {
        Map<?, ?> value = readMapOrDefault(
            stateKey,
            Map.of()
        );

        return value.entrySet()
                    .stream()
                    .filter(this::hasIntegerStringKey)
                    .filter(entry -> valueType.isInstance(entry.getValue()))
                    .collect(Collectors.toMap(
                        entry -> Integer.parseInt((String) entry.getKey()),
                        entry -> valueType.cast(entry.getValue())
                    ));
    }

    private boolean hasIntegerStringKey(
        Map.Entry<?, ?> entry
    ) {
        return switch (entry.getKey()) {
            case String key -> key.chars().allMatch(Character::isDigit);
            case null, default -> false;
        };
    }

    private Optional<List<?>> asList(Object value) {
        return switch (value) {
            case List<?> list -> Optional.of(list);
            case null, default -> Optional.empty();
        };
    }

    private Optional<Map<?, ?>> asMap(Object value) {
        return switch (value) {
            case Map<?, ?> map -> Optional.of(map);
            case null, default -> Optional.empty();
        };
    }

    public enum Key {
        JOB_ID("jobId"),
        DOCUMENT_PATH("documentPath"),
        PAGE_CLASSIFICATIONS("pageClassifications"),
        SECTIONS("sections"),
        TABLES("tables"),
        CLAUSES("clauses"),
        ENTITIES("entities"),
        CONFIDENCE_MAP("confidenceMap"),
        REPAIR_LOG("repairLog"),
        LOW_CONFIDENCE_QUEUE("lowConfidenceQueue"),
        MANUAL_REVIEW_REQUIRED("manualReviewRequired"),
        TOTAL_REPAIR_ITERATIONS("totalRepairIterations"),
        PAGE_TEXTS("pageTexts"),
        PAGE_CONFIDENCES("pageConfidences"),
        PAGE_EXTRACTION_METHODS("pageExtractionMethods"),
        REPAIR_EXHAUSTED("repairExhausted"),
        REPAIRABLE_COMPONENTS("repairableComponents"),
        RULE_PACK_RESULTS("rulePackResults");

        private final String stateKey;

        Key(String stateKey) {
            this.stateKey = stateKey;
        }

        public String value() {
            return stateKey;
        }
    }
}
