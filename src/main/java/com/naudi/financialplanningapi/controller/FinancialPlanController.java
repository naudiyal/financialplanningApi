package com.naudi.financialplanningapi.controller;

import com.naudi.financialplanningapi.model.FinancialPlanData;
import com.naudi.financialplanningapi.model.BankBalanceHistoryResponse;
import com.naudi.financialplanningapi.model.CloseCycleRequest;
import com.naudi.financialplanningapi.model.RevertCloseCycleRequest;
import com.naudi.financialplanningapi.model.SwitchTimelineRequest;
import com.naudi.financialplanningapi.model.CycleSlot;
import com.naudi.financialplanningapi.model.FinancialPlanCycleResponse;
import com.naudi.financialplanningapi.model.FinancialPlanViewerUserSummary;
import com.naudi.financialplanningapi.service.FinancialPlanStorageService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/financial-plan")
public class FinancialPlanController {

    private final FinancialPlanStorageService financialPlanStorageService;

    public FinancialPlanController(FinancialPlanStorageService financialPlanStorageService) {
        this.financialPlanStorageService = financialPlanStorageService;
    }

    @GetMapping
    public FinancialPlanCycleResponse getFinancialPlan(
        Authentication authentication,
        @RequestParam(defaultValue = "current") String cycle
    ) {
        return financialPlanStorageService.load(authentication, CycleSlot.fromParameter(cycle));
    }

    @GetMapping("/users")
    public List<FinancialPlanViewerUserSummary> listViewerUsers(Authentication authentication) {
        return financialPlanStorageService.listViewerUsers(authentication);
    }

    @GetMapping("/viewer")
    public FinancialPlanCycleResponse getViewerFinancialPlan(
        Authentication authentication,
        @RequestParam String userSub,
        @RequestParam(defaultValue = "current") String cycle
    ) {
        return financialPlanStorageService.loadViewerPlan(authentication, userSub, CycleSlot.fromParameter(cycle));
    }

    @GetMapping("/history")
    public BankBalanceHistoryResponse getBankBalanceHistory(
        Authentication authentication,
        @RequestParam(required = false) Integer limit
    ) {
        return financialPlanStorageService.loadBankBalanceHistory(authentication, limit);
    }

    @GetMapping("/viewer/history")
    public BankBalanceHistoryResponse getViewerBankBalanceHistory(
        Authentication authentication,
        @RequestParam String userSub,
        @RequestParam(required = false) Integer limit
    ) {
        return financialPlanStorageService.loadViewerBankBalanceHistory(authentication, userSub, limit);
    }

    @GetMapping("/sample")
    public FinancialPlanData getSampleFinancialPlan(Authentication authentication) {
        return financialPlanStorageService.loadSample(authentication);
    }

    @PutMapping
    public FinancialPlanCycleResponse saveFinancialPlan(
        Authentication authentication,
        @RequestParam(defaultValue = "current") String cycle,
        @RequestBody FinancialPlanData financialPlanData
    ) {
        return financialPlanStorageService.save(authentication, CycleSlot.fromParameter(cycle), financialPlanData);
    }

    @PostMapping("/close-cycle")
    public FinancialPlanCycleResponse closeCycle(Authentication authentication, @RequestBody CloseCycleRequest closeCycleRequest) {
        return financialPlanStorageService.closeCycle(authentication, closeCycleRequest);
    }

    @PostMapping("/revert-close-cycle")
    public FinancialPlanCycleResponse revertCloseCycle(
        Authentication authentication,
        @RequestBody RevertCloseCycleRequest revertCloseCycleRequest
    ) {
        return financialPlanStorageService.revertCloseCycle(authentication, revertCloseCycleRequest);
    }

    @PostMapping("/switch-timeline")
    public FinancialPlanCycleResponse switchTimeline(
        Authentication authentication,
        @RequestBody SwitchTimelineRequest switchTimelineRequest
    ) {
        return financialPlanStorageService.switchTimeline(authentication, switchTimelineRequest);
    }

    @DeleteMapping
    public void deleteFinancialPlan(Authentication authentication) {
        financialPlanStorageService.delete(authentication);
    }
}