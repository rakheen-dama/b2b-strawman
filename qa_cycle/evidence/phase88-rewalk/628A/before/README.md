# Slice 628A — Task 628.1 reproduction (BEFORE)

**Verdict: REPRODUCED** — E.8 / G-A2 / G-D16: legacy `Project` / `Customer` / `Invoice` terminology is visible across the firm app at 1440 px (111 distinct visible strings (incl. 1 native confirm); 75 owned by 628A frontend literals). **IntegrationCard PRO badge: PRESENT** on the "AI Assistant" card (Settings > Connections > Xero tab), with no upsell block and no disabled controls.

- Observed: 2026-10-28, E2E mock-auth stack on :3001 (Alice / owner, org `e2e-test-org`), built by Slice 627K from main `e4bc376d6`. Viewport 1440x900. Observe-only. No data was changed: the Void confirm was dismissed and every dialog was closed with Escape. The fee note stayed `Sent`, the matter stayed `Active`, the client stayed `Onboarding`, and the newest audit event (19:53) predates this session.
- Method: a Playwright init script (`window.__scan628`) walks visible text nodes plus `placeholder` / `aria-label` / `title` / `alt` with the case-insensitive regex `\b(projects?|customers?|invoices?|invoic(ed|ing))\b`. Each page and tab was visited, every hit stored, a full-page screenshot taken when there was a hit, then results deduplicated by (attribute, text). Raw per-visit data is in `scan-raw.json` (149 page/tab/dialog records).

## Grep before-number (worktree root, `e4bc376d6`)

```
grep -rnE '\b(Projects?|Customers?|Invoices?)\b' frontend/app frontend/components frontend/lib | wc -l                 -> 223
grep -rnE '\b(Projects?|Customers?|Invoices?)\b' frontend/app frontend/components frontend/lib frontend/hooks | wc -l  -> 223
```
Raw output: `grep-raw.txt`, `grep-raw-with-hooks.txt`. Counts: `grep-count.txt`. `frontend/hooks` adds 0. Note that this grep is case-sensitive and capitalised-only, so it misses lowercase copy such as "this project" and "for this customer". Many of the 223 lines are identifiers or comments (e.g. `CreateCustomerResult`), not visible copy.

## Visible hits by owner slice (distinct strings, see `hits.md`)

| Owner | Distinct hits |
|---|---|
| 628A: frontend literals (app/components/lib/hooks) | 75 (74 DOM + 1 native `confirm`) |
| 628B: breadcrumbs / nav / portal | 2 (report breadcrumb shows the raw slug `invoice-aging` / `project-profitability`) |
| 628C: packs / report definitions / template content | 24 |
| 628D: backend audit / registry labels | 10 (8 raw audit event keys + 2 VerticalModuleRegistry descriptions) |
| **Total** | **111** |

Clean (0 hits): sidebar fully expanded (`00-sidebar-expanded.png`), Home, Diary (Day/Week/Month/List), Matters list, New matter, Clients list, conflict check, adverse parties, AI reviews, compliance requests, notifications, team, timesheet, fee-notes list, unbilled, fee runs and new fee run, disbursements, tariffs, all 9 trust-accounting pages, matter Overview/Details/Team/Documents/Correspondence/Generated/Disbursements/Fee estimate/Statements/Trust/Clients/Requests/Client comments/Court dates/Activity/Audit, and settings General/Roles/Data requests/Subscription/Matter templates/Naming(*)/Request templates/Request settings/Clauses/Acceptance/Email/Time recording/Trust accounting/MCP/AI/Packs. (*) Naming has hits; see `hits.md`.

Dialogs opened (Escape, never saved): New Client (0), New fee note (`Search customers...`), Fee note lifecycle help (0), Record Payment (0), fee-note Generate Document (`Invoice Cover Letter`), Fee note numbering help (0), New Task (`Add a new task to this project.`), matter Generate Document (`Project Summary Report`), Generate Statement of Account (0), matter More actions (0), Close Matter (0), Complete Matter (0), Activate Customer (0), client More actions (0), Client actions (0), command palette (0). The Void native confirm reads "Are you sure you want to void this invoice?" (`components/invoices/use-invoice-detail.ts:226`). **Not opened:** Log time. The task detail sheet from My work exposes no "Log time" button for the seeded task, and the sheet's "Time Entries" tab click timed out. Fee-note preview render (`/api/invoices/{id}/preview`): the only hit is "constitutes a tax invoice as defined in section 20 of the VAT Act". That is a statutory term, so it is recorded as KEEP.

