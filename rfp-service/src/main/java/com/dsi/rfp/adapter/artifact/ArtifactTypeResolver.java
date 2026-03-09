package com.dsi.rfp.adapter.artifact;

import com.dsi.rfp.domain.model.ArtifactFileType;
import org.apache.tika.Tika;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class ArtifactTypeResolver {

    private static final Tika TIKA = new Tika();
    private static final MediaType DOCX_MEDIA_TYPE = MediaType.parseMediaType(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );
    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );
    private static final Map<ArtifactFileType, MediaType> MEDIA_TYPES = Map.of(
        ArtifactFileType.DOCX, DOCX_MEDIA_TYPE,
        ArtifactFileType.XLSX, XLSX_MEDIA_TYPE,
        ArtifactFileType.HTML, MediaType.TEXT_HTML
    );
    private static final Map<String, ArtifactFileType> FILE_TYPES_BY_MIME = Map.of(
        DOCX_MEDIA_TYPE.toString(), ArtifactFileType.DOCX,
        XLSX_MEDIA_TYPE.toString(), ArtifactFileType.XLSX,
        MediaType.TEXT_HTML.toString(), ArtifactFileType.HTML
    );

    public ArtifactFileType fileType(String filename) {
        return FILE_TYPES_BY_MIME.getOrDefault(
            TIKA.detect(filename),
            ArtifactFileType.HTML
        );
    }

    public MediaType mediaType(ArtifactFileType fileType) {
        return MEDIA_TYPES.get(fileType);
    }

    public String contentDisposition(
        ArtifactFileType fileType,
        String filename
    ) {
        return disposition(fileType, filename).toString();
    }

    private ContentDisposition disposition(
        ArtifactFileType fileType,
        String filename
    ) {
        return switch (fileType) {
            case HTML -> ContentDisposition.inline()
                                           .filename(filename, StandardCharsets.UTF_8)
                                           .build();
            case DOCX, XLSX -> ContentDisposition.attachment()
                                                 .filename(filename, StandardCharsets.UTF_8)
                                                 .build();
        };
    }
}
