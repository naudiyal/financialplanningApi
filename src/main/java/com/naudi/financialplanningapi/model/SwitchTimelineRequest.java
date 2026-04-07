package com.naudi.financialplanningapi.model;

public record SwitchTimelineRequest(
    FinancialPlanData financialPlanData,
    CyclePeriod expectedCurrentCycle,
    TimelineType targetTimelineType
) {
}