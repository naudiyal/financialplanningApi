# Credit Card: Next Balance & Latest Stmt Balance Display Logic

## Variables

| Variable | Source |
|---|---|
| `totalDueForCard` | `creditLimit - availableCredit` |
| `lastStatementBalance` | stored value (user-entered) |
| `nextPaymentDate` | stored on account |
| `lastStatementDate` | stored on account |
| `cycleStartDate` | start date of the active cycle period |
| `paidThisMonth` | boolean flag on account |
| `statementCycledAfterPayment` | boolean flag on account |

---

## Next Balance Calculation

```
totalDueForCard = creditLimit - availableCredit

if paidThisMonth:
    if paymentDate < statementDate:
        // payment falls before statement in this cycle
        if statementCycledAfterPayment  →  totalDueForCard - lastStatementBalance
        else                            →  totalDueForCard

    else if statementDate < paymentDate AND statementDate >= cycleStartDate:
        // statement is within the current cycle but before payment
        →  totalDueForCard
        (also display Latest Stmt Balance as totalDueForCard on UI)

    else:
        // statementDate < paymentDate AND statementDate < cycleStartDate
        // statement is from a prior cycle — show stored lastStatementBalance
        →  totalDueForCard

else:
    →  totalDueForCard - lastStatementBalance
```

> Note: `paymentDate == statementDate` cannot occur by design.

---

## Latest Stmt Balance Display Override

The **stored** `lastStatementBalance` value is never changed by this logic.
Only the **displayed** value on the UI is overridden.

| Condition | Displayed Value |
|---|---|
| `paidThisMonth` AND `statementDate < paymentDate` AND `statementDate < cycleStartDate` | `totalDueForCard` |
| All other cases | `lastStatementBalance` (stored value) |

The override fires only when the statement date is both before the payment date **and** before the current cycle began — meaning the statement on record is from a prior cycle and has already been superseded by the current balance.

---

## Close Cycle Carry-Forward

When a cycle is closed, the new cycle's `lastStatementBalance` is seeded with the computed **Next Balance** (not the raw stored value). This ensures the displayed value at the start of the new cycle reflects what was actually owed.

---

## Implementation Locations

| Location | File |
|---|---|
| `getCreditMetrics` | `FinancialPlanningUI/src/App.tsx` |
| `creditCardNextMonthBalance` reducer | `FinancialPlanningUI/src/App.tsx` |
| `calculateNextMonthBalance` | `FinancialPlanningApi/.../FinancialPlanCalculationService.java` |
| `startNewCycle` (carry-forward) | `FinancialPlanningApi/.../FinancialPlanCalculationService.java` |
