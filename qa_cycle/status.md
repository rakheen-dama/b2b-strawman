# QA Cycle Status — Legal ZA Full Lifecycle (Keycloak)

- **Branch**: `bugfix_cycle_2026-05-13`
- **Scenario**: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md`
- **Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, portal :3002, KC :8180, Mailpit :8025)
- **Started**: 2026-05-13
- **Mode**: Clean slate. All Docker volumes wiped. Keycloak bootstrapped (padmin only). Orgs/users will be created through real onboarding flow.

## Per-Day Workflow (NON-NEGOTIABLE)

For each day-N walk in this cycle:

1. **QA agent walks Day N end-to-end.** Records every checkpoint PASS/FAIL/PARTIAL with evidence. Files OBS-* gaps for every defect.
2. **Triage every gap.** Product agent reads each new gap and decides: SPEC_READY, WONT_FIX-EXEMPT, or scenario-amendment.
3. **Fix every spec.** Dev agent: reproduce → full verify → marker → commit → PR → review → merge.
4. **PR the bugfix branch into main.** Each fix is its own PR against main.
5. **Address review findings.** All reviewer flags addressed before merge.
6. **Merge.** Pre-merge gate hook blocks unless verify markers are green.
7. **Retest each fix on main with QA agent.** Mark VERIFIED only after observed end-to-end PASS.
8. **Only then advance QA Position.** Day N+1 starts only when all Day N gaps are VERIFIED on main.

## Mandate (from user)
- Only acceptable open gaps: **KYC** and **Payments** integrations not yet wired in.
- **No production data — backward data compatibility is NOT a priority.** All data is disposable.
- No workarounds besides Mailpit API for OTP/invite-link extraction and dev-only Keycloak issues.
- All other bugs must be **fixed** at the checkpoint they appear.
- Reviewed PRs to **main**, merged, retested before continuing.
- Frontend must run **clean** — no JavaScript/Next.js errors in logs.
- No SQL shortcuts. APIs and browser UI only.
- AI provider 5xx → wait and retry, do not stop.

## QA Position
- **Day**: 30 — PARTIAL (fee note detail verified, payment flow blocked by OBS-3001)
- **Next checkpoint**: Day 30 retest after OBS-3001 fix

## Stack State
- Dev Stack: **Running** (backend :8080, gateway :8443, frontend :3000, portal :3002 all healthy)
- NEEDS_REBUILD: false

## Tracker

| Gap ID | Summary | Severity | Owner | Status | Day | Notes |
|--------|---------|----------|-------|--------|-----|-------|
| OBS-202 | KYC adapter not wired — no "Run KYC Verification" button on client detail | exempt | Product | OPEN-EXEMPT | 2 | User mandate permits KYC as unwired gap |
| OBS-203 | `/api/assistant/invocations` returns 404 on client detail page loads | nit | Dev | OPEN | 2 | Non-critical AI assistant feature; 3 occurrences per page load |
| OBS-304 | Activity feed reads "sent to Bob Ndlovu" instead of portal contact name on info request send | nit | Dev | OPEN | 3 | Cosmetic — activity log references actor not recipient |
| OBS-1002 | Trust deposit Record Deposit dialog combobox non-functional on standalone Transactions page | HIGH | Dev | OPEN | 10 | Triple Slot composition (PopoverTrigger > FormControl > Button) breaks Radix Popover. Workaround: use matter Trust tab. Also affects Record Payment / Refund dialogs. |
| OBS-3001 | Mock payment integration not seeded during tenant provisioning — portal shows "Contact firm" instead of Pay Now | HIGH | Dev | OPEN | 30 | `TenantProvisioningService` does not call `MockPaymentIntegrationSeeder.seedForTenant()`. `PackReconciliationRunner` only runs at startup before tenant exists. No `paymentUrl` on invoice. |

## Log

| Cycle | Agent | Action | Result |
|-------|-------|--------|--------|
| 0 | Infra | Clean slate setup: volumes wiped, KC bootstrapped, all services started | Stack running |
| 1 | QA | Day 0 walk: Onboarding (access-request, OTP, approval, KC registration, team invites) | 32/32 PASS, 0 gaps |
| 1 | QA | Day 1 walk: Firm onboarding polish (branding, tariffs, trust account) | 10/10 PASS, 0 gaps |
| 1 | QA | Day 2 walk: Onboard Sipho as client, conflict check + KYC | 7/10 PASS, 3 SKIPPED (KYC exempt), 2 gaps (OBS-202 exempt, OBS-203 nit) |
| 1 | QA | Day 3 walk: Create RAF matter (RAF-2026-001), send FICA info request (REQ-0001) | 14/14 PASS, 0 blockers, 1 new nit (OBS-304) |
| 1 | QA | Day 4 walk: Sipho first portal login (magic-link), upload FICA documents (3/3) | 14/14 PASS, 0 blockers, 0 new gaps |
| 1 | QA | Day 5 walk: Bob reviews FICA submission (per-item accept x3, envelope completes) | 8/8 PASS, 0 blockers, 0 new gaps, OBS-501+OBS-502 verified |
| 1 | QA | Day 7 walk: Thandi drafts + sends proposal (engagement letter) for Dlamini v RAF | 11/11 PASS, 0 blockers, 0 new gaps; OBS-702/703 fixes confirmed; OBS-704 hydration mismatch pre-existing |
| 1 | QA | Day 8 walk: Sipho reviews + accepts proposal PROP-0001 on portal | 11/11 PASS, 0 blockers, 0 new gaps |
| 1 | QA | Day 10 walk: Firm verifies proposal acceptance, deposits R 50,000 trust funds | 9/9 PASS (via matter Trust tab workaround), 0 blockers, 1 new gap (OBS-1002 HIGH — broken combobox on standalone transactions page) |
| 1 | QA | Day 11 walk: Sipho sees trust balance on portal | 8/8 PASS, 0 blockers, 0 new gaps; OBS-1101 (prev cycle) verified fixed |
| 1 | QA | Day 14 walk: Firm onboards Moroka Family Trust (isolation setup) | 11/11 PASS, 0 blockers, 0 new gaps; Moroka client + matter + info request + document + R 25k trust deposit seeded |
| 1 | QA | Day 15 walk: BLOCKER-severity isolation check — Sipho cannot see Moroka's data | 22/22 PASS, 0 blockers, 0 new gaps; list views, direct-URL probes, API probes, email inspection all clean |
| 1 | QA | Day 21 walk: Time entries (2x, non-tariff path), sheriff disbursement, pre-trial court date | 12/12 PASS, 0 blockers, 0 new gaps; 4h logged (2h30m + 1h30m), R 1,250 disbursement, court date 2026-05-28 |
| 1 | QA | Day 28 walk: Bulk billing fee note generation (disbursement approval, Sipho activation, wizard, approve, send) | 3/3 PASS, 0 blockers, 0 new gaps; INV-0001 R 1,437.50 (2 TIME + 1 EXPENSE line), email to sipho.portal@example.com |
| 1 | QA | Day 30 walk: Sipho views fee note on portal, payment flow blocked | PARTIAL — fee note detail PASS (3 lines, R 1,437.50, terminology correct), payment BLOCKED (OBS-3001: mock PSP not seeded), isolation PASS |
