# 03 — Rate Cards, Budgets & Profitability Reports

**Domain:** Billing Rates (3-level hierarchy), Cost Rates, Project Budgets, Profitability & Utilization Reports
**Primary Tester:** Marcus Webb (Admin)
**Supporting:** Thandi Nkosi (Owner), James Chen (Admin), Sofia Reyes (Member), Aiden O'Brien (Member), Lerato Dlamini (Member)
**Severity Guide:** See `00-overview.md`

---

## Test Data Setup

All test cases assume the following baseline data has been configured before execution.

### Members & Roles

| Member | Org Role |
|--------|----------|
| Thandi Nkosi | Owner |
| Marcus Webb | Admin |
| James Chen | Admin |
| Sofia Reyes | Member |
| Aiden O'Brien | Member |
| Lerato Dlamini | Member |

### Customers & Projects

| Customer | Status | Linked Projects |
|----------|--------|-----------------|
| Acme Corp | ACTIVE | Acme Website Redesign |
| Dunbar & Associates | ACTIVE | Dunbar Audit 2026 |

### Billing Rates

| Member | Scope | Customer/Project | Rate | Currency | Effective From | Effective To |
|--------|-------|------------------|------|----------|----------------|--------------|
| Sofia Reyes | MEMBER_DEFAULT | -- | R500/hr | ZAR | 2026-01-01 | null (open) |
| Sofia Reyes | PROJECT_OVERRIDE | Acme Website Redesign | R650/hr | ZAR | 2026-02-01 | null (open) |
| Aiden O'Brien | MEMBER_DEFAULT | -- | R400/hr | ZAR | 2026-01-01 | null (open) |
| Aiden O'Brien | CUSTOMER_OVERRIDE | Dunbar & Associates | R550/hr | ZAR | 2026-01-01 | null (open) |

### Cost Rates

| Member | Cost | Currency | Effective From | Effective To |
|--------|------|----------|----------------|--------------|
| Sofia Reyes | R300/hr | ZAR | 2026-01-01 | null (open) |
| Aiden O'Brien | R250/hr | ZAR | 2026-01-01 | null (open) |

### Budget Configuration

| Project | Budget Hours | Budget Amount | Currency | Alert Threshold |
|---------|-------------|---------------|----------|-----------------|
| Acme Website Redesign | 100.00 | R65,000.00 | ZAR | 80% |

---

## 1. Rate Hierarchy Resolution

### RATE-001: Resolve project-specific override (highest priority)

**Severity:** Critical
**Tester:** Marcus Webb

**Given:**
- Sofia Reyes has a MEMBER_DEFAULT rate of R500/hr (effective 2026-01-01)
- Sofia Reyes has a PROJECT_OVERRIDE rate of R650/hr for Acme Website Redesign (effective 2026-02-01)

**When:**
```
GET /api/billing-rates/resolve?memberId={sofia}&projectId={acmeProject}&date=2026-03-01
```

**Then:**
- Response status: 200
- `hourlyRate` = 650.00
- `currency` = "ZAR"
- `source` = "PROJECT_OVERRIDE"
- `billingRateId` = UUID of Sofia's Acme project rate

---

### RATE-002: Resolve customer-specific override (second priority)

**Severity:** Critical
**Tester:** Marcus Webb

**Given:**
- Aiden O'Brien has a MEMBER_DEFAULT rate of R400/hr
- Aiden O'Brien has a CUSTOMER_OVERRIDE rate of R550/hr for Dunbar & Associates
- Dunbar Audit 2026 is linked to Dunbar & Associates

**When:**
```
GET /api/billing-rates/resolve?memberId={aiden}&projectId={dunbarProject}&date=2026-03-01
```

**Then:**
- Response status: 200
- `hourlyRate` = 550.00
- `currency` = "ZAR"
- `source` = "CUSTOMER_OVERRIDE"
- `billingRateId` = UUID of Aiden's Dunbar customer rate

---

### RATE-003: Fall back to member default (lowest priority)

**Severity:** Critical
**Tester:** Marcus Webb

**Given:**
- Aiden O'Brien has a MEMBER_DEFAULT rate of R400/hr
- Aiden O'Brien has NO project override for Acme Website Redesign
- Acme Website Redesign is linked to Acme Corp (no customer override for Aiden+Acme)

**When:**
```
GET /api/billing-rates/resolve?memberId={aiden}&projectId={acmeProject}&date=2026-03-01
```

**Then:**
- Response status: 200
- `hourlyRate` = 400.00
- `currency` = "ZAR"
- `source` = "MEMBER_DEFAULT"

---

### RATE-004: Project override takes precedence over customer override

**Severity:** Critical
**Tester:** Marcus Webb

**Given:**
- Create a CUSTOMER_OVERRIDE for Sofia+Acme Corp at R600/hr (effective 2026-01-01)
- Sofia already has a PROJECT_OVERRIDE for Acme Website Redesign at R650/hr (effective 2026-02-01)
- Acme Website Redesign is linked to Acme Corp

**When:**
```
GET /api/billing-rates/resolve?memberId={sofia}&projectId={acmeProject}&date=2026-03-01
```

**Then:**
- `hourlyRate` = 650.00 (project override wins, not customer override of 600.00)
- `source` = "PROJECT_OVERRIDE"

**Cleanup:** Delete the customer override created for this test.

---

### RATE-005: Date-effective resolution — rate not yet effective

**Severity:** High
**Tester:** Marcus Webb

**Given:**
- Sofia's PROJECT_OVERRIDE for Acme is R650/hr, effective from 2026-02-01

**When:**
```
GET /api/billing-rates/resolve?memberId={sofia}&projectId={acmeProject}&date=2026-01-15
```

**Then:**
- The project override is NOT matched (effectiveFrom is 2026-02-01, query date is 2026-01-15)
- Falls through to MEMBER_DEFAULT
- `hourlyRate` = 500.00
- `source` = "MEMBER_DEFAULT"

---

### RATE-006: Date-effective resolution — rate with effectiveTo in the past

**Severity:** High
**Tester:** Marcus Webb

