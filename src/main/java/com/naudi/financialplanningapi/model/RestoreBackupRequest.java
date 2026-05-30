package com.naudi.financialplanningapi.model;

import java.util.List;

public record RestoreBackupRequest(
    TimelineType timelineType,
    CyclePeriod currentCycle,
    FinancialPlanData financialPlanData,
    CyclePeriod previousCycle,
    FinancialPlanData previousFinancialPlanData,
    List<CycleBackupEntry> allCycles
) {
}
