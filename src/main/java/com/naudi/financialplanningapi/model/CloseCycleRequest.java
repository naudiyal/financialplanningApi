package com.naudi.financialplanningapi.model;

public record CloseCycleRequest(
    FinancialPlanData financialPlanData,
    CyclePeriod expectedCurrentCycle
) {
}