**Given:**
- Create a MEMBER_DEFAULT rate for Lerato: R350/hr, effectiveFrom=2026-01-01, effectiveTo=2026-01-31

**When:**
```
GET /api/billing-rates/resolve?memberId={lerato}&projectId={acmeProject}&date=2026-02-15
```

**Then:**
- Response status: 200
- `hourlyRate` = null
- `source` = null
- No rate resolves (the only rate expired on 2026-01-31)

**Cleanup:** Delete the rate created for this test.

---

### RATE-007: Date-effective resolution — query on effectiveFrom boundary

**Severity:** Medium
**Tester:** Marcus Webb

**Given:**
- Sofia's PROJECT_OVERRIDE for Acme is R650/hr, effectiveFrom=2026-02-01

**When:**
```
GET /api/billing-rates/resolve?memberId={sofia}&projectId={acmeProject}&date=2026-02-01
```

**Then:**
- `hourlyRate` = 650.00
- `source` = "PROJECT_OVERRIDE"
- The rate IS effective on its effectiveFrom date (inclusive boundary)

---

### RATE-008: Date-effective resolution — query on effectiveTo boundary

**Severity:** Medium
**Tester:** Marcus Webb

**Given:**
- Create a MEMBER_DEFAULT rate for Lerato: R350/hr, effectiveFrom=2026-01-01, effectiveTo=2026-03-31

**When:**
```
GET /api/billing-rates/resolve?memberId={lerato}&projectId={acmeProject}&date=2026-03-31
```

**Then:**
- `hourlyRate` = 350.00
- The rate IS effective on its effectiveTo date (inclusive boundary)

**Cleanup:** Delete the rate.

---

## 2. Rate CRUD & Validation

### RATE-009: Create a member default billing rate

**Severity:** High
**Tester:** Marcus Webb (Admin)

**Given:**
- Lerato Dlamini has no billing rates configured

**When:**
```
POST /api/billing-rates
{
  "memberId": "{lerato}",
  "currency": "ZAR",
  "hourlyRate": 350.00,
  "effectiveFrom": "2026-01-01",
  "effectiveTo": null
}
```

**Then:**
- Response status: 201
- `scope` = "MEMBER_DEFAULT"
- `projectId` = null
- `customerId` = null
- `hourlyRate` = 350.00
- `currency` = "ZAR"
- Audit event `billing_rate.created` is logged

**Cleanup:** Delete the rate after verification.

---

### RATE-010: Create a project-override billing rate

**Severity:** High
**Tester:** Marcus Webb

**When:**
```
POST /api/billing-rates
{
  "memberId": "{lerato}",
  "projectId": "{acmeProject}",
  "currency": "ZAR",
  "hourlyRate": 475.00,
  "effectiveFrom": "2026-02-01",
  "effectiveTo": null
}
```

**Then:**
- Response status: 201
- `scope` = "PROJECT_OVERRIDE"
- `projectId` = Acme project UUID
- `customerName` = null

**Cleanup:** Delete the rate.

---

### RATE-011: Create a customer-override billing rate

**Severity:** High
**Tester:** Marcus Webb

**When:**
```
POST /api/billing-rates
{
  "memberId": "{lerato}",
  "customerId": "{acmeCorp}",
  "currency": "ZAR",
  "hourlyRate": 425.00,
  "effectiveFrom": "2026-02-01",
  "effectiveTo": null
}
```

**Then:**
- Response status: 201
- `scope` = "CUSTOMER_OVERRIDE"
- `customerId` = Acme Corp UUID

**Cleanup:** Delete the rate.

---

### RATE-012: Reject rate with both projectId and customerId set

**Severity:** High
**Tester:** Marcus Webb

**When:**
```
POST /api/billing-rates
{
  "memberId": "{sofia}",
  "projectId": "{acmeProject}",
  "customerId": "{acmeCorp}",
  "currency": "ZAR",
  "hourlyRate": 700.00,
  "effectiveFrom": "2026-03-01"
}
```

**Then:**
- Response status: 400
- Error message contains: "cannot have both projectId and customerId"
- No rate is created

---

### RATE-013: Reject overlapping date ranges at the same scope

**Severity:** Critical
**Tester:** Marcus Webb

**Given:**
- Sofia already has a MEMBER_DEFAULT rate effective 2026-01-01 with no end date

**When:**
```
POST /api/billing-rates
{
  "memberId": "{sofia}",
  "currency": "ZAR",
  "hourlyRate": 550.00,
  "effectiveFrom": "2026-06-01",
  "effectiveTo": null
}
```

**Then:**
- Response status: 409
- Error: "Overlapping billing rate" / "A billing rate already exists for this scope and date range"
- The existing open-ended rate overlaps with the new one

---

### RATE-014: Allow adjacent non-overlapping date ranges

**Severity:** High
**Tester:** Marcus Webb

**Given:**
- Update Sofia's MEMBER_DEFAULT to effectiveTo=2026-05-31

**When:**
```
POST /api/billing-rates
{
  "memberId": "{sofia}",
  "currency": "ZAR",
  "hourlyRate": 550.00,
  "effectiveFrom": "2026-06-01",
  "effectiveTo": null
}
```

**Then:**
- Response status: 201
- Both rates coexist: R500/hr (Jan 1 - May 31) and R550/hr (Jun 1 - open)

**Cleanup:** Delete the new rate and restore Sofia's original rate to open-ended.

---

### RATE-015: Validation — missing required fields

**Severity:** Medium
**Tester:** Marcus Webb

**When:**
```
POST /api/billing-rates
{
  "currency": "ZAR",
  "hourlyRate": 500.00,
  "effectiveFrom": "2026-01-01"
}
```
(missing `memberId`)

**Then:**
- Response status: 400
- Validation error: "memberId is required"

---

### RATE-016: Validation — negative hourly rate

**Severity:** Medium
**Tester:** Marcus Webb

**When:**
```
POST /api/billing-rates
{
  "memberId": "{sofia}",
  "currency": "ZAR",
  "hourlyRate": -100.00,
  "effectiveFrom": "2026-01-01"
}
```

**Then:**
- Response status: 400
- Validation error: "hourlyRate must be positive"

---

### RATE-017: Validation — invalid currency code

