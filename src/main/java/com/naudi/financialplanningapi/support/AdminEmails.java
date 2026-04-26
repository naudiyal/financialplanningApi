package com.naudi.financialplanningapi.support;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class AdminEmails {

    private AdminEmails() {
    }

    public static Set<String> parse(String rawAdminEmails) {
        if (rawAdminEmails == null || rawAdminEmails.isBlank()) {
            return Set.of();
        }

        return Arrays.stream(rawAdminEmails.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .map(value -> value.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    }

    public static boolean contains(Set<String> adminEmails, String email) {
        if (email == null || email.isBlank()) {
            return false;
        }

        return adminEmails.contains(email.trim().toLowerCase(Locale.ROOT));
    }
}