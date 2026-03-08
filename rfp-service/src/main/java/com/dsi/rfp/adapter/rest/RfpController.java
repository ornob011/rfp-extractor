package com.dsi.rfp.adapter.rest;

import com.dsi.rfp.agent.ConfidenceScoringConfig;
import com.dsi.rfp.application.service.RfpJobService;
import com.dsi.rfp.application.service.RfpSubmissionService;
import com.dsi.rfp.domain.model.AnalysisStatus;
import com.dsi.rfp.domain.model.PageExtractionMethod;
import com.dsi.rfp.domain.model.PageSummary;
import com.dsi.rfp.domain.model.RfpDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1/rfp")
public class RfpController {

    private final RfpSubmissionService submissionService;
    private final RfpJobService jobService;
    private final ObjectMapper objectMapper;
    private final ConfidenceScoringConfig scoringConfig;

    public RfpController(
        RfpSubmissionService submissionService,
        RfpJobService jobService,
        ObjectMapper objectMapper,
        ConfidenceScoringConfig scoringConfig
    ) {
        this.submissionService = submissionService;
        this.jobService = jobService;
        this.objectMapper = objectMapper;
        this.scoringConfig = scoringConfig;
    }

    @PostMapping("/submit")
    public ResponseEntity<SubmitResponse> submit(
        @RequestParam("file") MultipartFile file
    ) throws IOException {
        Long jobId = submissionService.submit(
            file.getOriginalFilename(),
            file.getBytes()
        );

        SubmitResponse response = SubmitResponse.builder()
                                                .jobId(jobId)
                                                .status(AnalysisStatus.QUEUED)
                                                .message("RFP document submitted for processing")
                                                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                             .body(response);
    }

    @GetMapping("/status/{jobId}")
    public ResponseEntity<JobStatusResponse> getStatus(
        @PathVariable Long jobId
    ) {
        return jobService.findById(jobId)
                         .map(ResponseEntity::ok)
                         .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/jobs")
    public ResponseEntity<List<JobStatusResponse>> listJobs() {
        return ResponseEntity.ok(jobService.findAll());
    }

    @GetMapping("/result/{jobId}")
    public ResponseEntity<RfpResultResponse> getResult(
        @PathVariable Long jobId
    ) {
        return jobService.findById(jobId)
                         .map(job -> buildResultResponse(job.getJobId()))
                         .orElse(ResponseEntity.notFound().build());
    }

    private ResponseEntity<RfpResultResponse> buildResultResponse(Long jobId) {
        JsonNode sectionsJson = jobService.getSectionsJson(jobId);
        JsonNode resultJson = jobService.getResult(jobId)
                                        .orElse(null);

        ParsedResult parsedResult = parseResult(resultJson);

        RfpResultResponse response = RfpResultResponse.builder()
                                                      .jobId(jobId)
                                                      .sections(sectionsJson)
                                                      .entities(parsedResult.entities())
                                                      .confidenceMap(parsedResult.confidenceMap())
                                                      .tables(parsedResult.tables())
                                                      .pageDetails(parsedResult.pageDetails())
                                                      .rulePackResults(parsedResult.rulePackResults())
                                                      .badgeThresholds(buildBadgeThresholds())
                                                      .build();

        return ResponseEntity.ok(response);
    }

    private ParsedResult parseResult(JsonNode resultJson) {
        if (resultJson == null) {
            return new ParsedResult(null, null, null, null, null);
        }

        RfpDocument result = objectMapper.convertValue(
            resultJson,
            RfpDocument.class
        );

        return new ParsedResult(
            objectMapper.valueToTree(result.getEntities()),
            objectMapper.valueToTree(result.getConfidenceMap()),
            objectMapper.valueToTree(result.getTables()),
            objectMapper.valueToTree(buildPageDetails(result)),
            objectMapper.valueToTree(result.getRulePackResults())
        );
    }

    private List<PageDetailDto> buildPageDetails(RfpDocument result) {
        return result.getPageClassifications().stream()
                     .map(page -> buildPageDetail(page, result))
                     .toList();
    }

    private PageDetailDto buildPageDetail(
        PageSummary page,
        RfpDocument result
    ) {
        double confidence = result.getPageConfidences()
                                  .getOrDefault(page.getPageNumber(), 1.0);

        PageExtractionMethod method = resolveExtractionMethod(
            page,
            result
        );

        return new PageDetailDto(
            page.getPageNumber(),
            page.getClassification(),
            method,
            confidence
        );
    }

    private PageExtractionMethod resolveExtractionMethod(
        PageSummary page,
        RfpDocument result
    ) {
        return Optional.ofNullable(
                           pageExtractionMethods(result).get(page.getPageNumber())
                       )
                       .orElseGet(() -> defaultMethod(page));
    }

    private Map<Integer, PageExtractionMethod> pageExtractionMethods(
        RfpDocument result
    ) {
        return Optional.ofNullable(result.getPageExtractionMethods())
                       .orElse(Map.of());
    }

    private PageExtractionMethod defaultMethod(PageSummary page) {
        return switch (page.getClassification()) {
            case DIGITAL -> PageExtractionMethod.TEXT_LAYER;
            case SCANNED -> PageExtractionMethod.OCR;
            case MIXED -> PageExtractionMethod.TEXT_PLUS_OCR;
        };
    }

    private RfpResultResponse.BadgeThresholds buildBadgeThresholds() {
        return new RfpResultResponse.BadgeThresholds(
            scoringConfig.badgeHighThreshold(),
            scoringConfig.badgeMediumThreshold()
        );
    }

    private record ParsedResult(
        JsonNode entities,
        JsonNode confidenceMap,
        JsonNode tables,
        JsonNode pageDetails,
        JsonNode rulePackResults
    ) {
    }
}
