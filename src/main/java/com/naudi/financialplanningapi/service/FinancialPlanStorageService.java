package com.naudi.financialplanningapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naudi.financialplanningapi.model.EncryptedHistoryItem;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.naudi.financialplanningapi.model.BalanceItem;
import com.naudi.financialplanningapi.model.BankBalanceHistoryCycle;
import com.naudi.financialplanningapi.model.BankBalanceHistoryPoint;
import com.naudi.financialplanningapi.model.BankBalanceHistoryResponse;
import com.naudi.financialplanningapi.model.CloseCycleRequest;
import com.naudi.financialplanningapi.model.ColumnLabel;
import com.naudi.financialplanningapi.model.CreditAccount;
import com.naudi.financialplanningapi.model.CyclePeriod;
import com.naudi.financialplanningapi.model.CycleSlot;
import com.naudi.financialplanningapi.model.ExpenseItem;
import com.naudi.financialplanningapi.model.FinancialPlanColumnLabels;
import com.naudi.financialplanningapi.model.FinancialPlanCycleResponse;
import com.naudi.financialplanningapi.model.FinancialPlanData;
import com.naudi.financialplanningapi.model.FinancialPlanSectionTitles;
import com.naudi.financialplanningapi.model.FinancialPlanViewModes;
import com.naudi.financialplanningapi.model.FinancialPlanViewerUserSummary;
import com.naudi.financialplanningapi.model.IncomeSubsection;
import com.naudi.financialplanningapi.model.IncomeItem;
import com.naudi.financialplanningapi.model.RevertCloseCycleRequest;
import com.naudi.financialplanningapi.model.SwitchTimelineRequest;
import com.naudi.financialplanningapi.model.TimelineType;
import com.naudi.financialplanningapi.support.AdminEmails;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FinancialPlanStorageService {

    private static final String PLAN_TABLE = "app_user_financial_plan_cycle";
    private static final String SETTINGS_TABLE = "app_user_financial_plan_settings";
    private static final String HISTORY_TABLE = "app_user_financial_plan_cycle_history";

    private static final String NEW_USER_TEMPLATE_RESOURCE = "new-user-financial-plan.json";
    private static final String SAMPLE_PLAN_MID_TO_MID_USER_SUB = "sample-mid-to-mid-mybetterbudget-com";
    private static final String SAMPLE_PLAN_START_TO_END_USER_SUB = "sample-start-to-end-mybetterbudget-com";
    private static final String SAMPLE_PLAN_EMAIL = "sample@mybetterbudget.com";
    private static final String SAMPLE_PLAN_DISPLAY_NAME = "Sample Plan";
    private static final String SAMPLE_SOURCE_EMAIL = "innaudiyal@gmail.com";
    private static final String SAVINGS_NEXT_MONTH_ID = "savings-next-month";
    private static final String DEFAULT_BANK_EXPENSE_SOURCE_ID = "default-bank";
    private static final String LEGACY_NEXT_MONTH_ID = "net-balance-next-month-end";
    private static final String FIRST_PAYCHECK_ID = "first-paycheck";
    private static final String SECOND_PAYCHECK_ID = "second-paycheck";
    private static final String SAVINGS_NEXT_MONTH_LABEL = "Savings Next Cycle";
    private static final String PREVIOUS_SAVINGS_NEXT_MONTH_LABEL = "Savings Next Month";
    private static final String LEGACY_NEXT_MONTH_LABEL = "Net Balance @Next Month End";
    private static final double CLOSE_CYCLE_CURRENT_EXPENSE_TOLERANCE = 0.004d;
    private static final int MAX_HISTORY_LIMIT = 24;

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
        FIRST_PAYCHECK_ID,
        SECOND_PAYCHECK_ID,
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
        SAVINGS_NEXT_MONTH_ID
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
        new ColumnLabel("statement-date", "Prev Cycle Stmt Date"),
        new ColumnLabel("pay-date", "Payment Date"),
        new ColumnLabel("paid", "Paid"),
        new ColumnLabel("statement-cycled", "Stmt Cycled?"),
        new ColumnLabel("statement-balance", "Latest Stmt Balance"),
        new ColumnLabel("credit-limit", "Credit Limit"),
        new ColumnLabel("due", "Total Due"),
        new ColumnLabel("current-payment", "Curr Payment"),
        new ColumnLabel("next-balance", "Next Balance"),
        new ColumnLabel("utilization", "Util %")
    );
    private static final List<ColumnLabel> DEBIT_EXPENSE_COLUMN_LABELS = List.of(
        new ColumnLabel("expense", "Expense"),
        new ColumnLabel("pay-date", "Pay Date"),
        new ColumnLabel("pay-from", "Pay From"),
        new ColumnLabel("current-month", "Current Month"),
        new ColumnLabel("next-month", "Next Month")
    );
    private static final FinancialPlanSectionTitles DEFAULT_SECTION_TITLES = new FinancialPlanSectionTitles(
        "Credit Card Accounts",
        "Debit Card Expenses",
        "Bank Accounts",
        "Default Bank"
    );
    private static final FinancialPlanViewModes DEFAULT_VIEW_MODES = new FinancialPlanViewModes(
        "table",
        "table",
        "table"
    );

    private final ObjectMapper objectMapper;
    private final JdbcClient jdbcClient;
    private final FinancialPlanCalculationService financialPlanCalculationService;
    private final Set<String> adminAllowedEmails;
    private final Set<String> encryptionExemptEmails;
    private final int defaultBankBalanceHistoryCycleCount;
    private final CollectionType bankBalanceHistoryPointListType;

    public FinancialPlanStorageService(
        ObjectMapper objectMapper,
        JdbcClient jdbcClient,
        FinancialPlanCalculationService financialPlanCalculationService,
        @Value("${app.admin.emails:naudiyal@gmail.com}") String adminAllowedEmails,
        @Value("${app.encryption.exempt.emails:}") String encryptionExemptEmails,
        @Value("${app.bank-balance-history.cycle-count:12}") int defaultBankBalanceHistoryCycleCount
    ) {
        this.objectMapper = objectMapper;
        this.jdbcClient = jdbcClient;
        this.financialPlanCalculationService = financialPlanCalculationService;
        this.adminAllowedEmails = AdminEmails.parse(adminAllowedEmails);
        this.encryptionExemptEmails = AdminEmails.parse(encryptionExemptEmails);
        this.defaultBankBalanceHistoryCycleCount = defaultBankBalanceHistoryCycleCount;
        this.bankBalanceHistoryPointListType = objectMapper.getTypeFactory()
            .constructCollectionType(List.class, BankBalanceHistoryPoint.class);
    }

    public FinancialPlanCycleResponse load(Authentication authentication, CycleSlot cycleSlot) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);
        TimelineType timelineType = timelineTypeFor(authenticatedUser.userSub());
        StoredCycle currentCycle = loadStoredCycle(authenticatedUser, CycleSlot.CURRENT);
        StoredCycle previousCycle = loadStoredCycle(authenticatedUser, CycleSlot.PREVIOUS);

        CyclePeriod resolvedCurrentCycle = cyclePeriodFor(currentCycle, CycleSlot.CURRENT, timelineType);
        CyclePeriod resolvedPreviousCycle = previousCycle != null ? cyclePeriodFor(previousCycle, CycleSlot.PREVIOUS, timelineType) : null;
        FinancialPlanData currentData = currentCycle != null ? currentCycle.financialPlanData() : buildSeededPlanSafely();

        if (cycleSlot == CycleSlot.PREVIOUS) {
            if (previousCycle == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Previous cycle not found");
            }

            return buildResponse(
                financialPlanCalculationService.withCalculatedSummary(previousCycle.financialPlanData()),
                cycleSlot,
                timelineType,
                resolvedCurrentCycle,
                resolvedPreviousCycle,
                hasSavedPlan(currentCycle),
                canCloseCycle(currentData),
                currentCycle,
                previousCycle
            );
        }

        return buildResponse(
            currentData,
            cycleSlot,
            timelineType,
            resolvedCurrentCycle,
            resolvedPreviousCycle,
            hasSavedPlan(currentCycle),
            canCloseCycle(currentData),
            currentCycle,
            previousCycle
        );
    }

    public FinancialPlanCycleResponse save(Authentication authentication, CycleSlot cycleSlot, FinancialPlanData financialPlanData) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);
        if (cycleSlot == CycleSlot.PREVIOUS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Previous cycle is read only");
        }

        try {
            TimelineType timelineType = timelineTypeFor(authenticatedUser.userSub());
            StoredCycle currentCycle = loadStoredCycle(authenticatedUser, CycleSlot.CURRENT);
            StoredCycle previousCycle = loadStoredCycle(authenticatedUser, CycleSlot.PREVIOUS);
            CyclePeriod currentPeriod = cyclePeriodFor(currentCycle, CycleSlot.CURRENT, timelineType);
            FinancialPlanData normalizedData = normalizeIds(financialPlanData);
            FinancialPlanData enrichedData = financialPlanCalculationService.withCalculatedSummary(normalizedData);
            upsertSettings(authenticatedUser, timelineType);
            upsertPlan(authenticatedUser, CycleSlot.CURRENT, currentPeriod, enrichedData);
            StoredCycle savedCurrentCycle = loadStoredCycle(authenticatedUser, CycleSlot.CURRENT);
            return buildResponse(
                enrichedData,
                CycleSlot.CURRENT,
                timelineType,
                currentPeriod,
                previousCycle != null ? cyclePeriodFor(previousCycle, CycleSlot.PREVIOUS, timelineType) : null,
                true,
                canCloseCycle(enrichedData),
                savedCurrentCycle,
                previousCycle
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save financial plan data", exception);
        }
    }

    public List<FinancialPlanViewerUserSummary> listViewerUsers(Authentication authentication) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);
        ensureAdminAccess(authenticatedUser);

        return jdbcClient.sql("""
                                SELECT user_sub,
                                             MAX(NULLIF(email, '')) AS email,
                                             MAX(NULLIF(display_name, '')) AS display_name,
                                             MAX(updated_at) AS last_updated_at
                FROM app_user_financial_plan_cycle
                                WHERE user_sub <> :userSub
                  AND user_sub <> :sampleMidUserSub
                  AND user_sub <> :sampleStartUserSub
                                GROUP BY user_sub
                                ORDER BY COALESCE(MAX(NULLIF(display_name, '')), MAX(NULLIF(email, '')), user_sub)
                """)
            .param("userSub", authenticatedUser.userSub())
            .param("sampleMidUserSub", SAMPLE_PLAN_MID_TO_MID_USER_SUB)
            .param("sampleStartUserSub", SAMPLE_PLAN_START_TO_END_USER_SUB)
            .query((resultSet, rowNum) -> new FinancialPlanViewerUserSummary(
                resultSet.getString("user_sub"),
                resultSet.getString("email"),
                resultSet.getString("display_name"),
                resultSet.getTimestamp("last_updated_at") != null
                    ? resultSet.getTimestamp("last_updated_at").toInstant()
                    : null,
                AdminEmails.contains(encryptionExemptEmails, resultSet.getString("email"))
            ))
            .list();
    }

    public FinancialPlanCycleResponse loadViewerPlan(Authentication authentication, String userSub, CycleSlot cycleSlot) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);
        ensureAdminAccess(authenticatedUser);
        TimelineType viewerTimelineType = timelineTypeFor(userSub);

        if (userSub == null || userSub.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Viewed user is required");
        }

        StoredCycle currentCycle = loadStoredCycle(userSub, CycleSlot.CURRENT);
        StoredCycle previousCycle = loadStoredCycle(userSub, CycleSlot.PREVIOUS);

        if (currentCycle == null && previousCycle == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Viewed user was not found");
        }

        CyclePeriod resolvedPreviousCycle = previousCycle != null ? cyclePeriodFor(previousCycle, CycleSlot.PREVIOUS, viewerTimelineType) : null;
        CyclePeriod resolvedCurrentCycle = currentCycle != null
            ? cyclePeriodFor(currentCycle, CycleSlot.CURRENT, viewerTimelineType)
            : nextCyclePeriod(resolvedPreviousCycle);
        boolean viewerHasSavedPlan = hasSavedPlan(currentCycle != null ? currentCycle : previousCycle);

        if (cycleSlot == CycleSlot.PREVIOUS) {
            if (previousCycle == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Previous cycle not found");
            }

            return buildResponse(
                financialPlanCalculationService.withCalculatedSummary(previousCycle.financialPlanData()),
                cycleSlot,
                viewerTimelineType,
                resolvedCurrentCycle,
                resolvedPreviousCycle,
                viewerHasSavedPlan,
                false,
                true,
                currentCycle,
                previousCycle
            );
        }

        if (currentCycle == null) {
            return buildResponse(
                financialPlanCalculationService.withCalculatedSummary(previousCycle.financialPlanData()),
                CycleSlot.PREVIOUS,
                viewerTimelineType,
                resolvedCurrentCycle,
                resolvedPreviousCycle,
                viewerHasSavedPlan,
                false,
                true,
                currentCycle,
                previousCycle
            );
        }

        return buildResponse(
            financialPlanCalculationService.withCalculatedSummary(currentCycle.financialPlanData()),
            cycleSlot,
            viewerTimelineType,
            resolvedCurrentCycle,
            resolvedPreviousCycle,
            viewerHasSavedPlan,
            false,
            true,
            currentCycle,
            previousCycle
        );
    }

    public BankBalanceHistoryResponse loadBankBalanceHistory(Authentication authentication, Integer limit) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);
        TimelineType timelineType = timelineTypeFor(authenticatedUser.userSub());
        return loadBankBalanceHistory(authenticatedUser.userSub(), timelineType, sanitizeHistoryLimit(limit));
    }

    public BankBalanceHistoryResponse loadViewerBankBalanceHistory(Authentication authentication, String userSub, Integer limit) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);
        ensureAdminAccess(authenticatedUser);

        if (userSub == null || userSub.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Viewed user is required");
        }

        TimelineType timelineType = timelineTypeFor(userSub);
        return loadBankBalanceHistory(userSub, timelineType, sanitizeHistoryLimit(limit));
    }

    public FinancialPlanCycleResponse closeCycle(Authentication authentication, CloseCycleRequest closeCycleRequest) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);

        if (closeCycleRequest == null || closeCycleRequest.financialPlanData() == null || closeCycleRequest.expectedCurrentCycle() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current cycle payload is required");
        }

        try {
            TimelineType timelineType = timelineTypeFor(authenticatedUser.userSub());
            StoredCycle currentCycle = loadStoredCycle(authenticatedUser, CycleSlot.CURRENT);
            StoredCycle previousCycle = loadStoredCycle(authenticatedUser, CycleSlot.PREVIOUS);
            CyclePeriod currentPeriod = cyclePeriodFor(currentCycle, CycleSlot.CURRENT, timelineType);

            if (!currentPeriod.equals(closeCycleRequest.expectedCurrentCycle())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Current cycle changed. Reload before closing the cycle.");
            }

            FinancialPlanData archivedCurrentData = financialPlanCalculationService.withCalculatedSummary(
                normalizeIds(closeCycleRequest.financialPlanData())
            );

            if (!canCloseCycle(archivedCurrentData)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Current cycle is not ready to close");
            }

            upsertSettings(authenticatedUser, timelineType);
            archiveCycleHistory(authenticatedUser, timelineType, previousCycle);
            upsertPlan(authenticatedUser, CycleSlot.PREVIOUS, currentPeriod, archivedCurrentData);

            CyclePeriod nextCurrentPeriod = nextCyclePeriod(currentPeriod);
            FinancialPlanData nextCurrentData = financialPlanCalculationService.startNewCycle(archivedCurrentData);
            upsertPlan(authenticatedUser, CycleSlot.CURRENT, nextCurrentPeriod, nextCurrentData);
            StoredCycle savedCurrentCycle = loadStoredCycle(authenticatedUser, CycleSlot.CURRENT);
            StoredCycle savedPreviousCycle = loadStoredCycle(authenticatedUser, CycleSlot.PREVIOUS);

            return buildResponse(
                nextCurrentData,
                CycleSlot.CURRENT,
                timelineType,
                nextCurrentPeriod,
                currentPeriod,
                true,
                canCloseCycle(nextCurrentData),
                savedCurrentCycle,
                savedPreviousCycle
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to close financial cycle", exception);
        }
    }

    public FinancialPlanCycleResponse revertCloseCycle(Authentication authentication, RevertCloseCycleRequest revertCloseCycleRequest) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);

        if (revertCloseCycleRequest == null
            || revertCloseCycleRequest.expectedCurrentCycle() == null
            || revertCloseCycleRequest.expectedPreviousCycle() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current and previous cycle metadata are required");
        }

        try {
            TimelineType timelineType = timelineTypeFor(authenticatedUser.userSub());
            StoredCycle currentCycle = loadStoredCycle(authenticatedUser, CycleSlot.CURRENT);
            StoredCycle previousCycle = loadStoredCycle(authenticatedUser, CycleSlot.PREVIOUS);

            if (currentCycle == null || previousCycle == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "No recently closed cycle is available to reset");
            }

            CyclePeriod currentPeriod = cyclePeriodFor(currentCycle, CycleSlot.CURRENT, timelineType);
            CyclePeriod previousPeriod = cyclePeriodFor(previousCycle, CycleSlot.PREVIOUS, timelineType);

            if (!currentPeriod.equals(revertCloseCycleRequest.expectedCurrentCycle())
                || !previousPeriod.equals(revertCloseCycleRequest.expectedPreviousCycle())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cycle state changed. Reload before resetting.");
            }

            FinancialPlanData restoredCurrentData = financialPlanCalculationService.withCalculatedSummary(previousCycle.financialPlanData());
            upsertPlan(authenticatedUser, CycleSlot.CURRENT, previousPeriod, restoredCurrentData);
            deletePlan(authenticatedUser, CycleSlot.PREVIOUS);
            StoredCycle restoredCurrentCycle = loadStoredCycle(authenticatedUser, CycleSlot.CURRENT);

            return buildResponse(
                restoredCurrentData,
                CycleSlot.CURRENT,
                timelineType,
                previousPeriod,
                null,
                true,
                canCloseCycle(restoredCurrentData),
                restoredCurrentCycle,
                null
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to revert closed financial cycle", exception);
        }
    }

    public FinancialPlanCycleResponse loadSample(Authentication authentication, CycleSlot cycleSlot, TimelineType timelineType) {
        authenticatedUser(authentication);

        try {
            AuthenticatedUser sampleUser = sampleUserForTimeline(timelineType);
            StoredCycle currentCycle = loadStoredCycle(sampleUser, CycleSlot.CURRENT);
            if (currentCycle == null && cycleSlot == CycleSlot.CURRENT) {
                createSamplePlanFromSource(timelineType);
                currentCycle = loadStoredCycle(sampleUser, CycleSlot.CURRENT);
            }

            StoredCycle previousCycle = loadStoredCycle(sampleUser, CycleSlot.PREVIOUS);
            CyclePeriod resolvedCurrentCycle = cyclePeriodFor(currentCycle, CycleSlot.CURRENT, timelineType);
            CyclePeriod resolvedPreviousCycle = previousCycle != null ? cyclePeriodFor(previousCycle, CycleSlot.PREVIOUS, timelineType) : null;
            FinancialPlanData currentData = currentCycle != null ? currentCycle.financialPlanData() : buildSeededPlanSafely();

            if (cycleSlot == CycleSlot.PREVIOUS) {
                if (previousCycle == null) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Previous cycle not found");
                }

                return buildResponse(
                    financialPlanCalculationService.withCalculatedSummary(previousCycle.financialPlanData()),
                    cycleSlot,
                    timelineType,
                    resolvedCurrentCycle,
                    resolvedPreviousCycle,
                    hasSavedPlan(currentCycle),
                    canCloseCycle(currentData),
                    currentCycle,
                    previousCycle
                );
            }

            return buildResponse(
                currentData,
                cycleSlot,
                timelineType,
                resolvedCurrentCycle,
                resolvedPreviousCycle,
                hasSavedPlan(currentCycle),
                canCloseCycle(currentData),
                currentCycle,
                previousCycle
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read sample financial plan data", exception);
        }
    }

    public BankBalanceHistoryResponse loadSampleBankBalanceHistory(Authentication authentication, TimelineType timelineType, Integer limit) {
        authenticatedUser(authentication);
        return loadBankBalanceHistory(sampleUserSubForTimeline(timelineType), timelineType, sanitizeHistoryLimit(limit));
    }

    public FinancialPlanCycleResponse saveSample(Authentication authentication, TimelineType timelineType, FinancialPlanData financialPlanData) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);
        ensureAdminAccess(authenticatedUser);

        try {
            AuthenticatedUser sampleUser = sampleUserForTimeline(timelineType);
            StoredCycle currentCycle = loadStoredCycle(sampleUser, CycleSlot.CURRENT);
            StoredCycle previousCycle = loadStoredCycle(sampleUser, CycleSlot.PREVIOUS);
            CyclePeriod currentPeriod = cyclePeriodFor(currentCycle, CycleSlot.CURRENT, timelineType);
            FinancialPlanData normalizedData = normalizeIds(financialPlanData);
            FinancialPlanData enrichedData = financialPlanCalculationService.withCalculatedSummary(normalizedData);
            upsertSettings(sampleUser, timelineType);
            upsertPlan(
                sampleUser,
                CycleSlot.CURRENT,
                currentPeriod,
                enrichedData
            );
            StoredCycle savedCurrentCycle = loadStoredCycle(sampleUser, CycleSlot.CURRENT);
            return buildResponse(
                enrichedData,
                CycleSlot.CURRENT,
                timelineType,
                currentPeriod,
                previousCycle != null ? cyclePeriodFor(previousCycle, CycleSlot.PREVIOUS, timelineType) : null,
                true,
                canCloseCycle(enrichedData),
                savedCurrentCycle,
                previousCycle
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save sample financial plan data", exception);
        }
    }

    public FinancialPlanCycleResponse closeSampleCycle(Authentication authentication, TimelineType timelineType, CloseCycleRequest closeCycleRequest) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);
        ensureAdminAccess(authenticatedUser);

        if (closeCycleRequest == null || closeCycleRequest.financialPlanData() == null || closeCycleRequest.expectedCurrentCycle() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current cycle payload is required");
        }

        try {
            AuthenticatedUser sampleUser = sampleUserForTimeline(timelineType);
            StoredCycle currentCycle = loadStoredCycle(sampleUser, CycleSlot.CURRENT);
            StoredCycle previousCycle = loadStoredCycle(sampleUser, CycleSlot.PREVIOUS);
            CyclePeriod currentPeriod = cyclePeriodFor(currentCycle, CycleSlot.CURRENT, timelineType);

            if (!currentPeriod.equals(closeCycleRequest.expectedCurrentCycle())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Current cycle changed. Reload before closing the cycle.");
            }

            FinancialPlanData archivedCurrentData = financialPlanCalculationService.withCalculatedSummary(
                normalizeIds(closeCycleRequest.financialPlanData())
            );

            if (!canCloseCycle(archivedCurrentData)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Current cycle is not ready to close");
            }

            upsertSettings(sampleUser, timelineType);
            archiveCycleHistory(sampleUser, timelineType, previousCycle);
            upsertPlan(sampleUser, CycleSlot.PREVIOUS, currentPeriod, archivedCurrentData);

            CyclePeriod nextCurrentPeriod = nextCyclePeriod(currentPeriod);
            FinancialPlanData nextCurrentData = financialPlanCalculationService.startNewCycle(archivedCurrentData);
            upsertPlan(sampleUser, CycleSlot.CURRENT, nextCurrentPeriod, nextCurrentData);
            StoredCycle savedCurrentCycle = loadStoredCycle(sampleUser, CycleSlot.CURRENT);
            StoredCycle savedPreviousCycle = loadStoredCycle(sampleUser, CycleSlot.PREVIOUS);

            return buildResponse(
                nextCurrentData,
                CycleSlot.CURRENT,
                timelineType,
                nextCurrentPeriod,
                currentPeriod,
                true,
                canCloseCycle(nextCurrentData),
                savedCurrentCycle,
                savedPreviousCycle
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to close sample financial cycle", exception);
        }
    }

    public FinancialPlanCycleResponse revertSampleCloseCycle(
        Authentication authentication,
        TimelineType timelineType,
        RevertCloseCycleRequest revertCloseCycleRequest
    ) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);
        ensureAdminAccess(authenticatedUser);

        if (revertCloseCycleRequest == null
            || revertCloseCycleRequest.expectedCurrentCycle() == null
            || revertCloseCycleRequest.expectedPreviousCycle() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current and previous cycle metadata are required");
        }

        try {
            AuthenticatedUser sampleUser = sampleUserForTimeline(timelineType);
            StoredCycle currentCycle = loadStoredCycle(sampleUser, CycleSlot.CURRENT);
            StoredCycle previousCycle = loadStoredCycle(sampleUser, CycleSlot.PREVIOUS);

            if (currentCycle == null || previousCycle == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "No recently closed cycle is available to reset");
            }

            CyclePeriod currentPeriod = cyclePeriodFor(currentCycle, CycleSlot.CURRENT, timelineType);
            CyclePeriod previousPeriod = cyclePeriodFor(previousCycle, CycleSlot.PREVIOUS, timelineType);

            if (!currentPeriod.equals(revertCloseCycleRequest.expectedCurrentCycle())
                || !previousPeriod.equals(revertCloseCycleRequest.expectedPreviousCycle())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cycle state changed. Reload before resetting.");
            }

            FinancialPlanData restoredCurrentData = financialPlanCalculationService.withCalculatedSummary(previousCycle.financialPlanData());
            upsertPlan(sampleUser, CycleSlot.CURRENT, previousPeriod, restoredCurrentData);
            deletePlan(sampleUser, CycleSlot.PREVIOUS);
            StoredCycle restoredCurrentCycle = loadStoredCycle(sampleUser, CycleSlot.CURRENT);

            return buildResponse(
                restoredCurrentData,
                CycleSlot.CURRENT,
                timelineType,
                previousPeriod,
                null,
                true,
                canCloseCycle(restoredCurrentData),
                restoredCurrentCycle,
                null
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to revert closed sample financial cycle", exception);
        }
    }

    public FinancialPlanCycleResponse switchSampleTimeline(
        Authentication authentication,
        TimelineType currentTimelineType,
        SwitchTimelineRequest switchTimelineRequest
    ) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);
        ensureAdminAccess(authenticatedUser);

        if (switchTimelineRequest == null
            || switchTimelineRequest.financialPlanData() == null
            || switchTimelineRequest.expectedCurrentCycle() == null
            || switchTimelineRequest.targetTimelineType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Timeline switch payload is required");
        }

        try {
            AuthenticatedUser currentSampleUser = sampleUserForTimeline(currentTimelineType);
            StoredCycle currentCycle = loadStoredCycle(currentSampleUser, CycleSlot.CURRENT);
            CyclePeriod currentPeriod = cyclePeriodFor(currentCycle, CycleSlot.CURRENT, currentTimelineType);

            if (!currentPeriod.equals(switchTimelineRequest.expectedCurrentCycle())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Current cycle changed. Reload before switching timeline.");
            }

            TimelineType targetTimelineType = switchTimelineRequest.targetTimelineType();
            AuthenticatedUser targetSampleUser = sampleUserForTimeline(targetTimelineType);
            FinancialPlanData normalizedData = normalizeIds(switchTimelineRequest.financialPlanData());
            FinancialPlanData enrichedData = financialPlanCalculationService.withCalculatedSummary(normalizedData);
            CyclePeriod targetCurrentCycle = currentCycleForTimeline(targetTimelineType, LocalDate.now());

            upsertSettings(targetSampleUser, targetTimelineType);
            deletePlan(targetSampleUser, CycleSlot.PREVIOUS);
            upsertPlan(targetSampleUser, CycleSlot.CURRENT, targetCurrentCycle, enrichedData);
            StoredCycle savedCurrentCycle = loadStoredCycle(targetSampleUser, CycleSlot.CURRENT);

            return buildResponse(
                enrichedData,
                CycleSlot.CURRENT,
                targetTimelineType,
                targetCurrentCycle,
                null,
                true,
                canCloseCycle(enrichedData),
                savedCurrentCycle,
                null
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to switch sample timeline", exception);
        }
    }

    public void deleteSample(Authentication authentication, TimelineType timelineType) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);
        ensureAdminAccess(authenticatedUser);
        AuthenticatedUser sampleUser = sampleUserForTimeline(timelineType);

        jdbcClient.sql("""
            DELETE FROM app_user_financial_plan_cycle
                WHERE user_sub = :userSub
                """)
            .param("userSub", sampleUser.userSub())
            .update();

        jdbcClient.sql("""
            DELETE FROM app_user_financial_plan_cycle_history
                WHERE user_sub = :userSub
                """)
            .param("userSub", sampleUser.userSub())
            .update();
    }

    public void delete(Authentication authentication) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);

        if (isSampleUser(authenticatedUser.userSub(), authenticatedUser.email())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sample plan cannot be deleted");
        }

        jdbcClient.sql("""
            DELETE FROM app_user_financial_plan_cycle
                WHERE user_sub = :userSub
                """)
            .param("userSub", authenticatedUser.userSub())
            .update();

        jdbcClient.sql("""
            DELETE FROM app_user_financial_plan_cycle_history
                WHERE user_sub = :userSub
                """)
            .param("userSub", authenticatedUser.userSub())
            .update();

        deleteTermsAcceptance(authenticatedUser.userSub());
    }

    public void deleteAsAdmin(Authentication authentication, String targetUserSub) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);
        ensureAdminAccess(authenticatedUser);

        if (targetUserSub == null || targetUserSub.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target user is required");
        }

        if (isSampleUser(targetUserSub, null)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sample plan cannot be deleted");
        }

        jdbcClient.sql("""
            DELETE FROM app_user_financial_plan_cycle
                WHERE user_sub = :userSub
                """)
            .param("userSub", targetUserSub)
            .update();

        jdbcClient.sql("""
            DELETE FROM app_user_financial_plan_cycle_history
                WHERE user_sub = :userSub
                """)
            .param("userSub", targetUserSub)
            .update();

        deleteTermsAcceptance(targetUserSub);
    }

    private void deleteTermsAcceptance(String userSub) {
        jdbcClient.sql("""
            DELETE FROM app_user_terms_acceptance
                WHERE user_sub = :userSub
                """)
            .param("userSub", userSub)
            .update();
    }

    public FinancialPlanCycleResponse switchTimeline(Authentication authentication, SwitchTimelineRequest switchTimelineRequest) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);

        if (switchTimelineRequest == null
            || switchTimelineRequest.financialPlanData() == null
            || switchTimelineRequest.expectedCurrentCycle() == null
            || switchTimelineRequest.targetTimelineType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Timeline switch payload is required");
        }

        try {
            TimelineType currentTimelineType = timelineTypeFor(authenticatedUser.userSub());
            StoredCycle currentCycle = loadStoredCycle(authenticatedUser, CycleSlot.CURRENT);
            CyclePeriod currentPeriod = cyclePeriodFor(currentCycle, CycleSlot.CURRENT, currentTimelineType);

            if (!currentPeriod.equals(switchTimelineRequest.expectedCurrentCycle())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Current cycle changed. Reload before switching timeline.");
            }

            TimelineType targetTimelineType = switchTimelineRequest.targetTimelineType();
            FinancialPlanData normalizedData = normalizeIds(switchTimelineRequest.financialPlanData());
            FinancialPlanData enrichedData = financialPlanCalculationService.withCalculatedSummary(normalizedData);
            CyclePeriod targetCurrentCycle = currentCycleForTimeline(targetTimelineType, LocalDate.now());

            upsertSettings(authenticatedUser, targetTimelineType);
            deletePlan(authenticatedUser, CycleSlot.PREVIOUS);
            upsertPlan(authenticatedUser, CycleSlot.CURRENT, targetCurrentCycle, enrichedData);
            StoredCycle savedCurrentCycle = loadStoredCycle(authenticatedUser, CycleSlot.CURRENT);

            return buildResponse(
                enrichedData,
                CycleSlot.CURRENT,
                targetTimelineType,
                targetCurrentCycle,
                null,
                true,
                canCloseCycle(enrichedData),
                savedCurrentCycle,
                null
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to switch timeline", exception);
        }
    }

    @Transactional
    public int normalizeAllPlans(Authentication authentication) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);
        ensureAdminAccess(authenticatedUser);

        List<StoredPlanRow> storedPlans = jdbcClient.sql("""
                SELECT user_sub, cycle_slot, plan_data::text
                FROM app_user_financial_plan_cycle
                """)
            .query((resultSet, rowNum) -> new StoredPlanRow(
                resultSet.getString("user_sub"),
                resultSet.getString("cycle_slot"),
                resultSet.getString("plan_data")
            ))
            .list();

        int updatedCount = 0;
        for (StoredPlanRow storedPlan : storedPlans) {
            try {
                FinancialPlanData storedData = objectMapper.readValue(storedPlan.planDataJson(), FinancialPlanData.class);
                FinancialPlanData normalizedData = normalizeIds(storedData);
                FinancialPlanData enrichedData = financialPlanCalculationService.withCalculatedSummary(normalizedData);
                String planJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(enrichedData);

                jdbcClient.sql("""
                        UPDATE app_user_financial_plan_cycle
                        SET plan_data = CAST(:planData AS jsonb),
                            updated_at = NOW()
                        WHERE user_sub = :userSub
                          AND cycle_slot = :cycleSlot
                        """)
                    .param("planData", planJson)
                    .param("userSub", storedPlan.userSub())
                    .param("cycleSlot", storedPlan.cycleSlot())
                    .update();

                updatedCount++;
            } catch (IOException exception) {
                throw new IllegalStateException(
                    "Failed to normalize stored cycle for user " + storedPlan.userSub() + " and slot " + storedPlan.cycleSlot(),
                    exception
                );
            }
        }

        return updatedCount;
    }

    @Transactional
    public int repairStartToEndCycleDates(Authentication authentication) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);
        ensureAdminAccess(authenticatedUser);

        List<StoredCycleDateRow> liveRows = jdbcClient.sql("""
                SELECT cycle.user_sub, cycle.cycle_slot, cycle.cycle_start_date, cycle.cycle_end_date
                FROM app_user_financial_plan_cycle cycle
                JOIN app_user_financial_plan_settings settings
                  ON settings.user_sub = cycle.user_sub
                WHERE settings.timeline_type = 'START_TO_END'
                """)
            .query((resultSet, rowNum) -> new StoredCycleDateRow(
                resultSet.getString("user_sub"),
                resultSet.getString("cycle_slot"),
                resultSet.getObject("cycle_start_date", LocalDate.class),
                resultSet.getObject("cycle_end_date", LocalDate.class)
            ))
            .list();

        int updatedCount = 0;
        for (StoredCycleDateRow liveRow : liveRows) {
            CyclePeriod correctedCyclePeriod = correctedStartToEndCyclePeriod(liveRow.cycleEndDate());
            if (liveRow.cycleStartDate().equals(correctedCyclePeriod.startDate())
                && liveRow.cycleEndDate().equals(correctedCyclePeriod.endDate())) {
                continue;
            }

            jdbcClient.sql("""
                    UPDATE app_user_financial_plan_cycle
                    SET cycle_start_date = :cycleStartDate,
                        cycle_end_date = :cycleEndDate,
                        updated_at = NOW()
                    WHERE user_sub = :userSub
                      AND cycle_slot = :cycleSlot
                    """)
                .param("cycleStartDate", correctedCyclePeriod.startDate())
                .param("cycleEndDate", correctedCyclePeriod.endDate())
                .param("userSub", liveRow.userSub())
                .param("cycleSlot", liveRow.cycleSlot())
                .update();

            updatedCount++;
        }

        List<StoredHistoryDateRow> historyRows = jdbcClient.sql("""
                SELECT user_sub, timeline_type, cycle_start_date, cycle_end_date, email, display_name, history_data::text
                FROM app_user_financial_plan_cycle_history
                WHERE timeline_type = 'START_TO_END'
                """)
            .query((resultSet, rowNum) -> new StoredHistoryDateRow(
                resultSet.getString("user_sub"),
                resultSet.getString("timeline_type"),
                resultSet.getObject("cycle_start_date", LocalDate.class),
                resultSet.getObject("cycle_end_date", LocalDate.class),
                resultSet.getString("email"),
                resultSet.getString("display_name"),
                resultSet.getString("history_data")
            ))
            .list();

        for (StoredHistoryDateRow historyRow : historyRows) {
            CyclePeriod correctedCyclePeriod = correctedStartToEndCyclePeriod(historyRow.cycleEndDate());
            if (historyRow.cycleStartDate().equals(correctedCyclePeriod.startDate())
                && historyRow.cycleEndDate().equals(correctedCyclePeriod.endDate())) {
                continue;
            }

            jdbcClient.sql("""
                    INSERT INTO app_user_financial_plan_cycle_history (
                        user_sub,
                        timeline_type,
                        cycle_start_date,
                        cycle_end_date,
                        email,
                        display_name,
                        history_data
                    )
                    VALUES (
                        :userSub,
                        :timelineType,
                        :cycleStartDate,
                        :cycleEndDate,
                        :email,
                        :displayName,
                        CAST(:historyData AS jsonb)
                    )
                    ON CONFLICT (user_sub, timeline_type, cycle_start_date, cycle_end_date)
                    DO UPDATE SET
                        email = EXCLUDED.email,
                        display_name = EXCLUDED.display_name,
                        history_data = EXCLUDED.history_data,
                        updated_at = NOW()
                    """)
                .param("userSub", historyRow.userSub())
                .param("timelineType", historyRow.timelineType())
                .param("cycleStartDate", correctedCyclePeriod.startDate())
                .param("cycleEndDate", correctedCyclePeriod.endDate())
                .param("email", historyRow.email())
                .param("displayName", historyRow.displayName())
                .param("historyData", historyRow.historyDataJson())
                .update();

            jdbcClient.sql("""
                    DELETE FROM app_user_financial_plan_cycle_history
                    WHERE user_sub = :userSub
                      AND timeline_type = :timelineType
                      AND cycle_start_date = :oldCycleStartDate
                      AND cycle_end_date = :oldCycleEndDate
                    """)
                .param("userSub", historyRow.userSub())
                .param("timelineType", historyRow.timelineType())
                .param("oldCycleStartDate", historyRow.cycleStartDate())
                .param("oldCycleEndDate", historyRow.cycleEndDate())
                .update();

            updatedCount++;
        }

        return updatedCount;
    }

    private FinancialPlanData buildSeededPlan() throws IOException {
        FinancialPlanData defaultData = readNewUserTemplate();
        FinancialPlanData normalizedData = normalizeIds(defaultData);
        return financialPlanCalculationService.withCalculatedSummary(normalizedData);
    }

    private FinancialPlanData readNewUserTemplate() throws IOException {
        ClassPathResource defaultData = new ClassPathResource(NEW_USER_TEMPLATE_RESOURCE);
        try (InputStream inputStream = defaultData.getInputStream()) {
            return objectMapper.readValue(inputStream, FinancialPlanData.class);
        }
    }

    private FinancialPlanData readDefaultPlan() throws IOException {
        ClassPathResource defaultData = new ClassPathResource("default-financial-plan.json");
        try (InputStream inputStream = defaultData.getInputStream()) {
            return objectMapper.readValue(inputStream, FinancialPlanData.class);
        }
    }

    private void upsertPlan(
        AuthenticatedUser authenticatedUser,
        CycleSlot cycleSlot,
        CyclePeriod cyclePeriod,
        FinancialPlanData financialPlanData
    ) throws IOException {
        String planJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(financialPlanData);

        jdbcClient.sql("""
            INSERT INTO app_user_financial_plan_cycle (user_sub, email, display_name, cycle_slot, cycle_start_date, cycle_end_date, plan_data)
                VALUES (:userSub, :email, :displayName, :cycleSlot, :cycleStartDate, :cycleEndDate, CAST(:planData AS jsonb))
                ON CONFLICT (user_sub, cycle_slot)
                DO UPDATE SET
                    email = EXCLUDED.email,
                    display_name = EXCLUDED.display_name,
                    cycle_start_date = EXCLUDED.cycle_start_date,
                    cycle_end_date = EXCLUDED.cycle_end_date,
                    plan_data = EXCLUDED.plan_data,
                    updated_at = NOW()
                """)
            .param("userSub", authenticatedUser.userSub())
            .param("email", authenticatedUser.email())
            .param("displayName", authenticatedUser.displayName())
            .param("cycleSlot", cycleSlot.name())
            .param("cycleStartDate", cyclePeriod.startDate())
            .param("cycleEndDate", cyclePeriod.endDate())
            .param("planData", planJson)
            .update();
    }

    private void archiveCycleHistory(
        AuthenticatedUser authenticatedUser,
        TimelineType timelineType,
        StoredCycle storedCycle
    ) throws IOException {
        if (storedCycle == null || storedCycle.cycleStartDate() == null || storedCycle.cycleEndDate() == null) {
            return;
        }

        String historyJson = objectMapper.writeValueAsString(
            financialPlanCalculationService.buildBankBalanceHistoryPoints(storedCycle.financialPlanData())
        );

        jdbcClient.sql("""
            INSERT INTO app_user_financial_plan_cycle_history (
                user_sub,
                timeline_type,
                cycle_start_date,
                cycle_end_date,
                email,
                display_name,
                history_data
            )
                VALUES (
                    :userSub,
                    :timelineType,
                    :cycleStartDate,
                    :cycleEndDate,
                    :email,
                    :displayName,
                    CAST(:historyData AS jsonb)
                )
            ON CONFLICT (user_sub, timeline_type, cycle_start_date, cycle_end_date)
                DO UPDATE SET
                    email = EXCLUDED.email,
                    display_name = EXCLUDED.display_name,
                    history_data = EXCLUDED.history_data,
                    updated_at = NOW()
            """)
            .param("userSub", authenticatedUser.userSub())
            .param("timelineType", timelineType.name())
            .param("cycleStartDate", storedCycle.cycleStartDate())
            .param("cycleEndDate", storedCycle.cycleEndDate())
            .param("email", authenticatedUser.email())
            .param("displayName", authenticatedUser.displayName())
            .param("historyData", historyJson)
            .update();

            pruneArchivedCycleHistory(authenticatedUser.userSub(), timelineType);
    }

        private void deletePlan(AuthenticatedUser authenticatedUser, CycleSlot cycleSlot) {
                jdbcClient.sql("""
                        DELETE FROM app_user_financial_plan_cycle
                        WHERE user_sub = :userSub
                            AND cycle_slot = :cycleSlot
                        """)
                        .param("userSub", authenticatedUser.userSub())
                        .param("cycleSlot", cycleSlot.name())
                        .update();
        }

    private FinancialPlanData createSamplePlanFromSource(TimelineType timelineType) throws IOException {
        String sourcePlanJson = jdbcClient.sql("""
                SELECT plan_data::text
                                FROM app_user_financial_plan_cycle
                WHERE email = :email
                                    AND cycle_slot = 'CURRENT'
                ORDER BY updated_at DESC
                LIMIT 1
                """)
            .param("email", SAMPLE_SOURCE_EMAIL)
            .query(String.class)
            .optional()
            .orElse(null);

        FinancialPlanData sourcePlan = sourcePlanJson == null
            ? buildSeededPlan()
            : objectMapper.readValue(sourcePlanJson, FinancialPlanData.class);

        FinancialPlanData normalizedData = normalizeIds(sourcePlan);
        FinancialPlanData enrichedData = financialPlanCalculationService.withCalculatedSummary(normalizedData);
        AuthenticatedUser sampleUser = new AuthenticatedUser(sampleUserSubForTimeline(timelineType), SAMPLE_PLAN_EMAIL, sampleDisplayNameForTimeline(timelineType));
        upsertSettings(sampleUser, timelineType);
        upsertPlan(
            sampleUser,
            CycleSlot.CURRENT,
            currentCycleForTimeline(timelineType, LocalDate.now()),
            enrichedData
        );
        return enrichedData;
    }

    private StoredCycle loadStoredCycle(AuthenticatedUser authenticatedUser, CycleSlot cycleSlot) {
                return loadStoredCycle(authenticatedUser.userSub(), cycleSlot);
        }

        private StoredCycle loadStoredCycle(String userSub, CycleSlot cycleSlot) {
        return jdbcClient.sql("""
                SELECT plan_data::text, cycle_start_date, cycle_end_date, created_at, updated_at
                                FROM app_user_financial_plan_cycle
                WHERE user_sub = :userSub
                  AND cycle_slot = :cycleSlot
                """)
                        .param("userSub", userSub)
            .param("cycleSlot", cycleSlot.name())
            .query((resultSet, rowNum) -> {
                try {
                    return new StoredCycle(
                        financialPlanCalculationService.withCalculatedSummary(
                            normalizeIds(objectMapper.readValue(resultSet.getString("plan_data"), FinancialPlanData.class))
                        ),
                        resultSet.getObject("cycle_start_date", LocalDate.class),
                        resultSet.getObject("cycle_end_date", LocalDate.class),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("updated_at").toInstant()
                    );
                } catch (IOException exception) {
                    throw new IllegalStateException("Failed to read stored cycle", exception);
                }
            })
            .optional()
            .orElse(null);
    }

    private FinancialPlanCycleResponse buildResponse(
        FinancialPlanData financialPlanData,
        CycleSlot selectedCycle,
        TimelineType timelineType,
        CyclePeriod currentCycle,
        CyclePeriod previousCycle,
        boolean hasSavedPlan,
        boolean canCloseCycle,
        StoredCycle currentStoredCycle,
        StoredCycle previousStoredCycle
    ) {
        return buildResponse(
            financialPlanData,
            selectedCycle,
            timelineType,
            currentCycle,
            previousCycle,
            hasSavedPlan,
            canCloseCycle,
            selectedCycle == CycleSlot.PREVIOUS,
            currentStoredCycle,
            previousStoredCycle
        );
    }

    private FinancialPlanCycleResponse buildResponse(
        FinancialPlanData financialPlanData,
        CycleSlot selectedCycle,
        TimelineType timelineType,
        CyclePeriod currentCycle,
        CyclePeriod previousCycle,
        boolean hasSavedPlan,
        boolean canCloseCycle,
        boolean readOnly,
        StoredCycle currentStoredCycle,
        StoredCycle previousStoredCycle
    ) {
        return new FinancialPlanCycleResponse(
            financialPlanData,
            selectedCycle,
            timelineType,
            currentCycle,
            previousCycle,
            previousCycle != null,
            readOnly,
            hasSavedPlan,
            canCloseCycle,
            lastCycleSavedAt(currentStoredCycle, previousStoredCycle)
        );
    }

    private BankBalanceHistoryResponse loadBankBalanceHistory(String userSub, TimelineType timelineType, int limit) {
        StoredCycle currentCycle = loadStoredCycle(userSub, CycleSlot.CURRENT);
        StoredCycle previousCycle = loadStoredCycle(userSub, CycleSlot.PREVIOUS);
        List<BankBalanceHistoryCycle> archivedCycles = loadArchivedBankBalanceHistoryCycles(
            userSub,
            timelineType,
            Math.max(limit - 2, 0)
        );
        Map<String, BankBalanceHistoryCycle> cyclesByPeriod = new LinkedHashMap<>();

        archivedCycles.stream()
            .sorted(Comparator.comparing((BankBalanceHistoryCycle cycle) -> cycle.cycle().startDate()))
            .forEach(cycle -> cyclesByPeriod.put(cycleKey(cycle.cycle()), cycle));

        if (previousCycle != null) {
            CyclePeriod previousPeriod = cyclePeriodFor(previousCycle, CycleSlot.PREVIOUS, timelineType);
            cyclesByPeriod.put(cycleKey(previousPeriod), new BankBalanceHistoryCycle(
                previousPeriod,
                financialPlanCalculationService.buildBankBalanceHistoryPoints(previousCycle.financialPlanData()),
                null, null
            ));
        }

        if (currentCycle != null) {
            CyclePeriod currentPeriod = cyclePeriodFor(currentCycle, CycleSlot.CURRENT, timelineType);
            cyclesByPeriod.put(cycleKey(currentPeriod), new BankBalanceHistoryCycle(
                currentPeriod,
                financialPlanCalculationService.buildBankBalanceHistoryPoints(currentCycle.financialPlanData()),
                null, null
            ));
        }

        List<BankBalanceHistoryCycle> cycles = new ArrayList<>(cyclesByPeriod.values());
        cycles.sort(Comparator.comparing((BankBalanceHistoryCycle cycle) -> cycle.cycle().startDate()));

        if (cycles.size() > limit) {
            cycles = new ArrayList<>(cycles.subList(cycles.size() - limit, cycles.size()));
        }

        return new BankBalanceHistoryResponse(timelineType, cycles);
    }

    private List<BankBalanceHistoryCycle> loadArchivedBankBalanceHistoryCycles(String userSub, TimelineType timelineType, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        return jdbcClient.sql("""
                SELECT cycle_start_date, cycle_end_date, history_data::text
                FROM app_user_financial_plan_cycle_history
                WHERE user_sub = :userSub
                  AND timeline_type = :timelineType
                ORDER BY cycle_end_date DESC, cycle_start_date DESC
                LIMIT :limit
                """)
            .param("userSub", userSub)
            .param("timelineType", timelineType.name())
            .param("limit", limit)
            .query((resultSet, rowNum) -> {
                try {
                    CyclePeriod cyclePeriod = new CyclePeriod(
                        resultSet.getObject("cycle_start_date", LocalDate.class),
                        resultSet.getObject("cycle_end_date", LocalDate.class)
                    );
                    String rawHistoryData = resultSet.getString("history_data");
                    JsonNode historyNode = objectMapper.readTree(rawHistoryData);
                    if (historyNode.isArray()) {
                        return new BankBalanceHistoryCycle(
                            cyclePeriod,
                            objectMapper.convertValue(historyNode, bankBalanceHistoryPointListType),
                            null, null
                        );
                    } else {
                        return new BankBalanceHistoryCycle(
                            cyclePeriod,
                            List.of(),
                            historyNode.path("encryptedHistoryData").asText(null),
                            historyNode.path("encryptionIv").asText(null)
                        );
                    }
                } catch (IOException exception) {
                    throw new IllegalStateException("Failed to read archived cycle history", exception);
                }
            })
            .list();
    }

    private int sanitizeHistoryLimit(Integer limit) {
        int configuredCount = Math.min(Math.max(defaultBankBalanceHistoryCycleCount, 1), MAX_HISTORY_LIMIT);

        if (limit == null || limit <= 0) {
            return configuredCount;
        }

        return Math.min(Math.min(limit, configuredCount), MAX_HISTORY_LIMIT);
    }

    private void pruneArchivedCycleHistory(String userSub, TimelineType timelineType) {
        int archivedRetentionCount = Math.max(sanitizeHistoryLimit(null) - 2, 0);

        jdbcClient.sql("""
                WITH rows_to_delete AS (
                    SELECT cycle_start_date, cycle_end_date
                    FROM app_user_financial_plan_cycle_history
                    WHERE user_sub = :userSub
                      AND timeline_type = :timelineType
                    ORDER BY cycle_end_date DESC, cycle_start_date DESC
                    OFFSET :retentionCount
                )
                DELETE FROM app_user_financial_plan_cycle_history history
                USING rows_to_delete
                WHERE history.user_sub = :userSub
                  AND history.timeline_type = :timelineType
                  AND history.cycle_start_date = rows_to_delete.cycle_start_date
                  AND history.cycle_end_date = rows_to_delete.cycle_end_date
                """)
            .param("userSub", userSub)
            .param("timelineType", timelineType.name())
            .param("retentionCount", archivedRetentionCount)
            .update();
    }

    private String cycleKey(CyclePeriod cyclePeriod) {
        return cyclePeriod.startDate() + ":" + cyclePeriod.endDate();
    }

    private CyclePeriod cyclePeriodFor(StoredCycle storedCycle, CycleSlot cycleSlot, TimelineType timelineType) {
        if (storedCycle != null && storedCycle.cycleStartDate() != null && storedCycle.cycleEndDate() != null) {
            return new CyclePeriod(storedCycle.cycleStartDate(), storedCycle.cycleEndDate());
        }

        return cycleSlot == CycleSlot.CURRENT
            ? currentCycleForTimeline(timelineType, LocalDate.now())
            : previousCycleForTimeline(timelineType, LocalDate.now());
    }

    private CyclePeriod currentCycleForTimeline(TimelineType timelineType, LocalDate referenceDate) {
        return cycleWindowFor(referenceDate, timelineType);
    }

    private CyclePeriod previousCycleForTimeline(TimelineType timelineType, LocalDate referenceDate) {
        CyclePeriod currentCycle = currentCycleForTimeline(timelineType, referenceDate);
        LocalDate previousStartDate = currentCycle.startDate().minusMonths(1);
        LocalDate previousEndDate = currentCycle.startDate().minusDays(1);
        return new CyclePeriod(previousStartDate, previousEndDate);
    }

    private CyclePeriod nextCyclePeriod(CyclePeriod cyclePeriod) {
        LocalDate nextStartDate = cyclePeriod.endDate().plusDays(1);
        LocalDate nextEndDate = nextStartDate.plusMonths(1).minusDays(1);
        return new CyclePeriod(nextStartDate, nextEndDate);
    }

    private CyclePeriod correctedStartToEndCyclePeriod(LocalDate cycleEndDate) {
        LocalDate correctedStartDate = cycleEndDate.withDayOfMonth(1);
        LocalDate correctedEndDate = correctedStartDate.plusMonths(1).minusDays(1);
        return new CyclePeriod(correctedStartDate, correctedEndDate);
    }

    private CyclePeriod cycleWindowFor(LocalDate referenceDate, TimelineType timelineType) {
        if (timelineType == TimelineType.START_TO_END) {
            LocalDate currentCycleStart = referenceDate.withDayOfMonth(1);
            LocalDate currentCycleEnd = currentCycleStart.plusMonths(1).minusDays(1);
            return new CyclePeriod(currentCycleStart, currentCycleEnd);
        }

        LocalDate currentCycleStart = referenceDate.getDayOfMonth() >= 16
            ? referenceDate.withDayOfMonth(16)
            : referenceDate.minusMonths(1).withDayOfMonth(16);
        LocalDate currentCycleEnd = currentCycleStart.plusMonths(1).withDayOfMonth(15);
        return new CyclePeriod(currentCycleStart, currentCycleEnd);
    }

    private TimelineType timelineTypeFor(String userSub) {
        return jdbcClient.sql("""
                SELECT timeline_type
                FROM app_user_financial_plan_settings
                WHERE user_sub = :userSub
                """)
            .param("userSub", userSub)
            .query(String.class)
            .optional()
            .map(TimelineType::fromStoredValue)
                .orElse(TimelineType.START_TO_END);
    }

    private void upsertSettings(AuthenticatedUser authenticatedUser, TimelineType timelineType) {
        jdbcClient.sql("""
            INSERT INTO app_user_financial_plan_settings (user_sub, email, display_name, timeline_type)
                VALUES (:userSub, :email, :displayName, :timelineType)
            ON CONFLICT (user_sub)
                DO UPDATE SET
                    email = EXCLUDED.email,
                    display_name = EXCLUDED.display_name,
                    timeline_type = EXCLUDED.timeline_type,
                    updated_at = NOW()
            """)
            .param("userSub", authenticatedUser.userSub())
            .param("email", authenticatedUser.email())
            .param("displayName", authenticatedUser.displayName())
            .param("timelineType", timelineType.name())
            .update();
    }

    private String sampleUserSubForTimeline(TimelineType timelineType) {
        return timelineType == TimelineType.START_TO_END ? SAMPLE_PLAN_START_TO_END_USER_SUB : SAMPLE_PLAN_MID_TO_MID_USER_SUB;
    }

    private AuthenticatedUser sampleUserForTimeline(TimelineType timelineType) {
        return new AuthenticatedUser(
            sampleUserSubForTimeline(timelineType),
            SAMPLE_PLAN_EMAIL,
            sampleDisplayNameForTimeline(timelineType)
        );
    }

    private String sampleDisplayNameForTimeline(TimelineType timelineType) {
        return timelineType == TimelineType.START_TO_END
            ? SAMPLE_PLAN_DISPLAY_NAME + " (Start to End)"
            : SAMPLE_PLAN_DISPLAY_NAME + " (Mid to Mid)";
    }

    private boolean isSampleUser(String userSub, String email) {
        return SAMPLE_PLAN_MID_TO_MID_USER_SUB.equals(userSub)
            || SAMPLE_PLAN_START_TO_END_USER_SUB.equals(userSub)
            || SAMPLE_PLAN_EMAIL.equalsIgnoreCase(email);
    }

    private FinancialPlanData buildSeededPlanSafely() {
        try {
            return buildSeededPlan();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to build seeded plan", exception);
        }
    }

    private boolean hasSavedPlan(StoredCycle storedCycle) {
        return storedCycle != null && storedCycle.updatedAt().isAfter(storedCycle.createdAt());
    }

    private Instant lastCycleSavedAt(StoredCycle currentCycle, StoredCycle previousCycle) {
        Instant currentSavedAt = hasSavedPlan(currentCycle) ? currentCycle.updatedAt() : null;
        Instant previousSavedAt = hasSavedPlan(previousCycle) ? previousCycle.updatedAt() : null;

        if (currentSavedAt == null) {
            return previousSavedAt;
        }

        if (previousSavedAt == null) {
            return currentSavedAt;
        }

        return currentSavedAt.isAfter(previousSavedAt) ? currentSavedAt : previousSavedAt;
    }

    private boolean canCloseCycle(FinancialPlanData financialPlanData) {
        boolean allCreditAccountsClosed = financialPlanData.creditAccounts().stream()
            .allMatch(account -> account.paidThisMonth() && account.statementCycledAfterPayment());
        boolean allCurrentDebitExpensesCleared = allExpenseItems(financialPlanData).stream()
            .allMatch(item -> Math.abs(item.current()) < CLOSE_CYCLE_CURRENT_EXPENSE_TOLERANCE);
        return !financialPlanData.creditAccounts().isEmpty() && allCreditAccountsClosed && allCurrentDebitExpensesCleared;
    }

    private List<ExpenseItem> allExpenseItems(FinancialPlanData financialPlanData) {
        List<ExpenseItem> expenseItems = new ArrayList<>();
        expenseItems.addAll(financialPlanData.planoExpenses());
        expenseItems.addAll(financialPlanData.sanfordExpenses());
        expenseItems.addAll(financialPlanData.otherExpenses());
        return expenseItems;
    }

    private AuthenticatedUser authenticatedUser(Authentication authentication) {
        if (authentication == null
            || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken
            || !(authentication.getPrincipal() instanceof OAuth2User oauth2User)) {
            throw new IllegalStateException("Authenticated Google user is required");
        }

        String userSub = stringValue(oauth2User.getAttribute("sub"));
        if (userSub == null || userSub.isBlank()) {
            throw new IllegalStateException("Authenticated Google user is missing sub claim");
        }

        String email = stringValue(oauth2User.getAttribute("email"));
        String displayName = stringValue(oauth2User.getAttribute("name"));
        return new AuthenticatedUser(userSub, email, displayName);
    }

    private void ensureAdminAccess(AuthenticatedUser authenticatedUser) {
        if (!AdminEmails.contains(adminAllowedEmails, authenticatedUser.email())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access is restricted");
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private record AuthenticatedUser(String userSub, String email, String displayName) {
    }

    private record StoredCycle(
        FinancialPlanData financialPlanData,
        LocalDate cycleStartDate,
        LocalDate cycleEndDate,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    private record StoredPlanRow(String userSub, String cycleSlot, String planDataJson) {
    }

    private record StoredCycleDateRow(String userSub, String cycleSlot, LocalDate cycleStartDate, LocalDate cycleEndDate) {
    }

    private record StoredHistoryDateRow(
        String userSub,
        String timelineType,
        LocalDate cycleStartDate,
        LocalDate cycleEndDate,
        String email,
        String displayName,
        String historyDataJson
    ) {
    }

    private void initializeStorage() {
        try {
            readDefaultPlan();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize financial plan storage", exception);
        }
    }

    private FinancialPlanData normalizeIds(FinancialPlanData financialPlanData) {
        if (financialPlanData.encryptedData() != null) {
            return financialPlanData;
        }
        List<IncomeSubsection> normalizedIncomeSubsections = normalizeIncomeSubsections(financialPlanData.incomeSubsections());
        Set<String> validExpensePayFromIds = new HashSet<>();
        validExpensePayFromIds.add(DEFAULT_BANK_EXPENSE_SOURCE_ID);
        normalizedIncomeSubsections.forEach(subsection -> validExpensePayFromIds.add(subsection.id()));

        return new FinancialPlanData(
            normalizeCreditAccounts(financialPlanData.creditAccounts()),
            normalizeIncomeItems(financialPlanData.incomeItems()),
            normalizeBalanceItems(financialPlanData.balanceItems()),
            normalizeExpenseItems(financialPlanData.planoExpenses(), PLANO_EXPENSE_IDS, validExpensePayFromIds),
            normalizeExpenseItems(financialPlanData.sanfordExpenses(), SANFORD_EXPENSE_IDS, validExpensePayFromIds),
            normalizeExpenseItems(financialPlanData.otherExpenses(), OTHER_EXPENSE_IDS, validExpensePayFromIds),
            normalizeColumnLabels(financialPlanData.columnLabels()),
            normalizeSectionTitles(financialPlanData.sectionTitles()),
            normalizeViewModes(financialPlanData.viewModes()),
            normalizedIncomeSubsections,
            financialPlanData.summary(),
            null, null, null, null
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
                normalizeIncomeSubsectionBiMonthlySalaryLabel(subsection.biMonthlySalaryLabel()),
                subsection.biMonthlySalary(),
                normalizeIncomeSubsectionMidMonthSalaryLabel(subsection.midMonthSalaryLabel()),
                subsection.midMonthSalaryArrived(),
                normalizeIncomeSubsectionMonthEndSalaryLabel(subsection.monthEndSalaryLabel()),
                subsection.monthEndSalaryArrived(),
                normalizeIncomeSubsectionCheckingBalanceLabel(subsection.checkingBalanceLabel()),
                subsection.checkingBalance(),
                normalizeIncomeSubsectionAdditionalPaymentsLabel(subsection.additionalPaymentsLabel()),
                subsection.additionalPayments(),
                normalizeIncomeSubsectionTotalBalanceLabel(subsection.totalBalanceLabel()),
                normalizeIncomeSubsectionAdditionalIncomeLabel(subsection.additionalIncomeLabel()),
                subsection.additionalIncome(),
                normalizeIncomeSubsectionMonthEndBalanceLabel(subsection.monthEndBalanceLabel())
            ));
        }

        return normalized;
    }

    private String normalizeIncomeSubsectionBiMonthlySalaryLabel(String label) {
        return normalizeText(label, "Bi-monthly salary");
    }

    private String normalizeIncomeSubsectionMidMonthSalaryLabel(String label) {
        return normalizeIncomeLabel(FIRST_PAYCHECK_ID, label);
    }

    private String normalizeIncomeSubsectionMonthEndSalaryLabel(String label) {
        return normalizeIncomeLabel(SECOND_PAYCHECK_ID, label);
    }

    private String normalizeIncomeSubsectionCheckingBalanceLabel(String label) {
        if (label == null
            || label.isBlank()
            || "Checking account balance - primary bank".equals(label)
            || "Checking Account Balance - Chase".equals(label)) {
            return "Account Balance";
        }

        return label;
    }

    private String normalizeIncomeSubsectionAdditionalPaymentsLabel(String label) {
        if (label == null
            || label.isBlank()
            || "Additional payments - primary bank".equals(label)
            || "Additional Payments - Chase".equals(label)) {
            return "Additional Payments";
        }

        return label;
    }

    private String normalizeIncomeSubsectionTotalBalanceLabel(String label) {
        if (label == null
            || label.isBlank()
            || "Total balance - primary bank".equals(label)
            || "Total Balance - Chase".equals(label)) {
            return "Total Balance";
        }

        return label;
    }

    private String normalizeIncomeSubsectionAdditionalIncomeLabel(String label) {
        if (label == null
            || label.isBlank()
            || "Additional income - primary bank".equals(label)
            || "Additional Income - Chase".equals(label)) {
            return "Additional Income";
        }

        return label;
    }

    private String normalizeIncomeSubsectionMonthEndBalanceLabel(String label) {
        if (label == null
            || label.isBlank()
            || "Month End Balance".equals(label)
            || "Checking account balance month end - primary bank".equals(label)
            || "Checking Account Balance @Month End - Chase".equals(label)
            || "Checking account balance month end - Chase".equals(label)) {
            return "Month End Balance minus Dues";
        }

        return label;
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

    private FinancialPlanViewModes normalizeViewModes(FinancialPlanViewModes viewModes) {
        if (viewModes == null) {
            return DEFAULT_VIEW_MODES;
        }

        return new FinancialPlanViewModes(
            normalizeViewMode(viewModes.creditAccounts(), DEFAULT_VIEW_MODES.creditAccounts()),
            normalizeViewMode(viewModes.debitExpenses(), DEFAULT_VIEW_MODES.debitExpenses()),
            normalizeViewMode(viewModes.bankAccounts(), DEFAULT_VIEW_MODES.bankAccounts())
        );
    }

    private String normalizeViewMode(String viewMode, String defaultValue) {
        return "tab".equalsIgnoreCase(viewMode) ? "tab" : defaultValue;
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
        java.util.Map<String, ColumnLabel> actualLabelsById = new java.util.HashMap<>();

        if (actualLabels != null) {
            for (ColumnLabel actualLabel : actualLabels) {
                if (actualLabel != null && actualLabel.id() != null && !actualLabel.id().isBlank()) {
                    actualLabelsById.put(actualLabel.id(), actualLabel);
                }
            }
        }

        for (int index = 0; index < defaultLabels.size(); index++) {
            ColumnLabel defaultLabel = defaultLabels.get(index);
            ColumnLabel actualLabel = actualLabelsById.get(defaultLabel.id());

            if (actualLabel == null && actualLabels != null && index < actualLabels.size()) {
                ColumnLabel indexedLabel = actualLabels.get(index);
                if (indexedLabel != null && (indexedLabel.id() == null || indexedLabel.id().isBlank() || defaultLabel.id().equals(indexedLabel.id()))) {
                    actualLabel = indexedLabel;
                }
            }

            String id = actualLabel != null && actualLabel.id() != null && !actualLabel.id().isBlank() ? actualLabel.id() : defaultLabel.id();
            String label = actualLabel != null && actualLabel.label() != null && !actualLabel.label().isBlank() ? actualLabel.label() : defaultLabel.label();
            label = normalizeLegacyColumnLabel(id, label);
            normalized.add(new ColumnLabel(id, label));
        }
        return normalized;
    }

    private String normalizeLegacyColumnLabel(String id, String label) {
        if ("pay-date".equals(id) && "Pay Date".equals(label)) {
            return "Payment Date";
        }

        if ("statement-date".equals(id)
            && ("Stmt Date".equals(label) || "Last Stmt Date".equals(label))) {
            return "Prev Cycle Stmt Date";
        }

        if ("statement-cycled".equals(id)
            && ("Stmt Cycled".equals(label)
                || "New Stmt Cycled?".equals(label)
                || "Current Cycle Stmt Cycled?".equals(label))) {
            return "Stmt Cycled?";
        }

        if ("statement-balance".equals(id) && "Stmt Balance".equals(label)) {
            return "Latest Stmt Balance";
        }

        if ("credit-limit".equals(id) && "Limit".equals(label)) {
            return "Credit Limit";
        }

        return label;
    }

    private List<CreditAccount> normalizeCreditAccounts(List<CreditAccount> creditAccounts) {
        List<CreditAccount> normalized = new ArrayList<>();
        if (creditAccounts == null) {
            return normalized;
        }

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
        if (incomeItems == null) {
            return normalized;
        }

        for (int index = 0; index < incomeItems.size(); index++) {
            IncomeItem item = incomeItems.get(index);
            String normalizedId = normalizeId(item.id(), INCOME_ITEM_IDS, index, item.label());
            normalized.add(new IncomeItem(
                normalizedId,
                normalizeIncomeLabel(normalizedId, item.label()),
                item.amount(),
                item.month(),
                item.note()
            ));
        }
        return normalized;
    }

    private List<BalanceItem> normalizeBalanceItems(List<BalanceItem> balanceItems) {
        List<BalanceItem> normalized = new ArrayList<>();
        if (balanceItems == null) {
            return normalized;
        }

        for (int index = 0; index < balanceItems.size(); index++) {
            BalanceItem item = balanceItems.get(index);
            String normalizedId = normalizeId(item.id(), BALANCE_ITEM_IDS, index, item.label());
            normalized.add(new BalanceItem(
                normalizedId,
                normalizeBalanceLabel(normalizedId, item.label()),
                item.amount(),
                item.month()
            ));
        }
        return normalized;
    }

    private String normalizeBalanceLabel(String id, String label) {
        if ("checking-balance-chase".equals(id)
            && (label == null
                || label.isBlank()
                || "Checking account balance - primary bank".equals(label)
                || "Checking Account Balance - Chase".equals(label))) {
            return "Account Balance";
        }

        if ("checking-balance-pnc".equals(id)
            && (label == null
                || label.isBlank()
                || "Checking account balance - secondary bank".equals(label)
                || "Checking Account Balance - Secondary Bank".equals(label)
                || "Checking balance - PNC".equals(label))) {
            return "Checking Account Balance - PNC";
        }

        if ("additional-payments-chase".equals(id)
            && (label == null
                || label.isBlank()
                || "Additional payments - primary bank".equals(label)
                || "Additional Payments - Chase".equals(label))) {
            return "Additional Payments";
        }

        if ("total-balance-chase".equals(id)
            && (label == null
                || label.isBlank()
                || "Total balance - primary bank".equals(label)
                || "Total Balance - Chase".equals(label))) {
            return "Total Balance";
        }

        if ("additional-income-chase".equals(id)
            && (label == null
                || label.isBlank()
                || "Additional income - primary bank".equals(label)
                || "Additional Income - Chase".equals(label))) {
            return "Additional Income";
        }

        if ("checking-balance-month-end-chase".equals(id)
            && (label == null
                || label.isBlank()
                || "Month End Balance".equals(label)
                || "Checking account balance month end - primary bank".equals(label)
                || "Checking Account Balance @Month End - Chase".equals(label)
                || "Checking account balance month end - Chase".equals(label))) {
            return "Month End Balance minus Dues";
        }

        if ("additional-other-income".equals(id)
            && (label == null
                || label.isBlank()
                || "Additional other income".equals(label))) {
            return "Additional Other Income";
        }

        if (!SAVINGS_NEXT_MONTH_ID.equals(id)) {
            return label;
        }

        if (label == null
            || label.isBlank()
            || PREVIOUS_SAVINGS_NEXT_MONTH_LABEL.equals(label)
            || LEGACY_NEXT_MONTH_LABEL.equals(label)
            || "Net balance next month end".equals(label)) {
            return SAVINGS_NEXT_MONTH_LABEL;
        }

        return label;
    }

    private String normalizeIncomeLabel(String id, String label) {
        if (FIRST_PAYCHECK_ID.equals(id) && shouldNormalizeFirstPaycheckLabel(label)) {
            return "First Paycheck Arrived?";
        }

        if (SECOND_PAYCHECK_ID.equals(id) && shouldNormalizeSecondPaycheckLabel(label)) {
            return "Second Paycheck Arrived?";
        }

        return label;
    }

    private boolean shouldNormalizeFirstPaycheckLabel(String label) {
        if (label == null || label.isBlank()) {
            return true;
        }

        String comparableLabel = comparableLabel(label);
        return (comparableLabel.contains("first") && comparableLabel.contains("pay"))
            || comparableLabel.contains("15")
            || (comparableLabel.contains("mid") && comparableLabel.contains("salary"));
    }

    private boolean shouldNormalizeSecondPaycheckLabel(String label) {
        if (label == null || label.isBlank()) {
            return true;
        }

        String comparableLabel = comparableLabel(label);
        return (comparableLabel.contains("second") && comparableLabel.contains("pay"))
            || comparableLabel.contains("1st")
            || (comparableLabel.contains("month") && comparableLabel.contains("end") && comparableLabel.contains("salary"));
    }

    private String comparableLabel(String label) {
        return label.toLowerCase(java.util.Locale.US).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private List<ExpenseItem> normalizeExpenseItems(List<ExpenseItem> expenseItems, List<String> defaults, Set<String> validExpensePayFromIds) {
        List<ExpenseItem> normalized = new ArrayList<>();
        if (expenseItems == null) {
            return normalized;
        }

        for (int index = 0; index < expenseItems.size(); index++) {
            ExpenseItem item = expenseItems.get(index);
            normalized.add(new ExpenseItem(
                normalizeId(item.id(), defaults, index, item.label()),
                item.label(),
                item.payDate(),
                normalizeExpensePayFromId(item.payFromBankId(), validExpensePayFromIds),
                item.current(),
                item.next()
            ));
        }
        return normalized;
    }

    private String normalizeExpensePayFromId(String payFromBankId, Set<String> validExpensePayFromIds) {
        if (payFromBankId == null || payFromBankId.isBlank() || !validExpensePayFromIds.contains(payFromBankId)) {
            return DEFAULT_BANK_EXPENSE_SOURCE_ID;
        }

        return payFromBankId;
    }

    private String normalizeId(String currentId, List<String> defaults, int index, String fallbackText) {
        if (LEGACY_NEXT_MONTH_ID.equals(currentId)) {
            return SAVINGS_NEXT_MONTH_ID;
        }
        if (isLegacyFirstPaycheckId(currentId)) {
            return FIRST_PAYCHECK_ID;
        }
        if (isLegacySecondPaycheckId(currentId)) {
            return SECOND_PAYCHECK_ID;
        }
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

    private boolean isLegacyFirstPaycheckId(String currentId) {
        return currentId != null && currentId.startsWith("salary-") && currentId.endsWith("15th");
    }

    private boolean isLegacySecondPaycheckId(String currentId) {
        return currentId != null && currentId.startsWith("salary-") && currentId.endsWith("1st");
    }

    private String normalizeText(String value, String defaultValue) {
        return value != null && !value.isBlank() ? value : defaultValue;
    }

    public void bulkEncryptHistory(Authentication authentication, List<EncryptedHistoryItem> items) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);
        if (items == null || items.isEmpty()) {
            return;
        }
        for (EncryptedHistoryItem item : items) {
            try {
                String encryptedHistoryJson = objectMapper.writeValueAsString(Map.of(
                    "encryptedHistoryData", item.encryptedHistoryData(),
                    "encryptionIv", item.encryptionIv()
                ));
                jdbcClient.sql("""
                    UPDATE app_user_financial_plan_cycle_history
                    SET history_data = CAST(:historyData AS jsonb), updated_at = NOW()
                    WHERE user_sub = :userSub AND timeline_type = :timelineType
                      AND cycle_start_date = :cycleStartDate AND cycle_end_date = :cycleEndDate
                    """)
                    .param("userSub", authenticatedUser.userSub())
                    .param("timelineType", item.timelineType())
                    .param("cycleStartDate", LocalDate.parse(item.cycleStartDate()))
                    .param("cycleEndDate", LocalDate.parse(item.cycleEndDate()))
                    .param("historyData", encryptedHistoryJson)
                    .update();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to bulk encrypt history", e);
            }
        }
    }
}