package com.dsi.rfp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private final int corePoolSize;
    private final int maxPoolSize;
    private final int queueCapacity;
    private final JobStateAsyncExceptionHandler exceptionHandler;

    public AsyncConfig(
        @Value("${app.async.core-pool-size:2}") int corePoolSize,
        @Value("${app.async.max-pool-size:4}") int maxPoolSize,
        @Value("${app.async.queue-capacity:20}") int queueCapacity,
        JobStateAsyncExceptionHandler exceptionHandler
    ) {
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.queueCapacity = queueCapacity;
        this.exceptionHandler = exceptionHandler;
    }

    @Bean(name = "rfpTaskExecutor")
    public Executor rfpTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("rfp-worker-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "llmTaskExecutor")
    public Executor llmTaskExecutor(
        @Value("${app.llm.executor-pool-size:2}") int poolSize
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(Integer.MAX_VALUE);
        executor.setThreadNamePrefix("llm-caller-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "entityExtractionExecutor")
    public Executor entityExtractionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(7);
        executor.setMaxPoolSize(7);
        executor.setQueueCapacity(14);
        executor.setThreadNamePrefix("entity-extract-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "pageExtractionExecutor")
    public Executor pageExtractionExecutor(
        @Value("${app.extraction.page-processing-threads:4}") int threadCount
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threadCount);
        executor.setMaxPoolSize(threadCount);
        executor.setQueueCapacity(Integer.MAX_VALUE);
        executor.setThreadNamePrefix("page-extract-");
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return exceptionHandler;
    }
}
