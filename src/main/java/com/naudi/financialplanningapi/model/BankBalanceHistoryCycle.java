package com.naudi.financialplanningapi.model;

import java.util.List;

public record BankBalanceHistoryCycle(
    CyclePeriod cycle,
    List<BankBalanceHistoryPoint> banks
) {
}