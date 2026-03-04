package com.dsi.rfp.config;

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
        return RetryConfigCustomizer.of(
            "llm",
            builder -> builder.maxAttempts(3)
                              .intervalFunction(
                                  IntervalFunction.ofExponentialRandomBackoff(
                                      Duration.ofSeconds(1),
                                      2.0,
                                      0.3
                                  )
                              )
        );
    }

    @Bean
    CircuitBreakerConfigCustomizer llmCbCustomizer() {
        return CircuitBreakerConfigCustomizer.of(
            "llm",
            builder -> builder.recordExceptions(
                LlmUnavailableException.class,
                IOException.class,
                TimeoutException.class
            )
        );
    }

    @Bean
    RateLimiterConfigCustomizer llmRlCustomizer() {
        return RateLimiterConfigCustomizer.of(
            "llm",
            builder -> builder.limitForPeriod(props.getRateLimitPerMinute())
                              .limitRefreshPeriod(Duration.ofMinutes(1))
                              .timeoutDuration(Duration.ofSeconds(10))
        );
    }

    @Bean
    TimeLimiterConfigCustomizer llmTlCustomizer() {
        return TimeLimiterConfigCustomizer.of(
            "llm",
            builder -> builder.timeoutDuration(Duration.ofSeconds(props.getTimeoutSeconds()))
                              .cancelRunningFuture(true)
        );
    }
}
