package com.dsi.rfp.adapter.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "analysis_results")
@Getter
@Setter
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResultEntity extends BaseEntity {

    @ToString.Exclude
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_job_id", nullable = false)
    private AnalysisJobEntity analysisJob;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String resultJson;

    private String modelId;
    private String modelVersion;
    private int totalTokensUsed;

    private double overallConfidence;
}
