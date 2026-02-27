# Phase 29–36 Ideation — Lifecycle Integrity, Daily Work & Competitive Positioning
**Date**: 2026-02-27

## Lighthouse Domain
- Accounting/bookkeeping firms (primary), consultancies and agencies (secondary)
- Lifecycle gaps are vertical-agnostic — every practice-management product needs project completion and task terminal states
- Competitive targets: Practice/Ignition (accounting), Accelo (agencies), Clio (legal — future fork)

## Phase 29 — Entity Lifecycle & Relationship Integrity

Founder noticed projects have no completion state — "when does a project move off the current list?" Audit revealed 4 gaps:
1. **Project**: no status at all (immortal entities)
2. **Task**: no terminal state (DONE/CANCELLED missing), status is raw String not enum
3. **Project ↔ Customer**: no direct link (relationship only through invoices/documents)
4. **Delete protection**: no cascade guards (projects with children can be hard-deleted)

Also added: project due date (filing deadlines), task priority String → enum.

Key design preferences:
- Unbilled time on completion: warn + require explicit waiver
- Customer link: assignable/changeable anytime
- Archive: restorable (ARCHIVED → ACTIVE)
- Guardrails are server-enforced; archive replaces hard delete once data exists

~4 epics, ~8-12 slices. Requirements written.

## Phase 30 — Expenses, Recurring Tasks & Daily Work Completeness

Broader gap audit: are Projects+Tasks sufficient? What's missing for daily work?
1. **Expense tracking** — biggest revenue-capture gap. Firms bill 15-30% as disbursements.
2. **Recurring tasks** — monthly bank recs, weekly reports. Auto-creates next instance on completion.
3. **Unlogged time reminders** — #1 revenue leak in professional services. In-app notification.
4. **Calendar/deadline view** — frontend-only, month view + list view + overdue section.

Founder agreed all four are more important than accounting sync. "The system can't really be robust without rules and boundaries in place."

~5-6 epics, ~12-16 slices. Requirements written.

## Phase 31 — Accounting Sync (Xero + Sage)

Table stakes for accounting firms. They won't switch tools if they have to double-enter invoices.
- Two-way sync: contacts, invoices, payments, chart of accounts
- Xero first (dominant in SA/UK/AU accounting market), Sage second (SA enterprise)
- Builds on BYOAK infrastructure (Phase 21) — org configures their own API keys
- Benefits from Phases 29-30: syncs complete data (time + expenses + proper lifecycle states + project status)
- Invoice sync is the critical path; payment reconciliation closes the loop

~4-5 epics, ~10-14 slices.

## Phase 32 — Proposal → Engagement Pipeline

The "aha moment" for new sign-ups. This is Practice/Ignition's killer feature and Clio Grow's core.

The flow: create a proposal with scope of work + fee structure → send to client via portal → client accepts → auto-creates project (from template) with tasks, sets up billing schedule, assigns team, triggers onboarding checklist.

**DocTeams is closer than it looks**: document templates + clauses + acceptance workflow + project templates + recurring schedules + customer lifecycle + checklists all exist. The missing piece is a `Proposal` entity that **orchestrates** them — wiring existing capabilities into a single client-facing flow.

Key components:
- Proposal entity: wraps a document template + fee schedule (fixed/hourly/retainer) + project template reference
- Proposal builder UI: select template, configure fees, preview, send
- Acceptance trigger: client accepts → project instantiated, billing created, checklist assigned, team notified
- Proposal tracking: sent, viewed, accepted, declined, expired
- Dashboard: open proposals, conversion rate, average time-to-accept

This phase transforms DocTeams from "powerful toolkit" to "this runs my practice." The connective tissue between capabilities matters more than individual features.

~5-6 epics, ~12-16 slices.

## Phase 33 — Client Information Requests

Completes the client portal story. Every accounting firm's #1 friction: "please send me your bank statements, trial balance, and tax certificates."

Today this happens via email — documents get lost, reminders are manual, tracking is nonexistent. Practice/Ignition and Clio both nail this.

