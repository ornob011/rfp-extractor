package com.dsi.rfp.adapter.vision;

import com.dsi.rfp.adapter.extraction.PageImageRenderer;
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

        VisionPageResult pageResult = result.orElseThrow(
            () -> new LlmUnavailableException(
                String.format(
                    "VLM returned empty table result for page %d",
                    pageNum
                )
            )
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
}
