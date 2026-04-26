package com.naudi.financialplanningapi.model;

public record FinancialPlanViewModes(
    String creditAccounts,
    String debitExpenses,
    String bankAccounts
) {
}