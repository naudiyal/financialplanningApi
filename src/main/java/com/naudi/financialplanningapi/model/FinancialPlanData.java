package com.naudi.financialplanningapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FinancialPlanData(
    List<CreditAccount> creditAccounts,
    List<IncomeItem> incomeItems,
    List<BalanceItem> balanceItems,
    List<ExpenseItem> planoExpenses,
    List<ExpenseItem> sanfordExpenses,
    List<ExpenseItem> otherExpenses,
    FinancialPlanColumnLabels columnLabels,
    FinancialPlanSectionTitles sectionTitles,
    FinancialPlanViewModes viewModes,
    String firstPaycheckDate,
    String secondPaycheckDate,
    String thirdPaycheckDate,
    boolean additionalPaycheckExpectedNextMonth,
    Double defaultBankWarningThreshold,
    List<IncomeSubsection> incomeSubsections,
    FinancialPlanSummary summary,
    String notes,
    String encryptedData,
    String encryptionIv,
    String pinVerify,
    String pinVerifyIv
) {
}