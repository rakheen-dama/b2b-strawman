# 644B: dark-mode long-tail walk + debt list (task 644.6)

Observed 2026-11-13 ~21:50 UTC on the **Keycloak dev stack** (`main` @ `21be78447`, the same restarted stack as `../matrix/README.md` § Provenance). Persona: owner Thandi. Theme **dark** (`kazi-theme=dark` + OS dark), viewport **1440×900**. Read-only: pages were opened only; nothing was clicked, submitted or saved, and no dialogs were opened.

## Method

- Script: `scripts/darkwalk.cjs`, committed byte-for-byte as executed. It uses `../matrix/scripts/common-kc.cjs` for login, theme and audit. The deviation is the same as the matrix: Playwright MCP's profile is held by the user's session, so a library-script headless Chromium is used (ruling R4).
- **Pages:** the 49 long-tail `page.tsx` files from the brief's route inventory, minus `/settings` (a 6-line `redirect()` to `/settings/firm`, not a page), minus the 5 settings family hosts. The hosts are walked as their 31 `?tab=` panels from `frontend/lib/settings-nav.ts` instead.
  - That gives 43 routes + 31 tabs = 74.
  - One route was not walkable: `/invoices/billing-runs/[id]`. The tenant has **0 billing runs** (`SELECT id FROM tenant_5039f2d497cf.billing_runs` → 0 rows) and inventing an id would only show a not-found page.
  - **73 pages walked**, every one HTTP 200 with `html.dark` = true (`walk.json`).
- **Dynamic ids:** real rows on the KC tenant, taken with read-only SELECTs (`walk.json` → `ids`):

  | Route | Record |
  |---|---|
  | DSR | `e1a3adfe…` (COMPLETED) |
  | Information request | `de4cf6b8…` (REQ-0001) |
  | Fee note | `76b170cc…` (INV-0003) |
  | Disbursement | `dcd7d88f…` (Sheriff fee) |
  | Report slug | `timesheet` |
  | Document template | `b15cc653…` (Standard Engagement Letter) |
  | Checklist | `56bafb4e…` (Generic Client Onboarding) |
  | Request template | `866c7b1d…` (Conveyancing Intake) |
  | Matter template | `af0d2286…` (Litigation RAF) |
  | Client ledger | `713a1f9a…` (Moroka Family Trust) |

- **Screenshots:** full page, clipped at 3200 px height, named `<route-slug>-1440-dark.png`. Every PNG was reviewed. All 73 went through contact sheets at reduced size (a review aid, not committed). The pages with audit flags were then opened at full resolution: `/my-work`, `/compliance`, `/settings/firm?tab=general`, `/matters/new`, `/trust-accounting/transactions`.
- **Automated audit** (`common-kc.cjs` `pageAudit()`, per page in `walk.json` → `audit`). It catches white-on-white and black-on-black that a downscaled screenshot can hide:
  - WCAG contrast of every visible text owner against its alpha-blended ancestor background (oklch/lab normalised via canvas); everything < 3:1 is listed.
  - Light surfaces (luminance ≥ 0.45) on the dark page.
  - Form fields whose border is < 1.3:1 against the surround **and** whose fill is < 1.15:1.
- **Severity rubric (§15.6.5):**
  - **illegible** = text or control not readable or findable.
  - **degraded** = readable but off-token (low-contrast muted text, faint borders, light blocks).
- **Not counted as findings** (by design or the token baseline):
  - The primary button and selected filter chip are inverted (white) in dark mode: shadcn `--primary` in dark.
  - Disabled controls at ~1.8–2.6:1.
  - The kit `border-input` token itself is 1.25:1 on `bg-card`. The in-scope Phase 90 surfaces use the same token (e.g. profitability date inputs), so ratios ≥ 1.25 on `border-input` / `outline` buttons are the baseline, not debt.
- **Console probe:** before the walk, a `console.warn` probe is captured (`walk.json` → `probeCaptured: true`), so the sink works. Console over all 73 pages (`console.json`): **0 errors, 0 hydration warnings, 2 warnings**, both `[tiptap warn]: Duplicate extension names found: ['link', 'underline']` on `/settings/documents/templates/[id]/edit` and `/settings/documents/templates/new` (pre-existing editor config, not dark-related).

