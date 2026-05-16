package com.naudi.financialplanningapi.model;

public record IncomeSubsection(
    String id,
    String title,
    String biMonthlySalaryLabel,
    double biMonthlySalary,
    String midMonthSalaryLabel,
    String firstPaycheckDate,
    boolean midMonthSalaryArrived,
    String monthEndSalaryLabel,
    String secondPaycheckDate,
    boolean monthEndSalaryArrived,
    String checkingBalanceLabel,
    double checkingBalance,
    String additionalPaymentsLabel,
    double additionalPayments,
    String totalBalanceLabel,
    String additionalIncomeLabel,
    double additionalIncome,
    String monthEndBalanceLabel
) {
}