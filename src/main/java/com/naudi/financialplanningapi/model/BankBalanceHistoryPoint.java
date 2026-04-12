package com.naudi.financialplanningapi.model;

public record BankBalanceHistoryPoint(
    String bankId,
    String bankName,
    double monthEndBalanceMinusDues
) {
}