## Summary

- **73 pages walked: 60 PASS, 13 FAIL** (1 illegible, 12 degraded, of which 1 is theme-independent).
- **Illegible: 1 page** (`/my-work`, rows D-1 and D-2).
- **Degraded: 12 pages**, rows D-3 to D-5:
  - 7 pages for faint native-select borders.
  - 4 pages for low-contrast muted `text-slate-400 dark:text-slate-600` text.
  - 1 for the Xero brand button (D-5). It is theme-independent (the same 2.38:1 in light): counted because the walk found it, not dark-specific.
- No white-card-on-dark-page, black-heading or unreadable-status-badge defect was found on any other long-tail page. The 637A–643C token migration plus the shared `packages/ui` primitives already carry most of the long tail.
- **Founder decision (644.6):** whether D-1 blocks enabling dark mode for design partners. `/my-work` is a daily-use page, and its "time today" figure and weekly total are invisible in dark.

## Debt list (§15.6.5 table)

**The canonical debt list is `tasks/phase90-ui-modernization.md` § Closure report C.5** (rows D-1…D-5, with a per-page route → element file:line → screenshot list for D-3 and D-4). It is not duplicated here, so the two cannot drift. This README keeps the method, the walk index below (the PASS/FAIL result for every page, with its screenshot) and the raw data (`walk.json`, `console.json`).

### Non-dark observations made during the walk (listed, not part of the §15.6.5 table)

- **L-1** `/my-work` @1440: the "My Tasks" / "Available Tasks" tables clip at the card's right edge. The column after Title (status/priority dots) is cut at x≈955 inside a 675 px column next to the Time Breakdown card. Layout density, both themes.
- **L-2** `/trust-accounting/transactions` @1440: the Transaction History table shows only Date / Reference / Type. A 150-character unbroken test reference (`G89-L-F36-REF150-hijabc…-END`, G89 QA data) forces the Reference column wide, so the Amount and Status columns sit beyond the card edge, and the Date wraps to 3 lines. Data-triggered layout weakness (no `break-all` / max width on Reference). Both themes.
- **L-3** The tenant logo is a white image, so it shows as a white block in Settings › General › Logo and as an empty outlined box in the portal header. Tenant data, not code.

## Walk index: every page

