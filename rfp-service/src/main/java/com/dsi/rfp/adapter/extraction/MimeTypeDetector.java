package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.domain.port.out.MimeTypePort;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

@Slf4j
@Component
public class MimeTypeDetector implements MimeTypePort {

    private final Tika tika = new Tika();

    @Override
    public String detect(Path filePath) {
        try {
            String mimeType = tika.detect(filePath);

            log.debug(
                "event=mime.detected component=MimeTypeDetector file={} mimeType={}",
                filePath.getFileName(),
                mimeType
            );

            return mimeType;
        } catch (IOException exception) {
            log.error(
                "event=mime.detection.failed component=MimeTypeDetector file={} error={}",
                filePath.getFileName(),
                exception.getMessage(),
                exception
            );

            return "application/octet-stream";
        }
    }
}
