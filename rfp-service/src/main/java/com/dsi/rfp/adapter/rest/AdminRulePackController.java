package com.dsi.rfp.adapter.rest;

import com.dsi.rfp.adapter.security.Auditable;
import com.dsi.rfp.application.service.RulePackAdminService;
import com.dsi.rfp.domain.model.AuditAction;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/rule-packs")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRulePackController {

    private final RulePackAdminService adminService;

    public AdminRulePackController(RulePackAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    public ResponseEntity<List<RulePackSummaryDto>> listRulePacks() {
        return ResponseEntity.ok(adminService.listLoadedPacks());
    }

    @Auditable(action = AuditAction.RELOAD_RULE_PACK)
    @PostMapping("/reload")
    public ResponseEntity<ReloadResultDto> reloadRulePacks() {
        return ResponseEntity.ok(adminService.reloadAll());
    }
}