| # | Route | h1 / selected tab | HTTP | `html.dark` | Verdict | Screenshot |
|---|---|---|---|---|---|---|
| 1 | `/ai/reviews` | AI Reviews · tab: Pending Review | 200 | true | PASS | [png](ai-reviews-1440-dark.png) |
| 2 | `/compliance` | Compliance · tab: Overview | 200 | true | PASS · inverted primary pill (by design) | [png](compliance-1440-dark.png) |
| 3 | `/compliance/requests` | Data Requests | 200 | true | PASS | [png](compliance-requests-1440-dark.png) |
| 4 | `/compliance/requests/e1a3adfe-027b-4a74-9540-5bc1000f7180` | Khumalo Property Holdings | 200 | true | PASS | [png](compliance-requests-id-1440-dark.png) |
| 5 | `/conflict-check` | Conflict Check · tab: Run Check | 200 | true | FAIL — degraded (D-3) | [png](conflict-check-1440-dark.png) |
| 6 | `/diary` | Diary · tab: Week | 200 | true | PASS | [png](diary-1440-dark.png) |
| 7 | `/information-requests/de4cf6b8-18da-4f62-b57c-4030508a8b52` | REQ-0001 | 200 | true | PASS | [png](information-requests-id-1440-dark.png) |
| 8 | `/invoices` | Fee notes | 200 | true | PASS | [png](invoices-1440-dark.png) |
| 9 | `/invoices/76b170cc-164b-4f63-a88e-aa1bef24b2d6` | INV-0003 | 200 | true | PASS | [png](invoices-id-1440-dark.png) |
| 10 | `/invoices/billing-runs` | Fee runs | 200 | true | PASS | [png](invoices-billing-runs-1440-dark.png) |
| 11 | `/invoices/billing-runs/new` | New fee run | 200 | true | PASS | [png](invoices-billing-runs-new-1440-dark.png) |
| 12 | `/invoices/collections` | Debtors | 200 | true | PASS | [png](invoices-collections-1440-dark.png) |
| 13 | `/invoices/unbilled` | Unbilled work | 200 | true | PASS | [png](invoices-unbilled-1440-dark.png) |
| 14 | `/legal/adverse-parties` | Adverse Parties | 200 | true | FAIL — degraded (D-3) | [png](legal-adverse-parties-1440-dark.png) |
| 15 | `/legal/disbursements` | Disbursements | 200 | true | FAIL — degraded (D-3) | [png](legal-disbursements-1440-dark.png) |
| 16 | `/legal/disbursements/dcd7d88f-b7d5-40eb-ade9-9909a5a84f39` | Sheriff Fees | 200 | true | FAIL — degraded (D-4) | [png](legal-disbursements-id-1440-dark.png) |
| 17 | `/legal/tariffs` | Tariff Schedules | 200 | true | PASS | [png](legal-tariffs-1440-dark.png) |
| 18 | `/matters/new` | New Matter | 200 | true | FAIL — degraded (D-3) | [png](matters-new-1440-dark.png) |
| 19 | `/my-work` | My Work · tab: All | 200 | true | **FAIL — illegible** (D-1, D-2) · also non-dark note L-1 | [png](my-work-1440-dark.png) |
| 20 | `/my-work/timesheet` | Timesheet | 200 | true | PASS | [png](my-work-timesheet-1440-dark.png) |
| 21 | `/notifications` | Notifications | 200 | true | PASS | [png](notifications-1440-dark.png) |
| 22 | `/reports/timesheet` | Timesheet Report | 200 | true | PASS | [png](reports-timesheet-1440-dark.png) |
| 23 | `/settings/connections/ai/history` | Execution History | 200 | true | PASS | [png](settings-connections-ai-history-1440-dark.png) |
| 24 | `/settings/connections/automations/executions` | Execution Log | 200 | true | PASS | [png](settings-connections-automations-executions-1440-dark.png) |
| 25 | `/settings/connections/xero/sync-log` | Sync Log | 200 | true | PASS | [png](settings-connections-xero-sync-log-1440-dark.png) |
| 26 | `/settings/documents/templates/b15cc653-3e09-452b-ade4-be34363b0877/edit` | Standard Engagement Letter | 200 | true | PASS · dark OK; console: tiptap duplicate-extension warning | [png](settings-documents-templates-id-edit-1440-dark.png) |
| 27 | `/settings/documents/templates/new` | New Template | 200 | true | FAIL — degraded (D-3) · console: tiptap duplicate-extension warning | [png](settings-documents-templates-new-1440-dark.png) |
| 28 | `/settings/matters/checklists/56bafb4e-939f-43d6-ae33-1f6efa1c46a2` | Generic Client Onboarding | 200 | true | PASS | [png](settings-matters-checklists-id-1440-dark.png) |
| 29 | `/settings/matters/checklists/56bafb4e-939f-43d6-ae33-1f6efa1c46a2/edit` | Generic Client Onboarding | 200 | true | PASS | [png](settings-matters-checklists-id-edit-1440-dark.png) |
| 30 | `/settings/matters/checklists/new` | New Checklist Template | 200 | true | FAIL — degraded (D-3) | [png](settings-matters-checklists-new-1440-dark.png) |
| 31 | `/settings/matters/request-templates/866c7b1d-10f3-469c-9e94-0bc237812fe1` | Conveyancing Intake (SA) | 200 | true | PASS | [png](settings-matters-request-templates-id-1440-dark.png) |
| 32 | `/settings/matters/request-templates/new` | New Request Template | 200 | true | PASS | [png](settings-matters-request-templates-new-1440-dark.png) |
| 33 | `/settings/matters/templates/af0d2286-dea1-4112-a5ba-974ad99a690b` | Litigation (Road Accident Fund -- RAF) | 200 | true | FAIL — degraded (D-3) | [png](settings-matters-templates-id-1440-dark.png) |
| 34 | `/team` | Team · tab: Members | 200 | true | PASS | [png](team-1440-dark.png) |
| 35 | `/trust-accounting/client-ledgers` | Client Ledgers | 200 | true | PASS | [png](trust-accounting-client-ledgers-1440-dark.png) |
| 36 | `/trust-accounting/client-ledgers/713a1f9a-9cd4-406e-920e-507ac449c98a` | Moroka Family Trust | 200 | true | PASS | [png](trust-accounting-client-ledgers-id-1440-dark.png) |
| 37 | `/trust-accounting/interest` | Interest | 200 | true | PASS | [png](trust-accounting-interest-1440-dark.png) |
| 38 | `/trust-accounting/investments` | Investments | 200 | true | PASS | [png](trust-accounting-investments-1440-dark.png) |
| 39 | `/trust-accounting/reconciliation` | Reconciliation | 200 | true | PASS | [png](trust-accounting-reconciliation-1440-dark.png) |
| 40 | `/trust-accounting/reconciliation/new` | New Reconciliation | 200 | true | PASS | [png](trust-accounting-reconciliation-new-1440-dark.png) |
| 41 | `/trust-accounting/reports` | Trust Reports | 200 | true | PASS | [png](trust-accounting-reports-1440-dark.png) |
| 42 | `/trust-accounting/transactions` | Transactions | 200 | true | PASS · dark OK; non-dark note L-2 | [png](trust-accounting-transactions-1440-dark.png) |
| 43 | `/settings/firm?tab=general` | General · tab: General | 200 | true | FAIL — degraded (D-4) | [png](settings-firm--general-1440-dark.png) |
| 44 | `/settings/firm?tab=roles` | Roles & Permissions · tab: Roles | 200 | true | PASS | [png](settings-firm--roles-1440-dark.png) |
| 45 | `/settings/firm?tab=audit-trail` | Audit log · tab: Audit trail | 200 | true | PASS | [png](settings-firm--audit-trail-1440-dark.png) |
| 46 | `/settings/firm?tab=data-protection` | Data Protection · tab: Data protection | 200 | true | PASS | [png](settings-firm--data-protection-1440-dark.png) |
| 47 | `/settings/firm?tab=data-requests` | DSAR Requests · tab: Data requests | 200 | true | PASS | [png](settings-firm--data-requests-1440-dark.png) |
| 48 | `/settings/firm?tab=compliance` | Compliance · tab: Compliance | 200 | true | PASS | [png](settings-firm--compliance-1440-dark.png) |
| 49 | `/settings/firm?tab=features` | Features · tab: Features | 200 | true | PASS | [png](settings-firm--features-1440-dark.png) |
| 50 | `/settings/firm?tab=subscription` | Billing · tab: Subscription | 200 | true | PASS | [png](settings-firm--subscription-1440-dark.png) |
| 51 | `/settings/matters?tab=templates` | Matter Templates · tab: Templates | 200 | true | PASS | [png](settings-matters--templates-1440-dark.png) |
| 52 | `/settings/matters?tab=naming` | Matter Naming · tab: Naming | 200 | true | PASS | [png](settings-matters--naming-1440-dark.png) |
| 53 | `/settings/matters?tab=custom-fields` | Custom Fields · tab: Custom fields | 200 | true | PASS | [png](settings-matters--custom-fields-1440-dark.png) |
| 54 | `/settings/matters?tab=tags` | Tags · tab: Tags | 200 | true | PASS | [png](settings-matters--tags-1440-dark.png) |
| 55 | `/settings/matters?tab=checklists` | Checklists · tab: Checklists | 200 | true | FAIL — degraded (D-4) | [png](settings-matters--checklists-1440-dark.png) |
| 56 | `/settings/matters?tab=request-templates` | Request Templates · tab: Request templates | 200 | true | PASS | [png](settings-matters--request-templates-1440-dark.png) |
| 57 | `/settings/matters?tab=request-settings` | Request Settings · tab: Request settings | 200 | true | PASS | [png](settings-matters--request-settings-1440-dark.png) |
| 58 | `/settings/documents?tab=templates` | Templates · tab: Templates | 200 | true | PASS | [png](settings-documents--templates-1440-dark.png) |
| 59 | `/settings/documents?tab=clauses` | Clause Library · tab: Clauses | 200 | true | PASS | [png](settings-documents--clauses-1440-dark.png) |
| 60 | `/settings/documents?tab=acceptance` | Document Acceptance · tab: Acceptance | 200 | true | PASS | [png](settings-documents--acceptance-1440-dark.png) |
| 61 | `/settings/documents?tab=email` | Email · tab: Email | 200 | true | PASS | [png](settings-documents--email-1440-dark.png) |
| 62 | `/settings/fees?tab=tariffs` | Rates & Currency · tab: Rates | 200 | true | FAIL — degraded (D-4) | [png](settings-fees--tariffs-1440-dark.png) |
| 63 | `/settings/fees?tab=tax` | Tax Settings · tab: Tax | 200 | true | PASS | [png](settings-fees--tax-1440-dark.png) |
| 64 | `/settings/fees?tab=time-recording` | Time Tracking · tab: Time recording | 200 | true | PASS | [png](settings-fees--time-recording-1440-dark.png) |
| 65 | `/settings/fees?tab=fee-runs` | Batch Billing · tab: Fee runs | 200 | true | PASS | [png](settings-fees--fee-runs-1440-dark.png) |
| 66 | `/settings/fees?tab=collections` | Collections · tab: Collections | 200 | true | PASS | [png](settings-fees--collections-1440-dark.png) |
| 67 | `/settings/fees?tab=trust-accounting` | Trust Accounting Settings · tab: Trust accounting | 200 | true | PASS | [png](settings-fees--trust-accounting-1440-dark.png) |
| 68 | `/settings/connections?tab=xero` | Integrations · tab: Integrations | 200 | true | FAIL — degraded (D-5, theme-independent) | [png](settings-connections--xero-1440-dark.png) |
| 69 | `/settings/connections?tab=mcp` | Claude / MCP Connector · tab: MCP | 200 | true | PASS | [png](settings-connections--mcp-1440-dark.png) |
| 70 | `/settings/connections?tab=ai` | Set Up AI · tab: AI | 200 | true | PASS | [png](settings-connections--ai-1440-dark.png) |
| 71 | `/settings/connections?tab=notifications` | Notification Preferences · tab: Notifications | 200 | true | PASS | [png](settings-connections--notifications-1440-dark.png) |
| 72 | `/settings/connections?tab=packs` | Packs · tab: Packs | 200 | true | PASS | [png](settings-connections--packs-1440-dark.png) |
| 73 | `/settings/connections?tab=automations` | Automations · tab: Automations | 200 | true | PASS | [png](settings-connections--automations-1440-dark.png) |

