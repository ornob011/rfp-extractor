package com.dsi.rfp.adapter.security;

import com.dsi.rfp.application.service.UserAuditService;
import com.dsi.rfp.domain.model.AuditAction;
import com.dsi.rfp.domain.model.UserAuditEvent;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.util.Optional;

@Slf4j
@Aspect
@Component
public class AuditingAspect {

    private final UserAuditService auditService;
    private final Clock clock;

    public AuditingAspect(
        UserAuditService auditService,
        Clock clock
    ) {
        this.auditService = auditService;
        this.clock = clock;
    }

    @Around("@annotation(auditable)")
    public Object audit(
        ProceedingJoinPoint joinPoint,
        Auditable auditable
    ) throws Throwable {
        AuditAction action = auditable.action();
        String username = extractUsername();
        String ipAddress = extractIpAddress();
        Long jobId = extractJobId(joinPoint);

        try {
            Object result = joinPoint.proceed();
            recordEvent(username, action, jobId, ipAddress, true);
            return result;
        } catch (Throwable ex) {
            recordEvent(username, action, jobId, ipAddress, false);
            throw ex;
        }
    }

    private void recordEvent(
        String username,
        AuditAction action,
        Long jobId,
        String ipAddress,
        boolean success
    ) {
        UserAuditEvent event = UserAuditEvent.builder()
            .username(username)
            .action(action)
            .jobId(jobId)
            .timestamp(clock.instant())
            .ipAddress(ipAddress)
            .success(success)
            .build();

        auditService.record(event);
    }

    private String extractUsername() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                       .map(this::resolveUsername)
                       .orElse("anonymous");
    }

    private String resolveUsername(Authentication auth) {
        Object principal = auth.getPrincipal();
        if (principal instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        return auth.getName();
    }

    private String extractIpAddress() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                       .filter(ServletRequestAttributes.class::isInstance)
                       .map(ServletRequestAttributes.class::cast)
                       .map(ServletRequestAttributes::getRequest)
                       .map(HttpServletRequest::getRemoteAddr)
                       .orElse("unknown");
    }

    private Long extractJobId(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();

        for (Object arg : args) {
            if (arg instanceof Long jobId) {
                return jobId;
            }
        }

        return null;
    }
}
