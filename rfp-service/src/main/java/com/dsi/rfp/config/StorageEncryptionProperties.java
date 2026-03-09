package com.dsi.rfp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage.encryption")
public record StorageEncryptionProperties(
    String keyset
) {
}
