package com.dsi.rfp.adapter.vision;

import java.util.List;

public record VisionTablePresenceBatchResult(
    List<PagePresence> pages
) {
    public record PagePresence(
        int page,
        boolean hasTable
    ) {
    }
}
