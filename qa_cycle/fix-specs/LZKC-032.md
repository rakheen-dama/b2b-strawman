# Fix Spec: LZKC-032 — Billing-run notifications: empty run name renders `""` + no pluralisation

## Problem
Day 90 walk: firm Notifications show `Billing run "" — 1 invoices sent` and `Billing run "" completed — 1 invoices generated`. `billing_runs.name` is genuinely empty (DB-confirmed) — the Day-28 wizard doesn't require a name — and the notification templates neither fall back to a period/reference nor pluralize the count.

## Root Cause (confirmed)
`backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationEventHandler.java`:
- `:461-464` — `"Billing run \"%s\" completed — %d invoices generated".formatted(event.runName() != null ? event.runName() : "", event.totalInvoices())` — a null/blank name becomes empty quotes; `%d invoices` is count-blind.
- `:509-511` — same shape for `"Billing run \"%s\" — %d invoices sent"`.
- `:485-488` — same shape for `"Billing run \"%s\" had %d failures"` (same defect class; fix together).

The events carry only `runName` (`backend/.../billingrun/BillingRunEvents.java:11-20`), but the publish sites in `BillingRunGenerationService.java` (`:206`, `:210`, `:366`) hold the full `BillingRun` entity, which has `periodFrom`/`periodTo` (`backend/.../billingrun/BillingRun.java:32-35`) — so a period-based display name is available at publish time without schema changes.

## Fix
1. In `BillingRunGenerationService`, compute a non-blank display name once and pass it as the events' `runName`:
   `displayName = (run.getName() != null && !run.getName().isBlank()) ? run.getName() : "%s – %s".formatted(run.getPeriodFrom(), run.getPeriodTo())` (format the dates `dd MMM yyyy` to match UI conventions). Apply at all three publish sites (`:206`, `:210`, `:366`). Keep the event records unchanged.
2. In `NotificationEventHandler`, make the three titles safe regardless of caller:
   - Drop the quotes-with-empty-fallback: when the name is blank, render `Billing run completed — …` (no quoted token) — defensive, since other future publishers may not sanitise.
   - Pluralize: `%d invoice%s`.formatted(n, n == 1 ? "" : "s")` for `:462` and `:510`; same for `failures` at `:486` (`1 failure` vs `n failures`). A tiny private helper `pluralize(int, String)` in the handler keeps the three sites consistent.
3. Cross-spec note: these are in-app/notification **titles** in `NotificationEventHandler`, not the email templates LZKC-022 (PR #1533) touched — no conflict. LZKC-031 PR-2 touches `NotificationService.java` (different file, invoice-paid titles); if the same dev takes both, coordinate but keep the PRs separate per §7.

## Scope
Backend only.
Files to modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunGenerationService.java`, `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationEventHandler.java`.
Test: extend `backend/src/test/java/io/b2mash/b2b/b2bstrawman/notification/NotificationEventHandlerIntegrationTest.java` (exists) — publish `BillingRunCompletedEvent`/`BillingRunSentEvent` with blank name and count 1 → title contains the period fallback, no `""`, and "1 invoice" (singular); with count 2 → "2 invoices".
Migration needed: no (existing stored notification rows keep the old copy — acceptable, note in PR).

## Verification
- New integration test red-first against current main, green after.
- Full `bash scripts/verify.sh`.
- Live: run the Day-28 wizard without a run name → firm Notifications must read e.g. `Billing run 01 Jul 2026 – 31 Jul 2026 completed — 1 invoice generated`.

## Estimated Effort
S–M (~45 min including test)
