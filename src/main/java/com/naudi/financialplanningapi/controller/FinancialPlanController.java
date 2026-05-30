package com.naudi.financialplanningapi.controller;

import com.naudi.financialplanningapi.model.FinancialPlanData;
import com.naudi.financialplanningapi.model.BankBalanceHistoryResponse;
import com.naudi.financialplanningapi.model.CloseCycleRequest;
import com.naudi.financialplanningapi.model.RevertCloseCycleRequest;
import com.naudi.financialplanningapi.model.SwitchTimelineRequest;
import com.naudi.financialplanningapi.model.CycleSlot;
import com.naudi.financialplanningapi.model.FinancialPlanCycleResponse;
import com.naudi.financialplanningapi.model.FinancialPlanViewerUserSummary;
import com.naudi.financialplanningapi.model.RestoreBackupRequest;
import com.naudi.financialplanningapi.model.RestoreMultiCycleBackupRequest;
import com.naudi.financialplanningapi.model.TimelineType;
import com.naudi.financialplanningapi.model.UserPremiumStatusRequest;
import com.naudi.financialplanningapi.service.FinancialPlanStorageService;
import java.util.List;
import com.naudi.financialplanningapi.model.EncryptedHistoryItem;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.server.ResponseStatusException;

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
        return financialPlanStorageService.load(authentication, cycle);
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
        return financialPlanStorageService.loadViewerPlan(authentication, userSub, cycle);
    }

    @PutMapping("/users/{userSub}/premium")
    public FinancialPlanViewerUserSummary updateViewerUserPremium(
        Authentication authentication,
        @PathVariable String userSub,
        @RequestBody UserPremiumStatusRequest request
    ) {
        return financialPlanStorageService.updateViewerUserPremium(authentication, userSub, request);
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
        return financialPlanStorageService.loadSample(authentication, cycle, timelineType);
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
        @RequestHeader("X-Expected-User-Sub") String expectedUserSub,
        @RequestParam(defaultValue = "current") String cycle,
        @RequestBody FinancialPlanData financialPlanData
    ) {
        requireExpectedUserSub(authentication, expectedUserSub);
        return financialPlanStorageService.save(authentication, CycleSlot.fromParameter(cycle), financialPlanData);
    }

    @PostMapping("/restore-backup")
    public FinancialPlanCycleResponse restoreBackup(
        Authentication authentication,
        @RequestHeader("X-Expected-User-Sub") String expectedUserSub,
        @RequestBody RestoreBackupRequest restoreBackupRequest
    ) {
        requireExpectedUserSub(authentication, expectedUserSub);
        return financialPlanStorageService.restoreBackup(authentication, restoreBackupRequest);
    }

    @PostMapping("/restore-multi-cycle-backup")
    public FinancialPlanCycleResponse restoreMultiCycleBackup(
        Authentication authentication,
        @RequestHeader("X-Expected-User-Sub") String expectedUserSub,
        @RequestBody RestoreMultiCycleBackupRequest request
    ) {
        requireExpectedUserSub(authentication, expectedUserSub);
        return financialPlanStorageService.restoreMultiCycleBackup(authentication, request);
    }

    @PostMapping("/close-cycle")
    public FinancialPlanCycleResponse closeCycle(
        Authentication authentication,
        @RequestHeader("X-Expected-User-Sub") String expectedUserSub,
        @RequestBody CloseCycleRequest closeCycleRequest
    ) {
        requireExpectedUserSub(authentication, expectedUserSub);
        return financialPlanStorageService.closeCycle(authentication, closeCycleRequest);
    }

    @PostMapping("/revert-close-cycle")
    public FinancialPlanCycleResponse revertCloseCycle(
        Authentication authentication,
        @RequestHeader("X-Expected-User-Sub") String expectedUserSub,
        @RequestBody RevertCloseCycleRequest revertCloseCycleRequest
    ) {
        requireExpectedUserSub(authentication, expectedUserSub);
        return financialPlanStorageService.revertCloseCycle(authentication, revertCloseCycleRequest);
    }

    @PostMapping("/switch-timeline")
    public FinancialPlanCycleResponse switchTimeline(
        Authentication authentication,
        @RequestHeader("X-Expected-User-Sub") String expectedUserSub,
        @RequestBody SwitchTimelineRequest switchTimelineRequest
    ) {
        requireExpectedUserSub(authentication, expectedUserSub);
        return financialPlanStorageService.switchTimeline(authentication, switchTimelineRequest);
    }

    @PutMapping("/history/bulk-encrypt")
    public void bulkEncryptHistory(
        Authentication authentication,
        @RequestHeader("X-Expected-User-Sub") String expectedUserSub,
        @RequestBody List<EncryptedHistoryItem> items
    ) {
        requireExpectedUserSub(authentication, expectedUserSub);
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

    private void requireExpectedUserSub(Authentication authentication, String expectedUserSub) {
        if (expectedUserSub == null || expectedUserSub.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected user identity is required");
        }

        String authenticatedUserSub = authenticatedUserSub(authentication);
        if (!expectedUserSub.equals(authenticatedUserSub)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Your signed-in session changed in another browser window. Reload before saving."
            );
        }
    }

    private String authenticatedUserSub(Authentication authentication) {
        if (authentication == null
            || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken
            || !(authentication.getPrincipal() instanceof OAuth2User oauth2User)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated Google user is required");
        }

        Object userSub = oauth2User.getAttribute("sub");
        if (userSub == null || userSub.toString().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated Google user is missing sub claim");
        }

        return userSub.toString();
    }
}