**Severity:** Medium
**Tester:** Marcus Webb

**When:**
```
POST /api/billing-rates
{
  "memberId": "{sofia}",
  "currency": "RAND",
  "hourlyRate": 500.00,
  "effectiveFrom": "2026-01-01"
}
```

**Then:**
- Response status: 400
- Validation error: "currency must be exactly 3 characters"

---

### RATE-018: Update a billing rate

**Severity:** High
**Tester:** Marcus Webb

**Given:**
- Sofia's PROJECT_OVERRIDE for Acme is R650/hr

**When:**
```
PUT /api/billing-rates/{sofiaAcmeRateId}
{
  "currency": "ZAR",
  "hourlyRate": 700.00,
  "effectiveFrom": "2026-02-01",
  "effectiveTo": null
}
```

**Then:**
- Response status: 200
- `hourlyRate` = 700.00
- Audit event `billing_rate.updated` logged with from=650.00, to=700.00

**Cleanup:** Restore to R650/hr.

---

### RATE-019: Delete a billing rate

**Severity:** High
**Tester:** Marcus Webb

**Given:**
- Create a temporary MEMBER_DEFAULT rate for Lerato at R350/hr

**When:**
```
DELETE /api/billing-rates/{leratoRateId}
```

**Then:**
- Response status: 204
- Subsequent GET for Lerato returns no rates
- Audit event `billing_rate.deleted` logged

---

### RATE-020: List rates filtered by member

**Severity:** Medium
**Tester:** Marcus Webb

**When:**
```
GET /api/billing-rates?memberId={sofia}
```

**Then:**
- Response contains exactly 2 rates: MEMBER_DEFAULT (R500/hr) and PROJECT_OVERRIDE (R650/hr)
- Each response includes `memberName`, `scope`, and if applicable `projectName`

---

## 3. Rate Snapshot on Time Entry

### RATE-021: Billing rate snapshot captured at time-of-logging

**Severity:** Critical
**Tester:** Marcus Webb

**Given:**
- Sofia's resolved rate for Acme is R650/hr (PROJECT_OVERRIDE)
- Sofia's cost rate is R300/hr

**When:**
- Sofia logs 120 minutes (2 hours) of billable time on a task in Acme Website Redesign

**Then:**
- TimeEntry created with:
  - `billingRateSnapshot` = 650.00
  - `billingRateCurrency` = "ZAR"
  - `costRateSnapshot` = 300.00
  - `costRateCurrency` = "ZAR"
  - `billableValue` = 2.0 * 650.00 = R1,300.00
  - Cost value (computed) = 2.0 * 300.00 = R600.00

---

### RATE-022: Snapshot is immutable after rate update

**Severity:** Critical
**Tester:** Marcus Webb

**Given:**
- Sofia logged 2 hours on Acme at R650/hr (from RATE-021), snapshot = R650.00

**When:**
- Marcus updates Sofia's PROJECT_OVERRIDE rate from R650/hr to R750/hr

**Then:**
- The EXISTING time entry still has `billingRateSnapshot` = 650.00
- The existing `billableValue` remains R1,300.00
- A NEW time entry logged after the update would snapshot R750.00

**Cleanup:** Restore rate to R650/hr.

---

### RATE-023: Cost rate snapshot captured at time-of-logging

**Severity:** Critical
**Tester:** Marcus Webb

**Given:**
- Aiden's cost rate is R250/hr
- Aiden's billing rate for Dunbar project resolves to R550/hr (customer override)

**When:**
- Aiden logs 60 minutes (1 hour) of billable time on Dunbar Audit 2026

**Then:**
- `costRateSnapshot` = 250.00
- `costRateCurrency` = "ZAR"
- `billingRateSnapshot` = 550.00
- `billableValue` = 1.0 * 550.00 = R550.00
- Cost value = 1.0 * 250.00 = R250.00

---

### RATE-024: Non-billable time entry has null billing snapshot but captures cost snapshot

**Severity:** High
**Tester:** Marcus Webb

**Given:**
- Aiden's cost rate is R250/hr

**When:**
- Aiden logs 60 minutes of NON-billable time on Acme Website Redesign

**Then:**
- `billable` = false
- `billingRateSnapshot` = null (or not applied)
- `billableValue` = null
- `costRateSnapshot` = 250.00
- Cost value = 1.0 * 250.00 = R250.00

---

## 4. Cost Rate Management

### COST-001: Create a cost rate (Admin)

**Severity:** High
**Tester:** Marcus Webb (Admin)

**When:**
```
POST /api/cost-rates
{
  "memberId": "{lerato}",
  "currency": "ZAR",
  "hourlyCost": 200.00,
  "effectiveFrom": "2026-01-01",
  "effectiveTo": null
}
```

**Then:**
- Response status: 201
- `hourlyCost` = 200.00
- `memberName` populated in response

**Cleanup:** Delete the cost rate.

---

### COST-002: Create a cost rate (Owner)

**Severity:** High
**Tester:** Thandi Nkosi (Owner)

**When:**
```
POST /api/cost-rates
{
  "memberId": "{lerato}",
  "currency": "ZAR",
  "hourlyCost": 200.00,
  "effectiveFrom": "2026-01-01"
}
```

**Then:**
- Response status: 201

**Cleanup:** Delete the cost rate.

---

### COST-003: Member cannot access cost rates — list

**Severity:** Critical
**Tester:** Lerato Dlamini (Member)

**When:**
```
GET /api/cost-rates
```

**Then:**
- Response status: 403
- Cost rate data is not visible to Members

---

### COST-004: Member cannot access cost rates — create

**Severity:** Critical
**Tester:** Sofia Reyes (Member)

**When:**
```
POST /api/cost-rates
{
  "memberId": "{sofia}",
  "currency": "ZAR",
  "hourlyCost": 300.00,
  "effectiveFrom": "2026-01-01"
}
```

**Then:**
- Response status: 403

---

### COST-005: Member cannot access cost rates — update

**Severity:** Critical
**Tester:** Sofia Reyes (Member)

**When:**
```
PUT /api/cost-rates/{sofiaCostRateId}
{
  "currency": "ZAR",
  "hourlyCost": 350.00,
  "effectiveFrom": "2026-01-01"
}
```

