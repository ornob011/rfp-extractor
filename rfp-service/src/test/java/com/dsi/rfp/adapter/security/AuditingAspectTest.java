package com.dsi.rfp.adapter.security;

import com.dsi.rfp.application.service.UserAuditService;
import com.dsi.rfp.domain.model.AuditAction;
import com.dsi.rfp.domain.model.UserAuditEvent;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditingAspectTest {

    @Mock
    private UserAuditService auditService;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private Auditable auditable;

    private AuditingAspect aspect;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
            Instant.parse("2026-01-01T12:00:00Z"),
            ZoneOffset.UTC
        );
        aspect = new AuditingAspect(auditService, clock);

        SecurityContextHolder.getContext().setAuthentication(
            new TestingAuthenticationToken("testuser", null)
        );
    }

    @Test
    void shouldRecordSuccessEvent() throws Throwable {
        when(auditable.action()).thenReturn(AuditAction.SUBMIT_DOCUMENT);
        when(joinPoint.proceed()).thenReturn("result");
        when(joinPoint.getArgs()).thenReturn(new Object[]{42L});

        Object result = aspect.audit(joinPoint, auditable);

        assertThat(result).isEqualTo("result");

        ArgumentCaptor<UserAuditEvent> captor =
            ArgumentCaptor.forClass(UserAuditEvent.class);
        verify(auditService).record(captor.capture());

        UserAuditEvent event = captor.getValue();
        assertThat(event.getUsername()).isEqualTo("testuser");
        assertThat(event.getAction()).isEqualTo(AuditAction.SUBMIT_DOCUMENT);
        assertThat(event.isSuccess()).isTrue();
        assertThat(event.getJobId()).isEqualTo(42L);
    }

    @Test
    void shouldRecordFailureEvent() throws Throwable {
        when(auditable.action()).thenReturn(AuditAction.LOGIN);
        when(joinPoint.proceed()).thenThrow(new RuntimeException("fail"));
        when(joinPoint.getArgs()).thenReturn(new Object[]{});

        assertThatThrownBy(() -> aspect.audit(joinPoint, auditable))
            .isInstanceOf(RuntimeException.class);

        ArgumentCaptor<UserAuditEvent> captor =
            ArgumentCaptor.forClass(UserAuditEvent.class);
        verify(auditService).record(captor.capture());

        assertThat(captor.getValue().isSuccess()).isFalse();
    }
}
