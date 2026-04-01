# The Repeating Engagement Problem (And How to Automate It)

*Part 6 of "Run a Leaner Practice with Kazi." This is the final post.*

---

How many of your engagements are essentially the same thing, repeated monthly or annually?

Monthly bookkeeping. Quarterly VAT returns. Annual financial statements. PAYE reconciliations. Provisional tax submissions. For a firm with 30 clients on monthly bookkeeping, that's 30 engagements created every month — each with the same work items, the same team assignments, the same budget, the same document checklist.

Creating these manually takes 15-20 minutes per engagement. For 30 clients: 8-10 hours of admin per month. For a 3-person firm, that's an entire working day spent setting up work that hasn't started yet.

## Engagement Templates: Save Once, Use Forever

When you complete an engagement in Kazi — say, "Monthly Bookkeeping — January 2026" for Thornton Properties — you can save it as a template with one click.

The template captures:
- **Work items**: The standard task list (reconcile bank feeds, process debtors/creditors, prepare management accounts, submit VAT return if applicable)
- **Team assignments**: Who does what (article clerk does reconciliation, senior reviews, partner signs off)
- **Budget**: Hours and/or Rand amount
- **Custom fields**: Engagement type, complexity level, SARS reference
- **Document structure**: Which folders and document types to create

Next month, you create a new engagement from that template. All the work items, team assignments, budget, and document structure are pre-populated. You adjust the period dates and you're done — 2 minutes instead of 20.

For 30 monthly clients, that's 30 template-based creations: about an hour instead of a day.

## Recurring Schedules: Don't Even Click

Templates still require someone to create the engagement each month. For truly repeating work, Kazi goes one step further: **recurring schedules**.

A recurring schedule is a template on autopilot:

- **Template**: Monthly Bookkeeping (the template you saved)
- **Frequency**: Monthly (also supports weekly, fortnightly, quarterly, semi-annually, annually)
- **Client**: Thornton Properties
- **Start**: 1 February 2026
- **Auto-create**: 5 days before period start

On the 26th of each month, Kazi automatically creates the next month's bookkeeping engagement for Thornton Properties. Work items appear. Team members are assigned. Budget is set. The engagement is ready before the month starts.

Your team arrives on the 1st and the February bookkeeping engagements are already in their task lists. No admin. No forgotten clients. No "I'll set it up on Monday" that becomes Wednesday.

### Schedule Management

Schedules are living objects:

- **Pause**: Client going on leave for 3 months? Pause the schedule. No engagements created until you resume.
- **Adjust**: Client upgrading from quarterly to monthly VAT? Edit the schedule frequency.
- **Terminate**: Client leaving? End the schedule. Historical engagements and invoices are preserved.

Each schedule shows its history: every engagement it created, when, and the current status of each. If January's bookkeeping is still in progress when February's is auto-created, the dashboard shows both — and the health scoring flags January as stale.

## Retainers + Recurring Schedules: The Predictable Practice

The most powerful combination in Kazi is a recurring schedule linked to a retainer agreement:

```
Client: Thornton Properties
Retainer: R5,000/month, 8 hours included
Schedule: Monthly Bookkeeping template, auto-created on the 26th

What happens each month:
  26th: New bookkeeping engagement created from template
   1st: Team starts work, logging time against the engagement
  25th: Period close — retainer invoice auto-generated (R5,000)
  25th: Consumption report shows 7.2 of 8 hours used
  26th: Next month's engagement created
```

The firm has predictable revenue (R5,000/month × 30 clients = R150,000 guaranteed). The team has predictable work (30 engagements, pre-populated). The client has a predictable fee (no surprises).

When a client consistently exceeds their retainer hours (visible in the [consumption report](02-stop-billing-late.md)), you have data for the fee review conversation. When a client consistently under-uses their hours, you can offer additional services or adjust the retainer downward — building trust.

## The Operational Flywheel

