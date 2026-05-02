package com.naudi.financialplanningapi.controller;

import com.naudi.financialplanningapi.model.FinancialPlanData;
import com.naudi.financialplanningapi.model.BankBalanceHistoryResponse;
import com.naudi.financialplanningapi.model.CloseCycleRequest;
import com.naudi.financialplanningapi.model.RevertCloseCycleRequest;
import com.naudi.financialplanningapi.model.SwitchTimelineRequest;
import com.naudi.financialplanningapi.model.CycleSlot;
import com.naudi.financialplanningapi.model.FinancialPlanCycleResponse;
import com.naudi.financialplanningapi.model.FinancialPlanViewerUserSummary;
import com.naudi.financialplanningapi.model.TimelineType;
import com.naudi.financialplanningapi.service.FinancialPlanStorageService;
import java.util.List;
import com.naudi.financialplanningapi.model.EncryptedHistoryItem;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;

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
    public FinancialPlanCycleResponse getSampleFinancialPlan(
        Authentication authentication,
        @RequestParam(defaultValue = "current") String cycle,
        @RequestParam TimelineType timelineType
    ) {
        return financialPlanStorageService.loadSample(authentication, CycleSlot.fromParameter(cycle), timelineType);
    }

    @GetMapping("/sample/history")
    public BankBalanceHistoryResponse getSampleBankBalanceHistory(
        Authentication authentication,
        @RequestParam TimelineType timelineType,
        @RequestParam(required = false) Integer limit
    ) {
        return financialPlanStorageService.loadSampleBankBalanceHistory(authentication, timelineType, limit);
    }

    @PutMapping("/sample")
    public FinancialPlanCycleResponse saveSampleFinancialPlan(
        Authentication authentication,
        @RequestParam TimelineType timelineType,
        @RequestBody FinancialPlanData financialPlanData
    ) {
        return financialPlanStorageService.saveSample(authentication, timelineType, financialPlanData);
    }

    @PostMapping("/sample/close-cycle")
    public FinancialPlanCycleResponse closeSampleCycle(
        Authentication authentication,
        @RequestParam TimelineType timelineType,
        @RequestBody CloseCycleRequest closeCycleRequest
    ) {
        return financialPlanStorageService.closeSampleCycle(authentication, timelineType, closeCycleRequest);
    }

    @PostMapping("/sample/revert-close-cycle")
    public FinancialPlanCycleResponse revertSampleCloseCycle(
        Authentication authentication,
        @RequestParam TimelineType timelineType,
        @RequestBody RevertCloseCycleRequest revertCloseCycleRequest
    ) {
        return financialPlanStorageService.revertSampleCloseCycle(authentication, timelineType, revertCloseCycleRequest);
    }

    @PostMapping("/sample/switch-timeline")
    public FinancialPlanCycleResponse switchSampleTimeline(
        Authentication authentication,
        @RequestParam TimelineType timelineType,
        @RequestBody SwitchTimelineRequest switchTimelineRequest
    ) {
        return financialPlanStorageService.switchSampleTimeline(authentication, timelineType, switchTimelineRequest);
    }

    @DeleteMapping("/sample")
    public void deleteSampleFinancialPlan(
        Authentication authentication,
        @RequestParam TimelineType timelineType
    ) {
        financialPlanStorageService.deleteSample(authentication, timelineType);
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

    @PutMapping("/history/bulk-encrypt")
    public void bulkEncryptHistory(Authentication authentication, @RequestBody List<EncryptedHistoryItem> items) {
        financialPlanStorageService.bulkEncryptHistory(authentication, items);
    }

    @DeleteMapping
    public void deleteFinancialPlan(Authentication authentication) {
        financialPlanStorageService.delete(authentication);
    }

    @DeleteMapping("/users/{userSub}")
    public void deleteUserFinancialPlan(Authentication authentication, @PathVariable String userSub) {
        financialPlanStorageService.deleteAsAdmin(authentication, userSub);
    }
}