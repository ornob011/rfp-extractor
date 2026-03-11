package com.dsi.rfp.adapter.rest;

import com.dsi.rfp.adapter.security.Auditable;
import com.dsi.rfp.agent.ConfidenceScoringConfig;
import com.dsi.rfp.application.service.RfpJobService;
import com.dsi.rfp.application.service.RfpSubmissionService;
import com.dsi.rfp.domain.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Auditable(action = AuditAction.SUBMIT_DOCUMENT)
    @PostMapping("/submit")
    public ResponseEntity<SubmitResponse> submit(
        @RequestParam("file") MultipartFile file,
        Authentication authentication
    ) throws IOException {
        String username = extractUsername(authentication);
        Long jobId = submissionService.submit(
            file.getOriginalFilename(),
            file.getBytes(),
            username
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
        @PathVariable Long jobId,
        Authentication authentication
    ) {
        return jobService.findById(
                             jobId,
                             extractUsername(authentication),
                             extractRoles(authentication)
                         )
                         .map(ResponseEntity::ok)
                         .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/jobs")
    public ResponseEntity<List<JobStatusResponse>> listJobs(
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            jobService.findAll(
                extractUsername(authentication),
                extractRoles(authentication)
            )
        );
    }

    @Auditable(action = AuditAction.VIEW_RESULT)
    @GetMapping("/result/{jobId}")
    public ResponseEntity<RfpResultResponse> getResult(
        @PathVariable Long jobId,
        Authentication authentication
    ) {
        return jobService.findById(
                             jobId,
                             extractUsername(authentication),
                             extractRoles(authentication)
                         )
                         .map(job -> buildResultResponse(job.getJobId()))
                         .orElse(ResponseEntity.notFound().build());
    }

    private String extractUsername(Authentication authentication) {
        Object principal = authentication.getPrincipal();

        if (principal instanceof Jwt jwt) {
            return jwt.getSubject();
        }

        return authentication.getName();
    }

    private Set<UserRole> extractRoles(Authentication authentication) {
        return authentication.getAuthorities().stream()
                             .map(GrantedAuthority::getAuthority)
                             .map(role -> role.replace("ROLE_", ""))
                             .map(UserRole::valueOf)
                             .collect(Collectors.toUnmodifiableSet());
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
                                                      .rfpType(parsedResult.rfpType())
                                                      .badgeThresholds(buildBadgeThresholds())
                                                      .build();

        return ResponseEntity.ok(response);
    }

    private ParsedResult parseResult(JsonNode resultJson) {
        if (resultJson == null) {
            return new ParsedResult(null, null, null, null, null, null);
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
            objectMapper.valueToTree(result.getRulePackResults()),
            resolveRfpType(result)
        );
    }

    private RfpType resolveRfpType(RfpDocument result) {
        return Optional.ofNullable(result.getRulePackResults())
                       .map(RulePackResults::getRfpType)
                       .orElse(null);
    }

    private List<PageDetailDto> buildPageDetails(RfpDocument result) {
        return result.getPageClassifications().stream()
                     .map(page -> buildPageDetail(
                         page,
                         result
                     ))
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
        return Optional.ofNullable(pageExtractionMethods(result).get(page.getPageNumber()))
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
            case SCANNED -> PageExtractionMethod.VLM;
            case MIXED -> PageExtractionMethod.TEXT_PLUS_VLM;
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
        JsonNode rulePackResults,
        RfpType rfpType
    ) {
    }
}
