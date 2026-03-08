package com.dsi.rfp.adapter.rest;

import com.dsi.rfp.application.service.RulePackAdminService;
import com.dsi.rfp.domain.model.RfpType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminRulePackController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminRulePackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RulePackAdminService adminService;

    @Test
    void shouldReturnListOfLoadedPacks() throws Exception {
        Instant loadedAt = Instant.parse("2026-03-08T10:00:00Z");
        when(adminService.listLoadedPacks()).thenReturn(List.of(
            new RulePackSummaryDto("bd-govt-ict-v1", "1.0.0", RfpType.ICT, 80, loadedAt),
            new RulePackSummaryDto("bd-govt-works-v1", "1.0.0", RfpType.WORKS, 33, loadedAt)
        ));

        mockMvc.perform(get("/api/v1/admin/rule-packs"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.length()").value(2))
               .andExpect(jsonPath("$[0].packId").value("bd-govt-ict-v1"))
               .andExpect(jsonPath("$[0].ruleCount").value(80))
               .andExpect(jsonPath("$[1].packId").value("bd-govt-works-v1"))
               .andExpect(jsonPath("$[1].rfpType").value("WORKS"));
    }

    @Test
    void shouldReturnEmptyListWhenNoPacksLoaded() throws Exception {
        when(adminService.listLoadedPacks()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/rule-packs"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldReloadAllPacks() throws Exception {
        Instant timestamp = Instant.parse("2026-03-08T12:00:00Z");
        when(adminService.reloadAll()).thenReturn(
            new ReloadResultDto(
                List.of("bd-govt-ict-v1", "bd-govt-works-v1"),
                timestamp
            )
        );

        mockMvc.perform(post("/api/v1/admin/rule-packs/reload"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.reloadedPacks.length()").value(2))
               .andExpect(jsonPath("$.reloadedPacks[0]").value("bd-govt-ict-v1"))
               .andExpect(jsonPath("$.timestamp").exists());

        verify(adminService).reloadAll();
    }

    @Test
    void shouldReturnPackDetailsWithCorrectFields() throws Exception {
        Instant loadedAt = Instant.parse("2026-03-08T10:00:00Z");
        when(adminService.listLoadedPacks()).thenReturn(List.of(
            new RulePackSummaryDto("bd-govt-consultancy-v1", "1.0.0", RfpType.CONSULTANCY, 33, loadedAt)
        ));

        mockMvc.perform(get("/api/v1/admin/rule-packs"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].packId").value("bd-govt-consultancy-v1"))
               .andExpect(jsonPath("$[0].version").value("1.0.0"))
               .andExpect(jsonPath("$[0].rfpType").value("CONSULTANCY"))
               .andExpect(jsonPath("$[0].ruleCount").value(33))
               .andExpect(jsonPath("$[0].lastLoadedAt").exists());
    }

    @Test
    void shouldReturnGoodsPackInList() throws Exception {
        Instant loadedAt = Instant.parse("2026-03-08T10:00:00Z");
        when(adminService.listLoadedPacks()).thenReturn(List.of(
            new RulePackSummaryDto("bd-govt-goods-v1", "1.0.0", RfpType.GOODS, 22, loadedAt)
        ));

        mockMvc.perform(get("/api/v1/admin/rule-packs"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].packId").value("bd-govt-goods-v1"))
               .andExpect(jsonPath("$[0].rfpType").value("GOODS"))
               .andExpect(jsonPath("$[0].ruleCount").value(22));
    }

    @Test
    void shouldReloadAndReturnAllFourPacks() throws Exception {
        Instant timestamp = Instant.now();
        when(adminService.reloadAll()).thenReturn(
            new ReloadResultDto(
                List.of("bd-govt-ict-v1", "bd-govt-works-v1", "bd-govt-consultancy-v1", "bd-govt-goods-v1"),
                timestamp
            )
        );

        mockMvc.perform(post("/api/v1/admin/rule-packs/reload"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.reloadedPacks.length()").value(4));
    }
}
