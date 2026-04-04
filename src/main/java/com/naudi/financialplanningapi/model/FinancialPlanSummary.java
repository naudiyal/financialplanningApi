package com.naudi.financialplanningapi.model;

public record FinancialPlanSummary(
    double totalAvailableCredit,
    double totalStatementBalance,
    double totalCreditLimit,
    double totalDue,
    double totalCurrentMonthPayment,
    double totalNextMonthBalance,
    double totalUtilization,
    double debitCardExpensesTotalCurrent,
    double debitCardExpensesTotalNext,
    double expenseGrandTotal,
    double nextMonthExpenseGrandTotal,
    double monthAfterNextMonthExpense,
    double salaryTransferToChase,
    double salaryTransfersToPnc,
    double totalSalaryPerMonth,
    double totalBalanceChase,
    double checkingAccountBalanceMonthEndChase,
    double netBalanceMonthEnd,
    double netBalanceNextMonthEnd
) {
}