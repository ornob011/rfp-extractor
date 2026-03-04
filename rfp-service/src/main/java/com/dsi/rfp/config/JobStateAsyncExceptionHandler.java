package com.dsi.rfp.config;

import com.dsi.rfp.domain.model.AnalysisStatus;
import com.dsi.rfp.domain.port.out.JobStatePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Slf4j
@Component
public class JobStateAsyncExceptionHandler
    implements AsyncUncaughtExceptionHandler {

    private final JobStatePort jobStatePort;

    public JobStateAsyncExceptionHandler(JobStatePort jobStatePort) {
        this.jobStatePort = jobStatePort;
    }

    @Override
    public void handleUncaughtException(
        @NonNull Throwable ex,
        Method method,
        Object... params
    ) {
        log.error(
            "event=async.uncaught component=JobStateAsyncExceptionHandler method={} error={}",
            method.getName(),
            ex.getMessage(),
            ex
        );

        if (params.length > 0 && params[0] instanceof Long jobId) {
            jobStatePort.updateStatus(jobId, AnalysisStatus.FAILED);
        }
    }
}
