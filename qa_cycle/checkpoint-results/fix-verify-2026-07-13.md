# Fix-Verify Pass — Cycle 2026-07-12 — executed 2026-07-13 (QA Agent, Playwright MCP, Keycloak dev stack)

Scope: live browser verification of the 9 MERGED-AWAITING-VERIFY items (8 gap IDs; LZKC-031 = 2 PRs).
Actors: Thandi (Owner, fresh KC login — prior session expired), Carol (Member, fresh KC login), Sipho (portal, fresh magic link — prior session expired).
All product operations via UI; DB access read-only SELECT; Mailpit API for email verification.

## Verdicts

| Item | PR | Verdict |
|------|----|---------|
| LZKC-028 | #1552 | **VERIFIED** |
| LZKC-024 | #1553 | **VERIFIED** |
| LZKC-032 | #1554 | **VERIFIED** (fallback leg; named-run quoted leg not UI-exercisable — see notes) |
| LZKC-025 | #1555 | **VERIFIED** |
| LZKC-030 | #1556 | **VERIFIED** |
| LZKC-026 | #1557 | **VERIFIED** |
| LZKC-027 | #1558 | **VERIFIED** |
| LZKC-031 PR-1 | #1559 | **VERIFIED** (all enumerated sites) |
| LZKC-031 PR-2 | #1560 | **VERIFIED** (after environment remediation — stale-classes restart, see Environment) |

New gaps filed: **LZKC-033**, **LZKC-034** (below). No REOPENs.

---

## LZKC-028 — firm invoice-detail VAT labels (PR #1552) — VERIFIED

- INV-0001 detail (`/org/mathebula-partners/invoices/f44ef40d-…`): totals block renders **"VAT — Standard (15%)"** and **"Zero-rated (0%)"**; line items likewise. Programmatic scan: **0 occurrences of literal "(%)"** anywhere on the page.
- Bonus re-check on freshly created INV-0002: line tax cell + totals block both "VAT — Standard (15%)".
- Console: 0 errors.
- Evidence: `fixverify-028-invoice-vat-labels.png`, `fixverify-028-totals-block.png` (totals element crop shows Subtotal / VAT — Standard (15%) / Zero-rated (0%) / Total).

## LZKC-024 — StageReorder hydration (PR #1553) — VERIFIED

- `/settings/pipeline` loaded fresh **2×** (page renders 5 stages with Reorder buttons). Both loads: **0 console errors, 0 warnings, 0 hydration messages** (grep for "hydrat" over both console logs = 0 hits; only Fast Refresh/HMR/devtools INFO lines).
- Evidence: `fixverify-024-pipeline-stages-clean.png`; console logs `console-2026-07-13T12-28-30-950Z.log`, `console-2026-07-13T12-28-53-681Z.log`.

## LZKC-032 — billing-run notification empty-name + pluralisation (PR #1554) — VERIFIED

- Setup: Moroka is PROSPECT so excluded from billing discovery by design (`BillingRunSelectionService.discoverCustomers` filters `lifecycle_status='ACTIVE'`); billable scratch work created for **Sipho** instead — disbursement R 200 excl VAT on active matter "Engagement Letter — Litigation (Dlamini v RAF)", submitted + approved.
- Wizard run (no name field exists in the wizard — every UI-created run is unnamed, so the fallback is the mainline path): period 01–31 Jul, Sipho discovered R 230,00, drafts approved, run `6ad85389` COMPLETED with 1 invoice (INV-0002).
- **Notification title observed (DB + Notifications page): `Billing run 01 Jul 2026 – 31 Jul 2026 completed — 1 invoice generated`** — period fallback UNQUOTED, singular "invoice". Exactly the fixed shape.
- Named-run quoted leg (`Billing run "name"`): **not exercisable via the UI** — the wizard has no name input and no other product surface sets a run name. Covered by PR #1554's unit tests only; recorded honestly as not browser-verified.
- Email leg: no billing-run email fires (in-app only) — nothing to check in Mailpit for this event.
- Evidence: `fixverify-032-billing-run-notification.png`; DB `notifications` rows 12:58:00.
- Side find while here → **LZKC-033** (empty-quoted `“”` banner on the billing-runs list page, same empty-name class, different frontend site — see New Gaps).

## LZKC-025 — portal decline attribution (PR #1555) — VERIFIED

