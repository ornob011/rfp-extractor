package com.dsi.rfp.adapter.rest;

import com.dsi.rfp.adapter.artifact.ArtifactTypeResolver;
import com.dsi.rfp.application.service.ArtifactApplicationService;
import com.dsi.rfp.domain.model.ArtifactFileType;
import com.dsi.rfp.domain.model.ArtifactMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rfp/artifacts")
@RequiredArgsConstructor
public class ArtifactController {

    private final ArtifactApplicationService artifactApplicationService;
    private final ArtifactTypeResolver artifactTypeResolver;

    @GetMapping("/{jobId}")
    public ResponseEntity<List<ArtifactMetadataDto>> listArtifacts(
        @PathVariable Long jobId
    ) {
        return ResponseEntity.ok(
            artifactApplicationService.listArtifacts(jobId)
                                      .stream()
                                      .map(this::toDto)
                                      .toList()
        );
    }

    @GetMapping("/{jobId}/{filename}")
    public ResponseEntity<Resource> downloadArtifact(
        @PathVariable Long jobId,
        @PathVariable String filename
    ) {
        ArtifactApplicationService.ArtifactDownloadPayload artifact =
            artifactApplicationService.loadArtifact(
                jobId,
                filename
            );
        ArtifactFileType fileType = resolveFileType(
            artifact.metadata(),
            filename
        );

        return ResponseEntity.ok()
                             .header(
                                 HttpHeaders.CONTENT_DISPOSITION,
                                 artifactTypeResolver.contentDisposition(
                                     fileType,
                                     filename
                                 )
                             )
                             .contentType(resolveMediaType(fileType))
                             .contentLength(artifact.content().length)
                             .body(new ByteArrayResource(artifact.content()));
    }

    private ArtifactMetadataDto toDto(ArtifactMetadata metadata) {
        return ArtifactMetadataDto.builder()
                                  .jobId(metadata.getJobId())
                                  .filename(metadata.getFilename())
                                  .fileType(metadata.getFileType())
                                  .sizeBytes(metadata.getSizeBytes())
                                  .generatedAt(metadata.getGeneratedAt())
                                  .downloadUrl(metadata.getDownloadUrl())
                                  .build();
    }

    private MediaType resolveMediaType(ArtifactFileType fileType) {
        return artifactTypeResolver.mediaType(fileType);
    }

    private ArtifactFileType resolveFileType(
        ArtifactMetadata metadata,
        String filename
    ) {
        if (metadata.getFileType() != null) {
            return metadata.getFileType();
        }

        return artifactTypeResolver.fileType(filename);
    }
}
