package com.dsi.rfp.adapter.extraction.parser;

import com.dsi.rfp.domain.model.TextBlock;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FontSizeStatistics {

    public float median(List<TextBlock> blocks, float fallback) {
        DescriptiveStatistics stats = new DescriptiveStatistics();

        blocks.stream()
              .map(TextBlock::getFontSize)
              .filter(size -> size > 0)
              .forEach(stats::addValue);

        if (stats.getN() == 0) {
            return fallback;
        }

        return (float) stats.getPercentile(50.0);
    }
}
