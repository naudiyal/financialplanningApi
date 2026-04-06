package com.naudi.financialplanningapi.model;

public record RevertCloseCycleRequest(
    CyclePeriod expectedCurrentCycle,
    CyclePeriod expectedPreviousCycle
) {
}