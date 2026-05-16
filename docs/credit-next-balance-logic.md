# Credit Card: Next Balance & Latest Stmt Balance Display Logic

## Variables

| Variable | Source |
|---|---|
| `totalDueForCard` | `creditLimit - availableCredit` |
| `lastStatementBalance` | stored value (user-entered) |
| `nextPaymentDate` | stored on account |
| `lastStatementDate` | stored on account |
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

    else:
        // statement is before payment
        →  totalDueForCard

else:
    →  totalDueForCard - lastStatementBalance
```

> Note: `paymentDate == statementDate` cannot occur by design.

---

## Latest Stmt Balance Display Override

The **stored** `lastStatementBalance` value is never changed by this logic.
The UI now also displays the stored `lastStatementBalance` in all cases. There is no separate display override branch anymore.

---

## Close Cycle Carry-Forward

When a cycle is closed, the new cycle keeps the stored `lastStatementBalance` unchanged. The close-cycle carry-forward does not overwrite it with the computed **Next Balance**.

---

## Implementation Locations

| Location | File |
|---|---|
| `getCreditMetrics` | `FinancialPlanningUI/src/App.tsx` |
| `creditCardNextMonthBalance` reducer | `FinancialPlanningUI/src/App.tsx` |
| `calculateNextMonthBalance` | `FinancialPlanningApi/.../FinancialPlanCalculationService.java` |
| `startNewCycle` (carry-forward) | `FinancialPlanningApi/.../FinancialPlanCalculationService.java` |