**Then:**
- Response status: 403

---

### COST-006: Member cannot access cost rates — delete

**Severity:** Critical
**Tester:** Lerato Dlamini (Member)

**When:**
```
DELETE /api/cost-rates/{sofiaCostRateId}
```

**Then:**
- Response status: 403

---

### COST-007: Update a cost rate

**Severity:** High
**Tester:** Marcus Webb (Admin)

**Given:**
- Sofia's cost rate is R300/hr

**When:**
```
PUT /api/cost-rates/{sofiaCostRateId}
{
  "currency": "ZAR",
  "hourlyCost": 325.00,
  "effectiveFrom": "2026-01-01",
  "effectiveTo": null
}
```

**Then:**
- Response status: 200
- `hourlyCost` = 325.00

**Cleanup:** Restore to R300/hr.

---

### COST-008: Delete a cost rate

**Severity:** High
**Tester:** Thandi Nkosi (Owner)

**Given:**
- Create a temporary cost rate for Lerato at R200/hr

**When:**
```
DELETE /api/cost-rates/{leratoCostRateId}
```

**Then:**
- Response status: 204

---

### COST-009: List cost rates filtered by member

**Severity:** Medium
**Tester:** Marcus Webb (Admin)

**When:**
```
GET /api/cost-rates?memberId={sofia}
```

**Then:**
- Response contains exactly 1 cost rate
- `hourlyCost` = 300.00, `memberName` = "Sofia Reyes"

---

### COST-010: Reject overlapping cost rate date ranges

**Severity:** High
**Tester:** Marcus Webb

**Given:**
- Sofia already has a cost rate effective 2026-01-01 with no end date

**When:**
```
POST /api/cost-rates
{
  "memberId": "{sofia}",
  "currency": "ZAR",
  "hourlyCost": 350.00,
  "effectiveFrom": "2026-06-01"
}
```

**Then:**
- Response status: 409
- Overlapping date range rejected

---

## 5. Budget Configuration

### BUD-001: Set a project budget (hours and amount)

**Severity:** High
**Tester:** Marcus Webb (Admin)

**Given:**
- Acme Website Redesign has no budget configured yet (or start from clean state)

**When:**
```
PUT /api/projects/{acmeProject}/budget
{
  "budgetHours": 100.00,
  "budgetAmount": 65000.00,
  "budgetCurrency": "ZAR",
  "alertThresholdPct": 80,
  "notes": "Phase 1 budget cap"
}
```

**Then:**
- Response status: 200
- `budgetHours` = 100.00
- `budgetAmount` = 65000.00
- `budgetCurrency` = "ZAR"
- `alertThresholdPct` = 80
- `notes` = "Phase 1 budget cap"

---

### BUD-002: Update an existing budget

**Severity:** High
**Tester:** Marcus Webb

**Given:**
- Acme budget is 100 hours / R65,000

**When:**
```
PUT /api/projects/{acmeProject}/budget
{
  "budgetHours": 120.00,
  "budgetAmount": 78000.00,
  "budgetCurrency": "ZAR",
  "alertThresholdPct": 75,
  "notes": "Expanded scope - Phase 1+2"
}
```

**Then:**
- Response status: 200
- `budgetHours` = 120.00
- `budgetAmount` = 78000.00
- `alertThresholdPct` = 75
- `thresholdNotified` resets to false (budget values changed)

**Cleanup:** Restore to 100hrs / R65,000 / 80%.

---

### BUD-003: Delete a project budget

**Severity:** High
**Tester:** Marcus Webb

**Given:**
- Dunbar Audit 2026 has a budget configured

**When:**
```
DELETE /api/projects/{dunbarProject}/budget
```

**Then:**
- Response status: 204
- Subsequent GET returns 404 or empty budget

---

### BUD-004: Validation — alertThresholdPct below minimum (50)

**Severity:** Medium
**Tester:** Marcus Webb

**When:**
```
PUT /api/projects/{acmeProject}/budget
{
  "budgetHours": 100.00,
  "alertThresholdPct": 30
}
```

**Then:**
- Response status: 400
- Validation error: "alertThresholdPct must be at least 50"

---

### BUD-005: Validation — alertThresholdPct above maximum (100)

**Severity:** Medium
**Tester:** Marcus Webb

**When:**
```
PUT /api/projects/{acmeProject}/budget
{
  "budgetHours": 100.00,
  "alertThresholdPct": 110
}
```

**Then:**
- Response status: 400
- Validation error: "alertThresholdPct must be at most 100"

---

### BUD-006: Validation — negative budget hours

**Severity:** Medium
**Tester:** Marcus Webb

**When:**
```
PUT /api/projects/{acmeProject}/budget
{
  "budgetHours": -10.00,
  "budgetCurrency": "ZAR",
  "alertThresholdPct": 80
}
```

**Then:**
- Response status: 400
- Validation error: "budgetHours must be positive"

---

### BUD-007: Budget with hours only (no amount)

**Severity:** Medium
**Tester:** Marcus Webb

**When:**
```
PUT /api/projects/{dunbarProject}/budget
{
  "budgetHours": 50.00,
  "alertThresholdPct": 80,
  "notes": "Hours-only tracking"
}
```

**Then:**
- Response status: 200
- `budgetHours` = 50.00
- `budgetAmount` = null
- `amountConsumedPct` = null
- `amountStatus` = null

**Cleanup:** Delete the budget.

---

## 6. Budget Status Calculations

### BUD-008: ON_TRACK status — below alert threshold

**Severity:** Critical
**Tester:** Marcus Webb

**Given:**
- Acme budget: 100 hours / R65,000 / 80% alert threshold
- Sofia logs 2 hours billable at R650/hr on Acme = R1,300
- Aiden logs 1 hour non-billable on Acme (no billing value, but counts for hours)
- Total hours consumed: 3.00
- Total amount consumed (billable value in ZAR): R1,300.00

**When:**
```
GET /api/projects/{acmeProject}/budget
```

