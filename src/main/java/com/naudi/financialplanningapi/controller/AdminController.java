package com.naudi.financialplanningapi.controller;

import com.naudi.financialplanningapi.service.FinancialPlanStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final FinancialPlanStorageService financialPlanStorageService;

    public AdminController(FinancialPlanStorageService financialPlanStorageService) {
        this.financialPlanStorageService = financialPlanStorageService;
    }

    @PostMapping("/normalize-all-plans")
    public ResponseEntity<String> normalizeAllPlans(Authentication authentication) {
        int updatedCount = financialPlanStorageService.normalizeAllPlans(authentication);
        return ResponseEntity.ok("Normalized " + updatedCount + " stored cycles.");
    }

    @PostMapping("/repair-start-to-end-cycle-dates")
    public ResponseEntity<String> repairStartToEndCycleDates(Authentication authentication) {
        int updatedCount = financialPlanStorageService.repairStartToEndCycleDates(authentication);
        return ResponseEntity.ok("Repaired " + updatedCount + " malformed start-to-end cycle rows.");
    }
}
