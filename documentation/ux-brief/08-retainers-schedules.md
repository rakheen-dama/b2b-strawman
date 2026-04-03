# Retainers & Recurring Schedules

## Retainer Agreements

### What a Retainer Is
A recurring billing agreement between the firm and a customer. The customer pays for a fixed number of hours per period (monthly/quarterly). Hours are tracked against the retainer, and unused hours may roll over.

### Retainer Fields
| Field | Type | Notes |
|-------|------|-------|
| Customer | Link | Required |
| Name | Text | e.g., "Monthly Advisory Retainer" |
| Status | Enum | ACTIVE, PAUSED, CANCELLED, EXPIRED |
| Period Type | Enum | MONTHLY, QUARTERLY |
| Hours Per Period | Decimal | Contracted hours per billing period |
| Rate Per Hour | Decimal | Billing rate for retainer work |
| Currency | Text | e.g., "ZAR" |
| Start Date | Date | When the retainer begins |
| End Date | Date | Optional (null = indefinite) |
| Rollover Enabled | Boolean | Whether unused hours carry forward |
| Max Rollover Hours | Decimal | Cap on rollover (optional) |

### Retainer Status
| Status | Meaning |
|--------|---------|
| ACTIVE | Currently in effect, tracking hours |
| PAUSED | Temporarily suspended (hours not tracked) |
| CANCELLED | Terminated early |
| EXPIRED | Past end date |

### Retainer Period Management
Each billing period is tracked separately:

| Period Field | Type |
|-------------|------|
| Period Start | Date |
| Period End | Date |
| Hours Allocated | Decimal (contracted + rollover) |
| Hours Used | Decimal (sum of time entries) |
| Hours Remaining | Decimal |
| Status | OPEN, CLOSED |

### Close Period Flow
1. Period end date arrives (or admin manually closes)
2. System calculates: hours used, hours remaining
3. If rollover enabled: remaining hours (up to max) carry to next period
4. Generates invoice for the period (retainer fee)
5. New period opens automatically

### Retainer Consumption
- Time entries logged against tasks in the customer's projects are automatically tracked against the active retainer
- Time entry list shows retainer indicator badge when applicable
- Over-allocation: if hours exceed period allocation, excess is flagged (can be billed separately)

### Retainer Dashboard (`/retainers`)
- List of all retainer agreements
- Status badges (Active, Paused, etc.)
- Utilization progress bars (hours used / hours allocated)
- Summary cards: total active retainers, total hours committed, average utilization

### Retainer Detail Page (`/retainers/[id]`)
- Retainer metadata (customer, rate, period type, etc.)
- Current period: hours used vs allocated (progress bar)
- Actions: Edit, Close Period, Pause/Resume, Cancel
- Period History table: all past periods with hours and invoices generated

### Customer Retainer Tab
- Same retainer information accessible from customer detail page
- Shows all retainers for this customer

---

## Recurring Schedules

### What Schedules Are
Automated project creation on a recurring basis. Useful for monthly audits, quarterly reviews, annual filings — work that repeats predictably.

### Schedule Fields
| Field | Type | Notes |
|-------|------|-------|
| Name | Text | e.g., "Monthly Bookkeeping — Acme" |
| Project Template | Link | Template to create project from |
| Customer | Link | Optional (link new project to customer) |
| Frequency | Enum | DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY |
| Day/Date Config | Varies | Day of week (weekly), day of month (monthly), etc. |
| Start Date | Date | When scheduling begins |
| End Date | Date | Optional (null = indefinite) |
| Active | Boolean | Whether the schedule fires |
| Next Run | Date | Computed next execution date |

### Project Templates (Settings → Project Templates)
Reusable project structures that define:
- Project name pattern (with date variables like `{month}`, `{year}`)
- Description
- Default tasks (with titles, descriptions, priorities)
- Default member assignments (by role)
- Tags

### Template Management
- "Save as Template" from any existing project → captures current structure
- Create template from scratch
- View/edit template: name, tasks, assignments
- Use template: when creating a new project, select template → pre-fills structure

### Schedule Execution
1. Scheduler checks for due schedules (runs daily)
2. For each due schedule: instantiates the project template
3. New project created with: name (template name + date), tasks, assignments, customer link
4. Execution logged in history

### Schedule Detail Page (`/schedules/[id]`)
- Configuration: frequency, template, customer, next run
- Actions: Execute Now (manual trigger), Edit, Activate/Deactivate, Delete
- Execution History: table of past runs with project links, success/failure status

### Schedule List Page (`/schedules`)
- All schedules with status, frequency, next run, template name
- Search and filter
- "New Schedule" → create dialog