- Scratch proposal PROP-0003 "QA Scratch - Decline Attribution Test" created (via the LZKC-027 dialog), sent to Sipho; declined in the portal with reason text.
- **Firm-side audit history entry: actor "Portal Contact", Source: PORTAL**, reason payload captured; decline reason also rendered on the firm proposal detail ("Decline Reason: QA fix-verify LZKC-025 decline attribution test").
- **DB audit event**: `proposal.declined`, `actor_type=PORTAL_CONTACT`, `actor_id=fcb3147e-…` (Sipho's portal contact), `source=PORTAL`.
- **Portal "Your actions"**: "Engagement letter declined — You".
- Known residual present as documented (not a failure): payload `actor_name: "System"` — exact LZKC-020 parity.
- Evidence: `fixverify-025-portal-your-actions-decline.png`, `fixverify-025-firm-audit-portal-contact.png`.

## LZKC-030 — SoA opening balance (PR #1556) — VERIFIED

- Regenerated Statement of Account on CLOSED matter RAF-2026-001 via the matter-header "Generate Statement of Account" flow, period **2026-07-12 → 2026-07-13** (period-start day = DEP/2026/001's date, the exact defect boundary).
- Preview observed: **Opening balance R0,00** (period-start-day deposit EXCLUDED from opening); itemised DEP/2026/001 +R50 000, DEP/2026/003 +R20 000, PAY/2026/001 −R70 000; **Closing balance R0,00**. Statement self-reconciles: 0 + 50 000 + 20 000 − 70 000 = 0.
- PDF saved to matter documents (`statement-of-account-dlamini-v-road-accident-fund-2026-07-13.pdf`, 13:12:09 — residue, see below).
- Evidence: full a11y transcript in session snapshot `page-2026-07-13T13-12-33-038Z.yml` (trust block quoted in report); `fixverify-030-soa-preview-dialog.png` / `fixverify-030-soa-viewport.png` (note: PDF iframe paints blank in headless capture; the transcript is the content evidence).

## LZKC-026 — features-page module clobber (PR #1557) — VERIFIED

- Baseline DB: `deadlines` present in `org_settings.enabled_modules`.
- Toggled Automation Rule Builder ON (save 1) → DB: `automation_builder` added, **`deadlines` survived**. Toggled OFF (save 2) → DB back to exact baseline, **`deadlines` survived**.
- Backend log (both saves): `added=[automation_builder], removed=[]` then `added=[], removed=[automation_builder]` — **no `removed=[deadlines]`**.
- Console: only the known carried-forward `/api/assistant/invocations` 404 noise (observation c, not re-filed).
- Evidence: `fixverify-026-features-page.png`; log lines 12:29:41 / 12:30:03 quoted in tracker session.

## LZKC-027 — inert CreateProposalDialog client combobox (PR #1558) — VERIFIED

- Org-level Proposals ("Engagement Letters") → New Engagement Letter → **Client combobox opens** (popover + search listbox), lists Moroka + Sipho, **Sipho selectable**; form submitted end-to-end → PROP-0003 created and persisted (retainer R 1 000,00 rendered on detail).
- Shared-primitive regression canaries all healthy: proposal Title textbox + Retainer spinbutton, Log Time dialog (minutes + description), New Disbursement dialog (5 fields), Edit Customer dialog (tax number) — every FormControl-mediated input accepted values and persisted.
- Evidence: `fixverify-027-client-combobox-open.png`.

## LZKC-031 PR-1 — terminology residuals, frontend sites (PR #1559) — VERIFIED

Programmatic innerText scans (`\b(Projects?|Customers?|Invoices?)\b`) per route, all as Thandi:

- **My Work**: 0 hits; table headers "MATTER / TITLE / …" (×2 tables). `fixverify-031pr1-mywork-matter-headers.png`
- **Compliance**: 0 hits ("No clients currently in onboarding", "Check for Dormant Clients").
- **Settings > General**: 0 hits.
- **Schedules**: 0 hits ("Automate matter creation with recurring schedules…"). **Pause dialog** (the review round-trip site): "Pausing this schedule will stop automatic **matter** creation." `fixverify-031pr1-pause-dialog-matter-creation.png`
- **Profitability**: 0 hits in scan; section headings "Matter Profitability" and "Client Profitability". `fixverify-031pr1-profitability.png`
- **Billing Runs page**: 0 hits ("Fee Notes", CLIENTS/FEE NOTES headers). **Wizard step nav**: "Select Clients" (fixed site live).

Same-class NON-enumerated residuals re-observed (all belong to the deferred LZKC-031-PR-3/terminology bucket, NOT re-filed, NOT REOPENs — none were in PR-1's 17-site scope, confirmed against the #1559 diff):
- `customer-selection-step.tsx:119/124` — wizard step-2 panel heading "Select Customers" + "No customers with unbilled work found" + "0 customers selected".
- Profitability empty-states "No project/customer profitability data" (already reviewer-noted).
- ScheduleCreateDialog "Set up automatic project creation…" (already reviewer-noted, :136).
- Matter Finance>Time empty state "…see project time summaries here"; Log Time dialog "Project Settings > Rates".
- "Select a customer…" placeholders (CreateProposalDialog combobox, schedule + disbursement Client selects).
- Firm proposal detail: "Send Proposal" button/dialog, "Proposal Details" card on legal tenant.
- Send-step "Ready to send: 1 invoices" (count-blind, already reviewer-noted as send-step extras).
- "Browse projects" CTA in myWork empty-state catalog (PR-2 deliberately fixed only the `description` fields — cta untouched, confirmed against #1560 diff).

## LZKC-031 PR-2 — empty-states catalog + notification titles + portal role (PR #1560) — VERIFIED (after environment remediation)

- **Carol (Member, no tasks) My Work empty state**: "Your action items and time tracking **across all matters**"; "Tasks assigned to you **across all matters** will appear here. Head to a **matter** to create or pick up tasks."; "…calculate costs, and **generate accurate fee notes**." 0 console errors. `fixverify-031pr2-carol-mywork-empty-state.png`
- **Portal profile (Sipho)**: Role renders **"Contact"** (was "General Customer"). The sibling field *label* "Customer" is the known deferred residual. `fixverify-031pr2-portal-profile-role-contact.png`
- **Notification titles (invoiceTerm)**: INV-0002 paid (firm Record Payment) → DB title **"Fee Note INV-0002 for Sipho Dlamini has been paid"** (INVOICE_PAID). Email subject for the send leg: **"Fee Note INV-0002 from Mathebula & Partners"** (Mailpit). The INVOICE_SENT row created at 13:05 reads "Invoice INV-0002 … has been sent" — produced by the STALE pre-#1560 backend (see Environment); the sent/paid titles share the single fixed `invoiceTerm()` format string, and the paid title was observed correct post-remediation. Stored rows keep original copy by design.

## Environment finding (infra, remediated in-session)

The backend process started 14:22:58 (+0200) after the #1560 merge (14:22:24) but was executing **classes compiled at 12:39** — `javap` on the loaded `NotificationService.class` showed **0 `invoiceTerm` references** (restart raced the merge; Maven did not recompile). All backend fixes through #1557 (12:39:17 merge) WERE in that compile — including LZKC-030's `StatementOfAccountContextBuilder` — so only PR #1560's backend leg was affected. Remediation: `./mvnw compile` (invoiceTerm confirmed in class) + `svc.sh restart backend` (fresh PID 33881, health UP, verified via lsof/ps). Lesson: "backend restarted post-merge" needs a compiled-classes timestamp check, not just a process check.

## New gaps

| Gap ID | Summary | Severity | Owner | Status |
|--------|---------|----------|-------|--------|
| LZKC-033 | Billing-runs list active-run banner renders `“” is currently in preview.` for unnamed runs — `frontend/app/(app)/org/[slug]/invoices/billing-runs/page.tsx:93` hardcodes `&ldquo;{activeRun.name}&rdquo;`; every wizard-created run is unnamed (wizard has no name field), so the empty-quoted banner is the mainline rendering. Same empty-name class as LZKC-032, different (frontend) site outside #1554's scope. Evidence: `fixverify-033-empty-quoted-preview-banner.png` | Low | Product | OPEN |
| LZKC-034 | Client-detail header "Edit" smart-action button is fully inert for ACTIVE/DORMANT clients — `client-header-card.tsx:41` renders the "Edit" label but `client-header-card-with-lifecycle.tsx:70` passes `onPrimaryAction={targetLifecycleStatus ? … : undefined}` and ACTIVE/DORMANT have no target transition → button has no onClick (probed: no dialog, no navigation, no popup attrs). Working path exists via More actions → "Edit Client". Reproduced on Sipho (ACTIVE) | Low | Product | OPEN |

## Residue created (scratch, documented)

- **PROP-0003** "QA Scratch - Decline Attribution Test" — DECLINED engagement letter for Sipho (org-level, R 1 000 retainer shape).
- **Recurring schedule** "Sipho Dlamini - Collections" — created then immediately **PAUSED** (annual, start 2027-07-01, lead 0; created solely to exercise the pause dialog; safe while paused).
- **Moroka (EST-2026-002)**: 30-min billable time entry (R 0 value, unbilled) + disbursement `61b94679` R 100 excl VAT **APPROVED/UNBILLED** — inert while Moroka stays PROSPECT (excluded from billing discovery).
- **Sipho**: disbursement `0f5a33c1` R 200 excl VAT on the active Engagement-Letter matter → billed via unnamed billing run `6ad85389` (COMPLETED) → **INV-0002 R 230,00 SENT then PAID** (firm-recorded payment); fee-note PDF generated; email sent to sipho.portal@example.com. **Tax number `9012345678` added to Sipho's client profile** (was empty; send was blocked with `tax_number_missing` until set).
- **RAF-2026-001**: second `statement-of-account-…-2026-07-13.pdf` (13:12) in matter documents (portal-visible).
- Two intermediate PREVIEW billing runs created then cancelled — **cancel deletes the rows**, no DB residue.
- Sessions: Thandi signed out of :3000 (Carol currently logged in); Sipho portal session refreshed via new magic link.
- Backend recompiled + restarted (PID 33881).

## Console hygiene

0 new console errors across all navigations except the known carried-forward `/api/assistant/invocations` 404 on settings routes (observation c) and one initial 500 caused by the expired pre-session auth (cleared after fresh login). Benign Next.js `scroll-behavior` advisory warning observed on several routes (framework advisory, not product).