Here's what a well-run practice looks like with templates and recurring schedules:

**Month-end (automated)**:
- 30 new monthly engagements created automatically
- Retainer invoices generated for all monthly clients
- Aged debtors report updated

**Week 1 (team)**:
- Team opens "My Work" → tasks are already assigned
- Time logging starts immediately (no engagement setup delay)
- Health scoring active from day one

**Week 2-3 (execution)**:
- Budget alerts fire at 80% for complex engagements
- Client portal shows engagement progress
- Stale engagement detection catches anything stuck

**Week 4 (billing)**:
- Unbilled time summary shows hourly engagements ready to invoice
- [10-minute billing run](02-stop-billing-late.md) creates all invoices
- Retainer invoices already generated

**Month-end (again)**:
- Cycle repeats. 30 new engagements. Zero admin.

The flywheel spins faster each month because the templates improve — each completed engagement refines the work item list, the budget estimate, and the team assignments. After 6 months, the templates match your actual practice perfectly.

## Beyond Monthly Bookkeeping

The recurring schedule pattern works for any repeating engagement:

| Engagement | Frequency | Auto-Create Lead Time |
|------------|-----------|----------------------|
| Monthly Bookkeeping | Monthly | 5 days |
| Quarterly VAT Returns | Quarterly | 14 days |
| Provisional Tax | Semi-Annually | 30 days (Feb and Aug) |
| Annual Financial Statements | Annually | 60 days before year-end |
| PAYE Reconciliation | Annually | 30 days (before Oct deadline) |
| Annual Compliance Review | Annually | 30 days |

The lead time is configurable per schedule. Annual tax returns need more lead time (60 days) because they're larger engagements. Monthly bookkeeping needs less (5 days) because the work is routine.

## What This Replaces

| Before Kazi | With Kazi |
|------------|-----------|
| Partner creates engagements manually each month | Recurring schedules auto-create |
| Work items typed from memory or a Word template | Saved engagement templates with full task lists |
| Team assignments communicated verbally or via email | Pre-assigned in the template, visible in "My Work" |
| Budget tracked in a spreadsheet (or not at all) | Budget set in template, health-scored automatically |
| "Did we set up February for all clients?" | Schedule dashboard shows status for every client |
| Forgotten recurring work discovered late | Auto-creation with configurable lead time |

## Starting Small

You don't have to template every engagement type on day one. Start with:

1. **Your highest-volume repeating engagement** — probably monthly bookkeeping
2. **Complete one month manually** in Kazi, getting the work items and assignments right
3. **Save it as a template** at the end of the month
4. **Create a recurring schedule** for your top 10 clients
5. **Observe for one month** — adjust work items, budget, assignments based on actuals
6. **Expand** — add more clients to the schedule, create templates for quarterly and annual work

By month 3, you'll have templates for 80% of your repeating work. By month 6, the practice runs itself — and your time goes to advisory work, fee reviews, and new client acquisition instead of engagement setup.

---

*This is the final post in "Run a Leaner Practice with Kazi." The series covered:*

1. *[Which Clients Are Actually Profitable?](01-which-clients-are-profitable.md) — margin visibility, rate cards, utilization*
2. *[Stop Billing Late](02-stop-billing-late.md) — unbilled time to invoice in minutes, retainers, cash flow*
3. *[FICA Compliance Without the Filing Cabinet](03-fica-without-filing-cabinet.md) — checklists, auto-activation, inspection readiness*
4. *[Your Clients Can Help Themselves](04-client-portal.md) — portal, info requests, e-signing, magic links*
5. *[Seeing the Cracks Before They Widen](05-project-health-scoring.md) — health scores, budget alerts, stale detection*
6. *[The Repeating Engagement Problem](06-repeating-engagements.md) — templates, recurring schedules, the operational flywheel*

*Kazi is practice management software built for South African professional services firms. [Request early access →](#)*
