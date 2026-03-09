package com.dsi.rfp.domain.port.out;

import java.nio.file.Path;

public interface FileStoragePort {

    Path store(Long jobId, byte[] content, String filename);

    Path retrieve(Long jobId, String filename);

    Path jobDirectory(Long jobId);

    void deleteJobDirectory(Long jobId);
}