**Then:**
- `hoursConsumed` = 3.00
- `hoursRemaining` = 100.00 - 3.00 = 97.00
- `hoursConsumedPct` = (3.00 / 100.00) * 100 = 3.00%
- `amountConsumed` = 1300.00
- `amountRemaining` = 65000.00 - 1300.00 = 63700.00
- `amountConsumedPct` = (1300.00 / 65000.00) * 100 = 2.00%
- `hoursStatus` = "ON_TRACK" (3.00% < 80%)
- `amountStatus` = "ON_TRACK" (2.00% < 80%)
- `overallStatus` = "ON_TRACK"

---

### BUD-009: AT_RISK status — at or above threshold, below 100%

**Severity:** Critical
**Tester:** Marcus Webb

**Given:**
- Acme budget: 100 hours / R65,000 / 80% alert threshold
- Total hours consumed: 82.00 (above 80% threshold)
- Amount consumed: R52,000.00 (exactly 80% of R65,000)

**Calculation:**
- `hoursConsumedPct` = (82.00 / 100.00) * 100 = 82.00%
- `amountConsumedPct` = (52000.00 / 65000.00) * 100 = 80.00%

**When:**
```
GET /api/projects/{acmeProject}/budget
```

**Then:**
- `hoursStatus` = "AT_RISK" (82.00% >= 80% but < 100%)
- `amountStatus` = "AT_RISK" (80.00% >= 80% but < 100%)
- `overallStatus` = "AT_RISK"

---

### BUD-010: OVER_BUDGET status — at or above 100%

**Severity:** Critical
**Tester:** Marcus Webb

**Given:**
- Acme budget: 100 hours / R65,000 / 80% alert threshold
- Total hours consumed: 105.00
- Amount consumed: R68,250.00

**Calculation:**
- `hoursConsumedPct` = (105.00 / 100.00) * 100 = 105.00%
- `amountConsumedPct` = (68250.00 / 65000.00) * 100 = 105.00%

**When:**
```
GET /api/projects/{acmeProject}/budget
```

**Then:**
- `hoursConsumed` = 105.00
- `hoursRemaining` = 100.00 - 105.00 = -5.00 (negative = overage)
- `hoursConsumedPct` = 105.00%
- `amountConsumed` = 68250.00
- `amountRemaining` = 65000.00 - 68250.00 = -3250.00 (negative)
- `amountConsumedPct` = 105.00%
- `hoursStatus` = "OVER_BUDGET"
- `amountStatus` = "OVER_BUDGET"
- `overallStatus` = "OVER_BUDGET"

---

### BUD-011: Mixed status — hours AT_RISK, amount ON_TRACK; overall = AT_RISK

**Severity:** High
**Tester:** Marcus Webb

**Given:**
- Acme budget: 100 hours / R65,000 / 80% alert threshold
- Total hours consumed: 85.00
- Amount consumed: R40,000.00

**Calculation:**
- `hoursConsumedPct` = 85.00% (>= 80% threshold)
- `amountConsumedPct` = (40000.00 / 65000.00) * 100 = 61.54% (< 80% threshold)

**When:**
```
GET /api/projects/{acmeProject}/budget
```

**Then:**
- `hoursStatus` = "AT_RISK"
- `amountStatus` = "ON_TRACK"
- `overallStatus` = "AT_RISK" (worst of the two)

---

### BUD-012: Mixed status — hours ON_TRACK, amount OVER_BUDGET; overall = OVER_BUDGET

**Severity:** High
**Tester:** Marcus Webb

**Given:**
- Acme budget: 100 hours / R65,000 / 80% alert threshold
- Total hours consumed: 70.00
- Amount consumed: R66,000.00

**Calculation:**
- `hoursConsumedPct` = 70.00% (< 80%)
- `amountConsumedPct` = (66000.00 / 65000.00) * 100 = 101.54% (>= 100%)

**When:**
```
GET /api/projects/{acmeProject}/budget
```

**Then:**
- `hoursStatus` = "ON_TRACK"
- `amountStatus` = "OVER_BUDGET"
- `overallStatus` = "OVER_BUDGET" (worst of the two)

---

### BUD-013: Lightweight status endpoint returns only status fields

**Severity:** Medium
**Tester:** Marcus Webb

**When:**
```
GET /api/projects/{acmeProject}/budget/status
```

**Then:**
- Response contains: `hoursConsumedPct`, `amountConsumedPct`, `hoursStatus`, `amountStatus`, `overallStatus`
- Response does NOT contain: `budgetHours`, `budgetAmount`, `notes`, `hoursConsumed`, `hoursRemaining`

---

### BUD-014: Threshold notification resets when budget values change

**Severity:** High
**Tester:** Marcus Webb

**Given:**
- Acme budget threshold was previously triggered (thresholdNotified = true)

**When:**
- Update budget from 100 hours to 150 hours

**Then:**
- `thresholdNotified` resets to false
- The alert can fire again when the new threshold is breached

---

## 7. Profitability Calculations

### PROF-001: Project profitability with exact math

**Severity:** Critical
**Tester:** Marcus Webb

**Given:**
- Acme Website Redesign time entries:

| Member | Duration | Billable | Billing Rate Snapshot | Cost Rate Snapshot | Billable Value | Cost Value |
|--------|----------|----------|-----------------------|-------------------|----------------|------------|
| Sofia | 120 min (2h) | Yes | R650/hr | R300/hr | 2 * 650 = R1,300.00 | 2 * 300 = R600.00 |
| Sofia | 60 min (1h) | Yes | R650/hr | R300/hr | 1 * 650 = R650.00 | 1 * 300 = R300.00 |
| Aiden | 180 min (3h) | Yes | R400/hr | R250/hr | 3 * 400 = R1,200.00 | 3 * 250 = R750.00 |
| Aiden | 60 min (1h) | No | null | R250/hr | null | 1 * 250 = R250.00 |

**Totals (ZAR):**
- Billable hours: 2 + 1 + 3 = 6.00
- Non-billable hours: 1.00
- Total hours: 7.00
- Revenue (billableValue): R1,300 + R650 + R1,200 = R3,150.00
- Cost (costValue): R600 + R300 + R750 + R250 = R1,900.00
- Margin: R3,150.00 - R1,900.00 = R1,250.00
- Margin %: (R1,250.00 / R3,150.00) * 100 = 39.68%

