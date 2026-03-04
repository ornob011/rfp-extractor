package com.dsi.rfp.config;

import com.dsi.rfp.domain.model.AnalysisStatus;
import com.dsi.rfp.domain.port.out.JobStatePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class JobStateAsyncExceptionHandlerTest {

    @Mock
    private JobStatePort jobStatePort;

    private JobStateAsyncExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new JobStateAsyncExceptionHandler(jobStatePort);
    }

    @Test
    void shouldMarkJobAsFailedWhenFirstParamIsLong()
        throws NoSuchMethodException {
        Long jobId = 42L;
        Method method = getClass().getDeclaredMethod("setUp");
        RuntimeException ex = new RuntimeException("test error");

        handler.handleUncaughtException(ex, method, jobId);

        verify(jobStatePort)
            .updateStatus(jobId, AnalysisStatus.FAILED);
    }

    @Test
    void shouldNotFailWhenNoLongParam()
        throws NoSuchMethodException {
        Method method = getClass().getDeclaredMethod("setUp");
        RuntimeException ex = new RuntimeException("test error");

        handler.handleUncaughtException(ex, method, "not-a-long");

        verifyNoInteractions(jobStatePort);
    }
}
