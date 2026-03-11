package com.dsi.rfp.adapter.llm;

import org.springframework.util.MimeType;

public record LlmImageInput(
    byte[] data,
    MimeType mimeType
) {
}
