package com.dsi.rfp.domain.model;

public enum RepairStrategy {
    RETRY_SECTION_SEGMENTATION,
    SWITCH_TABLE_MODE,
    RETRY_SCANNED_TABLE_OCR_AT_HIGHER_DPI,
    WIDEN_ENTITY_CONTEXT,
    NO_OP
}
