package com.naudi.financialplanningapi.controller;

import com.naudi.financialplanningapi.model.AcceptTermsRequest;
import com.naudi.financialplanningapi.model.AuthUserResponse;
import com.naudi.financialplanningapi.model.TabAuthTokenResponse;
import com.naudi.financialplanningapi.security.TabSessionTokenService;
import com.naudi.financialplanningapi.support.AdminEmails;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final Set<String> adminAllowedEmails;
    private final Set<String> encryptionExemptEmails;
    private final JdbcClient jdbcClient;
    private final String currentTermsVersion;
    private final TabSessionTokenService tabSessionTokenService;

    public AuthController(
        JdbcClient jdbcClient,
        TabSessionTokenService tabSessionTokenService,
        @Value("${app.admin.emails:naudiyal@gmail.com}") String adminAllowedEmails,
        @Value("${app.encryption.exempt.emails:}") String encryptionExemptEmails,
        @Value("${app.terms.current-version:2026-05-02-v1}") String currentTermsVersion
    ) {
        this.jdbcClient = jdbcClient;
        this.tabSessionTokenService = tabSessionTokenService;
        this.adminAllowedEmails = AdminEmails.parse(adminAllowedEmails);
        this.encryptionExemptEmails = AdminEmails.parse(encryptionExemptEmails);
        this.currentTermsVersion = currentTermsVersion;
    }

    @GetMapping("/me")
    public AuthUserResponse currentUser(Authentication authentication) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);
        if (authenticatedUser == null) {
            return AuthUserResponse.unauthenticated();
        }

        return buildAuthResponse(authenticatedUser);
    }

    @PostMapping("/tab-token")
    public TabAuthTokenResponse issueTabToken(Authentication authentication) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);
        if (authenticatedUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated Google user is required");
        }

        return new TabAuthTokenResponse(
            tabSessionTokenService.issueToken(
                authenticatedUser.userSub(),
                authenticatedUser.email(),
                authenticatedUser.displayName(),
                authenticatedUser.pictureUrl()
            )
        );
    }

    @PostMapping("/terms/accept")
    public AuthUserResponse acceptTerms(
        Authentication authentication,
        @RequestBody AcceptTermsRequest acceptTermsRequest,
        HttpServletRequest httpServletRequest
    ) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);
        if (authenticatedUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated Google user is required");
        }

        String requestedTermsVersion = acceptTermsRequest == null ? null : stringValue(acceptTermsRequest.termsVersion());
        if (requestedTermsVersion == null || requestedTermsVersion.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Terms version is required");
        }
        if (!currentTermsVersion.equals(requestedTermsVersion)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Terms have changed. Reload and accept the current version.");
        }

        jdbcClient.sql("""
            INSERT INTO app_user_terms_acceptance (
                user_sub,
                email,
                display_name,
                terms_version,
                accepted_at,
                ip_address,
                user_agent
            ) VALUES (
                :userSub,
                :email,
                :displayName,
                :termsVersion,
                NOW(),
                :ipAddress,
                :userAgent
            )
            ON CONFLICT (user_sub, terms_version) DO UPDATE
                SET email = EXCLUDED.email,
                    display_name = EXCLUDED.display_name,
                    accepted_at = EXCLUDED.accepted_at,
                    ip_address = EXCLUDED.ip_address,
                    user_agent = EXCLUDED.user_agent
            """)
            .param("userSub", authenticatedUser.userSub())
            .param("email", authenticatedUser.email())
            .param("displayName", authenticatedUser.displayName())
            .param("termsVersion", currentTermsVersion)
            .param("ipAddress", clientIpAddress(httpServletRequest))
            .param("userAgent", stringValue(httpServletRequest.getHeader("User-Agent")))
            .update();

        return buildAuthResponse(authenticatedUser);
    }

    @PostMapping("/terms/reset")
    public AuthUserResponse resetTerms(Authentication authentication) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);
        if (authenticatedUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated Google user is required");
        }

        jdbcClient.sql("""
            DELETE FROM app_user_terms_acceptance
            WHERE user_sub = :userSub
            """)
            .param("userSub", authenticatedUser.userSub())
            .update();

        return buildAuthResponse(authenticatedUser);
    }

    private AuthUserResponse buildAuthResponse(AuthenticatedUser authenticatedUser) {
        TermsAcceptance termsAcceptance = currentTermsAcceptance(authenticatedUser.userSub());
        boolean admin = AdminEmails.contains(adminAllowedEmails, authenticatedUser.email());
        boolean premium = isPremiumUser(authenticatedUser.userSub());
        boolean allowAdminEdit = isAllowAdminEdit(authenticatedUser.userSub());
        boolean encryptionExempt = AdminEmails.contains(encryptionExemptEmails, authenticatedUser.email());
        boolean termsAccepted = termsAcceptance != null;

        return new AuthUserResponse(
            true,
            admin,
            premium,
            allowAdminEdit,
            encryptionExempt,
            termsAccepted,
            currentTermsVersion,
            termsAccepted ? currentTermsVersion : null,
            termsAccepted ? termsAcceptance.acceptedAt().toString() : null,
            authenticatedUser.userSub(),
            authenticatedUser.email(),
            authenticatedUser.displayName(),
            authenticatedUser.pictureUrl()
        );
    }

    @PostMapping("/admin-edit/enable")
    public AuthUserResponse enableAdminEdit(Authentication authentication) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);
        if (authenticatedUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated Google user is required");
        }

        jdbcClient.sql("""
            INSERT INTO app_user_financial_plan_settings (user_sub, email, display_name, timeline_type, allow_admin_edit)
            VALUES (
                :userSub,
                :email,
                :displayName,
                'START_TO_END',
                TRUE
            )
            ON CONFLICT (user_sub)
                DO UPDATE SET
                    allow_admin_edit = TRUE,
                    updated_at = NOW()
            """)
            .param("userSub", authenticatedUser.userSub())
            .param("email", authenticatedUser.email())
            .param("displayName", authenticatedUser.displayName())
            .update();

        return buildAuthResponse(authenticatedUser);
    }

    @PostMapping("/admin-edit/disable")
    public AuthUserResponse disableAdminEdit(Authentication authentication) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);
        if (authenticatedUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated Google user is required");
        }

        jdbcClient.sql("""
            UPDATE app_user_financial_plan_settings
            SET allow_admin_edit = FALSE,
                updated_at = NOW()
            WHERE user_sub = :userSub
            """)
            .param("userSub", authenticatedUser.userSub())
            .update();

        return buildAuthResponse(authenticatedUser);
    }

    private boolean isAllowAdminEdit(String userSub) {
        return jdbcClient.sql("""
            SELECT COALESCE(allow_admin_edit, false)
            FROM app_user_financial_plan_settings
            WHERE user_sub = :userSub
            """)
            .param("userSub", userSub)
            .query(Boolean.class)
            .optional()
            .orElse(false);
    }

    private boolean isPremiumUser(String userSub) {
        return jdbcClient.sql("""
            SELECT COALESCE(is_premium, false)
            FROM app_user_financial_plan_settings
            WHERE user_sub = :userSub
            """)
            .param("userSub", userSub)
            .query(Boolean.class)
            .optional()
            .orElse(false);
    }

    private TermsAcceptance currentTermsAcceptance(String userSub) {
        return jdbcClient.sql("""
            SELECT accepted_at
            FROM app_user_terms_acceptance
            WHERE user_sub = :userSub
              AND terms_version = :termsVersion
            """)
            .param("userSub", userSub)
            .param("termsVersion", currentTermsVersion)
            .query((resultSet, rowNum) -> new TermsAcceptance(
                resultSet.getTimestamp("accepted_at").toInstant()
            ))
            .optional()
            .orElse(null);
    }

    private AuthenticatedUser authenticatedUser(Authentication authentication) {
        if (authentication == null
            || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken
            || !(authentication.getPrincipal() instanceof OAuth2User oauth2User)) {
            return null;
        }

        Map<String, Object> attributes = oauth2User.getAttributes();
        String userSub = stringValue(attributes.get("sub"));
        if (userSub == null || userSub.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated Google user is missing sub claim");
        }

        String email = stringValue(attributes.get("email"));
        String displayName = stringValue(attributes.getOrDefault("name", authentication.getName()));
        String pictureUrl = stringValue(attributes.get("picture"));
        return new AuthenticatedUser(userSub, email, displayName, pictureUrl);
    }

    private String clientIpAddress(HttpServletRequest httpServletRequest) {
        String forwardedFor = stringValue(httpServletRequest.getHeader("X-Forwarded-For"));
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        return stringValue(httpServletRequest.getRemoteAddr());
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private record AuthenticatedUser(String userSub, String email, String displayName, String pictureUrl) {
    }

    private record TermsAcceptance(Instant acceptedAt) {
    }
}