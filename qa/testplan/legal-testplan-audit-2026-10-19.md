# Legal-ZA QA scenario audit (read-only) — 2026-10-19

> Copied verbatim from the dev-agent handover scratchpad (ephemeral) so the evidence survives. Governing spec: `tasks/qa-scenario-amendments-post86.md`. The audit was produced against `main` @ `4cfa0917e` and edits no repo files.

---

# Legal-ZA QA scenario audit vs current product (main = 4cfa0917e, 2026-10-19)

Read-only audit. No repo files edited. Evidence paths are relative to repo root.

Classes: **A** = deleted-feature step, pure deletion (authorised by Phase 86 "remove deleted-feature steps only"); **A-trim** = only a deleted-feature *fragment* inside an otherwise-valid step (remove the fragment; arguably inside the same authorisation, flag to user); **B** = needs rewording / replacement or a downstream dependency broke (needs user sign-off) — tagged **B-86** (caused by Phase 86) or **B-pre** (drift that predates Phase 86); **C** = valid step blocked by a tracked product bug (`FIXME(depth-ii:<slug>)`); **D** = stale but harmless wording / ids / labels.

## Summary

| File | A | A-trim | B-86 | B-pre | C | D | Runnable as-is? |
|---|---|---|---|---|---|---|---|
| `legal-za-90day-keycloak.md` | 21 lines (wow #2, 0.56a–e, Day-0 pipeline checkpoint, 1.12–1.24 + nav note, 90.7) | 3 (0.67, 3.10, E.2) | 2 (2.1, 68.1) | 8 (0.47, 0.48, 0.57–0.58, 2.3–2.7, 8.3, 9.4, 12.1, 80.5) | 9 groups | ~21 | **No** — Day 0 Phase H2 and Day 1 pipeline routes are gone (`/settings/pipeline`, `/pipeline` 404); Day 2.1 then asserts a state only the deleted deal-win produced. |
| `legal-za-full-lifecycle-keycloak.md` | 0 (Epic 597 #1566 already removed them) | 5 (selector ref L35, 3.5, 75.5, E.2, E.8) | 1 (client activation gap before Day 28) + 1 optional (engagement letter via ADR-336) | 3 (R71k trust amounts, unscripted PAY/2026/001, vacuous isolation probes) | 2 (60.23, 85.3) | ~14 | **Mostly, not cleanly** — runs to Day 21; Day 28 billing run needs Sipho ACTIVE, which the deleted Day-10 deal-win used to set up (PROSPECT→ONBOARDING). The last cycle "activated Sipho" off-script; today that path also needs a manual Start Onboarding + 8-item checklist. |

Context found during the audit:
- The Phase 86 scenario sweep **missed the 90-day file**. `qa/testplan/MIGRATION-NOTES.md:304-310` ("Amended plans") and `tasks/phase86-subtraction.md` §7.8(b) list the full-lifecycle, portal, admin-audit and AI-core plans, but not `legal-za-90day-keycloak.md`.
- **ADR-344 (Accepted, landed in HEAD 4cfa0917e)** already covers part of this. It authorises Phase 87 IA PRs to delete "the 12 stale Pipeline steps" in the 90-day doc (annotated citing Epic 596 / ADR-336) and to change URL/label/nav wording. Behaviour steps stay frozen. It does **not** cover 2.1, 68.1, the activation gap, or any B-pre item. Note: ADR-344 counts 12 Pipeline references, but the 90-day doc has more: 1.12–1.24 alone is 13 steps, plus 0.56a–e and wow #2.
- Phase 87 (architecture/phase87-legal-native-shell.md, ADR-337/339/340/341) will rename `/projects`→`/matters`, `/customers`→`/clients`, `/court-calendar`→`/diary` and remove the matter Expenses tab. None of these is live yet, so the steps below are judged against today's routes.

---

## File 1 — `qa/testplan/demos/legal-za-90day-keycloak.md`

| Day/step | Line | References | Evidence (file:line) | Class | Proposed change |
|---|---|---|---|---|---|
| Wow #2 | 33 | Pipeline board, deal → Won, win-loop | No `/pipeline` route; nav Clients group has no Pipeline (`frontend/lib/nav-items.ts:117-149`); 0 hits for `/pipeline`, `DEAL_WON` in frontend/backend main | A | Delete; renumber wow list (7→6). |
| 0.17 | 83 | `GET /api/orgs/{slug}/profile` | `provisioning/OrgController.java:14,24` (POST only); profile endpoints are `/api/profiles`, `/api/modules` (`verticals/VerticalProfileController.java:24,31`) | D | Rely on the "approval card" fallback, or drop the curl. |
| 0.26 | 102 | Settings > Team `/settings/team` | No `settings/team` dir; Team is top-level `/org/{slug}/team` (`nav-items.ts:254-266`) | D | "Team (sidebar Team group)" (same as full-lifecycle OBS-002). |
| 0.47 | 156 | `legal-za-customer` fields `client_type, physical_address, registration_number, beneficial_owners` | Pack has only `id_passport_number, postal_address, preferred_correspondence, referred_by` (`backend/src/main/resources/field-packs/legal-za-customer.json`); unchanged since Apr (c148d6987) | B-pre | Re-derive the expected field list (the others are native client fields now). |
| 0.48 | 157 | `legal-za-project` fields incl. `matter_type` | Pack: `case_number, court_name, opposing_party, opposing_attorney, advocate_name, date_of_instruction, estimated_value` (`field-packs/legal-za-project.json`); matter type = native Work Type | B-pre | Drop `matter_type` from the expected list. |
| 0.51 | 160 | `matter_type` inline on New Matter | Native "Work Type" input + suggestions (`components/projects/create-project-dialog.tsx:265-272`) | D | "Work Type input (suggests LITIGATION/CONVEYANCING/ESTATES/COLLECTIONS/COMMERCIAL)". |
| 0.53–0.54 | 165-166 | Settings > Templates → 4 matter templates | Matter templates live at Settings > Project Templates (`nav-items.ts` SETTINGS_ITEMS `/settings/project-templates`); legal-za now ships **6** (adds RAF and Property Transfer) (`project-template-packs/legal-za.json`) | D | Point at Matter Templates; "6 templates". |
| 0.56 | 168 | "legal profile has no automation pack" | `automation-templates/legal-za.json` has 4 templates; page is enable/disable only after the builder was deleted (#1576) (`nav-items.ts` SETTINGS_ITEMS "Automations … Enable or disable") | D | Expect the 4 legal templates/rules with toggles; no create button. |
| 0.56a–0.56e | 172-176 | Settings > Pipeline, stage pack, Pipeline nav, board | Deleted by Epic 596 (#1565); no `settings/pipeline` dir | **A** | Delete Phase H2 (ADR-344 already authorises this). |
| 0.57–0.58 | 180-181 | Settings > Modules: 4 legal modules enabled | Page is Settings > Features and lists **horizontal modules only** (`settings/ModuleSettingsController.java:17,32`); legal modules are fixed by profile (`vertical-profiles/legal-za.json:5`) | B-pre | Verify via sidebar (0.59) or `GET /api/modules`; Features shows bulk_billing/information_requests only. |
| 0.67 | 196 | Billing: member count, monthly amount, PayFast self-service | Page now shows only a status badge, MethodBadge and a "Managed Account" card (`settings/billing/page.tsx:57-96`, changed by #1574) | A-trim | Drop "member count, monthly amount, PayFast"; keep status badge + managed notice. |
| Day-0 checkpoint | 206 | Pipeline stage pack verified | as 0.56a | **A** | Delete line. |
| Day 1 note + 1.12–1.24 | 231-249 | New Enquiry, deal drag, Work > Deals tab, deal detail, win-loop, "You won a deal" email, Sales Pipeline widget, `deal.*` audit, screenshot | Customer tab groups have no Deals (`lib/constants/customer-tab-groups.ts:37-45`); 0 hits for `customer-deals-tab`, `deal-status-badge`, `pipeline-summary-widget` | **A** | Delete the whole "Day 1 (cont.) — Pipeline" section (ADR-344 scope). |
| 2.1 | 253 | "already ONBOARDING — auto-nudged by Day 1 deal win" | Downstream of the deleted 1.20. Manual action exists: PROSPECT → "Start Onboarding" (`components/customers/client-header-card.tsx:35-36`) | **B-86** | Replace with "click **Start Onboarding** → badge ONBOARDING, checklist instantiated". |
| 2.2 | 254 | pack `fica-kyc-za` | Actual pack `legal-za-individual-onboarding` (`compliance-packs/legal-za-individual-onboarding/pack.json`) | D | Rename. |
| 2.3–2.7 | 255-259 | items "Certified ID Copy / Proof of Address / Source of Funds"; marking 3 → 100% → auto ACTIVE | Pack has **9 items, 8 required** (Proof of Identity, Proof of Address, Beneficial Ownership Declaration, Source of Funds Declaration, Engagement Letter Signed, Conflict Check Performed, FICA Risk Assessment, Sanctions Screening); activation is blocked until all are complete (`compliance/CustomerLifecycleService.java:126-127,351`) | B-pre (+C) | Mark all 8 required items (the depth-ii e2e already does 9). Also C `checklist-stale-after-complete` (intermittent; reload works). |
| 3.3 | 267 | `matter_type = Litigation` | Work type is inherited from the template and normalised to upper-case **LITIGATION** (#1580/#1581) | D | Expect `LITIGATION`. |
| 3.8 | 272 | breadcrumb "Clients > Sipho Dlamini > Matter > …" | Breadcrumbs follow the URL path (`components/breadcrumbs.tsx:11,50`), so a matter reads "Matters > Matter …"; pageless segments are not links (#1579) | D | Expect path-based crumb. |
| 3.10 | 274 | Work group: **five** sub-tabs incl. Staffing | Work = Tasks, Documents, Correspondence, Generated Docs (`lib/constants/project-tab-groups.ts:37-46`); Staffing removed by #1570 | A-trim | "four sub-tabs", drop Staffing. |
| 4.1–4.3 | 278-280 | Engagement letter via Generate Document | Still valid: `engagement-letter-litigation/-general` in `template-packs/legal-za/pack.json` | ok (optional B) | Optional: add "Send for Acceptance" (ADR-336 replacement path), which needs sign-off as an addition. |
| 5.5 | 296 | "Legal → Disbursements" nav | Route exists (`app/(app)/org/[slug]/legal/disbursements/page.tsx`); no nav item (`nav-items.ts`, none ever per git -S) | D | "direct URL /legal/disbursements". |
| 5.9–5.10 | 300-301 | Trust-linked deeds-office disbursement, Thandi approves | `FIXME(depth-ii:trust-link-payment-type)` `frontend/e2e/tests/legal-depth-ii/day-05-disbursements.spec.ts:268,321` | **C** | Keep; blocked until the fix lands. |
| 5.12 | 303 | "Unbilled Disbursements: R2,000.00" summary | `FIXME(depth-ii:matter-unbilled-disbursements-summary)` `day-05-disbursements.spec.ts:454` | **C** | Keep. |
| 5.14 | 305 | screenshot incl. unbilled summary | as 5.12 | C | Keep. |
| 8.3 | 326 | `beneficial_owners` custom field | Not in `legal-za-customer.json`; the trust checklist has "Beneficial Ownership Declaration" (`compliance-packs/legal-za-trust-onboarding/pack.json`) | B-pre | Record beneficial owners via the checklist item. |
| 8.5, 14.2 | 328, 354 | TRUST FICA items; Khumalo FICA | Trust pack has 12 items (names differ: Trust Deed, Letters of Authority, …); `checklist-stale-after-complete` (`day-14-conveyancing.spec.ts:124`) | D + C | Align names; C is intermittent. |
| 9.3 | 335 | `matter_type = Estates` | Normalised `ESTATES` (#1581) | D | Expect `ESTATES`. |
| 9.4 | 336 | Estate tasks "L&D documents", "Master … appointment", "Advertisement — Section 29" … | Actual tasks: Obtain death certificate & will; Report estate to Master; Obtain Letters of Executorship; Advertise for creditors; Inventory of assets & liabilities; … (`project-template-packs/legal-za.json`, Deceased Estate) | B-pre | Re-derive the expected task list (a literal check fails). |
| 12.1 | 344 | mark "L&D documents" done | No such task (cascade from 9.4) | B-pre | Use "Obtain death certificate & will". |
| 14.3–14.6 | 355-358 | Property Transfer → CONVEYANCING + 10 fields | **Now achievable**: template work type inherited (#1580) and field groups matched case-insensitively (#1581); 10 fields in `field-packs/conveyancing-za-project.json` | ok (expected value fixed) | None (was failing before #1580). |
| 14.8 | 360 | OTP contains conveyancing clauses | `FIXME(depth-ii:otp-clause-associations)` `day-14-conveyancing.spec.ts:324` | **C** | Keep. |
| 14.9 | 361 | "button only shown because acceptanceEligible: true" | Send for Acceptance is shown for any saved doc with a customer (`components/templates/GenerateDocumentDialog.tsx:320-326`, `GeneratedDocumentsList.tsx:330-340`) | D | Drop the causal claim. |
| 14.12 | 364 | public `/accept/[token]` | Acceptance email → `portalBaseUrl + "/accept/"` (`acceptance/AcceptanceNotificationService.java:87`), i.e. portal :3002, but the stack line (L5) has no portal | D | Add portal :3002 to the stack line. |
| 22.4 | 392 | `matter_type = Road Accident Fund` | Free text → `ROAD_ACCIDENT_FUND` (#1581); the RAF template "Litigation (Road Accident Fund -- RAF)" carries `LITIGATION` | D (value changed by fix) | Use the RAF template; expect `LITIGATION`. |
| 23.1 | 397 | adverse party type "Government" | Types: Natural Person/Company/Trust/CC/Partnership/Other (`components/legal/adverse-party-dialog.tsx:33-40`) | D | "Other". |
| 30.2–30.4, 30.7 | 433-438 | SoA dialog preview + screenshot | `FIXME(depth-ii:soa-preview-unbounded-scale)` `day-30-45-soa-writeoff.spec.ts:205` (save works; layout runs away) | **C** | Keep. |
| 32.1 | 410 | LSSA tariff line "to matter" | Tariff lines exist on fee-note drafts (`invoice/InvoiceLineType.java:9`), not on matters | D | Move to the fee-note draft (45.3 already covers it). |
| 36.1 | 444 | Matter → "Billing tab" | No such matter tab (`project-tab-groups.ts:47-60`); use Invoices / client Finance > Invoices | D | Reword path. |
| 38.1 | 453 | Approve → SENT | Approve → APPROVED, then Send (`components/invoices/invoice-header-actions.tsx:74-85`) | D | Two clicks. |
| 45.5–45.11 | 469-475 | Write Off disbursement + audit + exclusion | `FIXME(depth-ii:no-write-off-action)` `day-30-45-soa-writeoff.spec.ts:311,338,359` (API exists, no UI) | **C** | Keep (45.9–45.11 are downstream). |
| 68.1–68.2 | 497-498 | "generate magic link for Sipho" → portal | No firm-side link generator; the legacy in-app `/portal` was deleted (#1566); portal contact is auto-created on client create (`portal/PortalContactAutoProvisioner.java:14-16,44`); login at portal `:3002/login` (`portal/app/login/page.tsx`) | **B-86** | "Sipho requests a login link at :3002/login → Mailpit → `/auth/exchange`". Add portal to the stack. |
| 75.2 | 510 | `sidebar-lifecycle-action` | Close Matter is in the header card `header-lifecycle-actions` / `close-matter-btn` (`components/projects/matter-header-card.tsx:90-97`, `matter-closure-action.tsx:38`); MatterSidebar is not rendered | D | Update selector (MIGRATION-NOTES already says header). |
| 75.10 | 518 | retention row end_date +5y | `FIXME(depth-ii:retention-period-unconfigurable)` `day-75-85-closure.spec.ts:294` | **C** | Keep. |
| 80.3 | 529 | utilization on Reports | Capacity utilization deleted (#1570); time-based "Team utilization" remains on **Profitability** (`profitability/page.tsx:39-58`, `report/ReportController.java:49`) | D | Point at Profitability. |
| 80.5 | 531 | My Work shows upcoming court events | `upcomingDeadlines` = tasks (`dashboard/DashboardService.java:501-510`) | B-pre | Drop "court events" or check Court Calendar. |
| 85.2–85.3, 85.17 | 536-537, 561 | audit filter by actor name / entity | Audit log filters by Actor **ID**, event type, entity type (`settings/audit-log/audit-log-client.tsx:193-195`) | D | Reword. |
| 85.7–85.8 | 548-549 | Bob routes to Step 3, no override toggle | `FIXME(depth-ii:admin-offered-closure-override)` `day-75-85-closure.spec.ts:316` | **C** | Keep. |
| 90.7 | 578 | Pipeline RBAC negative + DEAL-0001 | Deleted (#1565) | **A** | Delete. |
| E.2 | 585 | "All 7 wow moments" | 6 after wow #2 goes | A-trim | "6". |

Steps checked and still valid (examples): request-access with Industry "Legal Services" (`lib/access-request-data.ts:14`); `/platform-admin/access-requests`; Conflict Check page; Court Calendar create (`components/legal/create-court-date-dialog.tsx`); trust account GENERAL/SECTION_86 + LPFF (`TrustAccountType.java`, `components/trust/LpffRateDialog.tsx:138`); 9 closure gates (`verticals/legal/closure/gates/*`, 9 files); closure testids (`components/legal/matter-closure-dialog.tsx:181-443`); profitability; budget; comments with @mentions (`notification/NotificationService.java:750`).

---

## File 2 — `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md`

| Day/step | Line | References | Evidence (file:line) | Class | Proposed change |
|---|---|---|---|---|---|
| Selector ref | 35 | Work: … `staffing` | `project-tab-groups.ts:37-46` (#1570) | A-trim | Drop `staffing`. |
| 0.16 | 164 | `/api/orgs/{slug}/profile` | as 90day 0.17 | D | Use the approval card. |
| 0.G | 128 | PayFast sandbox in OrgIntegration | **Tenant** gateway retained (`integration/payment/PayFastPaymentGateway.java`, `MockPaymentIntegrationSeeder.java`); #1574 removed only the platform subscription path | ok | None. |
| 0.H | 129 | `/api/packs/status`; "packs" court-calendar/trust-accounting/… | Endpoints are `/api/packs/catalog`, `/installed` (`packs/PackCatalogController.java:30,38`); those names are **modules** (`legal-za.json:5`) | D | Use `/api/packs/installed` + `/api/modules`. |
| 1.3 | 206 | "from `legal-za` rate pack" | LSSA tariffs come from `tariff-seed/lssa-2024-2025-hc-pp.json`; legal never had a rate pack (604C note) | D | Reword source (R780 value still valid, seed line 12). |
| 2.3 | 232-239 | First Name / Last Name | Single "Name" field (`components/customers/create-customer-dialog.tsx:316`) | D | "Name = Sipho Dlamini". |
| 3.3 | 264 | Matter type "Litigation — Road Accident Fund" | Template "Litigation (Road Accident Fund -- RAF)" carries `LITIGATION` (#1580/#1581) | D (value changed by fix) | Expect `LITIGATION`. |
| 3.5 | 269 | Work group incl. Staffing; "Members → Work > Staffing" | `project-tab-groups.ts:37-46` | A-trim | Drop Staffing clauses. |
| 4.2 | 298 | magic link → `/accept/[token]` | Info-request link is `/auth/exchange?token=` (`informationrequest/InformationRequestEmailService.java:63`) | D | Fix URL. |
| **Day 10 / pre-Day 28 (gap)** | 353-361, 586-593, 620 | Sipho must be **ACTIVE** for the billing run | Removed 10.2a–d (deal win) was the only scripted PROSPECT→ONBOARDING step. Billing run selects `lifecycle_status='ACTIVE'` only (`billingrun/BillingRunSelectionService.java:533-535`); PROSPECT→ACTIVE is not allowed (`customer/LifecycleStatus.java:18-19`); ONBOARDING→ACTIVE requires a complete checklist (`CustomerLifecycleService.java:126-127,351`; 8 required items). The last cycle "activated Sipho" off-script (`qa_cycle/status.md:97`) | **B-86** | Add explicit steps (e.g. Day 5/10): Start Onboarding → complete `legal-za-individual-onboarding` → Activate Customer. Needs sign-off (an addition). |
| Engagement letter (gap) | 345-349, 686 | Day 7/8 removed; 45.3 says "per engagement letter"; checklist item "Engagement Letter Signed" | ADR-336 replacement = GeneratedDocument from `engagement-letter-*` + Phase 28 acceptance (`EngagementLetterWithoutProposalIntegrationTest`), not exercised anywhere | B-86 (optional) | Recommend a Day 7 replacement: Generate Engagement Letter — Litigation → Send for Acceptance → Sipho accepts at portal `/accept/[token]`. Sign-off required. |
| 14.6 | 434 | Matter type "Estates — Deceased" | `ESTATES` (#1581) | D | Expect `ESTATES`. |
| 14.8 | 440 | template "Liquidation and Distribution Account docs" | Pack name "Liquidation and Distribution Account Pack" (`request-packs/legal-za-liquidation-distribution.json`) | D | Rename. |
| 15.10–15.12 | 480-482 | portal URLs `/info-requests/[id]`, `/documents/[id]`, `/trust/transactions/[id]` | Portal routes are `/requests/[id]`, `/trust/[matterId]`, `/projects/[id]` (portal/app page list); these probes 404 **vacuously** | B-pre | Retarget to real routes (BLOCKER-severity gate is currently weaker than it reads). |
| 15.14–15.18 | 489-493 | `/portal/api/matters`, `/portal/api/info-requests`, `/portal/api/trust/transactions`, `/portal/api/documents`, `/portal/api/home` | Real endpoints: `/portal/requests/{id}`, `/portal/trust/matters/{id}/transactions`, `/portal/projects/{id}/summary`, … (`customerbackend/controller/Portal*Controller.java`) | B-pre | Retarget (same vacuity issue). |
| 15.6, 90.7 | 472, 899 | portal `/deadlines` | Still live: `portal/app/(authenticated)/deadlines/page.tsx`, `PortalDeadlineController.java:26`, module `deadlines` kept (`legal-za.json:5`). Only the **staff** `/deadlines` was deleted | ok | None. |
| 21.5 | 544 | task "Draft particulars of claim" | Not in the RAF template; the last cycle substituted it (`qa_cycle/status.md:111`) | D | Name an actual RAF task. |
| 28.1 | 620 | "Bulk Billing" | Nav label "Billing Runs" (`nav-items.ts:161-167`) | D | Relabel. |
| 45.4–45.5, 46.4–46.5 | 687-688, 705-706 | R71,000 incl. "Day 14 R1,000 carry-over" | A fresh run gives R70,000 (the last cycle applied a "cycle-correct amendment", `qa_cycle/status.md:94-95`) | B-pre | Restore R70,000 / two deposits. |
| 60.1, 60.6 | 737, 745 | "Work > Info Requests" | Requests is under the **Client** group (`project-tab-groups.ts:62-71`) | D | Fix the path. |
| 60.12 | 757 | PAY/2026/001 R70,000 "recorded by Thandi" | No step creates this payment | B-pre | Add the record-payment step (Thandi). |
| 60.16 | 767 | `sidebar-lifecycle-action` | `header-lifecycle-actions` / `close-matter-btn` (`matter-header-card.tsx:90-97`) | D | Fix the selector (the doc's own selector ref L41 is already right). |
| 60.23, 85.3 | 786, 857 | retention end_date +5y | `FIXME(depth-ii:retention-period-unconfigurable)` | **C** | Keep. |
| 61.4 | 805-813 | SoA lists R70k deposits; fee note R1,250 | Consistent once 45.x is fixed; fee note also carries time lines (Day 28) | D | Align after the 45.x fix. |
| 75.5 | 835 | activity from "Days 4, **8**, …" | Day 8 removed (#1566) | A-trim | Drop "8". |
| 85.4 | 858-860 | audit filter by matter / actor = Sipho | Filters by actor ID / entity type (`audit-log-client.tsx:193-195`) | D | Reword. |
| E.2 | 914 | "All 7 wow moments" | 6 listed since #1566 | A-trim | "6". |
| E.8 | 920 | magic link on Days 4, **8**, … | Day 8 removed | A-trim | Drop "8". |
| B.1–B.4 | 946-949 | matter **sidebar** expanded/collapsed/sheet baselines | Matter sidebar no longer rendered (only `MatterHeaderCard` imported, `projects/[id]/page.tsx:36,445`) | D | Retire B.1–B.4. |

Still valid: Day 30 portal Pay Now (`portal/app/(authenticated)/invoices/[id]/page.tsx:91-155`); trust dual approval + threshold (`components/trust/CreateTrustAccountDialog.tsx:270,284`); closure generate-SoA checkbox (`matter-closure-dialog.tsx:312`); portal trust/document/digest emails (`templates/email/portal-trust-activity.html`, `portal-document-ready.html`, `portal-weekly-digest.html`); FICA Onboarding Pack items (`request-packs/fica-onboarding-pack.json`); 90.4 billing still flat. No depth-ii C blocker hits Days 0–46 here: the disbursement is office-account (21.6) and the SoA is made by the closure flow, not the Statements dialog.

---

## Cross-step dependencies

1. **90day 1.20 → 2.1 → 2.7 → 3.1.** The deleted deal-win was the only PROSPECT→ONBOARDING step. Deleting Day 1 pipeline steps without rewording 2.1 leaves Sipho PROSPECT, so no checklist is instantiated (`CustomerLifecycleService.java:148`) and Day 2 cannot complete. 2.1 must become "Start Onboarding" **in the same amendment** as the A-deletions.
2. **90day DEAL-0001**: also referenced at 90.7 (deleted) and wow #2. No other step uses the deal. Day 4 engagement letter and Day 36 fee note do not depend on it.
3. **Full-lifecycle 10.2b (already deleted) → Day 28.2**: billing-run discovery requires ACTIVE. The gap was hidden in the 2026-07 run by an off-script activation. Now it also needs the 8-item checklist (incl. "Engagement Letter Signed", which requires a document), so the activation step and the engagement-letter step belong together.
4. **Full-lifecycle 45.4 (R71k) → 60.12 (R70k payout) → 60.17 trust gate**: run as scripted, a fresh tenant reaches R70k and the payout zeroes it. The R71k text exists only because of a prior-cycle artefact. If someone "fixes" 60.12 to R71k instead, the SoA (61.4) diverges.
5. **90day 5.9 (C) → 5.10, 5.12, 30.4 (SoA expects the deeds-office fee), 45.5 (write-off target).** One bug (`trust-link-payment-type`) cascades through four later checkpoints.
6. **90day 9.4 → 12.1, 20.1**: wrong task names cascade.
7. **90day 68.1 and 14.12** need the portal on :3002, which the 90day stack header does not start.

## Recommendation

**Minimal authorised deletions** (Phase 86 rule "remove deleted-feature steps only"; ADR-344 also authorises the Pipeline ones in a Phase 87 IA PR, annotated):
- 90day: wow #2 (L33), 0.56a–e, Day-0 Pipeline checkpoint (L206), the Day 1 (cont.) Pipeline section incl. note (L231-249), 90.7. Annotate "Removed — Epic 596 (#1565), ADR-336".
- A-trims (deleted-feature fragments only; flag them in the PR): 90day 0.67 (PayFast/member count/amount), 3.10 (Staffing), E.2 (7→6); full L35 + 3.5 (Staffing), 75.5 + E.8 (Day 8), E.2 (7→6).
- Add both plans' changes to the MIGRATION-NOTES "Amended plans" table; 90day is currently missing from it.

**Needs user sign-off (B):**
- B-86, must accompany the deletions: 90day **2.1** → manual Start Onboarding. Without it the A-deletion leaves the scenario incoherent.
- B-86: 90day **68.1** → portal :3002 `/login` path; full-lifecycle **client activation** steps before Day 28; optional **engagement-letter-via-acceptance** step (ADR-336) to replace Day 7/8 and satisfy the "Engagement Letter Signed" checklist item.
- B-pre (not Phase 86, separate decision): 90day 0.47/0.48/0.57–58/2.3–2.7/8.3/9.4/12.1/80.5; full 15.10–15.18 (vacuous isolation probes — highest value of these, because they sit under a BLOCKER gate), 45.x R71k→R70k, 60.12 missing payment step.
- D items can ride along with ADR-344 Phase 87 label/URL amendments, since most touch the same lines.

**C items**: keep the steps unchanged. They should fail until the nine `FIXME(depth-ii:…)` fixes land (5.9/5.10, 5.12, 14.8, 30.x, 45.5–45.11, 75.10/60.23/85.3, 85.7–85.8; checklist-stale is intermittent).

**Retire 90day? No.** It is not a subset of the full-lifecycle plan:
- **90day covers what full-lifecycle lacks:** three clients, Moroka estate work, conveyancing/OTP acceptance, budget, profitability, the disbursement trust-link and write-off, the closure override (Admin vs Owner), and the 90-day audit sweep. Its Phase 67 sections are mirrored 1:1 by `frontend/e2e/tests/legal-depth-ii/*` (and `legal-lifecycle/*`).
- **Full-lifecycle covers what 90day lacks:** portal interleaving, isolation, info requests, PayFast and portal SoA.
- **Canonical status:** `qa/testplan/demo-readiness-keycloak-master.md:19,175,221` still names 90day as *the* legal demo script, and ADR-344 names both docs as Phase 87 capstone inputs. The master doc itself is stale: it still lists the deleted accounting/agency scripts.

Keep both. Amend 90day as above, and treat the full-lifecycle plan as the canonical QA-cycle driver (it is the one with the 2026-07 verified lineage). If one plan is wanted later, merge 90day's unique sections into the full-lifecycle plan as a deliberate product decision, not as a retirement.
