package com.naudi.financialplanningapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naudi.financialplanningapi.model.BalanceItem;
import com.naudi.financialplanningapi.model.ColumnLabel;
import com.naudi.financialplanningapi.model.CreditAccount;
import com.naudi.financialplanningapi.model.ExpenseItem;
import com.naudi.financialplanningapi.model.FinancialPlanColumnLabels;
import com.naudi.financialplanningapi.model.FinancialPlanData;
import com.naudi.financialplanningapi.model.FinancialPlanSectionTitles;
import com.naudi.financialplanningapi.model.IncomeSubsection;
import com.naudi.financialplanningapi.model.IncomeItem;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class FinancialPlanStorageService {

    private static final List<String> CREDIT_ACCOUNT_IDS = List.of(
        "apple-card",
        "samsclub-store-card",
        "american-express",
        "amazon-store-card",
        "wellsfargo-card",
        "target-card",
        "barclays-frontier",
        "fidelity-card",
        "citi-bestbuy",
        "boa-spirit",
        "chase-amazon-prime",
        "chase-marriott",
        "chase-sapphire-reserve"
    );
    private static final List<String> INCOME_ITEM_IDS = List.of(
        "bi-monthly-salary",
        "salary-15th",
        "salary-1st",
        "salary-transfer-chase-month",
        "salary-transfer-pnc-home-loans",
        "total-salary-per-month"
    );
    private static final List<String> BALANCE_ITEM_IDS = List.of(
        "checking-balance-chase",
        "additional-payments-chase",
        "total-balance-chase",
        "additional-income-chase",
        "checking-balance-month-end-chase",
        "chase-cd-balance",
        "checking-balance-pnc",
        "additional-other-income",
        "net-balance-month-end",
        "net-balance-next-month-end"
    );
    private static final List<String> PLANO_EXPENSE_IDS = List.of(
        "plano-water",
        "plano-internet-att",
        "plano-hoa",
        "plano-electricity"
    );
    private static final List<String> SANFORD_EXPENSE_IDS = List.of(
        "sanford-water",
        "sanford-electricity",
        "sanford-internet-att",
        "sanford-hoa-quarterly"
    );
    private static final List<String> OTHER_EXPENSE_IDS = List.of(
        "other-att-mobile",
        "other-529-college-savings",
        "other-geico-car-insurance"
    );
    private static final List<ColumnLabel> CREDIT_ACCOUNT_COLUMN_LABELS = List.of(
        new ColumnLabel("account", "Account"),
        new ColumnLabel("available-credit", "Avail Credit"),
        new ColumnLabel("pay-date", "Pay Date"),
        new ColumnLabel("paid", "Paid"),
        new ColumnLabel("statement-cycled", "Stmt Cycled"),
        new ColumnLabel("statement-date", "Stmt Date"),
        new ColumnLabel("statement-balance", "Stmt Balance"),
        new ColumnLabel("credit-limit", "Limit"),
        new ColumnLabel("due", "Due"),
        new ColumnLabel("current-payment", "Curr Payment"),
        new ColumnLabel("next-balance", "Next Balance"),
        new ColumnLabel("utilization", "Util %")
    );
    private static final List<ColumnLabel> DEBIT_EXPENSE_COLUMN_LABELS = List.of(
        new ColumnLabel("expense", "Expense"),
        new ColumnLabel("pay-date", "Pay Date"),
        new ColumnLabel("current-month", "Current Month"),
        new ColumnLabel("next-month", "Next Month")
    );
    private static final FinancialPlanSectionTitles DEFAULT_SECTION_TITLES = new FinancialPlanSectionTitles(
        "Credit & Card Accounts",
        "Debit Card Expenses",
        "Income Schedule",
        "Chase"
    );

    private final ObjectMapper objectMapper;
    private final Path storagePath;
    private final FinancialPlanCalculationService financialPlanCalculationService;

    public FinancialPlanStorageService(
        ObjectMapper objectMapper,
        FinancialPlanCalculationService financialPlanCalculationService,
        @Value("${app.storage.path:./data/financial-plan.json}") String storagePath
    ) {
        this.objectMapper = objectMapper;
        this.financialPlanCalculationService = financialPlanCalculationService;
        this.storagePath = Path.of(storagePath).toAbsolutePath().normalize();
        initializeStorage();
    }

    public synchronized FinancialPlanData load() {
        try {
            FinancialPlanData storedData = normalizeIds(objectMapper.readValue(storagePath.toFile(), FinancialPlanData.class));
            FinancialPlanData enrichedData = financialPlanCalculationService.withCalculatedSummary(storedData);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storagePath.toFile(), enrichedData);
            return enrichedData;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read financial plan data", exception);
        }
    }

    public synchronized FinancialPlanData save(FinancialPlanData financialPlanData) {
        try {
            FinancialPlanData normalizedData = normalizeIds(financialPlanData);
            FinancialPlanData enrichedData = financialPlanCalculationService.withCalculatedSummary(normalizedData);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storagePath.toFile(), enrichedData);
            return enrichedData;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save financial plan data", exception);
        }
    }

    private void initializeStorage() {
        try {
            Files.createDirectories(storagePath.getParent());
            if (Files.exists(storagePath)) {
                return;
            }

            ClassPathResource defaultData = new ClassPathResource("default-financial-plan.json");
            try (InputStream inputStream = defaultData.getInputStream()) {
                Files.copy(inputStream, storagePath);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize financial plan storage", exception);
        }
    }

    private FinancialPlanData normalizeIds(FinancialPlanData financialPlanData) {
        return new FinancialPlanData(
            normalizeCreditAccounts(financialPlanData.creditAccounts()),
            normalizeIncomeItems(financialPlanData.incomeItems()),
            normalizeBalanceItems(financialPlanData.balanceItems()),
            normalizeExpenseItems(financialPlanData.planoExpenses(), PLANO_EXPENSE_IDS),
            normalizeExpenseItems(financialPlanData.sanfordExpenses(), SANFORD_EXPENSE_IDS),
            normalizeExpenseItems(financialPlanData.otherExpenses(), OTHER_EXPENSE_IDS),
            normalizeColumnLabels(financialPlanData.columnLabels()),
            normalizeSectionTitles(financialPlanData.sectionTitles()),
            normalizeIncomeSubsections(financialPlanData.incomeSubsections()),
            financialPlanData.summary()
        );
    }

    private List<IncomeSubsection> normalizeIncomeSubsections(List<IncomeSubsection> incomeSubsections) {
        List<IncomeSubsection> normalized = new ArrayList<>();
        if (incomeSubsections == null) {
            return normalized;
        }

        for (int index = 0; index < incomeSubsections.size(); index++) {
            IncomeSubsection subsection = incomeSubsections.get(index);
            String defaultTitle = "Subsection " + (index + 1);
            normalized.add(new IncomeSubsection(
                normalizeDynamicId(subsection.id(), "income-subsection", index + 1),
                normalizeText(subsection.title(), defaultTitle),
                normalizeText(subsection.biMonthlySalaryLabel(), "Bi-monthly salary"),
                subsection.biMonthlySalary(),
                normalizeText(subsection.midMonthSalaryLabel(), "Mid month salary Arrived"),
                subsection.midMonthSalaryArrived(),
                normalizeText(subsection.monthEndSalaryLabel(), "Month end salary Arrived"),
                subsection.monthEndSalaryArrived(),
                normalizeText(subsection.checkingBalanceLabel(), "Checking Account Balance"),
                subsection.checkingBalance(),
                normalizeText(subsection.additionalPaymentsLabel(), "Additional Payments"),
                subsection.additionalPayments(),
                normalizeText(subsection.totalBalanceLabel(), "Total Balance"),
                normalizeText(subsection.additionalIncomeLabel(), "Additional Income"),
                subsection.additionalIncome(),
                normalizeText(subsection.monthEndBalanceLabel(), "Month End Balance")
            ));
        }

        return normalized;
    }

    private FinancialPlanSectionTitles normalizeSectionTitles(FinancialPlanSectionTitles sectionTitles) {
        if (sectionTitles == null) {
            return DEFAULT_SECTION_TITLES;
        }

        return new FinancialPlanSectionTitles(
            normalizeText(sectionTitles.creditAccounts(), DEFAULT_SECTION_TITLES.creditAccounts()),
            normalizeText(sectionTitles.debitExpenses(), DEFAULT_SECTION_TITLES.debitExpenses()),
            normalizeText(sectionTitles.incomeSchedule(), DEFAULT_SECTION_TITLES.incomeSchedule()),
            normalizeText(sectionTitles.defaultBank(), DEFAULT_SECTION_TITLES.defaultBank())
        );
    }

    private FinancialPlanColumnLabels normalizeColumnLabels(FinancialPlanColumnLabels columnLabels) {
        if (columnLabels == null) {
            return new FinancialPlanColumnLabels(CREDIT_ACCOUNT_COLUMN_LABELS, DEBIT_EXPENSE_COLUMN_LABELS);
        }

        return new FinancialPlanColumnLabels(
            normalizeColumnLabelSet(columnLabels.creditAccounts(), CREDIT_ACCOUNT_COLUMN_LABELS),
            normalizeColumnLabelSet(columnLabels.debitExpenses(), DEBIT_EXPENSE_COLUMN_LABELS)
        );
    }

    private List<ColumnLabel> normalizeColumnLabelSet(List<ColumnLabel> actualLabels, List<ColumnLabel> defaultLabels) {
        List<ColumnLabel> normalized = new ArrayList<>();
        for (int index = 0; index < defaultLabels.size(); index++) {
            ColumnLabel defaultLabel = defaultLabels.get(index);
            ColumnLabel actualLabel = actualLabels != null && index < actualLabels.size() ? actualLabels.get(index) : null;
            String id = actualLabel != null && actualLabel.id() != null && !actualLabel.id().isBlank() ? actualLabel.id() : defaultLabel.id();
            String label = actualLabel != null && actualLabel.label() != null && !actualLabel.label().isBlank() ? actualLabel.label() : defaultLabel.label();
            normalized.add(new ColumnLabel(id, label));
        }
        return normalized;
    }

    private List<CreditAccount> normalizeCreditAccounts(List<CreditAccount> creditAccounts) {
        List<CreditAccount> normalized = new ArrayList<>();
        for (int index = 0; index < creditAccounts.size(); index++) {
            CreditAccount account = creditAccounts.get(index);
            normalized.add(new CreditAccount(
                normalizeId(account.id(), CREDIT_ACCOUNT_IDS, index, account.name()),
                account.name(),
                account.availableCredit(),
                account.nextPaymentDate(),
                account.paidThisMonth(),
                account.statementCycledAfterPayment(),
                account.lastStatementDate(),
                account.lastStatementBalance(),
                account.creditLimit()
            ));
        }
        return normalized;
    }

    private List<IncomeItem> normalizeIncomeItems(List<IncomeItem> incomeItems) {
        List<IncomeItem> normalized = new ArrayList<>();
        for (int index = 0; index < incomeItems.size(); index++) {
            IncomeItem item = incomeItems.get(index);
            normalized.add(new IncomeItem(
                normalizeId(item.id(), INCOME_ITEM_IDS, index, item.label()),
                item.label(),
                item.amount(),
                item.month(),
                item.note()
            ));
        }
        return normalized;
    }

    private List<BalanceItem> normalizeBalanceItems(List<BalanceItem> balanceItems) {
        List<BalanceItem> normalized = new ArrayList<>();
        for (int index = 0; index < balanceItems.size(); index++) {
            BalanceItem item = balanceItems.get(index);
            normalized.add(new BalanceItem(
                normalizeId(item.id(), BALANCE_ITEM_IDS, index, item.label()),
                item.label(),
                item.amount(),
                item.month()
            ));
        }
        return normalized;
    }

    private List<ExpenseItem> normalizeExpenseItems(List<ExpenseItem> expenseItems, List<String> defaults) {
        List<ExpenseItem> normalized = new ArrayList<>();
        for (int index = 0; index < expenseItems.size(); index++) {
            ExpenseItem item = expenseItems.get(index);
            normalized.add(new ExpenseItem(
                normalizeId(item.id(), defaults, index, item.label()),
                item.label(),
                item.payDate(),
                item.current(),
                item.next()
            ));
        }
        return normalized;
    }

    private String normalizeId(String currentId, List<String> defaults, int index, String fallbackText) {
        if (currentId != null && !currentId.isBlank()) {
            return currentId;
        }
        if (index < defaults.size()) {
            return defaults.get(index);
        }
        return fallbackText.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private String normalizeDynamicId(String currentId, String prefix, int suffix) {
        if (currentId != null && !currentId.isBlank()) {
            return currentId;
        }
        return prefix + "-" + suffix;
    }

    private String normalizeText(String value, String defaultValue) {
        return value != null && !value.isBlank() ? value : defaultValue;
    }
}