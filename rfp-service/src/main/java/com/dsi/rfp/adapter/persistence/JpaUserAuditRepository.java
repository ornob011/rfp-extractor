package com.dsi.rfp.adapter.persistence;

import com.dsi.rfp.adapter.persistence.entity.UserAuditEntity;
import com.dsi.rfp.adapter.persistence.repository.UserAuditRepository;
import com.dsi.rfp.domain.model.UserAuditEvent;
import com.dsi.rfp.domain.port.out.UserAuditPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;

@Slf4j
@Repository
public class JpaUserAuditRepository implements UserAuditPort {

    private final UserAuditRepository repository;

    public JpaUserAuditRepository(UserAuditRepository repository) {
        this.repository = repository;
    }

    @Override
    public void record(UserAuditEvent event) {
        UserAuditEntity entity = UserAuditEntity.builder()
                                                .username(event.getUsername())
                                                .action(event.getAction())
                                                .jobId(event.getJobId())
                                                .timestamp(event.getTimestamp())
                                                .ipAddress(event.getIpAddress())
                                                .success(event.isSuccess())
                                                .build();

        repository.save(entity);

        log.debug(
            "event=audit.recorded component=JpaUserAuditRepository username={} action={}",
            event.getUsername(),
            event.getAction()
        );
    }

    @Override
    public List<UserAuditEvent> findByUsername(
        String username,
        int limit
    ) {
        return repository.findByUsernameOrderByCreatedAtDesc(
                             username,
                             PageRequest.of(0, limit)
                         )
                         .stream()
                         .map(this::toDomain)
                         .toList();
    }

    @Override
    public List<UserAuditEvent> findAll(int limit) {
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit))
                         .stream()
                         .map(this::toDomain)
                         .toList();
    }

    private UserAuditEvent toDomain(UserAuditEntity entity) {
        return UserAuditEvent.builder()
                             .eventId(entity.getId())
                             .username(entity.getUsername())
                             .action(entity.getAction())
                             .jobId(entity.getJobId())
                             .timestamp(entity.getTimestamp())
                             .ipAddress(entity.getIpAddress())
                             .success(entity.isSuccess())
                             .build();
    }
}
