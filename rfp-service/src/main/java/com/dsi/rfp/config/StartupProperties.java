package com.dsi.rfp.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
    SecurityProperties.class,
    StorageEncryptionProperties.class,
    PromptInjectionProperties.class,
    BanglaEncodingProperties.class
})
public class StartupProperties {
}
