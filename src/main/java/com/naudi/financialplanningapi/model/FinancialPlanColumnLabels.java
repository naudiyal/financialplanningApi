package com.naudi.financialplanningapi.model;

import java.util.List;

public record FinancialPlanColumnLabels(
    List<ColumnLabel> creditAccounts,
    List<ColumnLabel> debitExpenses
) {
}