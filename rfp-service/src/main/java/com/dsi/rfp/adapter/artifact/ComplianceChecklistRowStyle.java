package com.dsi.rfp.adapter.artifact;

import com.dsi.rfp.domain.model.RuleStatus;

enum ComplianceChecklistRowStyle {
    FAIL,
    DEFAULT;

    static ComplianceChecklistRowStyle from(RuleStatus status) {
        return switch (status) {
            case FAIL -> FAIL;
            case PASS, SKIPPED -> DEFAULT;
        };
    }
}
