package com.naudi.financialplanningapi.model;

public record FinancialPlanCycleResponse(
    FinancialPlanData data,
    CycleSlot selectedCycle,
    TimelineType timelineType,
    CyclePeriod currentCycle,
    CyclePeriod previousCycle,
    boolean hasPreviousCycle,
    boolean readOnly,
    boolean hasSavedPlan,
    boolean canCloseCycle
) {
}