package com.naudi.financialplanningapi.model;

public record FinancialPlanSectionTitles(
    String creditAccounts,
    String debitExpenses,
    String incomeSchedule,
    String incomeScheduleChase
) {
}