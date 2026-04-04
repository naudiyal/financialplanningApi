package com.naudi.financialplanningapi.model;

public record BalanceItem(
    String id,
    String label,
    double amount,
    String month
) {
}