Most-affected surfaces: client detail (header action `Activate Customer` on every tab, `Projects linked`, `Create Invoice`, Customer Rate Overrides, `No customer documents yet`), matter empty states (`...on this project`), Settings > Notifications (Projects / Invoice * preference labels), Settings > Automations (trigger badges + pack rule names), Settings > Documents (raw `PROJECT` / `INVOICE` / `CUSTOMER` entity badges + pack names), Settings > Data protection (raw `CUSTOMER` record type + aria-labels), Reports (definition names + `Select a customer...` / `Select a project...` pickers + slug breadcrumb), audit trail (raw `invoice.sent` / `project.created` / `customer.created` keys as mono sub-lines under already-legal labels).

## IntegrationCard finding (E.8 PRO badge)

- Screenshots: `91-settings-connections-xero.png` (full tab), `91b-integrationcard-ai-pro-badge.png` (card crop).
- Observed: the "AI Assistant" card shows the badges **`PRO`** + `Not Configured`. There is **no** "AI Assistant requires the PRO plan / Upgrade to Pro" block and **no** disabled controls (provider select enabled). Other cards (Accounting, Document Signing, Email Delivery, Payment Gateway, KYC, MCP, Xero) have no PRO badge.
- Why: `app/(app)/org/[slug]/settings/connections/_tabs/xero.tsx:83-112` derives `tier` from `GET /api/billing/subscription`. A status of `ACTIVE`/`TRIALING`/`PENDING_CANCELLATION` gives `"DEDICATED"`; anything else gives `"STARTER"`. This org's subscription is `Trial` (`73b-settings-firm-subscription-status.png`), so `tier = "DEDICATED"`. `components/integrations/IntegrationCard.tsx:182` renders `{domain === "AI" && tier !== "PRO" && <Badge variant="pro">PRO</Badge>}`. `tier` can never be `"PRO"`, so the badge renders unconditionally for the AI card. `isStarter` (`:84`, `tier === "STARTER"`) is false here, so the upsell block (`:196-207`, which links to `/settings/billing`, a route that now redirects to Firm > Subscription) and the `disabled={... || isStarter}` controls are inactive. A firm on a non-active billing status would also see the upsell and disabled controls.

## Screenshots

-  00-sidebar-expanded.png
-  02-my-work-tab1-all.png
-  02-my-work.png
-  11-compliance.png
-  16-client-detail-tab1-details.png
-  16-client-detail.png
-  18-feenote-detail.png
-  20-collections.png
-  25-reports.png
-  26-report-profitability.png
-  27-report-invoice-aging.png
-  28-report-project-profitability.png
-  29-report-timesheet.png
-  31-matter-fields.png
-  34-matter-tasks.png
-  38-matter-time.png
-  41-matter-rates.png
-  42-matter-financials.png
-  48-matter-adverse-parties.png
-  52-client-details.png
-  53-client-fields.png
-  54-client-tags.png
-  55-client-overview.png
-  56-client-matters.png
-  57-client-documents.png
-  58-client-generated.png
-  59-client-invoices.png
-  60-client-rates.png
-  61-client-financials.png
-  62-client-trust.png
-  63-client-onboarding.png
-  64-client-requests.png
-  65-client-audit.png
-  68-settings-firm-audit-trail.png
-  69-settings-firm-data-protection.png
-  71-settings-firm-compliance.png
-  72-settings-firm-features.png
-  73b-settings-firm-subscription-status.png
-  75-settings-matters-naming.png
-  76-settings-matters-custom-fields.png
-  77-settings-matters-tags.png
-  78-settings-matters-checklists.png
-  81-settings-documents-templates.png
-  85-settings-fees-tariffs.png
-  86-settings-fees-tax.png
-  88-settings-fees-fee-runs.png
-  89-settings-fees-collections.png
-  91-settings-connections-xero.png
-  91b-integrationcard-ai-pro-badge.png
-  94-settings-connections-notifications.png
-  96-settings-connections-automations.png
-  107-settings-audit-log-legacy.png
-  108-doc-template-edit.png
-  110-matter-template-detail.png
-  111-checklist-detail.png
-  112-checklist-new.png
-  115-xero-sync-log.png
-  118-client-other-1.png
-  120-matter-other-1-time.png
-  121-matter-other-1-financials.png
-  122-matter-other-1-tasks.png
-  124-dlg-new-feenote.png
-  127-dlg-feenote-generate-doc.png
-  129-dlg-new-task.png
-  130-dlg-matter-generate-doc.png
-  139-task-detail-sheet.png
-  141-feenote-preview-render.png
