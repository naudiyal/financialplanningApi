package com.naudi.financialplanningapi.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Component tests for AuthController GET /api/auth/me.
 *
 * Real: AuthController, Spring Security filter chain, MVC routing, JSON serialization,
 *       admin email resolution from app.admin.email property, PostgreSQL (Testcontainers).
 * Mocked: Google OAuth — simulated via oauth2Login().
 *
 * AuthController has no persistence dependency. The PostgreSQL container is still
 * started by the shared base class so the full application context wires cleanly.
 */
class AuthControllerTest extends AbstractControllerComponentTest {

    @Autowired
    MockMvc mockMvc;

    // ── unauthenticated ───────────────────────────────────────────────────────

    @Test
    void me_unauthenticated_returnsUnauthenticatedResponse() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(anonymous()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(false))
            .andExpect(jsonPath("$.admin").value(false))
            .andExpect(jsonPath("$.email").doesNotExist())
            .andExpect(jsonPath("$.name").doesNotExist())
            .andExpect(jsonPath("$.pictureUrl").doesNotExist());
    }

    // ── authenticated non-admin ───────────────────────────────────────────────

    @Test
    void me_authenticatedAsNonAdmin_returnsAuthenticatedResponseWithAdminFalse() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                .with(oauth2Login().attributes(attrs -> {
                    attrs.put("sub", "user-sub-456");
                    attrs.put("email", "regularuser@gmail.com");
                    attrs.put("name", "Regular User");
                    attrs.put("picture", "https://example.com/pic.jpg");
                })))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.admin").value(false))
            .andExpect(jsonPath("$.email").value("regularuser@gmail.com"))
            .andExpect(jsonPath("$.name").value("Regular User"))
            .andExpect(jsonPath("$.pictureUrl").value("https://example.com/pic.jpg"));
    }

    // ── authenticated admin ───────────────────────────────────────────────────

    @Test
    void me_authenticatedAsAdmin_returnsAuthenticatedResponseWithAdminTrue() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                .with(oauth2Login().attributes(attrs -> {
                    attrs.put("sub", "admin-sub-123");
                    attrs.put("email", "admin@example.com");
                    attrs.put("name", "Admin User");
                    attrs.put("picture", "https://example.com/admin-pic.jpg");
                })))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.admin").value(true))
            .andExpect(jsonPath("$.email").value("admin@example.com"))
            .andExpect(jsonPath("$.name").value("Admin User"))
            .andExpect(jsonPath("$.pictureUrl").value("https://example.com/admin-pic.jpg"));
    }

    // ── admin email comparison is case-insensitive ────────────────────────────

    @Test
    void me_adminEmailMatchIsCaseInsensitive_returnsAdminTrue() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                .with(oauth2Login().attributes(attrs -> {
                    attrs.put("sub", "admin-sub-123");
                    attrs.put("email", "ADMIN@EXAMPLE.COM");
                    attrs.put("name", "Admin User");
                })))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.admin").value(true));
    }

    // ── missing optional claims ───────────────────────────────────────────────

    @Test
    void me_authenticatedWithNoPictureUrl_returnsPictureUrlNull() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                .with(oauth2Login().attributes(attrs -> {
                    attrs.put("sub", "user-sub-789");
                    attrs.put("email", "nopic@gmail.com");
                    attrs.put("name", "No Pic User");
                })))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.pictureUrl").doesNotExist());
    }
}
