package com.naudi.financialplanningapi.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naudi.financialplanningapi.model.CloseCycleRequest;
import com.naudi.financialplanningapi.model.CyclePeriod;
import com.naudi.financialplanningapi.model.RevertCloseCycleRequest;
import com.naudi.financialplanningapi.model.SwitchTimelineRequest;
import com.naudi.financialplanningapi.model.TimelineType;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Component tests for FinancialPlanController.
 *
 * Real: FinancialPlanController, FinancialPlanStorageService, FinancialPlanCalculationService,
 *       Spring Security filter chain, MVC routing, parameter binding, JSON serialization,
 *       admin enforcement, Flyway migrations, PostgreSQL (Testcontainers).
 * Mocked: Google OAuth — simulated via oauth2Login().
 *
 * Test scope covers:
 * - 401 for unauthenticated requests on all main routes
 * - Real service responses for authenticated requests against an empty test database
 * - 403 produced by the real ensureAdminAccess() check for admin-restricted operations
 * - 400 produced by the real service for invalid request payloads
 */
class FinancialPlanControllerTest extends AbstractControllerComponentTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    // ── GET /api/financial-plan ───────────────────────────────────────────────

    @Test
    void getFinancialPlan_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/financial-plan"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getFinancialPlan_authenticated_returns200WithSeededPlanForNewUser() throws Exception {
        mockMvc.perform(get("/api/financial-plan")
                .with(oauth2Login()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.selectedCycle").value("current"))
            .andExpect(jsonPath("$.timelineType").exists());
    }

    @Test
    void getFinancialPlan_previousCycle_withNoStoredData_returns404() throws Exception {
        mockMvc.perform(get("/api/financial-plan?cycle=previous")
                .with(oauth2Login()))
            .andExpect(status().isNotFound());
    }

    // ── GET /api/financial-plan/users ─────────────────────────────────────────

    @Test
    void listViewerUsers_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/financial-plan/users"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void listViewerUsers_admin_returns200WithEmptyList() throws Exception {
        mockMvc.perform(get("/api/financial-plan/users")
                .with(oauth2Login().attributes(attrs -> {
                    attrs.put("email", "admin@example.com");
                })))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void listViewerUsers_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/financial-plan/users")
                .with(oauth2Login().attributes(attrs -> {
                    attrs.put("email", "user@example.com");
                })))
            .andExpect(status().isForbidden());
    }

    // ── GET /api/financial-plan/viewer ────────────────────────────────────────

    @Test
    void getViewerPlan_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/financial-plan/viewer?userSub=sub-1"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getViewerPlan_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/financial-plan/viewer?userSub=sub-1")
                .with(oauth2Login().attributes(attrs -> {
                    attrs.put("email", "user@example.com");
                })))
            .andExpect(status().isForbidden());
    }

    @Test
    void getViewerPlan_admin_withUnknownUserSub_returns404() throws Exception {
        mockMvc.perform(get("/api/financial-plan/viewer?userSub=unknown-sub")
                .with(oauth2Login().attributes(attrs -> {
                    attrs.put("email", "admin@example.com");
                })))
            .andExpect(status().isNotFound());
    }

    // ── GET /api/financial-plan/history ──────────────────────────────────────

    @Test
    void getBankBalanceHistory_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/financial-plan/history"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getBankBalanceHistory_authenticated_returns200WithEmptyHistory() throws Exception {
        mockMvc.perform(get("/api/financial-plan/history")
                .with(oauth2Login()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timelineType").exists())
            .andExpect(jsonPath("$.cycles").isArray());
    }

    @Test
    void getBankBalanceHistory_withLimitParam_returns200() throws Exception {
        mockMvc.perform(get("/api/financial-plan/history?limit=6")
                .with(oauth2Login()))
            .andExpect(status().isOk());
    }

    // ── GET /api/financial-plan/sample ────────────────────────────────────────

    @Test
    void getSamplePlan_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/financial-plan/sample?timelineType=MID_TO_MID"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getSamplePlan_authenticated_returns200WithSamplePlan() throws Exception {
        mockMvc.perform(get("/api/financial-plan/sample?timelineType=MID_TO_MID")
                .with(oauth2Login()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timelineType").value("MID_TO_MID"));
    }

    // ── PUT /api/financial-plan/sample ────────────────────────────────────────

    @Test
    void saveSamplePlan_unauthenticated_returns401() throws Exception {
        mockMvc.perform(put("/api/financial-plan/sample?timelineType=MID_TO_MID")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void saveSamplePlan_nonAdmin_returns403() throws Exception {
        mockMvc.perform(put("/api/financial-plan/sample?timelineType=MID_TO_MID")
                .with(oauth2Login().attributes(attrs -> {
                    attrs.put("email", "user@example.com");
                }))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    // ── DELETE /api/financial-plan/sample ────────────────────────────────────

    @Test
    void deleteSamplePlan_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/financial-plan/sample?timelineType=MID_TO_MID"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteSamplePlan_nonAdmin_returns403() throws Exception {
        mockMvc.perform(delete("/api/financial-plan/sample?timelineType=MID_TO_MID")
                .with(oauth2Login().attributes(attrs -> {
                    attrs.put("email", "user@example.com");
                })))
            .andExpect(status().isForbidden());
    }

    @Test
    void deleteSamplePlan_admin_returns200() throws Exception {
        mockMvc.perform(delete("/api/financial-plan/sample?timelineType=MID_TO_MID")
                .with(oauth2Login().attributes(attrs -> {
                    attrs.put("email", "admin@example.com");
                })))
            .andExpect(status().isOk());
    }

    // ── PUT /api/financial-plan ───────────────────────────────────────────────

    @Test
    void saveFinancialPlan_unauthenticated_returns401() throws Exception {
        mockMvc.perform(put("/api/financial-plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void saveFinancialPlan_authenticated_returns200() throws Exception {
        mockMvc.perform(put("/api/financial-plan")
                .with(oauth2Login())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk());
    }

    // ── POST /api/financial-plan/close-cycle ─────────────────────────────────

    @Test
    void closeCycle_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/financial-plan/close-cycle")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CloseCycleRequest(null, null))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void closeCycle_authenticated_withNullPayload_returns400() throws Exception {
        mockMvc.perform(post("/api/financial-plan/close-cycle")
                .with(oauth2Login())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CloseCycleRequest(null, null))))
            .andExpect(status().isBadRequest());
    }

    // ── POST /api/financial-plan/switch-timeline ──────────────────────────────

    @Test
    void switchTimeline_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/financial-plan/switch-timeline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SwitchTimelineRequest(null, null, null))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void switchTimeline_authenticated_withNullPayload_returns400() throws Exception {
        mockMvc.perform(post("/api/financial-plan/switch-timeline")
                .with(oauth2Login())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SwitchTimelineRequest(null, null, null))))
            .andExpect(status().isBadRequest());
    }

    // ── DELETE /api/financial-plan ────────────────────────────────────────────

    @Test
    void deleteFinancialPlan_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/financial-plan"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteFinancialPlan_authenticated_returns200() throws Exception {
        mockMvc.perform(delete("/api/financial-plan")
                .with(oauth2Login()))
            .andExpect(status().isOk());
    }
}
