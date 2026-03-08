package com.dsi.rfp.adapter.extraction;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class MixedPageMergeService {

    public MixedPageContent merge(
        MixedPageMergeInput input
    ) {
        String mergedText = Optional.of(input)
                                    .filter(this::hasDetectedRegions)
                                    .map(this::mergeDetectedRegions)
                                    .orElse(input.orderedText());

        double confidence = Math.min(
            input.textLayerQuality(),
            input.ocrResult().pageConfidence()
        );

        return MixedPageContent.textPlusOcr(mergedText, confidence);
    }

    private boolean hasDetectedRegions(
        MixedPageMergeInput input
    ) {
        return !input.layout().textRegions().isEmpty()
               || !input.layout().tableRegions().isEmpty();
    }

    private String mergeDetectedRegions(
        MixedPageMergeInput input
    ) {
        String tableText = Optional.of(input.layout().tableRegions())
                                   .filter(regions -> !regions.isEmpty())
                                   .map(regions -> input.ocrResult().text())
                                   .orElse(StringUtils.EMPTY);

        return Stream.of(
                         input.orderedText(),
                         tableText
                     )
                     .filter(text -> !text.isBlank())
                     .collect(Collectors.joining(System.lineSeparator()))
                     .trim();
    }
}
