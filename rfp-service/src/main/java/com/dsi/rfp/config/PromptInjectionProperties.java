package com.dsi.rfp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.security.prompt-injection")
public record PromptInjectionProperties(
    List<String> phrases,
    String delimiterOpen,
    String delimiterClose,
    String filteredMarker
) {
}
