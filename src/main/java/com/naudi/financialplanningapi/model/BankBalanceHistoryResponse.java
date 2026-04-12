package com.naudi.financialplanningapi.model;

import java.util.List;

public record BankBalanceHistoryResponse(
    TimelineType timelineType,
    List<BankBalanceHistoryCycle> cycles
) {
}