package com.dsi.rfp.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleDefinition {

    private String id;

    private String name;

    private String pack;

    private String version;

    private RuleSeverity severity;

    @JsonProperty("check_type")
    private CheckType checkType;

    private String condition;

    @JsonProperty("evidence_path")
    private String evidencePath;

    @JsonProperty("llm_prompt")
    private String llmPrompt;

    private String message;
}