**When:**
```
GET /api/projects/{acmeProject}/profitability
```

**Then:**
- Response status: 200
- `projectName` = "Acme Website Redesign"
- `currencies` array contains 1 entry for "ZAR":
  - `totalBillableHours` = 6.00
  - `totalNonBillableHours` = 1.00
  - `totalHours` = 7.00
  - `billableValue` = 3150.00
  - `costValue` = 1900.00
  - `margin` = 1250.00
  - `marginPercent` = 39.68

---

### PROF-002: Project profitability with date range filter

**Severity:** High
**Tester:** Marcus Webb

**Given:**
- Time entries from PROF-001, but Sofia's first 2-hour entry was logged on 2026-02-15
- Sofia's second 1-hour entry was logged on 2026-03-01
- Aiden's entries were all logged on 2026-03-05

**When:**
```
GET /api/projects/{acmeProject}/profitability?from=2026-03-01&to=2026-03-31
```

**Then:**
- Only entries from March are included:
  - Sofia: 1h billable, R650 revenue, R300 cost
  - Aiden: 3h billable + 1h non-billable, R1,200 revenue, R1,000 cost
- `billableValue` = 650 + 1200 = R1,850.00
- `costValue` = 300 + 750 + 250 = R1,300.00
- `margin` = R1,850.00 - R1,300.00 = R550.00
- `marginPercent` = (550.00 / 1850.00) * 100 = 29.73%

---

### PROF-003: Project profitability — no cost rates configured (margin is null)

**Severity:** High
**Tester:** Marcus Webb

**Given:**
- Lerato has a billing rate but NO cost rate
- Lerato logs 2 hours billable at R350/hr on Dunbar Audit 2026
- `billingRateSnapshot` = 350.00, `costRateSnapshot` = null

**When:**
```
GET /api/projects/{dunbarProject}/profitability
```

**Then:**
- `billableValue` = 2 * 350 = R700.00
- `costValue` = null (no cost snapshot)
- `margin` = null (cannot compute without cost)
- `marginPercent` = null

---

## 8. Customer Profitability

### PROF-004: Customer profitability aggregates across all linked projects

**Severity:** Critical
**Tester:** Thandi Nkosi (Owner) or Marcus Webb (Admin)

**Given:**
- Acme Corp has 2 linked projects: Acme Website Redesign and Acme Mobile App
- Acme Website Redesign time (ZAR):
  - Revenue: R3,150.00, Cost: R1,900.00
- Acme Mobile App time (ZAR):
  - Sofia: 4h billable at R650/hr = R2,600.00 revenue, 4h at R300/hr = R1,200.00 cost

**Totals (ZAR):**
- Total billable hours: 6 + 4 = 10.00
- Revenue: R3,150 + R2,600 = R5,750.00
- Cost: R1,900 + R1,200 = R3,100.00
- Margin: R5,750 - R3,100 = R2,650.00
- Margin %: (2650.00 / 5750.00) * 100 = 46.09%

**When:**
```
GET /api/customers/{acmeCorp}/profitability
```

**Then:**
- `customerName` = "Acme Corp"
- ZAR currency breakdown:
  - `billableValue` = 5750.00
  - `costValue` = 3100.00
  - `margin` = 2650.00
  - `marginPercent` = 46.09

---

### PROF-005: Customer profitability — Admin/Owner access only

**Severity:** Critical
**Tester:** Sofia Reyes (Member)

**When:**
```
GET /api/customers/{acmeCorp}/profitability
```

**Then:**
- Response status: 403
- Customer profitability requires Admin or Owner role

---

### PROF-006: Customer profitability with date range filter

**Severity:** High
**Tester:** Marcus Webb

**When:**
```
GET /api/customers/{acmeCorp}/profitability?from=2026-03-01&to=2026-03-31
```

**Then:**
- Only time entries within March 2026 are aggregated
- Numbers are a subset of the unfiltered response

---

## 9. Org Profitability

### PROF-007: Org profitability — all projects across all customers

**Severity:** Critical
**Tester:** Thandi Nkosi (Owner)

**Given:**
- Acme Website Redesign: Revenue R3,150.00, Cost R1,900.00, Margin R1,250.00
- Dunbar Audit 2026: Revenue R700.00, Cost null (Lerato has no cost rate)

**When:**
```
GET /api/reports/profitability
```

**Then:**
- Response status: 200
- `projects` array contains entries per project-currency combination
- Entry for Acme Website Redesign (ZAR):
  - `billableValue` = 3150.00
  - `costValue` = 1900.00
  - `margin` = 1250.00
  - `marginPercent` = 39.68
  - `customerName` = "Acme Corp"
