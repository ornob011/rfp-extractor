package com.dsi.rfp.adapter.persistence;

import com.dsi.rfp.adapter.persistence.entity.UserAuditEntity;
import com.dsi.rfp.adapter.persistence.repository.UserAuditRepository;
import com.dsi.rfp.domain.model.AuditAction;
import com.dsi.rfp.domain.model.UserAuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaUserAuditRepositoryTest {

    @Mock
    private UserAuditRepository repository;

    private JpaUserAuditRepository auditRepository;

    @BeforeEach
    void setUp() {
        auditRepository = new JpaUserAuditRepository(repository);
    }

    @Test
    void shouldRecordEvent() {
        UserAuditEvent event = UserAuditEvent.builder()
            .username("testuser")
            .action(AuditAction.LOGIN)
            .jobId(42L)
            .timestamp(Instant.now())
            .ipAddress("127.0.0.1")
            .success(true)
            .build();

        when(repository.save(any())).thenReturn(new UserAuditEntity());

        auditRepository.record(event);

        ArgumentCaptor<UserAuditEntity> captor =
            ArgumentCaptor.forClass(UserAuditEntity.class);
        verify(repository).save(captor.capture());

        UserAuditEntity saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("testuser");
        assertThat(saved.getAction()).isEqualTo(AuditAction.LOGIN);
        assertThat(saved.getJobId()).isEqualTo(42L);
        assertThat(saved.isSuccess()).isTrue();
    }

    @Test
    void shouldFindByUsername() {
        UserAuditEntity entity = UserAuditEntity.builder()
            .username("user1")
            .action(AuditAction.SUBMIT_DOCUMENT)
            .jobId(10L)
            .timestamp(Instant.now())
            .ipAddress("10.0.0.1")
            .success(true)
            .build();

        when(repository.findByUsernameOrderByCreatedAtDesc(
            eq("user1"),
            eq(PageRequest.of(0, 10))
        )).thenReturn(new PageImpl<>(List.of(entity)));

        List<UserAuditEvent> result = auditRepository.findByUsername("user1", 10);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getUsername()).isEqualTo("user1");
        assertThat(result.getFirst().getAction()).isEqualTo(AuditAction.SUBMIT_DOCUMENT);
        assertThat(result.getFirst().getJobId()).isEqualTo(10L);
    }

    @Test
    void shouldFindAllWithLimit() {
        when(repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 50)))
            .thenReturn(new PageImpl<>(List.of()));

        List<UserAuditEvent> result = auditRepository.findAll(50);

        assertThat(result).isEmpty();
    }
}
