# Rates, Budgets & Profitability

## Billing Rates

### Rate Hierarchy (3 Levels)
Rates determine how much a member's time is billed. The most specific applicable rate wins:

1. **Project override**: Member + Project → "Alice bills R1,200/hr on the Smith project"
2. **Customer override**: Member + Customer → "Alice bills R1,000/hr for Acme Corp"
3. **Member default**: Member only → "Alice's standard rate is R850/hr"

### Rate Fields
| Field | Type | Notes |
|-------|------|-------|
| Member | Link | Required |
| Project | Link | Optional (for project override) |
| Customer | Link | Optional (for customer override) |
| Hourly Rate | Decimal | Billing rate per hour |
| Currency | Text | e.g., "ZAR" |
| Effective From | Date | When this rate starts |
| Effective To | Date | When this rate ends (null = indefinite) |

### Rate Management (Settings → Rates)
- Table of all billing rates grouped by member
- Shows resolved scope: "Default", "Project: Smith", "Customer: Acme"
- Add/edit/delete rate dialogs
- Project detail → Financials tab also shows project-specific rates
- Customer detail → Financials tab shows customer-specific rates

### Cost Rates (Internal)
Separate from billing rates. Represent the firm's internal cost of a member's time (salary/overhead):

| Field | Type |
|-------|------|
| Member | Link |
| Hourly Cost | Decimal |
| Currency | Text |
| Effective From/To | Dates |

Cost rates are used for profitability calculations but never shown to customers.

---

## Project Budgets

### What Budgets Are
A spending limit on a project — tracked in hours and/or currency.

### Budget Fields
| Field | Type | Notes |
|-------|------|-------|
| Project | Link | One budget per project |
| Budget Amount | Decimal | Total budget in currency |
| Currency | Text | e.g., "ZAR" |
| Alert Threshold | Percentage | When to warn (e.g., 80%) |

### Budget Status
Calculated from time entries on the project:

| Metric | Calculation |
|--------|-------------|
| Hours Used | Sum of all time entry durations |
| Cost Used | Sum of (duration × cost rate) for all entries |
| Billable Value Used | Sum of (duration × billing rate) for billable entries |
| Budget Consumed % | Cost Used / Budget Amount |

### Budget Alerts
- Visual indicator on project list and detail (green/amber/red dot)
- At threshold: amber warning
- Over budget: red indicator
- Budget status shown in project overview tab

### Budget Configuration (Project Detail → Financials Tab)
- Set budget amount and currency
- Set alert threshold percentage
- View budget vs actual breakdown

---

## Profitability

### Profitability Page (`/profitability`)
Firm-wide profitability analysis with three views:

**Project Profitability Table**
- All projects with: revenue (billable value), cost (cost rate × hours), margin, margin %
- Sortable by any column
- Date range filter
- Color-coded margin indicators

**Customer Profitability**
- All customers with: total revenue, total cost, margin, margin %
- Lifetime view (all time) or filtered by date range

**Team Utilization**
- All members with: total hours, billable hours, utilization %, billable value
- Date range filter
- Target utilization comparison (if configured)

### Project Financials Tab (Project Detail)
- Revenue: sum of billable time × rates
- Cost: sum of all time × cost rates
- Margin: revenue - cost
- Margin %: (margin / revenue) × 100
- Budget status (if configured)
- Rate cards in effect for this project

### Customer Financials Tab (Customer Detail)
- Lifetime revenue and cost across all projects
- Per-project breakdown
- Active retainer value

---

## Dashboard KPIs

### Company Dashboard
- **Total Billable Value** (this period)
- **Outstanding Invoices** (sent but unpaid total)
- **Team Utilization** (org average %)
- **Active Projects** count
- **Overdue Invoices** count and value
- **Revenue Trend** (sparkline chart)

### Personal Dashboard (My Work KPIs)
- **My Utilization** (billable / total hours %)
- **My Billable Hours** (this period)
- **Tasks Completed** (this period)
- **Average Daily Hours**
