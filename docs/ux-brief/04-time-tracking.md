# Time Tracking

## Time Entries

### What a Time Entry Is
A record of time spent on a task. Time entries are the atomic unit of billing — they carry rate snapshots and billable flags that drive invoicing and profitability.

### Time Entry Fields
| Field | Type | Notes |
|-------|------|-------|
| Task | Link | Required (time is always logged against a task) |
| Member | Link | Required (who did the work) |
| Date | Date | When the work was done |
| Duration | Integer | Minutes (displayed as hours:minutes) |
| Billable | Boolean | Whether this time can be invoiced |
| Description | Text | Optional note about the work |
| Invoice | Link | Set when this time entry is included in an invoice |
| Billing Rate Snapshot | Decimal | Rate at time of entry creation (immutable) |
| Billing Rate Currency | Text | e.g., "ZAR", "USD" |
| Cost Rate Snapshot | Decimal | Internal cost rate (for profitability) |
| Cost Rate Currency | Text | e.g., "ZAR" |

### Rate Snapshot Logic
When a time entry is created, the system looks up the applicable billing rate using the 3-level hierarchy:
1. **Project-specific rate** (member + project) — highest priority
2. **Customer-specific rate** (member + customer via project's linked customer)
3. **Member default rate** (member only) — fallback

The resolved rate is **snapshotted** (copied) into the time entry. This means:
- Changing a rate card later does NOT retroactively change existing time entries
- Invoices reflect the rate that was in effect when the work was done
- This is intentional — prevents billing disputes from rate changes

### Billable Value Calculation
```
billableValue = (durationMinutes / 60) × billingRateSnapshot    (only if billable=true AND rate exists)
costValue = (durationMinutes / 60) × costRateSnapshot           (always, for profitability)
margin = billableValue - costValue
```

### Billing Status
Time entries have an implicit billing status:
- **Unbilled**: No invoiceId, billable=true
- **Billed**: Has invoiceId (included in a draft/approved invoice)
- **Non-billable**: billable=false (never invoiceable)
- **Invoiced**: On a SENT or PAID invoice (effectively locked)

### Log Time Dialog
- Accessed from: task detail sheet, project time tab, My Work page
- Fields: task (pre-filled if from task context), date (defaults to today), duration (hours:minutes input), billable toggle, description
- Shows resolved rate preview: "Billing at R850/hr" (fetched from rate resolution endpoint)
- Shows retainer indicator if task's project has an active retainer

### Edit/Delete Time Entry
- Edit: same fields as create, but task is read-only
- Delete: confirmation dialog
- Both blocked if time entry is on an approved/sent/paid invoice

---

## My Work — Time Tracking Section

### Time Logged Today
- List of today's time entries across all projects
- Quick total: "4h 30m logged today"
- Each entry shows: task name, project name, duration, billable badge

### Weekly Time Summary
- Bar chart showing hours per day for the current week
- Distinguishes billable vs non-billable
- Target line (if org has a target hours/week setting)

### Time Breakdown
- Hours grouped by project (pie/bar chart)
- Drill down to see per-task breakdown

### Personal KPIs
- **Utilization rate**: billable hours / total hours (%)
- **Billable hours this period**: sum of billable time entries
- **Average daily hours**: total hours / working days

---

## Project Time Summary

### Time Summary Panel (Project Detail → Time Tab)
- Total hours (all members, all tasks)
- Billable hours / Non-billable hours
- Total billable value (sum of billable hours × rates)
- Budget consumed (if budget configured): progress bar with percentage

### Per-Member Breakdown
- Table: member name, total hours, billable hours, billable value
- Expandable to show per-task breakdown per member

---

## Unbilled Time

### What It Is
Time entries that are billable but not yet on any invoice. This is the "money left on the table" view.

### Where It Appears
1. **Customer detail → Invoices tab**: "Unbilled time" summary card showing total hours and value
2. **Invoice generation dialog**: selects unbilled time entries to include as invoice line items
3. **Project completion dialog**: warns if there's unbilled time before marking project complete

### Unbilled Time Endpoint
- `GET /api/customers/{id}/unbilled-time?from=&to=`
- Returns time entries grouped by project, with totals
- Used to generate invoices from tracked time
