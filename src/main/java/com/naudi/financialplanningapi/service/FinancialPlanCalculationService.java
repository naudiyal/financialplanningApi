package com.naudi.financialplanningapi.service;

import com.naudi.financialplanningapi.model.BalanceItem;
import com.naudi.financialplanningapi.model.BankBalanceHistoryPoint;
import com.naudi.financialplanningapi.model.CreditAccount;
import com.naudi.financialplanningapi.model.ExpenseItem;
import com.naudi.financialplanningapi.model.FinancialPlanData;
import com.naudi.financialplanningapi.model.FinancialPlanSectionTitles;
import com.naudi.financialplanningapi.model.IncomeItem;
import com.naudi.financialplanningapi.model.IncomeSubsection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class FinancialPlanCalculationService {

    private static final String DEFAULT_BANK_EXPENSE_SOURCE_ID = "default-bank";

    public FinancialPlanData startNewCycle(FinancialPlanData financialPlanData) {
        List<CreditAccount> refreshedCreditAccounts = financialPlanData.creditAccounts().stream()
            .map(account -> new CreditAccount(
                account.id(),
                account.name(),
                account.availableCredit(),
                advanceIsoDateByOneMonth(account.nextPaymentDate()),
                false,
                false,
                advanceIsoDateByOneMonth(account.lastStatementDate()),
                account.lastStatementBalance(),
                account.creditLimit()
            ))
            .toList();

        List<ExpenseItem> planoExpenses = advanceExpenseCycle(financialPlanData.planoExpenses());
        List<ExpenseItem> sanfordExpenses = advanceExpenseCycle(financialPlanData.sanfordExpenses());
        List<ExpenseItem> otherExpenses = advanceExpenseCycle(financialPlanData.otherExpenses());
        List<IncomeItem> refreshedIncomeItems = resetIncomeItemsForNewCycle(financialPlanData.incomeItems());
        List<IncomeSubsection> refreshedIncomeSubsections = resetIncomeSubsectionsForNewCycle(financialPlanData.incomeSubsections());

        return withCalculatedSummary(new FinancialPlanData(
            refreshedCreditAccounts,
            refreshedIncomeItems,
            financialPlanData.balanceItems(),
            planoExpenses,
            sanfordExpenses,
            otherExpenses,
            financialPlanData.columnLabels(),
            financialPlanData.sectionTitles(),
            financialPlanData.viewModes(),
            null,
            null,
            null,
            false,
            financialPlanData.defaultBankWarningThreshold(),
            refreshedIncomeSubsections,
            financialPlanData.summary(),
            financialPlanData.notes(),
            null, null, null, null
        ));
    }

    public FinancialPlanData withCalculatedSummary(FinancialPlanData financialPlanData) {
        return financialPlanData;
    }

    public List<BankBalanceHistoryPoint> buildBankBalanceHistoryPoints(FinancialPlanData financialPlanData) {
        if (financialPlanData.encryptedData() != null) {
            return List.of();
        }
        double biMonthlySalary = findIncomeAmount(financialPlanData.incomeItems(), "bi-monthly-salary");
        double firstPaycheck = findIncomeAmount(financialPlanData.incomeItems(), "first-paycheck") == 0 ? 0 : biMonthlySalary;
        double secondPaycheck = findIncomeAmount(financialPlanData.incomeItems(), "second-paycheck") == 0 ? 0 : biMonthlySalary;
        double thirdPaycheck = (!isIsoLocalDate(financialPlanData.thirdPaycheckDate()) || findIncomeAmount(financialPlanData.incomeItems(), "third-paycheck") == 0) ? 0 : biMonthlySalary;
        double checkingAccountBalanceChase = findBalanceAmount(financialPlanData.balanceItems(), "checking-balance-chase");
        double additionalPaymentsChase = findBalanceAmount(financialPlanData.balanceItems(), "additional-payments-chase");
        double additionalIncomeChase = findBalanceAmount(financialPlanData.balanceItems(), "additional-income-chase");

        Set<String> validExpensePayFromIds = new HashSet<>();
        validExpensePayFromIds.add(DEFAULT_BANK_EXPENSE_SOURCE_ID);
        financialPlanData.incomeSubsections().forEach(subsection -> validExpensePayFromIds.add(subsection.id()));

        List<ExpenseItem> allExpenses = new ArrayList<>();
        allExpenses.addAll(financialPlanData.planoExpenses());
        allExpenses.addAll(financialPlanData.sanfordExpenses());
        allExpenses.addAll(financialPlanData.otherExpenses());

        double defaultBankDebitExpensesCurrent = allExpenses.stream()
            .filter(item -> DEFAULT_BANK_EXPENSE_SOURCE_ID.equals(normalizeExpensePayFromId(item.payFromBankId(), validExpensePayFromIds)))
            .mapToDouble(ExpenseItem::current)
            .sum();
        double creditCardCurrentMonthPayments = financialPlanData.creditAccounts().stream()
            .mapToDouble(account -> account.paidThisMonth() ? 0 : account.lastStatementBalance())
            .sum();
        double defaultBankCurrentDues = creditCardCurrentMonthPayments + defaultBankDebitExpensesCurrent;
        FinancialPlanSectionTitles sectionTitles = financialPlanData.sectionTitles();
        String defaultBankName = sectionTitles == null
            || sectionTitles.defaultBank() == null
            || sectionTitles.defaultBank().isBlank()
                ? "Default Bank"
                : sectionTitles.defaultBank();
        double defaultBankMonthEndBalanceMinusDues = calculateBankMonthEndBalance(
            firstPaycheck + secondPaycheck + thirdPaycheck + checkingAccountBalanceChase - additionalPaymentsChase,
            additionalIncomeChase,
            defaultBankCurrentDues
        );

        List<BankBalanceHistoryPoint> historyPoints = new ArrayList<>();
        historyPoints.add(new BankBalanceHistoryPoint(
            DEFAULT_BANK_EXPENSE_SOURCE_ID,
            defaultBankName,
            roundCurrency(defaultBankMonthEndBalanceMinusDues)
        ));

        for (IncomeSubsection subsection : financialPlanData.incomeSubsections()) {
            double subsectionCurrentDues = allExpenses.stream()
                .filter(item -> subsection.id().equals(normalizeExpensePayFromId(item.payFromBankId(), validExpensePayFromIds)))
                .mapToDouble(ExpenseItem::current)
                .sum();
            double subsectionMonthEndBalanceMinusDues = calculateBankMonthEndBalance(
                calculateIncomeSubsectionTotalBalance(subsection),
                subsection.additionalIncome(),
                subsectionCurrentDues
            );
            historyPoints.add(new BankBalanceHistoryPoint(
                subsection.id(),
                subsection.title() == null || subsection.title().isBlank() ? "Unnamed Bank" : subsection.title(),
                roundCurrency(subsectionMonthEndBalanceMinusDues)
            ));
        }

        return historyPoints;
    }

    private double calculateIncomeSubsectionStartingBalance(IncomeSubsection subsection) {
        double firstPaycheck = subsection.midMonthSalaryArrived() ? 0 : subsection.biMonthlySalary();
        double secondPaycheck = subsection.monthEndSalaryArrived() ? 0 : subsection.biMonthlySalary();
        double thirdPaycheck = (subsection.thirdPaycheckArrived() || subsection.thirdPaycheckDate() == null || subsection.thirdPaycheckDate().isBlank()) ? 0 : subsection.biMonthlySalary();

        return subsection.checkingBalance() + firstPaycheck + secondPaycheck + thirdPaycheck;
    }

    private double calculateIncomeSubsectionTotalBalance(IncomeSubsection subsection) {
        return calculateIncomeSubsectionStartingBalance(subsection) - subsection.additionalPayments();
    }

    private List<IncomeItem> resetIncomeItemsForNewCycle(List<IncomeItem> incomeItems) {
        double biMonthlySalary = findIncomeAmount(incomeItems, "bi-monthly-salary");

        return incomeItems.stream()
            .map(item -> {
                if ("first-paycheck".equals(item.id()) || "second-paycheck".equals(item.id()) || "third-paycheck".equals(item.id())) {
                    return new IncomeItem(
                        item.id(),
                        item.label(),
                        biMonthlySalary,
                        item.month(),
                        item.note()
                    );
                }

                return item;
            })
            .toList();
    }

    private List<IncomeSubsection> resetIncomeSubsectionsForNewCycle(List<IncomeSubsection> incomeSubsections) {
        return incomeSubsections.stream()
            .map(subsection -> new IncomeSubsection(
                subsection.id(),
                subsection.title(),
                subsection.biMonthlySalaryLabel(),
                subsection.biMonthlySalary(),
                subsection.midMonthSalaryLabel(),
                null,
                false,
                subsection.monthEndSalaryLabel(),
                null,
                false,
                subsection.checkingBalanceLabel(),
                subsection.checkingBalance(),
                subsection.warningThreshold(),
                subsection.additionalPaymentsLabel(),
                subsection.additionalPayments(),
                subsection.totalBalanceLabel(),
                subsection.additionalIncomeLabel(),
                subsection.additionalIncome(),
                subsection.monthEndBalanceLabel(),
                subsection.thirdPaycheckLabel(),
                null,
                false,
                subsection.additionalPaycheckExpectedLabel(),
                false
            ))
            .toList();
    }

    private List<ExpenseItem> advanceExpenseCycle(List<ExpenseItem> expenseItems) {
        return expenseItems.stream()
            .map(item -> new ExpenseItem(
                item.id(),
                item.label(),
                advanceIsoDateByOneMonth(item.payDate()),
                item.payFromBankId(),
                false,
                item.next(),
                item.next()
            ))
            .toList();
    }

    private double calculateBankMonthEndBalance(double totalBalance, double additionalIncome, double currentDues) {
        return totalBalance + additionalIncome - currentDues;
    }

    private String normalizeExpensePayFromId(String payFromBankId, Set<String> validExpensePayFromIds) {
        if (payFromBankId == null || payFromBankId.isBlank() || !validExpensePayFromIds.contains(payFromBankId)) {
            return DEFAULT_BANK_EXPENSE_SOURCE_ID;
        }

        return payFromBankId;
    }

    private String advanceIsoDateByOneMonth(String value) {
        String[] parts = value.split("-");
        if (parts.length != 3) {
            return value;
        }

        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        int day = Integer.parseInt(parts[2]);

        java.time.LocalDate targetDate = java.time.LocalDate.of(year, month, 1).plusMonths(1);
        int lastDayOfTargetMonth = targetDate.lengthOfMonth();
        return targetDate.withDayOfMonth(Math.min(day, lastDayOfTargetMonth)).toString();
    }

        private double findIncomeAmount(List<IncomeItem> incomeItems, String id) {
        return incomeItems.stream()
            .filter(item -> id.equals(item.id()))
            .findFirst()
            .map(IncomeItem::amount)
            .orElse(0d);
    }

        private double findBalanceAmount(List<BalanceItem> balanceItems, String id) {
        return balanceItems.stream()
            .filter(item -> id.equals(item.id()))
            .findFirst()
            .map(BalanceItem::amount)
            .orElse(0d);
    }

    private double roundCurrency(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double roundPercentage(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
    private boolean isIsoLocalDate(String value) {
        return value != null && value.matches("\\d{4}-\\d{2}-\\d{2}");
    }}