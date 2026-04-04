package com.naudi.financialplanningapi.model;

public record ExpenseItem(
    String id,
    String label,
    String payDate,
    double current,
    double next
) {
}