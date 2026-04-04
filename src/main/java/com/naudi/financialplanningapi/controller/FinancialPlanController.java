package com.naudi.financialplanningapi.controller;

import com.naudi.financialplanningapi.model.FinancialPlanData;
import com.naudi.financialplanningapi.service.FinancialPlanStorageService;
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
    public FinancialPlanData getFinancialPlan() {
        return financialPlanStorageService.load();
    }

    @PutMapping
    public FinancialPlanData saveFinancialPlan(@RequestBody FinancialPlanData financialPlanData) {
        return financialPlanStorageService.save(financialPlanData);
    }
}