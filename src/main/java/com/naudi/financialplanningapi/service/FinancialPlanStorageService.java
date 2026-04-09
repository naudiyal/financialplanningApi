package com.naudi.financialplanningapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naudi.financialplanningapi.model.BalanceItem;
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
import com.naudi.financialplanningapi.model.FinancialPlanViewerUserSummary;
import com.naudi.financialplanningapi.model.IncomeSubsection;
import com.naudi.financialplanningapi.model.IncomeItem;
import com.naudi.financialplanningapi.model.RevertCloseCycleRequest;
import com.naudi.financialplanningapi.model.SwitchTimelineRequest;
import com.naudi.financialplanningapi.model.TimelineType;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FinancialPlanStorageService {

    private static final String PLAN_TABLE = "app_user_financial_plan_cycle";
    private static final String SETTINGS_TABLE = "app_user_financial_plan_settings";

    private static final String NEW_USER_TEMPLATE_RESOURCE = "new-user-financial-plan.json";
    private static final String SAMPLE_PLAN_MID_TO_MID_USER_SUB = "sample-mid-to-mid-mybetterbudget-com";
    private static final String SAMPLE_PLAN_START_TO_END_USER_SUB = "sample-start-to-end-mybetterbudget-com";
    private static final String SAMPLE_PLAN_EMAIL = "sample@mybetterbudget.com";
    private static final String SAMPLE_PLAN_DISPLAY_NAME = "Sample Plan";
    private static final String SAMPLE_SOURCE_EMAIL = "innaudiyal@gmail.com";
    private static final String TRACKERS_ALLOWED_EMAIL = "naudiyal@gmail.com";
    private static final String SAVINGS_NEXT_MONTH_ID = "savings-next-month";
    private static final String LEGACY_NEXT_MONTH_ID = "net-balance-next-month-end";
    private static final String SAVINGS_NEXT_MONTH_LABEL = "Savings Next Cycle";
    private static final String PREVIOUS_SAVINGS_NEXT_MONTH_LABEL = "Savings Next Month";
    private static final String LEGACY_NEXT_MONTH_LABEL = "Net Balance @Next Month End";
    private static final double CLOSE_CYCLE_CURRENT_EXPENSE_TOLERANCE = 0.004d;

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
        new ColumnLabel("pay-date", "Payment Date"),
        new ColumnLabel("paid", "Paid"),
        new ColumnLabel("statement-cycled", "Stmt Cycled"),
        new ColumnLabel("statement-date", "Stmt Date"),
        new ColumnLabel("statement-balance", "Stmt Balance"),
        new ColumnLabel("credit-limit", "Credit Limit"),
        new ColumnLabel("due", "Total Due"),
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
        "Credit Card Accounts",
        "Debit Card Expenses",
        "Bank Accounts",
        "Chase"
    );

    private final ObjectMapper objectMapper;
    private final JdbcClient jdbcClient;
    private final FinancialPlanCalculationService financialPlanCalculationService;

    public FinancialPlanStorageService(
        ObjectMapper objectMapper,
        JdbcClient jdbcClient,
        FinancialPlanCalculationService financialPlanCalculationService
    ) {
        this.objectMapper = objectMapper;
        this.jdbcClient = jdbcClient;
        this.financialPlanCalculationService = financialPlanCalculationService;
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
                canCloseCycle(currentData)
            );
        }

        return buildResponse(
            currentData,
            cycleSlot,
            timelineType,
            resolvedCurrentCycle,
            resolvedPreviousCycle,
            hasSavedPlan(currentCycle),
            canCloseCycle(currentData)
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
            return buildResponse(
                enrichedData,
                CycleSlot.CURRENT,
                timelineType,
                currentPeriod,
                previousCycle != null ? cyclePeriodFor(previousCycle, CycleSlot.PREVIOUS, timelineType) : null,
                true,
                canCloseCycle(enrichedData)
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save financial plan data", exception);
        }
    }

    public List<FinancialPlanViewerUserSummary> listViewerUsers(Authentication authentication) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);
        ensureTrackersAccess(authenticatedUser);

        return jdbcClient.sql("""
                                SELECT user_sub,
                                             MAX(NULLIF(email, '')) AS email,
                                             MAX(NULLIF(display_name, '')) AS display_name
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
                resultSet.getString("display_name")
            ))
            .list();
    }

    public FinancialPlanCycleResponse loadViewerPlan(Authentication authentication, String userSub, CycleSlot cycleSlot) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);
        ensureTrackersAccess(authenticatedUser);
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
                true
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
                true
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
            true
        );
    }

    public FinancialPlanCycleResponse closeCycle(Authentication authentication, CloseCycleRequest closeCycleRequest) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);

        if (closeCycleRequest == null || closeCycleRequest.financialPlanData() == null || closeCycleRequest.expectedCurrentCycle() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current cycle payload is required");
        }

        try {
            TimelineType timelineType = timelineTypeFor(authenticatedUser.userSub());
            StoredCycle currentCycle = loadStoredCycle(authenticatedUser, CycleSlot.CURRENT);
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
            upsertPlan(authenticatedUser, CycleSlot.PREVIOUS, currentPeriod, archivedCurrentData);

            CyclePeriod nextCurrentPeriod = nextCyclePeriod(currentPeriod);
            FinancialPlanData nextCurrentData = financialPlanCalculationService.startNewCycle(archivedCurrentData);
            upsertPlan(authenticatedUser, CycleSlot.CURRENT, nextCurrentPeriod, nextCurrentData);

            return buildResponse(
                nextCurrentData,
                CycleSlot.CURRENT,
                timelineType,
                nextCurrentPeriod,
                currentPeriod,
                true,
                canCloseCycle(nextCurrentData)
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

            return buildResponse(
                restoredCurrentData,
                CycleSlot.CURRENT,
                timelineType,
                previousPeriod,
                null,
                true,
                canCloseCycle(restoredCurrentData)
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to revert closed financial cycle", exception);
        }
    }

    public FinancialPlanData loadSample(Authentication authentication) {
        AuthenticatedUser authenticatedUser = authenticatedUser(authentication);
        TimelineType timelineType = timelineTypeFor(authenticatedUser.userSub());
        try {
            String planJson = jdbcClient.sql("""
                    SELECT plan_data::text
                                        FROM app_user_financial_plan_cycle
                    WHERE user_sub = :userSub
                      AND cycle_slot = 'CURRENT'
                    """)
                .param("userSub", sampleUserSubForTimeline(timelineType))
                .query(String.class)
                .optional()
                .orElse(null);

            if (planJson == null) {
                FinancialPlanData samplePlan = createSamplePlanFromSource(timelineType);
                return financialPlanCalculationService.withCalculatedSummary(normalizeIds(samplePlan));
            }

            FinancialPlanData storedData = objectMapper.readValue(planJson, FinancialPlanData.class);
            return financialPlanCalculationService.withCalculatedSummary(normalizeIds(storedData));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read sample financial plan data", exception);
        }
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

            return buildResponse(
                enrichedData,
                CycleSlot.CURRENT,
                targetTimelineType,
                targetCurrentCycle,
                null,
                true,
                canCloseCycle(enrichedData)
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to switch timeline", exception);
        }
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
        boolean canCloseCycle
    ) {
        return buildResponse(financialPlanData, selectedCycle, timelineType, currentCycle, previousCycle, hasSavedPlan, canCloseCycle, selectedCycle == CycleSlot.PREVIOUS);
    }

    private FinancialPlanCycleResponse buildResponse(
        FinancialPlanData financialPlanData,
        CycleSlot selectedCycle,
        TimelineType timelineType,
        CyclePeriod currentCycle,
        CyclePeriod previousCycle,
        boolean hasSavedPlan,
        boolean canCloseCycle,
        boolean readOnly
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
            lastCycleSavedAt(currentCycle, previousCycle)
        );
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
        return new CyclePeriod(cyclePeriod.endDate().plusDays(1), cyclePeriod.endDate().plusMonths(1));
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
            .orElse(TimelineType.MID_TO_MID);
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

    private void ensureTrackersAccess(AuthenticatedUser authenticatedUser) {
        if (authenticatedUser.email() == null || !TRACKERS_ALLOWED_EMAIL.equalsIgnoreCase(authenticatedUser.email())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Trackers access is restricted");
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

    private void initializeStorage() {
        try {
            readDefaultPlan();
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
                normalizeText(subsection.checkingBalanceLabel(), "Account Balance"),
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
            label = normalizeLegacyColumnLabel(id, label);
            normalized.add(new ColumnLabel(id, label));
        }
        return normalized;
    }

    private String normalizeLegacyColumnLabel(String id, String label) {
        if ("pay-date".equals(id) && "Pay Date".equals(label)) {
            return "Payment Date";
        }

        if ("credit-limit".equals(id) && "Limit".equals(label)) {
            return "Credit Limit";
        }

        return label;
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
        if (LEGACY_NEXT_MONTH_ID.equals(currentId)) {
            return SAVINGS_NEXT_MONTH_ID;
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

    private String normalizeText(String value, String defaultValue) {
        return value != null && !value.isBlank() ? value : defaultValue;
    }
}