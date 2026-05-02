package com.naudi.financialplanningapi.model;

import java.time.Instant;

public record FinancialPlanViewerUserSummary(
    String userSub,
    String email,
    String displayName,
    Instant lastUpdatedAt,
    boolean encryptionExempt
) {
}