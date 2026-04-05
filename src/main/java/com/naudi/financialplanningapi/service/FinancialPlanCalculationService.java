package com.naudi.financialplanningapi.service;

import com.naudi.financialplanningapi.model.BalanceItem;
import com.naudi.financialplanningapi.model.CreditAccount;
import com.naudi.financialplanningapi.model.ExpenseItem;
import com.naudi.financialplanningapi.model.FinancialPlanData;
import com.naudi.financialplanningapi.model.FinancialPlanSummary;
import com.naudi.financialplanningapi.model.IncomeItem;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FinancialPlanCalculationService {

    public FinancialPlanData withCalculatedSummary(FinancialPlanData financialPlanData) {
        FinancialPlanSummary summary = calculateSummary(financialPlanData);
        return new FinancialPlanData(
            financialPlanData.creditAccounts(),
            financialPlanData.incomeItems(),
            financialPlanData.balanceItems(),
            financialPlanData.planoExpenses(),
            financialPlanData.sanfordExpenses(),
            financialPlanData.otherExpenses(),
            financialPlanData.columnLabels(),
            financialPlanData.sectionTitles(),
            financialPlanData.incomeSubsections(),
            summary
        );
    }

    private FinancialPlanSummary calculateSummary(FinancialPlanData financialPlanData) {
        double totalAvailableCredit = financialPlanData.creditAccounts().stream()
            .mapToDouble(CreditAccount::availableCredit)
            .sum();
        double totalStatementBalance = financialPlanData.creditAccounts().stream()
            .mapToDouble(CreditAccount::lastStatementBalance)
            .sum();
        double totalCreditLimit = financialPlanData.creditAccounts().stream()
            .mapToDouble(CreditAccount::creditLimit)
            .sum();
        double totalDue = financialPlanData.creditAccounts().stream()
            .mapToDouble(account -> account.creditLimit() - account.availableCredit())
            .sum();
        double totalCurrentMonthPayment = financialPlanData.creditAccounts().stream()
            .mapToDouble(account -> account.paidThisMonth() ? 0 : account.lastStatementBalance())
            .sum();
        double totalNextMonthBalance = financialPlanData.creditAccounts().stream()
            .mapToDouble(this::calculateNextMonthBalance)
            .sum();
        double totalUtilization = totalCreditLimit > 0 ? (totalDue / totalCreditLimit) * 100 : 0;

        List<ExpenseItem> allExpenses = new ArrayList<>();
        allExpenses.addAll(financialPlanData.planoExpenses());
        allExpenses.addAll(financialPlanData.sanfordExpenses());
        allExpenses.addAll(financialPlanData.otherExpenses());

        double debitCardExpensesTotalCurrent = allExpenses.stream().mapToDouble(ExpenseItem::current).sum();
        double debitCardExpensesTotalNext = allExpenses.stream().mapToDouble(ExpenseItem::next).sum();
        double expenseGrandTotal = totalCurrentMonthPayment + debitCardExpensesTotalCurrent;
        double nextMonthExpenseGrandTotal = totalNextMonthBalance + debitCardExpensesTotalNext;
        double monthAfterNextMonthExpense = totalDue - totalCurrentMonthPayment - totalNextMonthBalance;

        double biMonthlySalary = findIncomeAmount(financialPlanData.incomeItems(), "bi-monthly-salary");
        double salaryTransferToChase = biMonthlySalary * 2;
        double salaryTransfersToPnc = 2000 * 2;
        double totalSalaryPerMonth = salaryTransferToChase;
        double salary15th = findIncomeAmount(financialPlanData.incomeItems(), "salary-15th") == 0 ? 0 : biMonthlySalary;
        double salary1st = findIncomeAmount(financialPlanData.incomeItems(), "salary-1st") == 0 ? 0 : biMonthlySalary;

        double checkingAccountBalanceChase = findBalanceAmount(financialPlanData.balanceItems(), "checking-balance-chase");
        double additionalPaymentsChase = findBalanceAmount(financialPlanData.balanceItems(), "additional-payments-chase");
        double additionalIncomeChase = findBalanceAmount(financialPlanData.balanceItems(), "additional-income-chase");
        double chaseCdBalance = findBalanceAmount(financialPlanData.balanceItems(), "chase-cd-balance");
        double checkingAccountBalancePnc = findBalanceAmount(financialPlanData.balanceItems(), "checking-balance-pnc");
        double additionalOtherIncome = findBalanceAmount(financialPlanData.balanceItems(), "additional-other-income");

        double totalBalanceChase = salary15th + salary1st + checkingAccountBalanceChase - additionalPaymentsChase;
        double checkingAccountBalanceMonthEndChase = totalBalanceChase + additionalIncomeChase - expenseGrandTotal;
        double netBalanceMonthEnd = checkingAccountBalanceMonthEndChase + chaseCdBalance + checkingAccountBalancePnc + additionalOtherIncome;
        double savingsNextMonth = salaryTransferToChase - nextMonthExpenseGrandTotal;

        return new FinancialPlanSummary(
            roundCurrency(totalAvailableCredit),
            roundCurrency(totalStatementBalance),
            roundCurrency(totalCreditLimit),
            roundCurrency(totalDue),
            roundCurrency(totalCurrentMonthPayment),
            roundCurrency(totalNextMonthBalance),
            roundPercentage(totalUtilization),
            roundCurrency(debitCardExpensesTotalCurrent),
            roundCurrency(debitCardExpensesTotalNext),
            roundCurrency(expenseGrandTotal),
            roundCurrency(nextMonthExpenseGrandTotal),
            roundCurrency(monthAfterNextMonthExpense),
            roundCurrency(salaryTransferToChase),
            roundCurrency(salaryTransfersToPnc),
            roundCurrency(totalSalaryPerMonth),
            roundCurrency(totalBalanceChase),
            roundCurrency(checkingAccountBalanceMonthEndChase),
            roundCurrency(netBalanceMonthEnd),
            roundCurrency(savingsNextMonth)
        );
    }

    private double calculateNextMonthBalance(CreditAccount account) {
        double totalDueForCard = account.creditLimit() - account.availableCredit();
        if (account.paidThisMonth()) {
            return account.statementCycledAfterPayment() ? account.lastStatementBalance() : totalDueForCard;
        }
        return totalDueForCard - account.lastStatementBalance();
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
}