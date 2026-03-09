package com.dsi.rfp.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Table(name = "extraction_state_checkpoints")
@Getter
@Setter
@NoArgsConstructor
public class ExtractionStateCheckpointEntity extends BaseEntity {

    @Column(nullable = false)
    private Long jobId;

    @Column(nullable = false)
    private int checkpointSequence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB", nullable = false)
    private Map<String, Object> stateJson;
}
