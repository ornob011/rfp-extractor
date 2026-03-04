package com.dsi.rfp.adapter.persistence.repository;

import com.dsi.rfp.adapter.persistence.entity.AgentExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentExecutionRepository extends JpaRepository<AgentExecutionEntity, Long> {

    List<AgentExecutionEntity> findByAnalysisJobId(Long analysisJobId);
}
