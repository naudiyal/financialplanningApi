package com.naudi.financialplanningapi.model;

public record CreditAccount(
    String id,
    String name,
    double availableCredit,
    String nextPaymentDate,
    boolean paidThisMonth,
    boolean statementCycledAfterPayment,
    String lastStatementDate,
    double lastStatementBalance,
    double creditLimit
) {
}