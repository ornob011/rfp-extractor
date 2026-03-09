package com.dsi.rfp.adapter.persistence.entity;

import com.dsi.rfp.domain.model.AgentStepType;
import com.dsi.rfp.domain.model.StepOutcome;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "agent_steps", indexes = @Index(columnList = "execution_id, sequence"))
@Getter
@Setter
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentStepEntity extends BaseEntity {

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_id", nullable = false)
    private AgentExecutionEntity execution;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentStepType stepType;

    @Column(nullable = false)
    private int sequence;

    private Instant startedAt;
    private Instant completedAt;
    private Long durationMs;

    @Enumerated(EnumType.STRING)
    private StepOutcome outcome;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;
}
