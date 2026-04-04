package com.naudi.financialplanningapi.model;

import java.util.List;

public record FinancialPlanData(
    List<CreditAccount> creditAccounts,
    List<IncomeItem> incomeItems,
    List<BalanceItem> balanceItems,
    List<ExpenseItem> planoExpenses,
    List<ExpenseItem> sanfordExpenses,
    List<ExpenseItem> otherExpenses,
    FinancialPlanColumnLabels columnLabels,
    FinancialPlanSectionTitles sectionTitles,
    List<IncomeSubsection> incomeSubsections,
    FinancialPlanSummary summary
) {
}