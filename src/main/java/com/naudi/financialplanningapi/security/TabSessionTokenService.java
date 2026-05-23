package com.naudi.financialplanningapi.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TabSessionTokenService {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final Duration TOKEN_TTL = Duration.ofDays(7);

    private final ObjectMapper objectMapper;
    private final byte[] secretKey;

    public TabSessionTokenService(
        ObjectMapper objectMapper,
        @Value("${app.auth.tab-token.secret:}") String configuredSecret
    ) {
        this.objectMapper = objectMapper;
        this.secretKey = configuredSecret == null || configuredSecret.isBlank()
            ? randomSecret()
            : configuredSecret.getBytes(StandardCharsets.UTF_8);
    }

    public String issueToken(String userSub, String email, String displayName, String pictureUrl) {
        Instant expiresAt = Instant.now().plus(TOKEN_TTL);
        TabTokenClaims claims = new TabTokenClaims(userSub, email, displayName, pictureUrl, expiresAt.getEpochSecond());

        try {
            String payload = URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(claims));
            String signature = URL_ENCODER.encodeToString(sign(payload));
            return payload + "." + signature;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize tab auth token", exception);
        }
    }

    public Optional<TabTokenClaims> validate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        int separator = token.indexOf('.');
        if (separator <= 0 || separator == token.length() - 1) {
            return Optional.empty();
        }

        String payload = token.substring(0, separator);
        String encodedSignature = token.substring(separator + 1);

        try {
            byte[] expectedSignature = sign(payload);
            byte[] actualSignature = URL_DECODER.decode(encodedSignature);
            if (!java.security.MessageDigest.isEqual(expectedSignature, actualSignature)) {
                return Optional.empty();
            }

            TabTokenClaims claims = objectMapper.readValue(URL_DECODER.decode(payload), TabTokenClaims.class);
            if (claims.userSub() == null || claims.userSub().isBlank()) {
                return Optional.empty();
            }
            if (claims.expiresAtEpochSecond() <= Instant.now().getEpochSecond()) {
                return Optional.empty();
            }
            return Optional.of(claims);
        } catch (IllegalArgumentException | IOException exception) {
            return Optional.empty();
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secretKey, HMAC_SHA256));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to sign tab auth token", exception);
        }
    }

    private byte[] randomSecret() {
        byte[] generatedSecret = new byte[32];
        new SecureRandom().nextBytes(generatedSecret);
        return generatedSecret;
    }

    public record TabTokenClaims(
        String userSub,
        String email,
        String displayName,
        String pictureUrl,
        long expiresAtEpochSecond
    ) {
    }
}