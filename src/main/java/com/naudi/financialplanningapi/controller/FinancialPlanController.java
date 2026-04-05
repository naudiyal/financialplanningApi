package com.naudi.financialplanningapi.controller;

import com.naudi.financialplanningapi.model.FinancialPlanData;
import com.naudi.financialplanningapi.service.FinancialPlanStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/financial-plan")
public class FinancialPlanController {

    private final FinancialPlanStorageService financialPlanStorageService;

    public FinancialPlanController(FinancialPlanStorageService financialPlanStorageService) {
        this.financialPlanStorageService = financialPlanStorageService;
    }

    @GetMapping
    public ResponseEntity<FinancialPlanData> getFinancialPlan(Authentication authentication) {
        FinancialPlanData financialPlanData = financialPlanStorageService.load(authentication);
        boolean hasSavedPlan = financialPlanStorageService.hasSavedPlan(authentication);

        return ResponseEntity.ok()
            .header("X-Has-Saved-Plan", Boolean.toString(hasSavedPlan))
            .body(financialPlanData);
    }

    @GetMapping("/sample")
    public FinancialPlanData getSampleFinancialPlan(Authentication authentication) {
        return financialPlanStorageService.loadSample(authentication);
    }

    @PutMapping
    public FinancialPlanData saveFinancialPlan(Authentication authentication, @RequestBody FinancialPlanData financialPlanData) {
        return financialPlanStorageService.save(authentication, financialPlanData);
    }

    @DeleteMapping
    public void deleteFinancialPlan(Authentication authentication) {
        financialPlanStorageService.delete(authentication);
    }
}