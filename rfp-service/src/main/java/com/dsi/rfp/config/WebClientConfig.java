package com.dsi.rfp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Bean("sidecarRestClient")
    public RestClient sidecarRestClient(
        @Value("${app.sidecar.url}") String sidecarUrl,
        @Value("${app.sidecar.connect-timeout-seconds:5}") int connectTimeoutSeconds,
        @Value("${app.sidecar.read-timeout-seconds:180}") int readTimeoutSeconds
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                                          .version(HttpClient.Version.HTTP_1_1)
                                          .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                                          .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));

        return RestClient.builder()
                         .baseUrl(sidecarUrl)
                         .requestFactory(requestFactory)
                         .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                         .build();
    }
}
