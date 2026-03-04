package com.dsi.rfp.config;

import com.dsi.rfp.domain.exception.LlmResponseParseException;
import com.dsi.rfp.domain.exception.LlmUnavailableException;
import io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigCustomizer;
import io.github.resilience4j.common.ratelimiter.configuration.RateLimiterConfigCustomizer;
import io.github.resilience4j.common.retry.configuration.RetryConfigCustomizer;
import io.github.resilience4j.common.timelimiter.configuration.TimeLimiterConfigCustomizer;
import io.github.resilience4j.core.IntervalFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Configuration
@RequiredArgsConstructor
class LlmResilienceCustomizers {

    private final LlmProviderProperties props;

    @Bean
    RetryConfigCustomizer llmRetryCustomizer() {
        return RetryConfigCustomizer.of("llm", b -> b
            .maxAttempts(3)
            .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(
                Duration.ofSeconds(1), 2.0, 0.3))
            .retryOnException(e -> !(e instanceof LlmResponseParseException)));
    }

    @Bean
    CircuitBreakerConfigCustomizer llmCbCustomizer() {
        return CircuitBreakerConfigCustomizer.of("llm", b -> b
            .recordExceptions(LlmUnavailableException.class,
                IOException.class,
                TimeoutException.class));
    }

    @Bean
    RateLimiterConfigCustomizer llmRlCustomizer() {
        return RateLimiterConfigCustomizer.of("llm", b -> b
            .limitForPeriod(props.getRateLimitPerMinute())
            .limitRefreshPeriod(Duration.ofMinutes(1))
            .timeoutDuration(Duration.ofSeconds(10)));
    }

    @Bean
    TimeLimiterConfigCustomizer llmTlCustomizer() {
        return TimeLimiterConfigCustomizer.of("llm", b -> b
            .timeoutDuration(Duration.ofSeconds(props.getTimeoutSeconds()))
            .cancelRunningFuture(true));
    }
}
