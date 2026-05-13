package com.naudi.financialplanningapi.model;

public record ExpenseItem(
    String id,
    String label,
    String payDate,
    String payFromBankId,
    Boolean paid,
    double current,
    double next
) {
}