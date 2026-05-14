# Day 90 — Final regression + exit sweep (cycle 2)

**Cycle**: 2 (2026-05-13, branch `bugfix_cycle_2026-05-13`)
**Actors**: Thandi (firm `:3000` via Keycloak), Sipho (portal `:3002` via magic-link)
**Date executed**: 2026-05-14
**Result**: **PASS** — all exit gates green; one cosmetic terminology finding (portal `/projects` heading)

---

## Firm-side regression sweep

| # | Checkpoint | Result | Evidence |
|---|---|---|---|
| E.1 | Terminology sweep — zero `Project`/`Customer`/`Invoice` leaks in sidebar | **PASS** | Sidebar groups: Work (Dashboard, My Work, Calendar, Court Calendar), Matters (Matters, Recurring Schedules), Clients (Clients, Engagement Letters, Mandates, Compliance, Conflict Check, Adverse Parties), Finance (Fee Notes, Billing Runs, Profitability, Reports, Trust Accounting, Transactions, Client Ledgers, Reconciliation, Interest, Investments, Trust Reports, Tariffs), Team. Zero accounting/consulting vocabulary. |
| E.2 | Legal modules visible: Matters, Trust Accounting, Court Calendar, Conflict Check + Tariffs | **PASS** | All 5 modules present in sidebar. No cross-vertical nav items. |
| E.3 | No accounting/consulting nav leaks | **PASS** | No non-legal nav items visible. |
| E.4 | No tier/upgrade/billing upsell visible | **PASS** | Settings > Billing shows "Trial / Manual / Managed Account — Your account is managed by your administrator." Zero Starter/Pro/Enterprise tier UI. |
| E.5 | Settings > Billing: flat, no plan tiers | **PASS** | Same as E.4. Settings nav also shows "Matter Templates" and "Matter Naming" (correct legal terminology, not "Project Templates"). |
| E.6 | Progressive disclosure: legal nav groups only | **PASS** | All legal modules present; no cross-vertical leaks in sidebar, settings, or breadcrumbs. |
| E.7 | Mailpit sweep: 43 total emails, 0 bounces/failures | **PASS** | 43 emails total. 0 bounced / 0 undeliverable / 0 delivery-status-failed. Subjects include: verification OTP, KC invitations (3), portal access links (17), info requests, trust activity, fee notes, proposals, weekly digests, document-ready, item-accepted, request-completed. |

### Firm console errors

Zero new functional JS errors on routes navigated during this sweep (dashboard, projects, customers, invoices, trust-accounting, court-calendar, conflict-check, tariffs, my-work, team, settings/billing). Historical errors from prior session pages (matter detail 500s, `assistant/invocations` 404s) are pre-existing and tracked as OBS-203 (nit).

---

## Portal-side regression sweep

| # | Checkpoint | Result | Evidence |
|---|---|---|---|
| E.8 | Walk every portal route — zero JS errors / 500s | **PASS** | `/home`, `/projects`, `/invoices`, `/trust`, `/deadlines`, `/proposals`, `/profile`, `/settings/notifications`, `/requests`, `/activity` — all return 200. Console: 1 cosmetic error (favicon.ico 404). Zero functional JS errors. |
| E.9 | Portal terminology: Matter / Fee Note (not Project/Invoice leaks) | **PASS-WITH-NOTE** | Sidebar reads: Home, **Matters**, Trust, Deadlines, **Fee Notes**, Proposals, Requests, Activity. `/invoices` heading = "Fee Notes" (correct). `/trust` heading implicit. **NOTE**: `/projects` page heading reads "Your Projects" instead of "Your Matters" — cosmetic terminology leak in the portal projects page title. Sidebar label is correct ("Matters"). |
| E.10 | BLOCKER: Day 15 isolation re-run — Moroka IDs invisible to Sipho | **PASS** | **Frontend probes**: `/projects/43c3dd6b-...` returns "The requested resource was not found." `/requests/d114eae8-...` returns "The requested resource was not found." **API probes**: `GET /portal/api/projects/43c3dd6b-...` = 404. `GET /portal/api/info-requests/d114eae8-...` = 404. `GET /portal/api/trust/transactions/d52ff25d-...` = 404. **Home endpoint**: zero Moroka references (regex `/moroka\|EST-2026\|liquidation\|43c3dd6b\|d114eae8\|d52ff25d/i` = false). **Trust page**: R 0,00 balance (Sipho only, not aggregated R 25,000 Moroka). **E.10 isolation BLOCKER gate: PASS.** |
| E.11 | Trust reconciliation: Sipho R 0 (3 deposits + 1 payment), no Moroka leaks | **PASS** | Trust page shows 3 transactions: R 50,000 deposit (Day 10, running balance R 50,000) + R 20,000 deposit (Day 45, running R 70,000) + R 70,000 payment (Day 60, running R 0). Final balance R 0,00. Zero Moroka R 25,000 leaks. Currency ZAR throughout. |
| E.12 | Fee note + payment verified (INV-0001 PAID) | **PASS** | `/invoices` shows INV-0001 status PAID, R 1 437,50. Download button functional. `/home` "Recent fee notes" card shows INV-0001 R 1 437,50. |
| E.13 | Matter closure verified (RAF-2026-001 CLOSED with docs) | **PASS** | `/projects/c90832a4-...` shows status badge **CLOSED**. Documents tab: `matter-closure-letter-dlamini-v-road-accident-fund-2026-05-14.pdf` (1.6 KB) + `statement-of-account-dlamini-v-road-accident-fund-2026-05-14.pdf` (5.0 KB). All 9 RAF tasks CANCELLED (consistent with closure). |
| E.14 | Audit trail completeness | **PASS** | Activity page "Your actions" tab: 13 events (document downloads, info request submissions, uploads, fee note payment). "Firm actions" tab: 13 events (SoA generated, document generated, info request completed/accepted/sent/created). All are Sipho-related. Zero Moroka references in either tab. |
| E.15 | Console errors: zero functional JS errors across all routes | **PASS** | 1 cosmetic error (favicon.ico 404). 12 intentional isolation-probe 404s (Moroka entity IDs — confirms backend correctly denies access). Zero functional JS errors. |

