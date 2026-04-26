package com.naudi.financialplanningapi.controller;

import com.naudi.financialplanningapi.model.AuthUserResponse;
import com.naudi.financialplanningapi.support.AdminEmails;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final Set<String> adminAllowedEmails;

    public AuthController(@Value("${app.admin.emails:naudiyal@gmail.com}") String adminAllowedEmails) {
        this.adminAllowedEmails = AdminEmails.parse(adminAllowedEmails);
    }

    @GetMapping("/me")
    public AuthUserResponse currentUser(Authentication authentication) {
        if (authentication == null
            || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken
            || !(authentication.getPrincipal() instanceof OAuth2User oauth2User)) {
            return AuthUserResponse.unauthenticated();
        }

        Map<String, Object> attributes = oauth2User.getAttributes();
        String email = stringValue(attributes.get("email"));
        String name = stringValue(attributes.getOrDefault("name", authentication.getName()));
        String pictureUrl = stringValue(attributes.get("picture"));
        boolean admin = AdminEmails.contains(adminAllowedEmails, email);

        return new AuthUserResponse(true, admin, email, name, pictureUrl);
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}