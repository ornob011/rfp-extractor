package com.dsi.rfp.adapter.rest;

import com.dsi.rfp.application.service.RulePackAdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/rule-packs")
public class AdminRulePackController {

    private final RulePackAdminService adminService;

    // TODO: Sprint 11 — add @PreAuthorize("hasRole('ADMIN')")

    public AdminRulePackController(RulePackAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    public ResponseEntity<List<RulePackSummaryDto>> listRulePacks() {
        return ResponseEntity.ok(adminService.listLoadedPacks());
    }

    @PostMapping("/reload")
    public ResponseEntity<ReloadResultDto> reloadRulePacks() {
        return ResponseEntity.ok(adminService.reloadAll());
    }
}
