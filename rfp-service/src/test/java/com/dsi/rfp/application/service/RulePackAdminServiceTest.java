package com.dsi.rfp.application.service;

import com.dsi.rfp.adapter.rest.ReloadResultDto;
import com.dsi.rfp.adapter.rest.RulePackSummaryDto;
import com.dsi.rfp.domain.model.RfpType;
import com.dsi.rfp.domain.model.RuleDefinition;
import com.dsi.rfp.domain.model.RulePackDefinition;
import com.dsi.rfp.domain.port.out.RulePackManagementPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RulePackAdminServiceTest {

    @Mock
    private RulePackManagementPort rulePackManagementPort;

    @InjectMocks
    private RulePackAdminService adminService;

    @Test
    void shouldListLoadedPacks() {
        Instant loadedAt = Instant.now();
        RulePackDefinition pack = RulePackDefinition.builder()
                                                    .packId("bd-govt-ict-v1")
                                                    .packVersion("1.0.0")
                                                    .rfpType(RfpType.ICT)
                                                    .rules(List.of(
                                                        RuleDefinition.builder()
                                                                      .id("BD-ICT-001")
                                                                      .build()
                                                    ))
                                                    .loadedAt(loadedAt)
                                                    .build();

        when(rulePackManagementPort.listPacks()).thenReturn(List.of(pack));

        List<RulePackSummaryDto> result = adminService.listLoadedPacks();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().packId()).isEqualTo("bd-govt-ict-v1");
        assertThat(result.getFirst().version()).isEqualTo("1.0.0");
        assertThat(result.getFirst().rfpType()).isEqualTo(RfpType.ICT);
        assertThat(result.getFirst().ruleCount()).isEqualTo(1);
        assertThat(result.getFirst().lastLoadedAt()).isEqualTo(loadedAt);
    }

    @Test
    void shouldReturnEmptyListWhenNoPacksLoaded() {
        when(rulePackManagementPort.listPacks()).thenReturn(List.of());

        List<RulePackSummaryDto> result = adminService.listLoadedPacks();

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReloadAllPacks() {
        List<String> packIds = List.of("bd-govt-ict-v1", "bd-govt-works-v1");
        when(rulePackManagementPort.reloadAll()).thenReturn(packIds);

        ReloadResultDto result = adminService.reloadAll();

        verify(rulePackManagementPort).reloadAll();
        assertThat(result.reloadedPacks()).containsExactlyElementsOf(packIds);
        assertThat(result.timestamp()).isNotNull();
    }
}
