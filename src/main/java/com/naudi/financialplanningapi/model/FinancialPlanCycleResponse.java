package com.naudi.financialplanningapi.model;

import java.time.Instant;
import java.util.List;

public record FinancialPlanCycleResponse(
    FinancialPlanData data,
    CycleSlot selectedCycle,
    TimelineType timelineType,
    CyclePeriod currentCycle,
    CyclePeriod previousCycle,
    List<CyclePeriod> closedCycles,
    CyclePeriod selectedClosedCycle,
    boolean hasPreviousCycle,
    boolean readOnly,
    boolean hasSavedPlan,
    boolean canCloseCycle,
    Instant lastCycleSavedAt
) {
}