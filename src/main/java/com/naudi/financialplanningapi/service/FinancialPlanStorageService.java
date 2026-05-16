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
import com.naudi.financialplanningapi.model.RestoreBackupRequest;
import com.naudi.financialplanningapi.model.SwitchTimelineRequest;
import com.naudi.financialplanningapi.model.TimelineType;
import com.naudi.financialplanningapi.model.UserPremiumStatusRequest;
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
    private static final String ARCHIVE_TABLE = "app_user_financial_plan_cycle_archive";

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
    private static final int PREMIUM_VISIBLE_CLOSED_CYCLE_COUNT = 12;
    private static final int REGULAR_VISIBLE_CLOSED_CYCLE_COUNT = 1;
    private static final int ARCHIVED_CLOSED_CYCLE_RETENTION_COUNT = PREMIUM_VISIBLE_CLOSED_CYCLE_COUNT - 1;
    private static final String CLOSED_CYCLE_SELECTION_PREFIX = "closed:";

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
        new ColumnLabel("statement-cycled", "Stmt for Next Cycle Pymnt Cycled?"),
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
        new ColumnLabel("paid", "Paid"),
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

    public FinancialPlanCycleResponse load(Authentication authentication, String cycleSelection) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);
        UserSettings userSettings = userSettingsFor(authenticatedUser.userSub());
        TimelineType timelineType = userSettings.timelineType();
        StoredCycle currentCycle = loadStoredCycle(authenticatedUser, CycleSlot.CURRENT);
        StoredCycle previousCycle = loadStoredCycle(authenticatedUser, CycleSlot.PREVIOUS);

        CyclePeriod resolvedCurrentCycle = cyclePeriodFor(currentCycle, CycleSlot.CURRENT, timelineType);
        CyclePeriod resolvedPreviousCycle = previousCycle != null ? cyclePeriodFor(previousCycle, CycleSlot.PREVIOUS, timelineType) : null;
        FinancialPlanData currentData = currentCycle != null ? currentCycle.financialPlanData() : buildSeededPlanSafely();

        ResolvedClosedCycle selectedClosedCycle = resolveSelectedClosedCycle(
            authenticatedUser.userSub(),
            timelineType,
            cycleSelection,
            previousCycle,
            userSettings.premium()
        );

        if (selectedClosedCycle != null) {
            return buildResponse(
                selectedClosedCycle.financialPlanData(),
                CycleSlot.PREVIOUS,
                timelineType,
                resolvedCurrentCycle,
                resolvedPreviousCycle,
                hasSavedPlan(currentCycle),
                canCloseCycle(currentData),
                currentCycle,
                previousCycle,
                authenticatedUser.userSub(),
                userSettings.premium(),
                selectedClosedCycle.cyclePeriod()
            );
        }

        return buildResponse(
            currentData,
            CycleSlot.CURRENT,
            timelineType,
            resolvedCurrentCycle,
            resolvedPreviousCycle,
            hasSavedPlan(currentCycle),
            canCloseCycle(currentData),
            currentCycle,
            previousCycle,
            authenticatedUser.userSub(),
            userSettings.premium(),
            null
        );
    }

    public FinancialPlanCycleResponse save(Authentication authentication, CycleSlot cycleSlot, FinancialPlanData financialPlanData) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);
        try {
            TimelineType timelineType = timelineTypeFor(authenticatedUser.userSub());
            StoredCycle currentCycle = loadStoredCycle(authenticatedUser, CycleSlot.CURRENT);
            StoredCycle previousCycle = loadStoredCycle(authenticatedUser, CycleSlot.PREVIOUS);
            CyclePeriod currentPeriod = cyclePeriodFor(currentCycle, CycleSlot.CURRENT, timelineType);

            if (cycleSlot == CycleSlot.PREVIOUS) {
                if (!hasEncryptedPayload(financialPlanData)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Previous cycle is read only");
                }
                if (previousCycle == null) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Previous cycle not found");
                }

                CyclePeriod previousPeriod = cyclePeriodFor(previousCycle, CycleSlot.PREVIOUS, timelineType);
                FinancialPlanData normalizedData = normalizeIds(financialPlanData);
                FinancialPlanData enrichedData = financialPlanCalculationService.withCalculatedSummary(normalizedData);
                upsertSettings(authenticatedUser, timelineType);
                upsertPlan(authenticatedUser, CycleSlot.PREVIOUS, previousPeriod, enrichedData);
                StoredCycle savedPreviousCycle = loadStoredCycle(authenticatedUser, CycleSlot.PREVIOUS);

                FinancialPlanData currentData = currentCycle != null ? currentCycle.financialPlanData() : buildSeededPlanSafely();
                return buildResponse(
                    enrichedData,
                    CycleSlot.PREVIOUS,
                    timelineType,
                    currentPeriod,
                    previousPeriod,
                    hasSavedPlan(currentCycle),
                    canCloseCycle(currentData),
                    currentCycle,
                    savedPreviousCycle,
                    authenticatedUser.userSub(),
                    isPremiumUser(authenticatedUser.userSub()),
                    previousPeriod
                );
            }

            boolean encryptionExempt = AdminEmails.contains(encryptionExemptEmails, authenticatedUser.email());
            if (!encryptionExempt
                && currentCycle != null
                && hasEncryptedPayload(currentCycle.financialPlanData())
                && !hasEncryptedPayload(financialPlanData)) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Your tracker is encrypted. Unlock it with your Encryption Key before saving."
                );
            }

            FinancialPlanData normalizedData = normalizeIds(financialPlanData);
            validateRequiredPaycheckDates(normalizedData);
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
                previousCycle,
                authenticatedUser.userSub(),
                isPremiumUser(authenticatedUser.userSub()),
                null
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save financial plan data", exception);
        }
    }

    @Transactional
    public FinancialPlanCycleResponse restoreBackup(Authentication authentication, RestoreBackupRequest restoreBackupRequest) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);
        try {
            TimelineType timelineType = restoreBackupRequest.timelineType() != null
                ? restoreBackupRequest.timelineType()
                : timelineTypeFor(authenticatedUser.userSub());

            boolean encryptionExempt = AdminEmails.contains(encryptionExemptEmails, authenticatedUser.email());

            CyclePeriod currentPeriod = restoreBackupRequest.currentCycle() != null
                ? restoreBackupRequest.currentCycle()
                : cycleWindowFor(LocalDate.now(), timelineType);

            FinancialPlanData requestCurrentData = restoreBackupRequest.financialPlanData() != null
                ? restoreBackupRequest.financialPlanData()
                : buildSeededPlanSafely();

            StoredCycle existingCurrentCycle = loadStoredCycle(authenticatedUser, CycleSlot.CURRENT);
            if (!encryptionExempt
                && existingCurrentCycle != null
                && hasEncryptedPayload(existingCurrentCycle.financialPlanData())
                && !hasEncryptedPayload(requestCurrentData)) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Your tracker is encrypted. Unlock it with your Encryption Key before restoring a backup."
                );
            }

            FinancialPlanData normalizedCurrentData = normalizeIds(requestCurrentData);
            FinancialPlanData enrichedCurrentData = financialPlanCalculationService.withCalculatedSummary(normalizedCurrentData);

            upsertSettings(authenticatedUser, timelineType);
            deleteArchivedClosedCycles(authenticatedUser.userSub(), timelineType);
            upsertPlan(authenticatedUser, CycleSlot.CURRENT, currentPeriod, enrichedCurrentData);

            CyclePeriod previousPeriod = null;
            FinancialPlanData requestPreviousData = restoreBackupRequest.previousFinancialPlanData();

            if (requestPreviousData != null && restoreBackupRequest.previousCycle() != null) {
                StoredCycle existingPreviousCycle = loadStoredCycle(authenticatedUser, CycleSlot.PREVIOUS);
                if (!encryptionExempt
                    && existingPreviousCycle != null
                    && hasEncryptedPayload(existingPreviousCycle.financialPlanData())
                    && !hasEncryptedPayload(requestPreviousData)) {
                    throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Your tracker is encrypted. Unlock it with your Encryption Key before restoring a backup."
                    );
                }
                previousPeriod = restoreBackupRequest.previousCycle();
                FinancialPlanData normalizedPreviousData = normalizeIds(requestPreviousData);
                FinancialPlanData enrichedPreviousData = financialPlanCalculationService.withCalculatedSummary(normalizedPreviousData);
                upsertPlan(authenticatedUser, CycleSlot.PREVIOUS, previousPeriod, enrichedPreviousData);
            } else {
                jdbcClient.sql("""
                        DELETE FROM app_user_financial_plan_cycle
                        WHERE user_sub = :userSub
                          AND cycle_slot = :cycleSlot
                        """)
                    .param("userSub", authenticatedUser.userSub())
                    .param("cycleSlot", CycleSlot.PREVIOUS.name())
                    .update();
            }

            StoredCycle savedCurrentCycle = loadStoredCycle(authenticatedUser, CycleSlot.CURRENT);
            StoredCycle savedPreviousCycle = loadStoredCycle(authenticatedUser, CycleSlot.PREVIOUS);
            CyclePeriod resolvedPrevious = savedPreviousCycle != null
                ? cyclePeriodFor(savedPreviousCycle, CycleSlot.PREVIOUS, timelineType)
                : null;

            return buildResponse(
                enrichedCurrentData,
                CycleSlot.CURRENT,
                timelineType,
                currentPeriod,
                resolvedPrevious,
                true,
                canCloseCycle(enrichedCurrentData),
                savedCurrentCycle,
                savedPreviousCycle,
                authenticatedUser.userSub(),
                isPremiumUser(authenticatedUser.userSub()),
                null
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to restore backup", exception);
        }
    }

    private boolean hasEncryptedPayload(FinancialPlanData financialPlanData) {
        if (financialPlanData == null) {
            return false;
        }

        String encryptedData = financialPlanData.encryptedData();
        String encryptionIv = financialPlanData.encryptionIv();
        return encryptedData != null
            && !encryptedData.isBlank()
            && encryptionIv != null
            && !encryptionIv.isBlank();
    }

    public List<FinancialPlanViewerUserSummary> listViewerUsers(Authentication authentication) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);
        ensureAdminAccess(authenticatedUser);

        return jdbcClient.sql("""
                                                                SELECT cycles.user_sub,
                                                                                         MAX(NULLIF(cycles.email, '')) AS email,
                                                                                         MAX(NULLIF(cycles.display_name, '')) AS display_name,
                                                                                         MAX(cycles.updated_at) AS last_updated_at,
                                                                                         BOOL_OR(COALESCE(NULLIF(cycles.plan_data->>'encryptedData', ''), '') <> '') AS has_encrypted_data,
                                                                                         COALESCE(MAX(CASE WHEN settings.is_premium THEN 1 ELSE 0 END), 0) = 1 AS is_premium
                                FROM app_user_financial_plan_cycle cycles
                                LEFT JOIN app_user_financial_plan_settings settings
                                    ON settings.user_sub = cycles.user_sub
                                                                WHERE cycles.user_sub <> :sampleMidUserSub
                                                  AND cycles.user_sub <> :sampleStartUserSub
                                                                GROUP BY cycles.user_sub
                                                                ORDER BY COALESCE(MAX(NULLIF(cycles.display_name, '')), MAX(NULLIF(cycles.email, '')), cycles.user_sub)
                """)
            .param("sampleMidUserSub", SAMPLE_PLAN_MID_TO_MID_USER_SUB)
            .param("sampleStartUserSub", SAMPLE_PLAN_START_TO_END_USER_SUB)
            .query((resultSet, rowNum) -> new FinancialPlanViewerUserSummary(
                resultSet.getString("user_sub"),
                resultSet.getString("email"),
                resultSet.getString("display_name"),
                resultSet.getTimestamp("last_updated_at") != null
                    ? resultSet.getTimestamp("last_updated_at").toInstant()
                    : null,
                !resultSet.getBoolean("has_encrypted_data"),
                resultSet.getBoolean("is_premium")
            ))
            .list();
    }

    public FinancialPlanViewerUserSummary updateViewerUserPremium(
        Authentication authentication,
        String targetUserSub,
        UserPremiumStatusRequest request
    ) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);
        ensureAdminAccess(authenticatedUser);

        if (targetUserSub == null || targetUserSub.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target user is required");
        }

        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Premium status payload is required");
        }

        upsertPremiumStatus(targetUserSub, request.premium());
        return loadViewerUserSummary(targetUserSub);
    }

    public FinancialPlanCycleResponse loadViewerPlan(Authentication authentication, String userSub, String cycleSelection) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);
        ensureAdminAccess(authenticatedUser);

        if (userSub == null || userSub.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Viewed user is required");
        }

        UserSettings viewerSettings = userSettingsFor(userSub);
        TimelineType viewerTimelineType = viewerSettings.timelineType();

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

        ResolvedClosedCycle selectedClosedCycle = resolveSelectedClosedCycle(
            userSub,
            viewerTimelineType,
            cycleSelection,
            previousCycle,
            viewerSettings.premium()
        );

        if (selectedClosedCycle != null) {
            return buildResponse(
                selectedClosedCycle.financialPlanData(),
                CycleSlot.PREVIOUS,
                viewerTimelineType,
                resolvedCurrentCycle,
                resolvedPreviousCycle,
                viewerHasSavedPlan,
                false,
                true,
                currentCycle,
                previousCycle,
                userSub,
                viewerSettings.premium(),
                selectedClosedCycle.cyclePeriod()
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
                previousCycle,
                userSub,
                viewerSettings.premium(),
                resolvedPreviousCycle
            );
        }

        return buildResponse(
            financialPlanCalculationService.withCalculatedSummary(currentCycle.financialPlanData()),
            CycleSlot.CURRENT,
            viewerTimelineType,
            resolvedCurrentCycle,
            resolvedPreviousCycle,
            viewerHasSavedPlan,
            false,
            true,
            currentCycle,
            previousCycle,
            userSub,
            viewerSettings.premium(),
            null
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
            archiveClosedCycleSnapshot(authenticatedUser, timelineType, previousCycle);
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
                savedPreviousCycle,
                authenticatedUser.userSub(),
                isPremiumUser(authenticatedUser.userSub()),
                null
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
            StoredArchivedCycle restoredPreviousArchivedCycle = loadLatestArchivedClosedCycle(authenticatedUser.userSub(), timelineType);
            StoredCycle restoredPreviousCycle = null;
            if (restoredPreviousArchivedCycle != null) {
                upsertPlan(
                    authenticatedUser,
                    CycleSlot.PREVIOUS,
                    restoredPreviousArchivedCycle.cyclePeriod(),
                    restoredPreviousArchivedCycle.financialPlanData()
                );
                deleteArchivedClosedCycle(authenticatedUser.userSub(), timelineType, restoredPreviousArchivedCycle.cyclePeriod());
                restoredPreviousCycle = loadStoredCycle(authenticatedUser, CycleSlot.PREVIOUS);
            } else {
                deletePlan(authenticatedUser, CycleSlot.PREVIOUS);
            }
            StoredCycle restoredCurrentCycle = loadStoredCycle(authenticatedUser, CycleSlot.CURRENT);

            return buildResponse(
                restoredCurrentData,
                CycleSlot.CURRENT,
                timelineType,
                previousPeriod,
                restoredPreviousCycle != null ? cyclePeriodFor(restoredPreviousCycle, CycleSlot.PREVIOUS, timelineType) : null,
                true,
                canCloseCycle(restoredCurrentData),
                restoredCurrentCycle,
                restoredPreviousCycle,
                authenticatedUser.userSub(),
                isPremiumUser(authenticatedUser.userSub()),
                null
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to revert closed financial cycle", exception);
        }
    }

    public FinancialPlanCycleResponse loadSample(Authentication authentication, String cycleSelection, TimelineType timelineType) {
        authenticatedUser(authentication);

        try {
            AuthenticatedUser sampleUser = sampleUserForTimeline(timelineType);
            StoredCycle currentCycle = loadStoredCycle(sampleUser, CycleSlot.CURRENT);
            if (currentCycle == null && isCurrentSelection(cycleSelection)) {
                createSamplePlanFromSource(timelineType);
                currentCycle = loadStoredCycle(sampleUser, CycleSlot.CURRENT);
            }

            StoredCycle previousCycle = loadStoredCycle(sampleUser, CycleSlot.PREVIOUS);
            CyclePeriod resolvedCurrentCycle = cyclePeriodFor(currentCycle, CycleSlot.CURRENT, timelineType);
            CyclePeriod resolvedPreviousCycle = previousCycle != null ? cyclePeriodFor(previousCycle, CycleSlot.PREVIOUS, timelineType) : null;
            FinancialPlanData currentData = currentCycle != null ? currentCycle.financialPlanData() : buildSeededPlanSafely();

            boolean premiumUser = isPremiumUser(sampleUser.userSub());
            ResolvedClosedCycle selectedClosedCycle = resolveSelectedClosedCycle(
                sampleUser.userSub(),
                timelineType,
                cycleSelection,
                previousCycle,
                premiumUser
            );

            if (selectedClosedCycle != null) {
                return buildResponse(
                    selectedClosedCycle.financialPlanData(),
                    CycleSlot.PREVIOUS,
                    timelineType,
                    resolvedCurrentCycle,
                    resolvedPreviousCycle,
                    hasSavedPlan(currentCycle),
                    canCloseCycle(currentData),
                    currentCycle,
                    previousCycle,
                    sampleUser.userSub(),
                    premiumUser,
                    selectedClosedCycle.cyclePeriod()
                );
            }

            return buildResponse(
                currentData,
                CycleSlot.CURRENT,
                timelineType,
                resolvedCurrentCycle,
                resolvedPreviousCycle,
                hasSavedPlan(currentCycle),
                canCloseCycle(currentData),
                currentCycle,
                previousCycle,
                sampleUser.userSub(),
                premiumUser,
                null
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
                previousCycle,
                sampleUser.userSub(),
                isPremiumUser(sampleUser.userSub()),
                null
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
            archiveClosedCycleSnapshot(sampleUser, timelineType, previousCycle);
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
                savedPreviousCycle,
                sampleUser.userSub(),
                isPremiumUser(sampleUser.userSub()),
                null
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
            StoredArchivedCycle restoredPreviousArchivedCycle = loadLatestArchivedClosedCycle(sampleUser.userSub(), timelineType);
            StoredCycle restoredPreviousCycle = null;
            if (restoredPreviousArchivedCycle != null) {
                upsertPlan(
                    sampleUser,
                    CycleSlot.PREVIOUS,
                    restoredPreviousArchivedCycle.cyclePeriod(),
                    restoredPreviousArchivedCycle.financialPlanData()
                );
                deleteArchivedClosedCycle(sampleUser.userSub(), timelineType, restoredPreviousArchivedCycle.cyclePeriod());
                restoredPreviousCycle = loadStoredCycle(sampleUser, CycleSlot.PREVIOUS);
            } else {
                deletePlan(sampleUser, CycleSlot.PREVIOUS);
            }
            StoredCycle restoredCurrentCycle = loadStoredCycle(sampleUser, CycleSlot.CURRENT);

            return buildResponse(
                restoredCurrentData,
                CycleSlot.CURRENT,
                timelineType,
                previousPeriod,
                restoredPreviousCycle != null ? cyclePeriodFor(restoredPreviousCycle, CycleSlot.PREVIOUS, timelineType) : null,
                true,
                canCloseCycle(restoredCurrentData),
                restoredCurrentCycle,
                restoredPreviousCycle,
                sampleUser.userSub(),
                isPremiumUser(sampleUser.userSub()),
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
            deleteArchivedClosedCycles(targetSampleUser.userSub(), targetTimelineType);
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
                null,
                targetSampleUser.userSub(),
                isPremiumUser(targetSampleUser.userSub()),
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

        deleteArchivedClosedCycles(sampleUser.userSub(), null);
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

        deleteArchivedClosedCycles(authenticatedUser.userSub(), null);

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

        deleteArchivedClosedCycles(targetUserSub, null);

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

            boolean encryptionExempt = AdminEmails.contains(encryptionExemptEmails, authenticatedUser.email());
            if (!encryptionExempt
                && currentCycle != null
                && hasEncryptedPayload(currentCycle.financialPlanData())
                && !hasEncryptedPayload(switchTimelineRequest.financialPlanData())) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Your tracker is encrypted. Unlock it with your Encryption Key before switching timeline."
                );
            }

            if (!currentPeriod.equals(switchTimelineRequest.expectedCurrentCycle())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Current cycle changed. Reload before switching timeline.");
            }

            TimelineType targetTimelineType = switchTimelineRequest.targetTimelineType();
            FinancialPlanData normalizedData = normalizeIds(switchTimelineRequest.financialPlanData());
            FinancialPlanData enrichedData = financialPlanCalculationService.withCalculatedSummary(normalizedData);
            CyclePeriod targetCurrentCycle = currentCycleForTimeline(targetTimelineType, LocalDate.now());

            upsertSettings(authenticatedUser, targetTimelineType);
            deleteArchivedClosedCycles(authenticatedUser.userSub(), targetTimelineType);
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
                null,
                authenticatedUser.userSub(),
                isPremiumUser(authenticatedUser.userSub()),
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
        StoredCycle previousStoredCycle,
        String userSub,
        boolean premiumUser,
        CyclePeriod selectedClosedCycle
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
            previousStoredCycle,
            userSub,
            premiumUser,
            selectedClosedCycle
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
        StoredCycle previousStoredCycle,
        String userSub,
        boolean premiumUser,
        CyclePeriod selectedClosedCycle
    ) {
        List<CyclePeriod> closedCycles = loadVisibleClosedCycles(userSub, timelineType, premiumUser, previousStoredCycle);
        return new FinancialPlanCycleResponse(
            financialPlanData,
            selectedCycle,
            timelineType,
            currentCycle,
            previousCycle,
            closedCycles,
            selectedClosedCycle,
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

    private List<CyclePeriod> loadVisibleClosedCycles(
        String userSub,
        TimelineType timelineType,
        boolean premiumUser,
        StoredCycle previousStoredCycle
    ) {
        int visibleCount = premiumUser ? PREMIUM_VISIBLE_CLOSED_CYCLE_COUNT : REGULAR_VISIBLE_CLOSED_CYCLE_COUNT;
        if (visibleCount <= 0) {
            return List.of();
        }

        List<CyclePeriod> closedCycles = new ArrayList<>();
        if (previousStoredCycle != null) {
            closedCycles.add(cyclePeriodFor(previousStoredCycle, CycleSlot.PREVIOUS, timelineType));
        }

        int archivedLimit = Math.max(visibleCount - closedCycles.size(), 0);
        if (archivedLimit > 0) {
            closedCycles.addAll(loadArchivedClosedCyclePeriods(userSub, timelineType, archivedLimit));
        }

        return closedCycles;
    }

    private List<CyclePeriod> loadArchivedClosedCyclePeriods(String userSub, TimelineType timelineType, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        return jdbcClient.sql("""
                SELECT cycle_start_date, cycle_end_date
                FROM app_user_financial_plan_cycle_archive
                WHERE user_sub = :userSub
                  AND timeline_type = :timelineType
                ORDER BY cycle_end_date DESC, cycle_start_date DESC
                LIMIT :limit
                """)
            .param("userSub", userSub)
            .param("timelineType", timelineType.name())
            .param("limit", limit)
            .query((resultSet, rowNum) -> new CyclePeriod(
                resultSet.getObject("cycle_start_date", LocalDate.class),
                resultSet.getObject("cycle_end_date", LocalDate.class)
            ))
            .list();
    }

    private ResolvedClosedCycle resolveSelectedClosedCycle(
        String userSub,
        TimelineType timelineType,
        String cycleSelection,
        StoredCycle previousStoredCycle,
        boolean premiumUser
    ) {
        if (isCurrentSelection(cycleSelection)) {
            return null;
        }

        if (previousStoredCycle == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Closed cycle not found");
        }

        CyclePeriod latestClosedCycle = cyclePeriodFor(previousStoredCycle, CycleSlot.PREVIOUS, timelineType);
        if (cycleSelection == null || cycleSelection.isBlank() || CycleSlot.PREVIOUS.wireValue().equalsIgnoreCase(cycleSelection)) {
            return new ResolvedClosedCycle(
                financialPlanCalculationService.withCalculatedSummary(previousStoredCycle.financialPlanData()),
                latestClosedCycle
            );
        }

        CyclePeriod requestedClosedCycle = parseClosedCycleSelection(cycleSelection);
        List<CyclePeriod> visibleClosedCycles = loadVisibleClosedCycles(userSub, timelineType, premiumUser, previousStoredCycle);
        if (visibleClosedCycles.stream().noneMatch(requestedClosedCycle::equals)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Closed cycle not found");
        }

        if (requestedClosedCycle.equals(latestClosedCycle)) {
            return new ResolvedClosedCycle(
                financialPlanCalculationService.withCalculatedSummary(previousStoredCycle.financialPlanData()),
                latestClosedCycle
            );
        }

        StoredArchivedCycle archivedCycle = loadArchivedClosedCycle(userSub, timelineType, requestedClosedCycle);
        if (archivedCycle == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Closed cycle not found");
        }

        return new ResolvedClosedCycle(
            financialPlanCalculationService.withCalculatedSummary(archivedCycle.financialPlanData()),
            archivedCycle.cyclePeriod()
        );
    }

    private boolean isCurrentSelection(String cycleSelection) {
        return cycleSelection == null
            || cycleSelection.isBlank()
            || CycleSlot.CURRENT.wireValue().equalsIgnoreCase(cycleSelection);
    }

    private CyclePeriod parseClosedCycleSelection(String cycleSelection) {
        String normalizedSelection = cycleSelection != null && cycleSelection.startsWith(CLOSED_CYCLE_SELECTION_PREFIX)
            ? cycleSelection.substring(CLOSED_CYCLE_SELECTION_PREFIX.length())
            : cycleSelection;

        if (normalizedSelection == null || normalizedSelection.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Closed cycle selection is required");
        }

        String[] parts = normalizedSelection.split(":", 2);
        if (parts.length != 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported cycle selection");
        }

        try {
            return new CyclePeriod(LocalDate.parse(parts[0]), LocalDate.parse(parts[1]));
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported cycle selection");
        }
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

    private UserSettings userSettingsFor(String userSub) {
        return jdbcClient.sql("""
                SELECT timeline_type, is_premium
                FROM app_user_financial_plan_settings
                WHERE user_sub = :userSub
                """)
            .param("userSub", userSub)
            .query((resultSet, rowNum) -> new UserSettings(
                TimelineType.fromStoredValue(resultSet.getString("timeline_type")),
                resultSet.getBoolean("is_premium")
            ))
            .optional()
            .orElse(new UserSettings(TimelineType.START_TO_END, false));
    }

    private TimelineType timelineTypeFor(String userSub) {
        return userSettingsFor(userSub).timelineType();
    }

    private boolean isPremiumUser(String userSub) {
        return userSettingsFor(userSub).premium();
    }

    private StoredArchivedCycle loadArchivedClosedCycle(String userSub, TimelineType timelineType, CyclePeriod cyclePeriod) {
        return jdbcClient.sql("""
                SELECT plan_data::text, cycle_start_date, cycle_end_date, created_at, updated_at
                FROM app_user_financial_plan_cycle_archive
                WHERE user_sub = :userSub
                  AND timeline_type = :timelineType
                  AND cycle_start_date = :cycleStartDate
                  AND cycle_end_date = :cycleEndDate
                """)
            .param("userSub", userSub)
            .param("timelineType", timelineType.name())
            .param("cycleStartDate", cyclePeriod.startDate())
            .param("cycleEndDate", cyclePeriod.endDate())
            .query((resultSet, rowNum) -> {
                try {
                    return new StoredArchivedCycle(
                        objectMapper.readValue(resultSet.getString("plan_data"), FinancialPlanData.class),
                        new CyclePeriod(
                            resultSet.getObject("cycle_start_date", LocalDate.class),
                            resultSet.getObject("cycle_end_date", LocalDate.class)
                        ),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("updated_at").toInstant()
                    );
                } catch (IOException exception) {
                    throw new IllegalStateException("Failed to read archived cycle", exception);
                }
            })
            .optional()
            .orElse(null);
    }

    private StoredArchivedCycle loadLatestArchivedClosedCycle(String userSub, TimelineType timelineType) {
        return jdbcClient.sql("""
                SELECT plan_data::text, cycle_start_date, cycle_end_date, created_at, updated_at
                FROM app_user_financial_plan_cycle_archive
                WHERE user_sub = :userSub
                  AND timeline_type = :timelineType
                ORDER BY cycle_end_date DESC, cycle_start_date DESC
                LIMIT 1
                """)
            .param("userSub", userSub)
            .param("timelineType", timelineType.name())
            .query((resultSet, rowNum) -> {
                try {
                    return new StoredArchivedCycle(
                        objectMapper.readValue(resultSet.getString("plan_data"), FinancialPlanData.class),
                        new CyclePeriod(
                            resultSet.getObject("cycle_start_date", LocalDate.class),
                            resultSet.getObject("cycle_end_date", LocalDate.class)
                        ),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("updated_at").toInstant()
                    );
                } catch (IOException exception) {
                    throw new IllegalStateException("Failed to read archived cycle", exception);
                }
            })
            .optional()
            .orElse(null);
    }

    private void archiveClosedCycleSnapshot(AuthenticatedUser authenticatedUser, TimelineType timelineType, StoredCycle storedCycle) throws IOException {
        if (storedCycle == null) {
            return;
        }

        CyclePeriod cyclePeriod = cyclePeriodFor(storedCycle, CycleSlot.PREVIOUS, timelineType);
        String planJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(storedCycle.financialPlanData());

        jdbcClient.sql("""
            INSERT INTO app_user_financial_plan_cycle_archive (
                user_sub,
                timeline_type,
                cycle_start_date,
                cycle_end_date,
                email,
                display_name,
                plan_data
            )
            VALUES (
                :userSub,
                :timelineType,
                :cycleStartDate,
                :cycleEndDate,
                :email,
                :displayName,
                CAST(:planData AS jsonb)
            )
            ON CONFLICT (user_sub, timeline_type, cycle_start_date, cycle_end_date)
                DO UPDATE SET
                    email = EXCLUDED.email,
                    display_name = EXCLUDED.display_name,
                    plan_data = EXCLUDED.plan_data,
                    updated_at = NOW()
            """)
            .param("userSub", authenticatedUser.userSub())
            .param("timelineType", timelineType.name())
            .param("cycleStartDate", cyclePeriod.startDate())
            .param("cycleEndDate", cyclePeriod.endDate())
            .param("email", authenticatedUser.email())
            .param("displayName", authenticatedUser.displayName())
            .param("planData", planJson)
            .update();

        pruneArchivedClosedCycles(authenticatedUser.userSub(), timelineType);
    }

    private void pruneArchivedClosedCycles(String userSub, TimelineType timelineType) {
        jdbcClient.sql("""
                WITH rows_to_delete AS (
                    SELECT cycle_start_date, cycle_end_date
                    FROM app_user_financial_plan_cycle_archive
                    WHERE user_sub = :userSub
                      AND timeline_type = :timelineType
                    ORDER BY cycle_end_date DESC, cycle_start_date DESC
                    OFFSET :retentionCount
                )
                DELETE FROM app_user_financial_plan_cycle_archive archive
                USING rows_to_delete
                WHERE archive.user_sub = :userSub
                  AND archive.timeline_type = :timelineType
                  AND archive.cycle_start_date = rows_to_delete.cycle_start_date
                  AND archive.cycle_end_date = rows_to_delete.cycle_end_date
                """)
            .param("userSub", userSub)
            .param("timelineType", timelineType.name())
            .param("retentionCount", ARCHIVED_CLOSED_CYCLE_RETENTION_COUNT)
            .update();
    }

    private void deleteArchivedClosedCycle(String userSub, TimelineType timelineType, CyclePeriod cyclePeriod) {
        jdbcClient.sql("""
                DELETE FROM app_user_financial_plan_cycle_archive
                WHERE user_sub = :userSub
                  AND timeline_type = :timelineType
                  AND cycle_start_date = :cycleStartDate
                  AND cycle_end_date = :cycleEndDate
                """)
            .param("userSub", userSub)
            .param("timelineType", timelineType.name())
            .param("cycleStartDate", cyclePeriod.startDate())
            .param("cycleEndDate", cyclePeriod.endDate())
            .update();
    }

    private void deleteArchivedClosedCycles(String userSub, TimelineType timelineType) {
        if (timelineType == null) {
            jdbcClient.sql("""
                    DELETE FROM app_user_financial_plan_cycle_archive
                    WHERE user_sub = :userSub
                    """)
                .param("userSub", userSub)
                .update();
            return;
        }

        jdbcClient.sql("""
                DELETE FROM app_user_financial_plan_cycle_archive
                WHERE user_sub = :userSub
                  AND timeline_type = :timelineType
                """)
            .param("userSub", userSub)
            .param("timelineType", timelineType.name())
            .update();
    }

    private void upsertPremiumStatus(String userSub, boolean premium) {
        TimelineType timelineType = timelineTypeFor(userSub);

        jdbcClient.sql("""
            INSERT INTO app_user_financial_plan_settings (user_sub, email, display_name, timeline_type, is_premium)
            VALUES (
                :userSub,
                COALESCE((SELECT MAX(NULLIF(email, '')) FROM app_user_financial_plan_cycle WHERE user_sub = :userSub), ''),
                COALESCE((SELECT MAX(NULLIF(display_name, '')) FROM app_user_financial_plan_cycle WHERE user_sub = :userSub), ''),
                :timelineType,
                :premium
            )
            ON CONFLICT (user_sub)
                DO UPDATE SET
                    is_premium = EXCLUDED.is_premium,
                    updated_at = NOW()
            """)
            .param("userSub", userSub)
            .param("timelineType", timelineType.name())
            .param("premium", premium)
            .update();
    }

    private FinancialPlanViewerUserSummary loadViewerUserSummary(String userSub) {
        return jdbcClient.sql("""
                SELECT cycles.user_sub,
                       MAX(NULLIF(cycles.email, '')) AS email,
                       MAX(NULLIF(cycles.display_name, '')) AS display_name,
                       MAX(cycles.updated_at) AS last_updated_at,
                       BOOL_OR(COALESCE(NULLIF(cycles.plan_data->>'encryptedData', ''), '') <> '') AS has_encrypted_data,
                       COALESCE(MAX(CASE WHEN settings.is_premium THEN 1 ELSE 0 END), 0) = 1 AS is_premium
                FROM app_user_financial_plan_cycle cycles
                LEFT JOIN app_user_financial_plan_settings settings
                  ON settings.user_sub = cycles.user_sub
                WHERE cycles.user_sub = :userSub
                GROUP BY cycles.user_sub
                """)
            .param("userSub", userSub)
            .query((resultSet, rowNum) -> new FinancialPlanViewerUserSummary(
                resultSet.getString("user_sub"),
                resultSet.getString("email"),
                resultSet.getString("display_name"),
                resultSet.getTimestamp("last_updated_at") != null
                    ? resultSet.getTimestamp("last_updated_at").toInstant()
                    : null,
                !resultSet.getBoolean("has_encrypted_data"),
                resultSet.getBoolean("is_premium")
            ))
            .optional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Viewed user was not found"));
    }

    private void upsertSettings(AuthenticatedUser authenticatedUser, TimelineType timelineType) {
        jdbcClient.sql("""
            INSERT INTO app_user_financial_plan_settings (user_sub, email, display_name, timeline_type, is_premium)
                VALUES (
                    :userSub,
                    :email,
                    :displayName,
                    :timelineType,
                    COALESCE((SELECT is_premium FROM app_user_financial_plan_settings WHERE user_sub = :userSub), FALSE)
                )
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
        if (!hasRequiredPaycheckDates(financialPlanData)) {
            return false;
        }

        boolean allCreditAccountsClosed = financialPlanData.creditAccounts().stream()
            .allMatch(account -> account.paidThisMonth() && account.statementCycledAfterPayment());
        boolean allCurrentDebitExpensesCleared = allExpenseItems(financialPlanData).stream()
            .allMatch(item -> Math.abs(item.current()) < CLOSE_CYCLE_CURRENT_EXPENSE_TOLERANCE);
        return !financialPlanData.creditAccounts().isEmpty() && allCreditAccountsClosed && allCurrentDebitExpensesCleared;
    }

    private void validateRequiredPaycheckDates(FinancialPlanData financialPlanData) {
        if (!hasRequiredPaycheckDates(financialPlanData)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Enter Paycheck Arrived Dates?"
            );
        }
    }

    private boolean hasRequiredPaycheckDates(FinancialPlanData financialPlanData) {
        if (financialPlanData == null || hasEncryptedPayload(financialPlanData)) {
            return true;
        }

        boolean defaultBankHasRequiredDates = incomeAmountFor(financialPlanData.incomeItems(), "bi-monthly-salary") <= 0
            || (isIsoLocalDate(financialPlanData.firstPaycheckDate()) && isIsoLocalDate(financialPlanData.secondPaycheckDate()));

        if (!defaultBankHasRequiredDates) {
            return false;
        }

        return financialPlanData.incomeSubsections().stream().allMatch(subsection ->
            subsection.biMonthlySalary() <= 0
                || (isIsoLocalDate(subsection.firstPaycheckDate()) && isIsoLocalDate(subsection.secondPaycheckDate()))
        );
    }

    private double incomeAmountFor(List<IncomeItem> incomeItems, String id) {
        if (incomeItems == null) {
            return 0;
        }

        return incomeItems.stream()
            .filter(item -> id.equals(item.id()))
            .mapToDouble(IncomeItem::amount)
            .findFirst()
            .orElse(0);
    }

    private boolean isIsoLocalDate(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        try {
            LocalDate.parse(value);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
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

    private record UserSettings(TimelineType timelineType, boolean premium) {
    }

    private record ResolvedClosedCycle(FinancialPlanData financialPlanData, CyclePeriod cyclePeriod) {
    }

    private record StoredCycle(
        FinancialPlanData financialPlanData,
        LocalDate cycleStartDate,
        LocalDate cycleEndDate,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    private record StoredArchivedCycle(
        FinancialPlanData financialPlanData,
        CyclePeriod cyclePeriod,
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
            normalizeOptionalDate(financialPlanData.firstPaycheckDate()),
            normalizeOptionalDate(financialPlanData.secondPaycheckDate()),
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
                normalizeOptionalDate(subsection.firstPaycheckDate()),
                subsection.midMonthSalaryArrived(),
                normalizeIncomeSubsectionMonthEndSalaryLabel(subsection.monthEndSalaryLabel()),
                normalizeOptionalDate(subsection.secondPaycheckDate()),
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

    private String normalizeOptionalDate(String value) {
        if (value == null) {
            return null;
        }

        String trimmedValue = value.trim();
        return trimmedValue.isEmpty() ? null : trimmedValue;
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
                || "Stmt Cycled?".equals(label)
                || "New Stmt Cycled?".equals(label)
                || "Current Cycle Stmt Cycled?".equals(label)
                || "Next Payment Stmt Cycled?".equals(label)
                || "Next Cycle Payment Stmt Cycled?".equals(label)
                || "Next Cycle Pymnt Stmt Cycled?".equals(label)
                || "Stmt for Next Cycle Pymnt Cycled?".equals(label))) {
            return "Stmt for Next Cycle Pymnt Cycled?";
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
                item.paid() != null ? item.paid() : Math.abs(item.current()) < 0.004,
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