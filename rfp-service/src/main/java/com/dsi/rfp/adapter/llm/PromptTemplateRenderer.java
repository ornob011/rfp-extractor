package com.dsi.rfp.adapter.llm;

import org.apache.commons.text.StringSubstitutor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PromptTemplateRenderer {

    public String render(
        String template,
        Map<String, Object> values
    ) {
        return new StringSubstitutor(
            values,
            "{{",
            "}}"
        ).replace(template);
    }
}