---

## Portal terminology finding

| Page | Expected | Actual | Severity |
|---|---|---|---|
| `/projects` (heading) | "Your Matters" | "Your Projects" | nit (cosmetic) |

The portal sidebar correctly shows "Matters" but the `/projects` page heading hardcodes "Your Projects" instead of using the terminology mapping. This was noted in prior cycles and is a pre-existing cosmetic issue — not a blocker or new gap.

---

## Exit gate summary

| Gate | Description | Verdict |
|---|---|---|
| E.1 | Terminology sweep (firm sidebar) | **PASS** |
| E.2 | Legal modules visible | **PASS** |
| E.3 | No accounting/consulting nav leaks | **PASS** |
| E.4 | No tier/upgrade/billing upsell | **PASS** |
| E.5 | Settings > Billing flat | **PASS** |
| E.6 | Progressive disclosure | **PASS** |
| E.7 | Mailpit sweep (43 emails, 0 bounces) | **PASS** |
| E.8 | Portal routes all 200, zero JS errors | **PASS** |
| E.9 | Portal terminology | **PASS-WITH-NOTE** (heading nit) |
| E.10 | **Isolation BLOCKER** — Day 15 re-run | **PASS** |
| E.11 | Trust reconciliation | **PASS** |
| E.12 | Fee note + payment | **PASS** |
| E.13 | Matter closure + docs | **PASS** |
| E.14 | Audit trail completeness | **PASS** |
| E.15 | Console errors | **PASS** |

---

## Open gaps at Day 90

| Gap ID | Summary | Severity | Status | Notes |
|---|---|---|---|---|
| OBS-203 | `/api/assistant/invocations` returns 404 on matter detail page loads | nit | OPEN | Pre-existing. Non-critical AI assistant feature. |
| OBS-304 | Activity feed reads actor name not recipient on info request send | nit | OPEN | Cosmetic. |
| OBS-1002 | Trust deposit dialog combobox non-functional on standalone Transactions page | HIGH | OPEN | Workaround: use matter Trust tab. |
| OBS-3002 | `refreshPaymentLink()` does not publish `InvoiceSyncEvent` | LOW | OPEN | Non-blocking workaround available. |
| OBS-6001 | SoA document-ready email not sent after closure | LOW | OPEN | Closure letter email works. SoA accessible via portal Documents tab. |
| KYC | KYC adapter not wired | exempt | WONT_FIX-EXEMPT | Per mandate. |
| Payments | PayFast sandbox not wired (mock PSP used) | exempt | WONT_FIX-EXEMPT | Mock provider proven equivalent (Day 30 PASS). |

Zero BLOCKER or HIGH items with status OPEN that block demo readiness. OBS-1002 (HIGH) has a workaround and does not affect the lifecycle scenario path.

---

## Verdict

**Day 90 PASS.** All exit gates green. Lifecycle scenario complete end-to-end from Day 0 (org onboarding) through Day 90 (exit sweep). Isolation holds at both frontend and API levels. Trust accounting reconciles. Fee note + payment verified. Matter closure + SoA download confirmed. Audit trail complete with firm + portal events. No new gaps discovered.
