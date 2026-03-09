package com.dsi.rfp.adapter.rest;

import com.dsi.rfp.application.service.ArtifactApplicationService;
import com.dsi.rfp.domain.exception.ResourceNotFoundException;
import com.dsi.rfp.domain.model.ArtifactFileType;
import com.dsi.rfp.domain.model.ArtifactMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ArtifactController.class)
@AutoConfigureMockMvc(addFilters = false)
class ArtifactControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArtifactApplicationService artifactApplicationService;

    @MockitoBean
    private com.dsi.rfp.adapter.artifact.ArtifactTypeResolver artifactTypeResolver;

    @Test
    void shouldReturn200WithMetadataListWhenArtifactsExist() throws Exception {
        when(artifactApplicationService.listArtifacts(1L))
            .thenReturn(List.of(
                artifact("clarification-questions.docx", ArtifactFileType.DOCX),
                artifact("ambiguity-register.xlsx", ArtifactFileType.XLSX),
                artifact("compliance-checklist.xlsx", ArtifactFileType.XLSX),
                artifact("risk-log.xlsx", ArtifactFileType.XLSX),
                artifact("audit-report.html", ArtifactFileType.HTML)
            ));

        mockMvc.perform(get("/api/v1/rfp/artifacts/1"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.length()").value(5))
               .andExpect(jsonPath("$[0].filename")
                   .value("clarification-questions.docx"));
    }

    @Test
    void shouldStreamDocxWithCorrectContentType() throws Exception {
        when(artifactApplicationService.loadArtifact(eq(1L), eq("clarification-questions.docx")))
            .thenReturn(new ArtifactApplicationService.ArtifactDownloadPayload(
                artifact("clarification-questions.docx", ArtifactFileType.DOCX),
                "docx content".getBytes()
            ));
        when(artifactTypeResolver.contentDisposition(
            ArtifactFileType.DOCX,
            "clarification-questions.docx"
        )).thenReturn("attachment; filename=\"clarification-questions.docx\"");
        when(artifactTypeResolver.mediaType(ArtifactFileType.DOCX))
            .thenReturn(org.springframework.http.MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            ));

        mockMvc.perform(get("/api/v1/rfp/artifacts/1/clarification-questions.docx"))
               .andExpect(status().isOk())
               .andExpect(header().string(
                   "Content-Type",
                   "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
               ))
               .andExpect(header().string(
                   "Content-Disposition",
                   "attachment; filename=\"clarification-questions.docx\""
               ));
    }

    @Test
    void shouldReturn404WhenArtifactNotFound() throws Exception {
        when(artifactApplicationService.loadArtifact(eq(1L), eq("missing.docx")))
            .thenThrow(new ResourceNotFoundException("Not found"));

        mockMvc.perform(get("/api/v1/rfp/artifacts/1/missing.docx"))
               .andExpect(status().isNotFound());
    }

    @Test
    void shouldStreamXlsxWithCorrectContentType() throws Exception {
        when(artifactApplicationService.loadArtifact(eq(1L), eq("ambiguity-register.xlsx")))
            .thenReturn(new ArtifactApplicationService.ArtifactDownloadPayload(
                artifact("ambiguity-register.xlsx", ArtifactFileType.XLSX),
                "xlsx content".getBytes()
            ));
        when(artifactTypeResolver.contentDisposition(
            ArtifactFileType.XLSX,
            "ambiguity-register.xlsx"
        )).thenReturn("attachment; filename=\"ambiguity-register.xlsx\"");
        when(artifactTypeResolver.mediaType(ArtifactFileType.XLSX))
            .thenReturn(org.springframework.http.MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ));

        mockMvc.perform(get("/api/v1/rfp/artifacts/1/ambiguity-register.xlsx"))
               .andExpect(status().isOk())
               .andExpect(header().string(
                   "Content-Type",
                   "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
               ));
    }

    @Test
    void shouldStreamHtmlWithCorrectContentType() throws Exception {
        when(artifactApplicationService.loadArtifact(eq(1L), eq("audit-report.html")))
            .thenReturn(new ArtifactApplicationService.ArtifactDownloadPayload(
                artifact("audit-report.html", ArtifactFileType.HTML),
                "<html></html>".getBytes()
            ));
        when(artifactTypeResolver.contentDisposition(
            ArtifactFileType.HTML,
            "audit-report.html"
        )).thenReturn("inline; filename=\"audit-report.html\"");
        when(artifactTypeResolver.mediaType(ArtifactFileType.HTML))
            .thenReturn(org.springframework.http.MediaType.TEXT_HTML);

        mockMvc.perform(get("/api/v1/rfp/artifacts/1/audit-report.html"))
               .andExpect(status().isOk())
               .andExpect(header().string(
                   "Content-Type",
                   "text/html"
               ))
               .andExpect(header().string(
                   "Content-Disposition",
                   "inline; filename=\"audit-report.html\""
               ));
    }

    private ArtifactMetadata artifact(
        String filename,
        ArtifactFileType type
    ) {
        return ArtifactMetadata.builder()
                               .jobId(1L)
                               .filename(filename)
                               .fileType(type)
                               .sizeBytes(1234)
                               .generatedAt(Instant.now())
                               .downloadUrl(String.format(
                                   "/api/v1/rfp/artifacts/1/%s",
                                   filename
                               ))
                               .build();
    }
}
