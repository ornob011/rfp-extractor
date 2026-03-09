package com.dsi.rfp.adapter.persistence.entity;

import com.dsi.rfp.domain.model.ExecutionStatus;
import com.dsi.rfp.domain.model.TerminationReason;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "agent_executions")
@Getter
@Setter
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentExecutionEntity extends BaseEntity {

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_job_id", nullable = false)
    private AnalysisJobEntity analysisJob;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutionStatus status;

    @Enumerated(EnumType.STRING)
    private TerminationReason terminationReason;

    private Instant startedAt;
    private Instant completedAt;
    private int totalSteps;
    private int completedSteps;
    private int repairIterations;
}
