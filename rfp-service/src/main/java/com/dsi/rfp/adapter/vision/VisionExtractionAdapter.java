package com.dsi.rfp.adapter.vision;

import com.dsi.rfp.adapter.extraction.PageImageRenderer;
import com.dsi.rfp.adapter.llm.LlmImageInput;
import com.dsi.rfp.adapter.llm.LlmAdapter;
import com.dsi.rfp.adapter.llm.PromptTemplateRenderer;
import com.dsi.rfp.domain.exception.LlmUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
public class VisionExtractionAdapter {

    private final PageImageRenderer pageImageRenderer;
    private final LlmAdapter llmAdapter;
    private final PromptTemplateRenderer promptTemplateRenderer;
    private final VisionExtractionConfig config;

    public VisionExtractionAdapter(
        PageImageRenderer pageImageRenderer,
        LlmAdapter llmAdapter,
        PromptTemplateRenderer promptTemplateRenderer,
        VisionExtractionConfig config
    ) {
        this.pageImageRenderer = pageImageRenderer;
        this.llmAdapter = llmAdapter;
        this.promptTemplateRenderer = promptTemplateRenderer;
        this.config = config;
    }

    public VisionPageResult extractFullPage(
        String documentPath,
        int pageNum
    ) throws IOException {
        byte[] imageBytes = pageImageRenderer.renderPageJpeg(
            documentPath,
            pageNum,
            config.fullPageDpi(),
            config.jpegQuality()
        );

        Resource systemPrompt = config.fullPageSystemPromptResource();
        String userPrompt = promptTemplateRenderer.render(
            config.fullPageUserPromptTemplate(),
            Map.of("additional_instructions", "")
        );

        Optional<VisionPageResult> result = llmAdapter.extractStructuredWithImage(
            systemPrompt,
            userPrompt,
            imageBytes,
            MimeTypeUtils.IMAGE_JPEG,
            VisionPageResult.class
        );

        VisionPageResult pageResult = result.orElseThrow(
            () -> new LlmUnavailableException(
                String.format(
                    "VLM returned empty result for page %d",
                    pageNum
                )
            )
        );

        log.info(
            "event=vision.fullPage component=VisionExtractionAdapter"
            + " page={} confidence={} tables={} hasTable={}",
            pageNum,
            pageResult.confidence(),
            pageResult.tables().size(),
            pageResult.hasTable()
        );

        return pageResult;
    }

    public VisionPageResult extractTablesOnly(
        String documentPath,
        int pageNum
    ) throws IOException {
        byte[] imageBytes = pageImageRenderer.renderPageJpeg(
            documentPath,
            pageNum,
            config.tableOnlyDpi(),
            config.jpegQuality()
        );

        Resource systemPrompt = config.tableOnlySystemPromptResource();
        String userPrompt = promptTemplateRenderer.render(
            config.tableOnlyUserPromptTemplate(),
            Map.of("additional_instructions", "")
        );

        Optional<VisionPageResult> result = llmAdapter.extractStructuredWithImage(
            systemPrompt,
            userPrompt,
            imageBytes,
            MimeTypeUtils.IMAGE_JPEG,
            VisionPageResult.class
        );

        VisionPageResult pageResult = result.orElseGet(
            () -> noTablesDetected(pageNum)
        );

        log.info(
            "event=vision.tableOnly component=VisionExtractionAdapter"
            + " page={} tables={} hasTable={}",
            pageNum,
            pageResult.tables().size(),
            pageResult.hasTable()
        );

        return pageResult;
    }

    public boolean detectTablePresence(
        String documentPath,
        int pageNum
    ) throws IOException {
        return detectTablePresenceBatch(
            documentPath,
            java.util.List.of(pageNum)
        ).getOrDefault(
            pageNum,
            false
        );
    }

    public Map<Integer, Boolean> detectTablePresenceBatch(
        String documentPath,
        java.util.List<Integer> pageNumbers
    ) throws IOException {
        java.util.List<LlmImageInput> images = new java.util.ArrayList<>();

        for (Integer pageNum : pageNumbers) {
            images.add(renderPresenceImage(
                documentPath,
                pageNum
            ));
        }

        Optional<VisionTablePresenceBatchResult> result =
            llmAdapter.extractStructuredWithImages(
                config.tablePresenceBatchSystemPromptResource(),
                promptTemplateRenderer.render(
                    config.tablePresenceBatchUserPromptTemplate(),
                    Map.of(
                        "page_list",
                        pageNumbers.stream()
                                   .map(String::valueOf)
                                   .collect(Collectors.joining(", "))
                    )
                ),
                images,
                VisionTablePresenceBatchResult.class
            );

        Map<Integer, Boolean> tablePresence =
            pageNumbers.stream()
                       .collect(Collectors.toMap(
                           pageNum -> pageNum,
                           pageNum -> false
                       ));

        result.map(VisionTablePresenceBatchResult::pages)
              .orElseGet(java.util.List::of)
              .forEach(page -> tablePresence.put(
                  page.page(),
                  page.hasTable()
              ));

        log.info(
            "event=vision.tablePresence.batch component=VisionExtractionAdapter"
            + " pages={} candidatePages={}",
            pageNumbers.size(),
            tablePresence.values().stream().filter(Boolean::booleanValue).count()
        );

        return tablePresence;
    }

    private LlmImageInput renderPresenceImage(
        String documentPath,
        int pageNum
    ) throws IOException {
        byte[] imageBytes = pageImageRenderer.renderPageJpeg(
            documentPath,
            pageNum,
            config.tablePresenceDpi(),
            config.jpegQuality()
        );

        return new LlmImageInput(
            imageBytes,
            MimeTypeUtils.IMAGE_JPEG
        );
    }

    private VisionPageResult noTablesDetected(
        int pageNum
    ) {
        log.info(
            "event=vision.tableOnly.empty component=VisionExtractionAdapter"
            + " page={} action=return_empty_tables",
            pageNum
        );

        return new VisionPageResult(
            "",
            config.tableVlmConfidence(),
            java.util.List.of(),
            false
        );
    }
}
