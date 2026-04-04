package com.naudi.financialplanningapi.model;

public record IncomeItem(
    String id,
    String label,
    double amount,
    String month,
    String note
) {
}