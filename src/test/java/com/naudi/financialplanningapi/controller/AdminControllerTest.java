package com.naudi.financialplanningapi.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Component tests for AdminController.
 *
 * Real: AdminController, FinancialPlanStorageService, Spring Security filter chain,
 *       MVC routing, admin enforcement logic, PostgreSQL (Testcontainers).
 * Mocked: Google OAuth — simulated via oauth2Login().
 *
 * Admin enforcement exercised through the real service ensureAdminAccess() check.
 * With an empty test database, normalize and repair operations process zero rows.
 */
class AdminControllerTest extends AbstractControllerComponentTest {

    @Autowired
    MockMvc mockMvc;

    // ── normalize-all-plans ───────────────────────────────────────────────────

    @Test
    void normalizeAllPlans_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/admin/normalize-all-plans"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void normalizeAllPlans_admin_returns200WithNormalizedCount() throws Exception {
        mockMvc.perform(post("/api/admin/normalize-all-plans")
                .with(oauth2Login().attributes(attrs -> {
                    attrs.put("sub", "admin-sub-123");
                    attrs.put("email", "admin@example.com");
                    attrs.put("name", "Admin User");
                })))
            .andExpect(status().isOk())
            .andExpect(content().string("Normalized 0 stored cycles."));
    }

    @Test
    void normalizeAllPlans_nonAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/normalize-all-plans")
                .with(oauth2Login().attributes(attrs -> {
                    attrs.put("sub", "user-sub-456");
                    attrs.put("email", "user@example.com");
                    attrs.put("name", "Regular User");
                })))
            .andExpect(status().isForbidden());
    }

    // ── repair-start-to-end-cycle-dates ──────────────────────────────────────

    @Test
    void repairStartToEndCycleDates_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/admin/repair-start-to-end-cycle-dates"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void repairStartToEndCycleDates_admin_returns200WithRepairedCount() throws Exception {
        mockMvc.perform(post("/api/admin/repair-start-to-end-cycle-dates")
                .with(oauth2Login().attributes(attrs -> {
                    attrs.put("sub", "admin-sub-123");
                    attrs.put("email", "admin@example.com");
                    attrs.put("name", "Admin User");
                })))
            .andExpect(status().isOk())
            .andExpect(content().string("Repaired 0 malformed start-to-end cycle rows."));
    }

    @Test
    void repairStartToEndCycleDates_nonAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/repair-start-to-end-cycle-dates")
                .with(oauth2Login().attributes(attrs -> {
                    attrs.put("sub", "user-sub-456");
                    attrs.put("email", "user@example.com");
                    attrs.put("name", "Regular User");
                })))
            .andExpect(status().isForbidden());
    }
}
