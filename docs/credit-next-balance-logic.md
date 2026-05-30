# Credit Card: Next Balance & Latest Stmt Balance Display Logic

All computation is **frontend-only**. The backend no longer calculates any summary or next-month-balance fields.

---

## Variables

| Variable | Source |
|---|---|
| `totalDueForCard` | `creditLimit - availableCredit` |
| `lastStatementBalance` | stored value (user-entered) |
| `nextPaymentDate` | stored on account |
| `lastStatementDate` | stored on account |
| `paidThisMonth` | boolean flag on account |
| `statementCycledAfterPayment` | boolean flag on account |
| `cycleStartDate` | start date of the active budget cycle |

---

## Derived Booleans

```
statementDateInCurrentCycle = lastStatementDate >= cycleStartDate
paymentDateBeforeStatementDate = nextPaymentDate < lastStatementDate
statementDateBeforePaymentDate = lastStatementDate < nextPaymentDate
```

---

## Next Balance Calculation (`getCreditMetrics`)

There are 14 branches organized into 3 top-level cases based on whether the statement date falls in the current cycle and the relative ordering of statement vs payment dates.

### Branch A: Statement date IS in the current cycle (`statementDateInCurrentCycle`)

#### A1: Payment date < Statement date

| `paidThisMonth` | `stmtCycledAfterPmt` | Result |
|---|---|---|
| `true` | `true` | `totalDueForCard - lastStatementBalance` |
| `true` | `false` | `totalDueForCard` |
| `false` | `true` | `totalDueForCard` (contradictory — keep numeric) |
| `false` | `false` | `totalDueForCard` |

#### A2: Statement date < Payment date

| `paidThisMonth` | Result | Rationale |
|---|---|---|
| `true` | `totalDueForCard` | Payment already reflected in `totalDueForCard` — don't subtract it again |
| `false` | `totalDueForCard - lastStatementBalance` | Payment still pending — subtract it to forecast post-payment balance |

> **Bug found (2026-05-30):** The original code unconditionally subtracted `lastStatementBalance` without checking `paidThisMonth`. When `paidThisMonth` is true, `totalDueForCard` already reflects the payment, so subtracting `lastStatementBalance` again produces a negative (incorrect) result. Example: Chase Slate with `totalDueForCard`=$905.79, `lastStatementBalance`=$1,047.33, `paidThisMonth`=true → $905.79 − $1,047.33 = −$141.54. The fix adds a `paidThisMonth` guard: if true → `totalDueForCard`, else → subtract.

#### A3: Statement date == Payment date

| `stmtCycledAfterPmt` | Result |
|---|---|
| `true` | `totalDueForCard` |
| `false` | `totalDueForCard - lastStatementBalance` |

### Branch B: Statement date is NOT in the current cycle, AND Statement date < Payment date

| `stmtCycledAfterPmt` | `paidThisMonth` | Result |
|---|---|---|
| `false` | `false` | `totalDueForCard - lastStatementBalance` |
| `false` | `true` | `totalDueForCard` |
| `true` | `true` | `totalDueForCard - lastStatementBalance` |
| `true` | `false` | `totalDueForCard - lastStatementBalance` |

### Branch C: Statement date is NOT in the current cycle, AND Statement date >= Payment date

| Result |
|---|
| `totalDueForCard` |

---

## Current Month Payment

```
currentMonthPayment = paidThisMonth ? 0 : lastStatementBalance
```

This is the amount the user needs to pay this month for the card.

---

## Displayed Last Statement Balance

The **stored** `lastStatementBalance` value is always displayed as-is. No override logic is applied.

---

## Close Cycle Carry-Forward

When a cycle is closed, the stored `lastStatementBalance` is preserved unchanged into the new cycle. There is no backend computation that would overwrite it.

---

## Implementation Locations

| Location | File |
|---|---|
| `getCreditMetrics` (sole computation) | `FinancialPlanningUI/src/App.tsx` (~line 1060) |
| `creditCardNextMonthBalance` reducer (aggregates across cards for KPI) | `FinancialPlanningUI/src/App.tsx` (~line 2941) |

### Removed (previous cleanup)

| Location | Status |
|---|---|
| `calculateNextMonthBalance` in `FinancialPlanCalculationService.java` | Removed — was dead code; frontend never read backend summary |
| `calculateSummary` / `withCalculatedSummary` | Removed / simplified to pass-through no-op |
