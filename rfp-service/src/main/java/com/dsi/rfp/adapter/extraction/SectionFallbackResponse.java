package com.dsi.rfp.adapter.extraction;

import java.util.List;

public record SectionFallbackResponse(
    List<SectionSuggestion> sections
) {
    public record SectionSuggestion(
        String title,
        int level,
        int approximate_page
    ) {
    }
}
