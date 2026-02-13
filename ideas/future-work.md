# Future Work & Ideas Log

Ideas deferred from current phases. Revisit when scoping future phases.

## Deferred from Phase 9 (Dashboards)

### Reporting Engine (Option B scope)
- Pre-built reports: utilization, project profitability, time-by-customer
- Date range filtering, CSV/PDF export
- Scheduled report delivery via email
- **Why deferred**: Dashboards first, reports layer on top naturally once the aggregation queries exist.

### Customizable Dashboard Widgets
- User-configurable widget layout (drag/drop)
- Widget library (pick which KPIs to show)
- **Why deferred**: Opinionated defaults ship faster. Customization is a v2 enhancement once we know which widgets people actually use.

---

## Backlog (Not Yet Scoped)

### Invoicing & Billing
- Convert tracked time into invoices (draft → approved → sent → paid)
- Line items from time entries, flat fees, expenses
- Invoice templates, numbering schemes
- Payment tracking (manual for v1, Stripe integration later)
- **Depends on**: Phase 8 (rate cards, billable amounts)
- **Priority**: High — the revenue-generating feature

### Resource Planning & Capacity
- Team capacity view (available hours per member per week)
- Allocation vs. actual
- Capacity forecasting
- **Depends on**: Phase 9 (utilization metrics), Phase 8 (cost rates)

### Recurring Work & Retainers
- Retainer agreements with hour banks
- Recurring project/task templates
- Automatic rollover logic
- **Depends on**: Invoicing (for billing retainers)

### Proposals & Engagement Letters
- Template-based proposals with variable substitution
- Send/track/accept workflow
- Convert accepted proposal → project
- **Depends on**: Customer model (exists), rate cards (Phase 8)

### Integrations Platform
- Accounting: QuickBooks, Xero sync
- Calendar: Google/Outlook integration
- Communication: Slack notifications
- Webhook-out for custom integrations

### Client Portal (Full)
- Customer self-service beyond read-only
- Approve deliverables, view invoices, submit requests
- **Depends on**: Phase 7 (portal backend), Invoicing

---

## User Interview Notes

_Add notes from user interviews and customer conversations here._
