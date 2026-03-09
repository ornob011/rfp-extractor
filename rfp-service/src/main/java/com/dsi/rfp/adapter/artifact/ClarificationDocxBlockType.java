package com.dsi.rfp.adapter.artifact;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;

enum ClarificationDocxBlockType {
    TITLE(
        ParagraphAlignment.CENTER,
        true,
        false
    ),
    META(
        ParagraphAlignment.LEFT,
        false,
        false
    ),
    QUESTION(
        ParagraphAlignment.LEFT,
        false,
        false
    ),
    SOURCE(
        ParagraphAlignment.LEFT,
        false,
        true
    ),
    RESPONSE(
        ParagraphAlignment.LEFT,
        false,
        true
    ),
    SPACER(
        ParagraphAlignment.LEFT,
        false,
        false
    );

    private final ParagraphAlignment alignment;
    private final boolean bold;
    private final boolean italic;

    ClarificationDocxBlockType(
        ParagraphAlignment alignment,
        boolean bold,
        boolean italic
    ) {
        this.alignment = alignment;
        this.bold = bold;
        this.italic = italic;
    }

    ParagraphAlignment alignment() {
        return alignment;
    }

    boolean bold() {
        return bold;
    }

    boolean italic() {
        return italic;
    }
}
