package com.dsi.rfp.domain.port.out;

import java.nio.file.Path;

public interface MimeTypePort {

    String detect(Path filePath);
}
