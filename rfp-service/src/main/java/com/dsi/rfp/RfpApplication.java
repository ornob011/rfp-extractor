package com.dsi.rfp;

import com.dsi.rfp.config.LlmProviderProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(LlmProviderProperties.class)
public class RfpApplication {

    public static void main(String[] args) {
        SpringApplication.run(RfpApplication.class, args);
    }
}