Key components:
- Information request entity: list of items to collect from a portal contact, with per-item file upload slots
- Request templates: reusable checklists ("Annual Audit Document Pack", "Tax Return Supporting Docs")
- Portal experience: client sees checklist of items to provide, uploads files, marks items complete
- Firm dashboard: which clients have outstanding requests, what's overdue, completion percentage
- Automated reminders: "You have 3 outstanding items for your annual audit — please upload by [date]"
- Auto-file: uploaded documents attach to the correct project + customer

Infrastructure reuse: portal + checklists + document upload + notifications + email delivery. Most of the machinery exists — this phase builds the client-facing UX and orchestration.

~3-4 epics, ~8-10 slices. Small but high-impact.

## Phase 34 — Resource Planning & Capacity

The growth ceiling breaker. Every firm with 10+ people asks: "who has bandwidth this week?"

Accelo's strongest feature. The data already exists (time entries show actual hours per member per day) — what's missing is the planning layer on top.

Key components:
- Member capacity config: expected hours per week, working days, leave/unavailability periods
- Allocation view: planned hours per member per project per week (lightweight — not full Gantt)
- Availability dashboard: who's over/under-allocated, who has capacity, utilization forecast
- Project staffing: assign members to projects with expected hours, track planned vs. actual
- Utilization reports: extend existing profitability reports with utilization rate per member
- Leave/unavailability: simple date ranges (not a full HR system), affects capacity calculations

This unlocks the agency/consulting vertical where utilization management is the core operational challenge.

~5-6 epics, ~12-16 slices.

## Phase 35 — Bulk Billing & Batch Operations

Scales the platform for firms with 50+ engagements. Generating invoices one-by-one is the #1 complaint from growing accounting firms at month-end.

Key components:
- Batch invoice generation: select multiple projects/clients → review unbilled time + expenses → generate all invoices → bulk review/approve → bulk send
- Invoice queue: review screen showing all generated draft invoices with quick approve/edit/reject
- Batch email: send all approved invoices in one action
- Batch status operations: bulk approve, bulk send, bulk archive
- Period close workflow: "close February" → generate all outstanding invoices → review → send → mark period complete
- Extend to other entities if natural: bulk task operations, bulk project status changes

~3-4 epics, ~8-12 slices.

## Phase 36 — Workflow Automations (v1)

The moat. Simple trigger → action rules that reduce manual coordination.

Start with predefined triggers and actions (not a visual builder):
- **Triggers**: task completed, project completed, invoice overdue (7/14/30 days), client accepts proposal, document uploaded, expense logged above threshold
- **Actions**: send notification, send email, create task, change project status, assign team member, update custom field
- **Rules**: if [trigger] + [optional condition] then [action]. Configurable by org admin
- **Templates**: ship common rules pre-built ("send reminder when invoice 14 days overdue", "notify manager when project budget hits 80%")

Future expansion: visual rule builder, multi-step workflows, conditional branching. But v1 is the predefined set that covers 80% of cases.

~4-5 epics, ~10-14 slices.

## Competitive Positioning Summary

After Phase 36, DocTeams competes on:
- **vs. Practice/Ignition**: proposal pipeline, client info requests, accounting sync — their core features matched
- **vs. Accelo**: resource planning, workflow automations, batch billing — their operational strengths matched
- **vs. Clio**: everything except trust accounting and conflict checks (fork-specific for legal vertical)
- **vs. spreadsheets + generic tools**: not even a contest after Phase 30

The key insight: DocTeams isn't missing foundational capabilities — it's missing the **connective tissue** that turns capabilities into workflows. Phases 32-33 wire existing features into flows. That's where "powerful toolkit" becomes "this runs my practice."

## Founder Preferences (Emerged This Session)
- **"First impressions last. I want a premium look — even if it takes longer."** — quality and polish over speed. No MVP-grade UX in shipping features.
- Structural integrity before external integrations (Phases 29-30 before 31)
- Daily work completeness before competitive feature-matching
- Honest about competitive gaps — not trying to be everything at once
