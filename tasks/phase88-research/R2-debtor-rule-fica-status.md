# R2 — Debtor rule / missing due dates (Cluster A) and FICA status source (Cluster B)

Read-only research on `main` @ 50b88ed5c (2026-10-22). Gap rows: `qa_cycle/evidence/phase87-capstone/gaps.md` G-D11 (Outstanding + FICA parts), G-D21, G-B10; attribution table rows 88–97; `tasks/phase87-capstone-findings.md` §5.4.
All paths are relative to `/Users/rakheendama/Projects/2026/b2b-strawman/`. `B` = `backend/src/main/java/io/b2mash/b2b/b2bstrawman`, `T` = `backend/src/test/java/io/b2mash/b2b/b2bstrawman`.

---

## 1. Findings — Cluster A (debtor rule and missing due dates)

### Q1. The debtor-book predicate, and why the Fee notes page disagrees

**CONFIRMED** — debtor book (`B/collections/CollectionsReadService.java`):
- `:81-82` DEBTOR_BOOK_SQL `WHERE i.status = 'SENT' AND i.due_date IS NOT NULL`
- `:103-104` DEBTOR_BOOK_COUNT_SQL — same predicate (mirrors the page query)
- `:120-121` OUTSTANDING_INVOICES_SQL (drill-in) — same predicate
- `:53,64` the age expression is `CURRENT_DATE - i.due_date`; `oldest_days_overdue` and the 4 bucket FILTERs (`AgingBuckets`) all derive from `due_date`. A NULL due date would make every bucket predicate NULL (row excluded from all four sums) — the `IS NOT NULL` guard is what keeps the row totals and the bucket sums consistent. Introduced in Epic 591A (`97783be48`, #1540); `git log -S` shows no later change.

**CONFIRMED** — Clients table (`B/customer/ClientListRowRepository.java`):
- `:50-58` LATERAL `... WHERE i.customer_id = c.id AND i.status = :outstandingStatus AND i.due_date IS NOT NULL GROUP BY i.currency ... LIMIT 1`; `:32` binds `InvoiceStatus.SENT`. Javadoc `:16-18` explicitly says "the debtor-book definition … SENT with a due date".
- `B/customer/CustomerService.java:342-351` maps a NULL/absent aggregate to `0` → the UI shows "—".

**CONFIRMED** — Home **Debtors** widget: `frontend/lib/dashboard-home-fetch.ts:113` fetches `debtors` via `frontend/lib/api/collections.ts:130` → `GET /api/collections/debtors` → `CollectionsReadService.getDebtors` (same predicate). `frontend/components/dashboard/debtors-widget.tsx` renders "No client owes fees." on an empty page.

**CONFIRMED** — the same rule is also in:
- `B/collections/CashDigestService.java:92,104` (`AND i.due_date IS NOT NULL`)
- `B/reporting/InvoiceAgingReportQuery.java:76` (`AND i.due_date IS NOT NULL`) — the Invoice Aging report
- `B/collections/CollectionsScanService.java:59` (`i.due_date < CURRENT_DATE`, implicitly null-excluding)

**CONFIRMED** — Fee notes page "Total Outstanding" is a **client-side sum with no due-date predicate** and a wider status set: `frontend/app/(app)/org/[slug]/invoices/page.tsx:32-39` iterates the invoice list and adds `inv.total` for `status === "APPROVED" || status === "SENT"`; "overdue" there needs `inv.dueDate && inv.dueDate < todayStr` (`:36`). So the page counts INV-0002/INV-0003 (SENT, due_date NULL) while every backend aggregate excludes them. There is no backend "outstanding summary" endpoint — `InvoiceController` only has `/unbilled-summary` (`:86`).

### Q2. Where `invoices.due_date` is written — and why the walk left it NULL

**CONFIRMED** — the only writers of `Invoice.dueDate`:
1. `B/invoice/Invoice.java:158-170` `updateDraft(...)` — sets `this.dueDate = dueDate` verbatim (DRAFT only). Called from
   - `B/invoice/InvoiceCreationService.java:166-183` `createDraft` — only if the request carries any of dueDate/notes/paymentTerms/poNumber/taxType/billingPeriod; `CreateInvoiceRequest.dueDate` (`dto/CreateInvoiceRequest.java:17`) is nullable, no default.
   - `B/invoice/InvoiceCreationService.java:344-353` `updateDraft` (PUT) — from `UpdateInvoiceRequest.dueDate`.
2. `B/invoice/Invoice.java:220-235` `applyDefaults(defaultDueDate, defaultPaymentTerms)` — fills a NULL due date on an APPROVED invoice. **Only caller**: `B/billingrun/BillingRunGenerationService.java:327` (batch send), from `BatchSendRequest.defaultDueDate` (`billingrun/dto/BillingRunDtos.java:206`) which the frontend passes as an optional string (`frontend/lib/api/billing-runs.ts:149`).

**CONFIRMED** — nothing in the single-invoice lifecycle computes a due date:
- `Invoice.approve` `:203-213` sets `issueDate = today` if null — **not** dueDate.
- `Invoice.markSent` `:238-244` flips status only.
- `InvoiceTransitionService.send` `:207-...` validates (`InvoiceValidationService.validateInvoiceSend` has checks `customer_required_fields`, `customer_tax_number`, `org_name`, `time_entry_rates`, `template_required_fields` — `B/invoice/InvoiceValidationService.java:100-184`; **no due-date check**), calls `markSent`, generates the payment link, publishes `InvoiceSyncEvent` with `invoice.getDueDate()` (`:295`, so the portal read model and PDF context also carry NULL).
- `SendInvoiceRequest` is `record SendInvoiceRequest(boolean overrideWarnings)` — no due-date input.

**CONFIRMED** — no firm-level payment-terms default exists. `B/settings/` embeddables: Billing, Branding, Collections, DataProtection, DataRequest, Expense, PackStatus, Portal, Tax, TimeReminder. `BillingSettings.java:32-38` has only `billingBatchAsyncThreshold`, `billingEmailRateLimit`, `defaultBillingRunCurrency`. A repo-wide grep for `paymentTerm|dueDays|defaultDue` finds only the per-invoice free-text `payment_terms` column (`Invoice.java:61-62`) and the batch-send request. There is nothing for the walker "to set".

**CONFIRMED** — the walk's UI path never sends a due date:
- Generate-from-unbilled: `frontend/components/invoices/use-invoice-generation.ts:249-256` posts `{clientId, currency, timeEntryIds, expenseIds?, disbursementIds?}` — no `dueDate`.
- The draft detail form has an optional, blank `Due Date` input (`frontend/components/invoices/invoice-draft-form.tsx:58-67`; `use-invoice-detail.ts:43` initialises from `invoice.dueDate ?? ""`, `:134` sends `dueDate || undefined`). The scenario (`qa/testplan/demos/legal-za-90day-keycloak.md`) has **zero** mentions of "due date"/"payment terms", so the walker correctly never filled it.
- Therefore create → approve → send yields `SENT, due_date NULL` by construction. The capstone's read-only SELECT confirmed INV-0002/INV-0003 are exactly that.

**HYPOTHESIS** — `B/demo/seed/LegalDemoDataSeeder.java:599-620` (`createInvoiceFromTimeEntries` → `approve` → `markSent`) also never sets a due date (I did not open `createInvoiceFromTimeEntries`), so demo tenants probably show the same empty debtor book. Worth one SELECT on a demo tenant before deciding whether the data fix must cover it.

### Root cause statement (Cluster A)

Two surfaces define "outstanding" differently and neither definition is wrong on its own:
- Collections (591A) defines the debtor book as *SENT with a due date*, because its aging buckets, days-overdue and dunning stages are all functions of `due_date`.
- The fee-note lifecycle (create → approve → send) has **no step that assigns a due date** and **no firm default** to derive one from; the only auto-fill is the batch-send path, which the single-invoice path never touches.

Phase 87 then put the collections definition on three new dense surfaces (Home Debtors 614B, Clients "Outstanding fee notes" 615B, and the pre-existing Fee notes page keeps its own client-side rule), so a SENT-without-due-date fee note reads as "no debt" on Home/Clients and as "outstanding" on Fee notes. G-D11(b) and G-D21(Debtors) are one defect: **a SENT fee note can exist with no due date, and the debtor book treats that as not owed.**

---

## 2. Findings — Cluster B (FICA status source)

### Q4. What decides NOT_STARTED / IN_PROGRESS / DONE today

**CONFIRMED** — `B/informationrequest/FicaStatusProjector.java:46-86` (614A, `78dec7af5`, #1625; extracted from `InformationRequestService.getFicaStatus`, which dates from GAP-L-46 `e5f5aeff0`, #1127):
- Input: the set of `request_templates` ids whose `pack_id = 'fica-onboarding-pack'` (`InformationRequestService.java:751,822-826` `ficaTemplateIds()`), the client's `information_requests`, and an item lookup.
- Rule: filter requests to FICA templates, drop CANCELLED, newest first. None → `NOT_STARTED`. Newest COMPLETED request with ≥1 item and every item ACCEPTED → `DONE` (+ `completedAt`); COMPLETED but not all accepted → `IN_PROGRESS`; no COMPLETED → `IN_PROGRESS` with the newest live request id.
- **It reads information requests only.** It never reads `checklist_instances`. The Javadoc at `InformationRequestService.java:754-758` says so: "Info-request-only signal".
- `getFicaStatus(UUID)` `:761-769` (single) and `getFicaStatuses(Collection)` `:777-818` (batched, 3 queries) both delegate to the projector.
- The `fica-onboarding-pack` request template IS seeded for legal-za tenants (`backend/src/main/resources/vertical-profiles/legal-za.json:12` `"request": [..., "fica-onboarding-pack", ...]`, `request-packs/fica-onboarding-pack.json:2`), so `ficaTemplateIds` is non-empty and the projector really is evaluating "has anyone sent this client a FICA *information request*" — which nobody did in the walk.
- **No code auto-creates a FICA information request.** The only references to `FICA_ONBOARDING_PACK_ID` are the projection (`CustomerController.java:257-265`, `InformationRequestService.java:751-826`). `AutoDraftInformationRequestTest` covers matter templates with a `requestTemplateId` (conveyancing intake), not FICA.

**CONFIRMED** — consumers of the projection:
| Surface | Code |
|---|---|
| Matter Overview `FicaStatusCard` (G-B10) | `frontend/app/(app)/org/[slug]/matters/[id]/page.tsx:230-235` → `getClientFicaStatus` → `GET /api/clients/{id}/fica-status` (`CustomerController.java:263`); card copy `frontend/components/compliance/FicaStatusCard.tsx:84` "No FICA onboarding request yet." |
| Home **FICA outstanding** widget (G-D21) | `frontend/lib/dashboard-home-fetch.ts:123-124` → `GET /api/dashboard/fica-outstanding` → `DashboardService.getFicaOutstanding` `B/dashboard/DashboardService.java:417-455` (ONBOARDING/ACTIVE non-archived clients, `getFicaStatuses`, filter `!= DONE`) |
| Clients table FICA column + `ficaStatus=OUTSTANDING\|DONE` filter (G-D11c) | `CustomerService.java:308-312` `matchesFica`, `:361-362` `toRow` (`fica == null → NOT_STARTED`); system view `frontend/lib/constants/system-views.ts:68-71` "FICA outstanding" |
| Also | `frontend/components/projects/kpi-dashboard.tsx`, `overview-tab.tsx`, `components/views/CreateViewDialog.tsx` (filter option), `lib/clients-list.ts` |

### Q5. What the checklist flow writes, and the lifecycle hook

**CONFIRMED** — tables/entities:
- `checklist_templates` (`B/checklist/ChecklistTemplate.java:15-50`): `slug`, `customer_type`, `source`, `pack_id`, `pack_template_key`, `active`, `auto_instantiate`. Legal-za packs: `compliance-packs/legal-za-individual-onboarding/pack.json` (`packId legal-za-individual-onboarding`, slug `legal-za-individual-client-onboarding`, customerType INDIVIDUAL, autoInstantiate true, 9 items / 8 required) and `legal-za-trust-onboarding` (slug `legal-za-trust-client-onboarding`, 12 items / 11 required).
- `checklist_instances` (`ChecklistInstance.java:13-50`): `template_id`, `customer_id`, `status` (`IN_PROGRESS`/`COMPLETED`/cancelled), `started_at`, `completed_at`, `completed_by`. Repository (`ChecklistInstanceRepository.java:9-15`): `findByCustomerId`, `findByCustomerIdIn`, `existsByCustomerIdAndTemplateId`, `existsByCustomerIdAndStatusNot`. Template repo has `findBySlug`, `findByPackIdAndPackTemplateKey` (`ChecklistTemplateRepository.java:20-22`).
- Instantiation: Start Onboarding (PROSPECT → ONBOARDING) → `CustomerLifecycleService.java:148-152` → `ChecklistInstantiationService.instantiateForCustomer` (`B/compliance/ChecklistInstantiationService.java:36-83`; typed pack wins over ANY pack).
- Completion cascade: `ChecklistInstanceService.checkInstanceCompletion` `:449-480` (all required items COMPLETED/SKIPPED → `instance.complete`, audit `checklist.instance.completed`) → `checkLifecycleAdvance` `:522-550`: if client is ONBOARDING and **no instance has status ≠ COMPLETED** (`existsByCustomerIdAndStatusNot(customerId, "COMPLETED")`) and `PrerequisiteService.checkForContext(LIFECYCLE_ACTIVATION, CUSTOMER)` passes → `customerLifecycleService.transition(... ACTIVE)`. The manual ONBOARDING → ACTIVE transition also runs the prerequisite check (`CustomerLifecycleService.java:126-134`) but does **not** require checklist completion.
- The lifecycle hook therefore reads **checklist instances**, never information requests. The product already treats "all onboarding checklists COMPLETED" as the FICA-done event (it is what promotes the client to ACTIVE), while every FICA *display* reads a different store.

### Q6. Which mechanism is the live legal-za onboarding path

**CONFIRMED** — the 90-day scenario (`qa/testplan/demos/legal-za-90day-keycloak.md`):
- Day 2 `2.1-2.9` (`:231-241`): Start Onboarding → **Legal Individual Client Onboarding checklist** auto-instantiated → Mark Complete × 9 → "9/9 completed; checklist instance status COMPLETED" → auto-ACTIVE via `checkLifecycleAdvance`.
- Day 8 `8.3, 8.5` (`:309-311`): Moroka trust → **Legal Trust Client Onboarding** checklist (12 items, beneficial owners recorded in the checklist item's note).
- Day 14 `14.2` and Day 22 `22.3` repeat the 9-item checklist for Khumalo and the fourth client.
- The scenario never sends a `fica-onboarding-pack` information request. The information-request pack is the *client-portal self-service* variant (contact uploads, firm accepts items); the checklist is the *firm-driven* variant. For the legal-za demo the checklist is the live path.

**Recommended single source of truth**: the FICA status of a client = the state of its **onboarding checklist instance(s)**, with the information-request projection as a secondary signal (a client whose portal FICA pack is DONE but who has no checklist should still read DONE). Shape in §4.

### Q7. Blast radius — test classes

Backend (`T/…`):
- Collections read: `collections/CollectionsReadApiTest` (seeds all invoices **with** due dates `:173-187`; `:356` asserts `dueDate` non-null on the drill-in), `collections/CashDigestServiceTest`, `collections/CashDigestDataTest`, `collections/CollectionsScanServiceTest`, `collections/CollectionsScanIdempotencyTest`, `collections/CollectionsEscalationTest`, `collections/AiReminderComposerTest`, `collections/CollectionsTriageServiceTest`, `collections/CollectionsTenantIsolationTest`, `collections/CollectionsPaymentCancellationTest`, `verticals/legal/collections/TrustAwareCollectionsAdvisorTest`, `settings/CollectionsSettingsApiTest`.
- Client list: `customer/ClientListRowIntegrationTest` — fixture `:203` `seedSentInvoice(clientA, 777, currency, null) // SENT without a due date`; `ownerSeesFullyPopulatedRow` `:248,257` pins `outstandingFeeNoteCount 2` / `outstandingFeeNoteTotal 1500.00` (the 777 row must stay excluded or the pins change to 3 / 2277.00); `:155-159` FICA fixture is information-request based (A DONE, B IN_PROGRESS, D DONE), `ficaStatusDoneAndOutstandingSplitTheClients` `:329`.
- FICA: `informationrequest/FicaStatusProjectorTest` (17 unit cases incl. batch/single parity), `informationrequest/CustomerFicaStatusIntegrationTest` (3 cases via `/fica-status`, info-request fixtures), `dashboard/FicaOutstandingIntegrationTest` (6 cases, info-request fixtures incl. `batchStatusMatchesSingleClientEndpoint`), `informationrequest/InformationRequestControllerTest`, `informationrequest/InformationRequestNotificationAuditIntegrationTest`, `informationrequest/InformationRequestAutomationIsolationIntegrationTest`, `customerbackend/PortalInformationRequestControllerTest`, `customerbackend/InformationRequestReadModelSyncIntegrationTest`.
- Dashboard: `dashboard/DashboardCompanyIntegrationTest`, `DashboardPersonalIntegrationTest`, `DashboardProjectIntegrationTest`, `DashboardServiceUnbilledWipTest`, `TrustPositionIntegrationTest`, `DashboardWithoutCapacityTest`, `DashboardWithoutPipelineTest`.
- Checklist/lifecycle: `checklist/ChecklistInstanceServiceTest`, `ChecklistInstanceAdvancedTest`, `compliance/ChecklistInstantiationServiceTest`, helper `testutil/TestChecklistHelper` (`transitionToActive`, `fillStructuralPrerequisiteFields`, `completeChecklistItems`).
- Invoice send path (for fix A): `invoice/InvoiceLifecycleIntegrationTest` (no `dueDate` assertions today), `InvoiceGenerationValidationIntegrationTest`, `InvoiceSentDocumentPersistenceIntegrationTest`, `PaymentLinkServiceIntegrationTest`, `PaymentReconciliationServiceIntegrationTest`, `billingrun/BillingRunBatchOperationsTest`, `BillingRunNotificationTest`.

Frontend (vitest): `frontend/__tests__/dashboard/debtors-widget.test.tsx`, `fica-outstanding-widget.test.tsx`, `home-matrix.test.tsx`, `__tests__/clients/clients-table.test.tsx`, `__tests__/lib/clients-list.test.ts`, `components/compliance/__tests__/FicaStatusCard.test.tsx`, `app/(app)/org/[slug]/invoices/collections/__tests__/collections-client.test.tsx`.

---

## 3. Recommended fix shapes

### Cluster A — options

**A1 (recommended): assign a due date at approve/send when missing; backfill existing rows.**
- Domain change: in `Invoice.approve` (or `markSent`) — `if (dueDate == null) dueDate = issueDate.plusDays(termsDays)`. Prefer **approve**: `issueDate` is set there (`Invoice.java:210-211`), the number is allocated there, and the PDF/email/portal sync on send (`InvoiceTransitionService.java:295,414,510`) then carry a real due date. `termsDays` = a new firm setting (e.g. `BillingSettings.defaultPaymentTermsDays`, default 30) — add the field to the embeddable + `OrgSettings` DTO/controller; the embeddable → column mapping needs a tenant migration **V4** (`backend/src/main/resources/db/migration/tenant/` currently has V1–V3) adding `org_settings.default_payment_terms_days INT NOT NULL DEFAULT 30`. If a setting is judged over-scope, a constant 30 in `Invoice` is acceptable as a first cut but leaves no firm control.
- Tenant data fix: V4 (or a one-off data migration) `UPDATE invoices SET due_date = COALESCE(issue_date, created_at::date) + 30 WHERE status IN ('APPROVED','SENT') AND due_date IS NULL`. Runs per tenant via Flyway tenant migrations, so it also covers the walked Keycloak tenant and any demo tenants.
- Pros: debtor book, cash digest, aging report, dunning scan all become correct without touching their SQL; the Fee notes page's "overdue" tile (`invoices/page.tsx:36`) also starts working; the PDF shows a due date. Matches the batch-send `applyDefaults` precedent. Cons: slightly larger PR (entity + settings + migration + tests); product must pick the default (30 days is the SA norm and what the Phase 83 bucket boundaries assume).
- Tests to change/add: `ClientListRowIntegrationTest` — the `777` fixture is seeded via `updateDraft(null)` + direct `markSent` on the entity (`:564-578`), so it bypasses approve; it stays NULL and the `1500.00` pin holds, but the Javadoc "SENT one WITHOUT a due date" becomes a legacy case — either keep it (documents the guard) or seed it via `approve()` and move the pin to `2277.00`/count 3. Add an `InvoiceLifecycleIntegrationTest` case "approve without due date → dueDate = issueDate + 30"; add a `CollectionsReadApiTest` case "fee note sent from the unbilled path appears in the debtor book in the `current` bucket".

**A2: make the debtor rule treat NULL due date as due-on-send (`COALESCE(i.due_date, i.issue_date)`).**
- Change the predicate and `AGE_EXPR` in `CollectionsReadService` (3 places), `CashDigestService:92,104`, `ClientListRowRepository:55`, `InvoiceAgingReportQuery:72,76`, `CollectionsScanService:55-61`.
- Pros: no write-path or settings change, no data fix. Cons: five SQL sites to keep in sync; the aging starts at issue date rather than a terms date, so every such fee note is "overdue from day 1" (the dunning scan `due_date < CURRENT_DATE` would start chasing the day after send); PDFs/portal still show no due date; `ClientListRowIntegrationTest` pins (`1500.00`, count 2) and its Javadoc must change to 3 / `2277.00`; `CollectionsReadApiTest:356` (`dueDate` non-null in the drill-in) needs a nullable DTO. Not recommended as the primary fix.

**A3 (complement, small): frontend coherence** — make `invoices/page.tsx` "Total Outstanding" use the same definition (either call `/api/collections/debtors` totals or keep client-side but exclude APPROVED). Separate PR; lower priority once A1 lands.

### Cluster B — options

**B1 (recommended): projector reads checklist instances first, information requests as fallback.**
- New rule (keep `FicaStatusProjector` pure; add a second input): for a client, take its `checklist_instances` whose template is an onboarding pack (match `checklist_templates.pack_id IN ('legal-za-individual-onboarding','legal-za-trust-onboarding')`, or more generically `pack_id LIKE '%onboarding%'` / a new boolean on the template — product call). Any instance `COMPLETED` → `DONE` with `completed_at`; any `IN_PROGRESS` → `IN_PROGRESS`; none → fall through to the existing information-request rule.
- Batched read: `ChecklistInstanceRepository.findByCustomerIdIn` already exists; add a template-id set lookup like `ficaTemplateIds()` (`ChecklistTemplateRepository` has `findByPackIdAndPackTemplateKey`; add `findByPackIdIn`). `getFicaStatuses` becomes 4–5 queries, still batched — the `ClientListRowIntegrationTest` statement cap is **10**, `MatterListRow` is 7; count statements after the change.
- `FicaStatusResponse.requestId` semantics: keep `requestId` for the info-request branch; add `checklistInstanceId` (nullable) so `FicaStatusCard` / the Home widget can deep-link to the checklist. Card copy `FicaStatusCard.tsx:84` "No FICA onboarding request yet." → "No FICA onboarding started." Also wire the DONE branch's "lastVerifiedAt" from `checklist_instances.completed_at`.
- Tests: extend `FicaStatusProjectorTest` (checklist-wins, fallback, cancelled instance ignored, batch/single parity); add checklist fixtures to `CustomerFicaStatusIntegrationTest`, `FicaOutstandingIntegrationTest`, `ClientListRowIntegrationTest` (use `TestChecklistHelper.completeChecklistItems`); existing info-request cases keep passing because the fallback is unchanged.

**B2: lifecycle-based shortcut** — treat `lifecycle_status = ACTIVE` as DONE. Cheaper but wrong for clients activated manually without a checklist (the manual ONBOARDING → ACTIVE transition does not require checklist completion, `CustomerLifecycleService.java:126-134`) and for OFFBOARDED/REACTIVATED clients. Not recommended.

**B3: write-through** — on `checkInstanceCompletion` create+complete a FICA information request so the projector sees it. Rejected: fabricates portal records, duplicates the checklist, and would notify the client.

---

## 4. Related gaps / same root

- G-D11(a) "Fee earner —" and G-D13 are the `LEAD`/`lead` case mismatch (`ProjectTemplateService.java:569,739` vs `MatterListRowRepository.java:46`, `ProjectMemberRepository.java:40`, `ProjectAccessService.java:47`) — a different cluster, not covered here.
- Invoice Aging report (`InvoiceAgingReportQuery.java:76`) and the Cash digest (`CashDigestService.java:104`) are silently empty for the same fee notes — A1 fixes them for free; A2 must touch them.
- The dunning scan (`CollectionsScanService.java:59`) can never chase a fee note with no due date — the Phase 83 collections lifecycle QA (`qa/testplan/phase83-collections-lifecycle.md:195-219`) only passed because its fixtures backdated due dates by hand.
- The portal fee-note list and the sent PDF carry a NULL due date today (`InvoiceTransitionService.java:295,414,510`).
- The Fee notes page "Overdue" tile (`invoices/page.tsx:36`) is always 0 for the same reason.

## 5. Product questions for the founder

1. Default payment terms: fix at 30 days from issue date, or a firm setting (`Settings → Billing`) with 30 as default? Should it be per client later (`customers.payment_terms_days`)?
2. Due date assigned at **approve** (recommended; PDF and number allocated together) or at **send**?
3. Backfill existing APPROVED/SENT fee notes with `issue_date + 30` — acceptable for the walked tenant and demo tenants? (It changes the aging of anything already sent.)
4. FICA truth order: checklist first, information request as fallback (B1) — confirm. Should a client with BOTH a completed checklist and an open portal FICA pack read DONE or IN_PROGRESS?
5. Which checklist templates count as "FICA onboarding": the two legal-za packs by id, or any `auto_instantiate` onboarding template?
6. Fee notes page "Total Outstanding" — align with the debtor book (SENT only, i.e. drop APPROVED), or keep APPROVED and rename the tile ("Billed, not yet paid")?

## 6. Tests to run (per CLAUDE.md the merge bar is a clean full `./mvnw verify`; these are the inner-loop sets)

Cluster A: `-Dtest='Invoice*IntegrationTest,ClientListRowIntegrationTest,CollectionsReadApiTest,CashDigest*Test,CollectionsScan*Test,CollectionsEscalationTest,BillingRunBatchOperationsTest,InvoiceAging*Test,OrgSettings*Test'`, then `backend/scripts/verify.sh`. Frontend: full `pnpm lint && pnpm build && pnpm test` (debtors-widget, collections-client, invoices page tests) + `pnpm format:check`.

Cluster B: `-Dtest='FicaStatusProjectorTest,CustomerFicaStatusIntegrationTest,FicaOutstandingIntegrationTest,ClientListRowIntegrationTest,ChecklistInstance*Test,ChecklistInstantiationServiceTest,Dashboard*IntegrationTest,InformationRequest*Test'`, then full verify. Frontend: full vitest (FicaStatusCard, fica-outstanding-widget, clients-table, home-matrix).

Observed proof (rule 3): on the e2e stack (`:3001`), create a client → Start Onboarding → complete the 9-item checklist → Home FICA outstanding excludes it, Clients row reads Done, matter Overview card reads Done; create a fee note from unbilled time → approve → send → Home Debtors lists the client in the `current` bucket, Clients "Outstanding fee notes" shows the total, Fee notes "Total Outstanding" agrees.
