# Reports & Data Export

## Report System

### What It Is
Configurable reports that query across projects, customers, time entries, and invoices. Reports can be run with parameters and exported.

### Available Report Types

**Timesheet Report**
- Parameters: date range, member(s), project(s)
- Output: time entries grouped by date/member/project
- Shows: task, hours, billable flag, rate, value

**Invoice Aging Report**
- Parameters: as-of date
- Output: outstanding invoices grouped by age bucket (current, 30 days, 60 days, 90+ days)
- Shows: invoice number, customer, amount, days outstanding

**Project Profitability Report**
- Parameters: date range, project(s), customer(s)
- Output: revenue, cost, margin per project
- Shows: hours breakdown, rate analysis, margin %

**Utilization Report**
- Parameters: date range
- Output: per-member utilization metrics
- Shows: total hours, billable hours, utilization %, target comparison

### Report UI

**Reports Page (`/reports`)**
- List of available report definitions
- Click report → report detail page

**Report Detail Page (`/reports/[reportSlug]`)**
- Parameter form (date range pickers, entity selectors, filters)
- "Run Report" button
- Results table (sortable, filterable)
- Export actions: CSV, PDF

### Report Execution Flow
1. Select report type
2. Configure parameters
3. Run → backend executes query
4. Results displayed in table
5. Export to CSV or generate PDF report document

### Report Export
- **CSV**: raw data download
- **PDF**: formatted report using backend rendering pipeline
- Generated reports tracked (similar to generated documents)
