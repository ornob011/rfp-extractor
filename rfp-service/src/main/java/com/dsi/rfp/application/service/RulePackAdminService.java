package com.dsi.rfp.application.service;

import com.dsi.rfp.adapter.rest.ReloadResultDto;
import com.dsi.rfp.adapter.rest.RulePackSummaryDto;
import com.dsi.rfp.domain.model.RulePackDefinition;
import com.dsi.rfp.domain.port.out.RulePackManagementPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
public class RulePackAdminService {

    private final RulePackManagementPort rulePackManagementPort;

    public RulePackAdminService(RulePackManagementPort rulePackManagementPort) {
        this.rulePackManagementPort = rulePackManagementPort;
    }

    public List<RulePackSummaryDto> listLoadedPacks() {
        return rulePackManagementPort.listPacks()
                                     .stream()
                                     .map(this::toSummary)
                                     .toList();
    }

    public ReloadResultDto reloadAll() {
        List<String> reloaded = rulePackManagementPort.reloadAll();

        log.info("Rule packs reloaded: {}", reloaded);

        return new ReloadResultDto(reloaded, Instant.now());
    }

    private RulePackSummaryDto toSummary(RulePackDefinition pack) {
        return new RulePackSummaryDto(
            pack.getPackId(),
            pack.getPackVersion(),
            pack.getRfpType(),
            pack.getRules().size(),
            pack.getLoadedAt()
        );
    }
}
