package com.naudi.financialplanningapi.model;

public record ExpenseItem(
    String id,
    String label,
    String payDate,
    String payFromBankId,
    double current,
    double next
) {
}