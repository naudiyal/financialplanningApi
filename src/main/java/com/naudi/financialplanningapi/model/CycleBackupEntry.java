package com.naudi.financialplanningapi.model;

public record CycleBackupEntry(
    CyclePeriod cycle,
    FinancialPlanData data
) {
}