- Entry for Dunbar Audit 2026 (ZAR):
  - `billableValue` = 700.00
  - `costValue` = null (or 0 depending on whether Lerato's entries have cost)
  - `margin` = null
- Results sorted by margin DESC (nulls last)

---

### PROF-008: Org profitability — Admin/Owner access only

**Severity:** Critical
**Tester:** Lerato Dlamini (Member)

**When:**
```
GET /api/reports/profitability
```

**Then:**
- Response status: 403

---

### PROF-009: Org profitability — filter by customer

**Severity:** High
**Tester:** Thandi Nkosi (Owner)

**When:**
```
GET /api/reports/profitability?customerId={acmeCorp}
```

**Then:**
- `projects` only contains entries for projects linked to Acme Corp
- Dunbar Audit 2026 is NOT included

---

### PROF-010: Org profitability with date range filter

**Severity:** High
**Tester:** Marcus Webb (Admin)

**When:**
```
GET /api/reports/profitability?from=2026-03-01&to=2026-03-31
```

**Then:**
- Only time entries within March are aggregated
- Per-project subtotals reflect the filtered window

---

## 10. Utilization Report

### UTIL-001: Utilization for a single member

**Severity:** Critical
**Tester:** Marcus Webb (Admin)

**Given:**
- Sofia's time entries in date range 2026-03-01 to 2026-03-31:
  - 3 hours billable (various tasks)
  - 0 hours non-billable
- Aiden's time entries in same range:
  - 3 hours billable
  - 1 hour non-billable

**When:**
```
GET /api/reports/utilization?from=2026-03-01&to=2026-03-31&memberId={sofia}
```

**Then:**
- Response contains exactly 1 member record for Sofia:
  - `totalHours` = 3.00
  - `billableHours` = 3.00
  - `nonBillableHours` = 0.00
  - `utilizationPercent` = (3.00 / 3.00) * 100 = 100.00%
  - `currencies` array: ZAR entry with `billableValue` and `costValue`

---

### UTIL-002: Utilization for all members (Admin/Owner)

**Severity:** Critical
**Tester:** Marcus Webb (Admin)

**When:**
```
GET /api/reports/utilization?from=2026-03-01&to=2026-03-31
```

**Then:**
- Response includes all members who logged time in the date range
- Sofia: `utilizationPercent` = 100.00% (3h billable / 3h total)
- Aiden: `utilizationPercent` = (3.00 / 4.00) * 100 = 75.00% (3h billable / 4h total)
- Each member has per-currency `billableValue` and `costValue` breakdowns

---

### UTIL-003: Member can only view own utilization

**Severity:** Critical
**Tester:** Sofia Reyes (Member)

**When:**
```
GET /api/reports/utilization?from=2026-03-01&to=2026-03-31&memberId={aiden}
```

**Then:**
- Response status: 403
- Members cannot view other members' utilization

---

### UTIL-004: Member can view self utilization

**Severity:** High
**Tester:** Sofia Reyes (Member)

**When:**
```
GET /api/reports/utilization?from=2026-03-01&to=2026-03-31&memberId={sofia}
```

**Then:**
- Response status: 200
- Contains only Sofia's utilization record

---

### UTIL-005: Member with no memberId param is auto-restricted to self

**Severity:** High
**Tester:** Sofia Reyes (Member)

**When:**
```
GET /api/reports/utilization?from=2026-03-01&to=2026-03-31
```

**Then:**
- Response status: 200
- Contains ONLY Sofia's own utilization record (auto-restricted)
- Does NOT return all members' data

---

### UTIL-006: Utilization with zero total hours

**Severity:** Medium
**Tester:** Marcus Webb (Admin)

**Given:**
- Lerato has no time entries in the queried date range

**When:**
```
GET /api/reports/utilization?from=2026-03-01&to=2026-03-31&memberId={lerato}
```

**Then:**
- Either Lerato is not included in the response (no time logged), OR:
- `totalHours` = 0.00, `utilizationPercent` = 0.00 (division by zero handled)

---

### UTIL-007: Utilization percentage math — partial billable

**Severity:** High
**Tester:** Marcus Webb

**Given:**
- Aiden logged in March:
  - 180 min (3h) billable on Dunbar
  - 60 min (1h) non-billable on Acme

**When:**
```
GET /api/reports/utilization?from=2026-03-01&to=2026-03-31&memberId={aiden}
```

**Then:**
- `totalHours` = 4.00
- `billableHours` = 3.00
- `nonBillableHours` = 1.00
- `utilizationPercent` = (3.00 * 100) / 4.00 = 75.00%
- ZAR currency breakdown:
  - `billableValue` = 3 * 550 = R1,650.00 (Dunbar customer override rate)
  - `costValue` = 4 * 250 = R1,000.00 (all hours incur cost)

---

## 11. Edge Cases

### EDGE-001: No rates configured — resolve returns null

**Severity:** High
**Tester:** Marcus Webb

**Given:**
- David Molefe has NO billing rates configured at any level

**When:**
```
GET /api/billing-rates/resolve?memberId={david}&projectId={acmeProject}&date=2026-03-01
```

**Then:**
- Response status: 200
- `hourlyRate` = null
- `currency` = null
- `source` = null
- `billingRateId` = null

---

### EDGE-002: Rate gap — no effective rate for a specific date

**Severity:** High
**Tester:** Marcus Webb

**Given:**
- Create two rates for Lerato:
  - MEMBER_DEFAULT R350/hr, effectiveFrom=2026-01-01, effectiveTo=2026-02-28
  - MEMBER_DEFAULT R400/hr, effectiveFrom=2026-04-01, effectiveTo=null
- (Gap: 2026-03-01 to 2026-03-31 has no rate)

**When:**
```
GET /api/billing-rates/resolve?memberId={lerato}&projectId={acmeProject}&date=2026-03-15
```

**Then:**
- `hourlyRate` = null (no rate effective during the gap)

**Cleanup:** Delete both rates.

---

### EDGE-003: Zero-cost member — profitability shows full margin

**Severity:** Medium
**Tester:** Marcus Webb

**Given:**
- Lerato has a billing rate of R350/hr but NO cost rate
- Lerato logs 2h billable on Dunbar (billingRateSnapshot=350, costRateSnapshot=null)

**When:**
```
GET /api/projects/{dunbarProject}/profitability
```

**Then:**
- `billableValue` = R700.00
- `costValue` = null
- `margin` = null (per ADR-043: margin is null when cost is unknown, not "full margin")
- `marginPercent` = null

---

### EDGE-004: Budget with only hours, no amount

**Severity:** Medium
**Tester:** Marcus Webb

**Given:**
```
PUT /api/projects/{dunbarProject}/budget
{
  "budgetHours": 50.00,
  "alertThresholdPct": 80
}
```

**When:**
- Log 42 hours total on Dunbar
```
GET /api/projects/{dunbarProject}/budget
```

**Then:**
- `hoursConsumed` = 42.00
- `hoursConsumedPct` = (42.00 / 50.00) * 100 = 84.00%
- `hoursStatus` = "AT_RISK" (84% >= 80%)
- `budgetAmount` = null
- `amountConsumed` = (value still reported but no percentage)
- `amountConsumedPct` = null (no budget amount to divide by)
- `amountStatus` = null
- `overallStatus` = "AT_RISK" (only hours status applies; null amount status is ignored)

**Cleanup:** Delete the budget.

---

### EDGE-005: Member with no time entries — profitability is zero

**Severity:** Low
**Tester:** Marcus Webb

**Given:**
- Lerato has rates configured but has NOT logged any time on Acme Website Redesign

**When:**
```
GET /api/projects/{acmeProject}/profitability
```

**Then:**
- Lerato does not appear in the profitability breakdown
- Only members who logged time contribute to the aggregation

---

### EDGE-006: Non-existent project — 404

**Severity:** Medium
**Tester:** Marcus Webb

**When:**
```
GET /api/projects/{randomUUID}/profitability
```

**Then:**
- Response status: 404

---

### EDGE-007: Non-existent customer — 404

**Severity:** Medium
**Tester:** Marcus Webb

**When:**
```
GET /api/customers/{randomUUID}/profitability
```

**Then:**
- Response status: 404

---

### EDGE-008: Billing rate for non-existent project — 404

**Severity:** Medium
**Tester:** Marcus Webb

**When:**
```
POST /api/billing-rates
{
  "memberId": "{sofia}",
  "projectId": "{randomUUID}",
  "currency": "ZAR",
  "hourlyRate": 500.00,
  "effectiveFrom": "2026-01-01"
}
```

**Then:**
- Response status: 404
- "Project" not found

---

### EDGE-009: Billing rate for non-existent customer — 404

**Severity:** Medium
**Tester:** Marcus Webb

**When:**
```
POST /api/billing-rates
{
  "memberId": "{sofia}",
  "customerId": "{randomUUID}",
  "currency": "ZAR",
  "hourlyRate": 500.00,
  "effectiveFrom": "2026-01-01"
}
```

**Then:**
- Response status: 404
- "Customer" not found

---

### EDGE-010: Budget percentage rounding — precision check

**Severity:** High
**Tester:** Marcus Webb

**Given:**
- Budget: 30 hours, 80% threshold
- Hours consumed: 7.00

**Calculation:**
- `hoursConsumedPct` = (7.00 / 30.00) * 100 = 23.3333...
- Rounded to 2 decimal places: 23.33%

**When:**
```
GET /api/projects/{project}/budget
```

**Then:**
- `hoursConsumedPct` = 23.33 (2 decimal places, HALF_UP rounding)
- `hoursStatus` = "ON_TRACK"

---

### EDGE-011: Profitability margin percentage rounding — precision check

**Severity:** High
**Tester:** Marcus Webb

**Given:**
- Project revenue = R3,150.00
- Project cost = R1,900.00
- Margin = R1,250.00

**Calculation:**
- marginPercent = (1250.00 / 3150.00) * 100 = 39.6825...
- Rounded to 2 decimal places: 39.68% (HALF_UP)

**Then:**
- `marginPercent` = 39.68

---

### EDGE-012: Member RBAC — regular member cannot create billing rates

**Severity:** Critical
**Tester:** Lerato Dlamini (Member)

**When:**
```
POST /api/billing-rates
{
  "memberId": "{lerato}",
  "currency": "ZAR",
  "hourlyRate": 500.00,
  "effectiveFrom": "2026-01-01"
}
```

**Then:**
- Response status: 403
- "Only admins, owners, or project leads can manage billing rates"

---

### EDGE-013: Budget exactly at threshold boundary

**Severity:** High
**Tester:** Marcus Webb

**Given:**
- Budget: 100 hours, alert threshold 80%
- Hours consumed: exactly 80.00

**Calculation:**
- `hoursConsumedPct` = (80.00 / 100.00) * 100 = 80.00%

**When:**
```
GET /api/projects/{project}/budget
```

**Then:**
- `hoursStatus` = "AT_RISK" (80.00% >= 80% threshold; AT_RISK is inclusive of the threshold)

---

### EDGE-014: Budget exactly at 100%

**Severity:** High
**Tester:** Marcus Webb

**Given:**
- Budget: 100 hours
- Hours consumed: exactly 100.00

**Calculation:**
- `hoursConsumedPct` = 100.00%

**When:**
```
GET /api/projects/{project}/budget
```

**Then:**
- `hoursStatus` = "OVER_BUDGET" (100.00% >= 100%; OVER_BUDGET is inclusive of 100%)

---

### EDGE-015: Utilization with fractional minutes

**Severity:** Medium
**Tester:** Marcus Webb

**Given:**
- Sofia logs 45 minutes billable and 15 minutes non-billable

**Calculation:**
- Billable hours: 45 / 60 = 0.75
- Non-billable hours: 15 / 60 = 0.25
- Total hours: 1.00
- Utilization: (0.75 / 1.00) * 100 = 75.00%

**Then:**
- `billableHours` = 0.75
- `nonBillableHours` = 0.25
- `utilizationPercent` = 75.00

---

## Test Case Summary

| Section | ID Range | Count | Critical | High | Medium | Low |
|---------|----------|-------|----------|------|--------|-----|
| 1. Rate Hierarchy Resolution | RATE-001 to RATE-008 | 8 | 4 | 2 | 2 | 0 |
| 2. Rate CRUD & Validation | RATE-009 to RATE-020 | 12 | 1 | 6 | 5 | 0 |
| 3. Rate Snapshot on Time Entry | RATE-021 to RATE-024 | 4 | 3 | 1 | 0 | 0 |
| 4. Cost Rate Management | COST-001 to COST-010 | 10 | 4 | 4 | 2 | 0 |
| 5. Budget Configuration | BUD-001 to BUD-007 | 7 | 0 | 3 | 4 | 0 |
| 6. Budget Status Calculations | BUD-008 to BUD-014 | 7 | 3 | 3 | 1 | 0 |
| 7. Profitability Calculations | PROF-001 to PROF-003 | 3 | 1 | 2 | 0 | 0 |
| 8. Customer Profitability | PROF-004 to PROF-006 | 3 | 2 | 1 | 0 | 0 |
| 9. Org Profitability | PROF-007 to PROF-010 | 4 | 2 | 2 | 0 | 0 |
| 10. Utilization Report | UTIL-001 to UTIL-007 | 7 | 3 | 2 | 2 | 0 |
| 11. Edge Cases | EDGE-001 to EDGE-015 | 15 | 2 | 7 | 5 | 1 |
| **Total** | | **80** | **25** | **33** | **21** | **1** |
