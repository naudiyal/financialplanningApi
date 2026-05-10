package com.naudi.financialplanningapi.model;

public record RestoreBackupRequest(
    TimelineType timelineType,
    CyclePeriod currentCycle,
    FinancialPlanData financialPlanData,
    CyclePeriod previousCycle,
    FinancialPlanData previousFinancialPlanData
) {
}