Not walked: `/settings` (redirect to `/settings/firm`, covered by the `?tab=general` row); `/invoices/billing-runs/[id]` (0 billing runs on the tenant; nothing real to open).

## Remaining in-scope `dark:` and shared-component dark debt (cross-reference)

This walk did not re-audit the 8 in-scope pages. Seven are the matrix surfaces, all OK in dark (`../matrix/README.md`). The eighth, `/reports`, was observed in dark by 643C (`../643C/after-fix/reports-1440-dark.png`, `reports-1024-dark.png`). The shared-component dark debt carried from 640B (`packages/ui` `Card` border pair, `Badge` status variants, `Skeleton`, `health-badge.tsx`, `completion-progress-bar.tsx`, `severity-pill.tsx`, `todays-agenda.tsx`/`TodaysDiaryList`) is unchanged. D-1/D-2 are the visible symptom of the `todays-agenda.tsx` part of that list, now observed on `/my-work`.

## Files

- `walk.json`: per page: route, concrete URL, HTTP status, h1, selected tab, `dark`, audit (low-contrast text, light surfaces, faint fields, scrollers), plus `ids` and `probeCaptured`. Compact JSON.
- `console.json`: probe + the 2 tiptap warnings.
- `*-1440-dark.png`: 73 screenshots.
- `scripts/darkwalk.cjs`: the walk script.
