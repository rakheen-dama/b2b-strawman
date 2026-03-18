# Phase 48 — Agent Gap Report
## Generated: 2026-03-16
## Scenario: tasks/phase48-lifecycle-script.md (post-Phase 48 QA test plan)
## Vertical: accounting-za

## Relationship to Phase 47

The Phase 47 QA cycle identified 31 gaps. Of those:

### Fixed Since Phase 47 (do not re-log)
| ID | Summary | Status |
|----|---------|--------|
| GAP-008 | Template pack seeding | FIXED (PR #687-695) |
| GAP-027 | Customer SSR crash | FIXED (PR #687) |
| GAP-030 | Log Time null crash | FIXED (PR #691-692) |
| GAP-025 | Team member API port | FIXED (PR #688) |
| GAP-026 | FICA checklist activation | FIXED (PR #690) |
| GAP-008C | Projects page null guards | FIXED (PR #689) |
| GAP-004 | Statement of account template | FIXED (PR #694) |
| GAP-031 | Report UUID parsing | FIXED (PR #695) |
| GAP-001 | `PROPOSAL_SENT` trigger wiring | FIXED — `ProposalSentEvent` now mapped in `TriggerTypeMapping` |
| GAP-002 | `FIELD_DATE_APPROACHING` trigger | FIXED — `FieldDateScannerJob`, `FieldDateApproachingEvent`, full automation wiring |
| GAP-003 | `CustomerStatusChangedEvent` not a DomainEvent | FIXED — now in `DomainEvent` sealed interface, mapped in `TriggerTypeMapping` |
| GAP-005 | Terminology overrides not loaded | FIXED — `TerminologyProvider` in org layout, `terminology-map.ts` with accounting-za overrides |
| GAP-008A | Org settings "Coming Soon" | FIXED — `/settings` redirects to `/settings/general` which has full settings form |
| GAP-019 | Currency displays as USD not ZAR | FIXED — vertical profile sets `currency: "ZAR"` during provisioning, `formatCurrency` supports ZAR locale |

### Remain Open from Phase 47
| ID | Summary | Status |
|----|---------|--------|
| GAP-008B | FICA field groups not auto-attached during customer creation | OPEN — still requires manual "Add Group" post-creation |
| GAP-009 | FICA checklist does not filter by entity type | OPEN — all customers see same 9-item checklist |
| GAP-010 | Trust-specific custom fields missing | PARTIALLY FIXED — pack exists at `field-packs/accounting-za-customer-trust.json` but NOT registered in vertical profile `field` array |
| GAP-020 | Portal contacts required for information requests | OPEN — no auto-creation from customer email |

---

## Summary Statistics

| Category | Blocker | Major | Minor | Cosmetic | Total |
|----------|---------|-------|-------|----------|-------|
| missing-feature | 1 | 2 | 1 | 0 | 4 |
| bug | 0 | 0 | 0 | 0 | 0 |
| ux | 0 | 2 | 2 | 1 | 5 |
| vertical-specific | 0 | 1 | 0 | 0 | 1 |
| content | 0 | 1 | 0 | 0 | 1 |
| **Total** | **1** | **6** | **3** | **1** | **11** |

---

## Critical Path Blockers

### GAP-P48-001: No "Create Proposal" UI — entire proposal creation flow is blocked

**Day**: 1
**Step**: 1.19-1.24 (Create, save, and send proposal)
**Category**: missing-feature
**Severity**: blocker
**Description**: The proposals page (`/proposals`) lists proposals and has filtering, summary cards, and an attention list. However, there is NO "New Proposal" button, no create proposal dialog, and no proposal detail page (`/proposals/[id]` route does not exist). The proposal table links to `/proposals/{id}` but that route returns a 404. The backend has a complete Proposal API (`POST /api/proposals`, `POST /api/proposals/{id}/send`, milestones, team members), but the frontend has zero creation/editing/viewing UI. The lifecycle script cannot execute steps 1.19-1.24.
**Evidence**:
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/app/(app)/org/[slug]/proposals/page.tsx` — no create button
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/components/proposals/` — only `proposal-table.tsx`, `proposal-summary-cards.tsx`, `proposals-attention-list.tsx`
- No `frontend/app/(app)/org/[slug]/proposals/[id]/` directory exists
- Backend fully ready: `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalController.java`
**Suggested fix**: Create proposal detail page (`/proposals/[id]/page.tsx`) with view/edit/send actions, and a "New Proposal" dialog/button on the proposals list page. Backend is complete — this is purely frontend work. Effort: **M** (2-3 days)

---

## All Gaps (Chronological)

### GAP-P48-001: No "Create Proposal" UI — entire proposal creation flow is blocked
**Day**: 1
**Step**: 1.19-1.24 — Create proposal, save as DRAFT, send, check email
**Category**: missing-feature
**Severity**: blocker
**Description**: The proposals page lists proposals but has no creation button, no creation dialog, and no detail page. The backend has full CRUD + lifecycle management for proposals (DRAFT, SENT, ACCEPTED, DECLINED, EXPIRED) including milestones and team members. The frontend only has a list view with filtering and summary cards. Steps 1.19-1.24 cannot be executed.
**Evidence**:
- `frontend/app/(app)/org/[slug]/proposals/page.tsx` — no create button
- No `frontend/app/(app)/org/[slug]/proposals/[id]/` directory
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalController.java` — full API exists
**Suggested fix**: Build proposal detail page + create dialog. Effort: **M**

### GAP-P48-002: No "New Invoice" button on the invoices list page
**Day**: 30
**Step**: 30.1-30.2 — Navigate to `/invoices` and create a new invoice
**Category**: ux
**Severity**: major
**Description**: The lifecycle script instructs the user to navigate to `/invoices` and click to create a new invoice. The invoices page has no "New Invoice" button. Invoice creation is only available from the customer detail page's "Invoices" tab via the `InvoiceGenerationDialog`. Furthermore, this dialog requires selecting unbilled time entries or expenses — it cannot create an empty draft for manual line items. The backend supports creating a blank draft (`POST /api/invoices` with just customerId + currency, timeEntryIds can be empty list), but the frontend enforces `selectedEntryIds.size === 0 && selectedExpenseIds.size === 0) return` in `handleCreateDraft()`.
**Evidence**:
- `frontend/app/(app)/org/[slug]/invoices/page.tsx` — no create button
- `frontend/components/invoices/use-invoice-generation.ts` line 204 — blocks creation with no selections
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/dto/CreateInvoiceRequest.java` — timeEntryIds/expenseIds are optional Lists
**Suggested fix**: (a) Add a "New Invoice" button to the invoices list page that opens a customer-selection dialog, then delegates to the existing generation flow. (b) Allow creating a blank draft (remove the empty-selection guard) so users can manually add line items after creation. Effort: **S** (1 day)

### GAP-P48-003: Retainer invoice requires manual line items — no retainer-specific invoice flow
**Day**: 30
**Step**: 30.10-30.12 — Create retainer invoice with flat fee line
**Category**: ux
**Severity**: major
**Description**: The lifecycle script asks to create a retainer invoice with a single line item "Monthly Bookkeeping Retainer — January" for R5,500 + VAT. The current invoice creation flow only pulls unbilled time entries. There is no "create retainer invoice" button or flow that auto-populates the retainer fee as a line item. The workaround is: create a draft from the customer page (with at least one unbilled time entry), navigate to the invoice detail page, delete the auto-generated lines, and manually add the retainer line. This is cumbersome. Note: the `RetainerPeriodService.closePeriod()` does create invoices with base fee + overage lines, but there's no UI to trigger period close.
**Evidence**:
- `frontend/components/invoices/use-invoice-generation.ts` — time-entry-only flow
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerPeriodService.java` — period close creates invoices but no frontend trigger
- `frontend/app/(app)/org/[slug]/retainers/[id]/page.tsx` — retainer detail exists but close-period action not verified
**Suggested fix**: Add a "Close Period" button on the retainer detail page that invokes the backend's period-close logic and auto-creates the retainer invoice. Alternatively, allow blank draft creation (see GAP-P48-002). Effort: **S** (1 day)

### GAP-P48-004: Trust field pack not registered in vertical profile
**Day**: 2-3
**Step**: 2.11 — Create Moroka Family Trust customer
**Category**: content
**Severity**: major
**Description**: The trust-specific field pack exists at `field-packs/accounting-za-customer-trust.json` with 6 fields (Trust Registration Number, Trust Deed Date, Trust Type, Names of Trustees, Trustee Appointment Type, Letters of Authority Date), all with visibility conditions keyed on `acct_entity_type == "TRUST"`. However, this pack is NOT listed in the vertical profile's `field` array: `"field": ["accounting-za-customer", "accounting-za-project"]`. It should include `"accounting-za-customer-trust"`. Without this registration, the trust fields are never seeded during provisioning.
**Evidence**:
- `backend/src/main/resources/field-packs/accounting-za-customer-trust.json` — pack exists with `autoApply: true`
- `backend/src/main/resources/vertical-profiles/accounting-za.json` line 8 — `"field": ["accounting-za-customer", "accounting-za-project"]` (no trust pack)
**Suggested fix**: Add `"accounting-za-customer-trust"` to the `field` array in `vertical-profiles/accounting-za.json`. Effort: **S** (10 minutes)

### GAP-P48-005: Rate card and tax defaults not auto-seeded from vertical profile
**Day**: 0
**Step**: 0.6-0.14 — Set up rates and tax
**Category**: vertical-specific
**Severity**: major
**Description**: The vertical profile `accounting-za.json` defines `rateCardDefaults` (3 billing rates for Owner/Admin/Member at R1500/R850/R450 and 3 cost rates) and `taxDefaults` (VAT 15%). However, no provisioning code reads or applies these defaults. The `TenantProvisioningService.setVerticalProfile()` method only reads `currency` from the profile. Users must manually create all billing rates, cost rates, and tax rates during Day 0 setup. For an accounting firm onboarding, having ZAR rates and VAT pre-configured would remove ~10 manual steps.
**Evidence**:
- `backend/src/main/resources/vertical-profiles/accounting-za.json` lines 15-30 — `rateCardDefaults` and `taxDefaults` defined
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java` lines 174-193 — only reads `currency`
- No code references `rateCardDefaults` or `taxDefaults` anywhere in Java source
**Suggested fix**: Extend `TenantProvisioningService.setVerticalProfile()` to read and seed billing rates, cost rates, and tax rates from the profile JSON. Effort: **M** (1-2 days)

### GAP-P48-006: Invoice "Mark as Sent" label mismatch with lifecycle script expectation
**Day**: 30
**Step**: 30.14 — Click "Send" on invoice
**Category**: ux
**Severity**: cosmetic
**Description**: The lifecycle script says "Click Send" but the UI button label is "Mark as Sent". While the action does trigger the invoice email event listener (which renders PDF and sends via email), the label doesn't match the expected action verb. An accounting firm user expects "Send" to mean "email to client," which it does — but "Mark as Sent" implies it's a manual status update without actual sending.
**Evidence**:
- `frontend/components/invoices/invoice-header-actions.tsx` line 100 — `Mark as Sent`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceEmailEventListener.java` — triggers email on `INVOICE_STATUS_CHANGED` to SENT
**Suggested fix**: Rename button to "Send Invoice" or "Send to Client". Effort: **S** (5 minutes)

### GAP-P48-007: FICA field groups not auto-attached during customer creation (GAP-008B still open)
**Day**: 1
**Step**: 1.3-1.4 — Create customer with custom fields visible
**Category**: ux
**Severity**: minor
**Description**: During customer creation, only "Contact & Address" fields are shown. The 16 accounting-specific fields (Company Registration, VAT Number, Entity Type, SARS Tax Reference, etc.) must be manually added via "Add Group" on the customer detail page AFTER creation. For an accounting firm, FICA details should be captured during intake. This is GAP-008B from Phase 47, still open.
**Evidence**:
- `frontend/components/customers/intake-fields-section.tsx` — exists but not auto-populating vertical field groups
- `backend/src/main/resources/field-packs/accounting-za-customer.json` — 16 fields defined with `autoApply: true`
**Suggested fix**: During customer creation (or on the create dialog), auto-include field groups marked `autoApply: true` for the org's vertical profile. Effort: **M**

### GAP-P48-008: FICA checklist does not filter by entity type (GAP-009 still open)
**Day**: 1-3
**Step**: 1.10, 2.2, 2.7, 2.12 — FICA checklist for different entity types
**Category**: ux
**Severity**: minor
**Description**: All customers see the same 9-item FICA checklist regardless of entity type. Sole proprietors see "Company Registration (CM29/CoR14.3)" which is irrelevant. Trusts see nothing about Letters of Authority. This is GAP-009 from Phase 47, still open.
**Evidence**:
- `backend/src/main/resources/compliance-packs/fica-kyc-za/pack.json` — single checklist for all entity types
**Suggested fix**: Create entity-type-specific checklist templates or add conditional visibility per item. Effort: **M**

### GAP-P48-009: Portal contact required for information request send (GAP-020 still open)
**Day**: 1
**Step**: 1.14-1.18 — Create and send information request
**Category**: missing-feature
**Severity**: minor
**Description**: The `CreateRequestDialog` component fetches portal contacts and requires one to be selected before sending. The lifecycle script expects to create an information request and send it directly. If no portal contact exists for the customer, the user must first create one. This is GAP-020 from Phase 47, still open.
**Evidence**:
- `frontend/components/information-requests/create-request-dialog.tsx` — fetches portal contacts
- `frontend/app/(app)/org/[slug]/customers/[id]/request-actions.ts` — `fetchPortalContactsAction` required
**Suggested fix**: Auto-create a portal contact from the customer's email when transitioning to ONBOARDING or when creating the first information request. Effort: **S**

### GAP-P48-010: Carol (Member) gets 404 instead of permission denied on admin pages
**Day**: 90
**Step**: 90.16 — Carol navigates to `/settings/rates` expecting "access blocked"
**Category**: ux
**Severity**: minor (not cosmetic because it affects comprehension)
**Description**: The lifecycle script expects Carol to see a "permission error" or "access blocked" message when navigating to `/settings/rates`. The rates page uses `notFound()` for unauthorized users, which returns a generic Next.js 404 page. This is confusing — the user can't distinguish between "page doesn't exist" and "you don't have permission." Other settings pages (e.g., `/settings/general`) show a proper permission message ("You do not have permission to manage general settings").
**Evidence**:
- `frontend/app/(app)/org/[slug]/settings/rates/page.tsx` line 18-20 — `notFound()` for non-admin without `FINANCIAL_VISIBILITY`
- `frontend/app/(app)/org/[slug]/settings/general/page.tsx` lines 16-34 — shows proper permission denied message
**Suggested fix**: Replace `notFound()` with a permission-denied message for authenticated users who lack the required capability. Match the pattern used in `/settings/general`. Effort: **S** (15 minutes)

### GAP-P48-011: No close-period UI for retainers
**Day**: 30, 60
**Step**: 30.10-30.12, 60.1-60.3 — Create retainer invoices
**Category**: missing-feature
**Severity**: major
**Description**: The retainer agreement model has a full period lifecycle including `closePeriod()` which auto-generates invoices with base fee + overage lines. However, there is no frontend button to trigger period close. The retainer detail page exists (`/retainers/[id]`) but it's unclear whether it exposes a "Close Period" action. Without this, retainer billing must be done manually via the invoice creation flow (which requires unbilled time entries).
**Evidence**:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerPeriodService.java` — `closePeriod()` creates invoice with fee + overage
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerPeriodController.java` — endpoint exists
- `frontend/app/(app)/org/[slug]/retainers/[id]/page.tsx` — detail page exists
**Suggested fix**: Verify and ensure the retainer detail page has a "Close Period" button that triggers the backend period-close and displays the generated invoice. Effort: **S**

---

## Checkpoint Likelihood Summary

| Step | Description | Verdict | Notes |
|------|-------------|---------|-------|
| **Day 0** | | | |
| 0.1 | Login as Alice | LIKELY_PASS | Mock auth works |
| 0.2 | Dashboard shows getting-started card | LIKELY_PASS | `GettingStartedCard` component present |
| 0.3 | Navigate to Settings > General | LIKELY_PASS | Page exists with form |
| 0.4 | Set default currency to ZAR | LIKELY_PASS | Auto-seeded via vertical profile |
| 0.5 | Set brand colour | LIKELY_PASS | `GeneralSettingsForm` has brandColor field |
| 0.6 | Navigate to Settings > Rates | LIKELY_PASS | Page exists |
| 0.7-0.9 | Create billing rates | LIKELY_PASS | Rate CRUD works; not auto-seeded (GAP-P48-005) but manual creation works |
| 0.10-0.12 | Create cost rates | LIKELY_PASS | Same as above |
| 0.13 | Navigate to Settings > Tax | LIKELY_PASS | Page exists with `TaxRateTable` |
| 0.14 | Create VAT 15% tax rate | LIKELY_PASS | Tax CRUD works; not auto-seeded but manual creation works |
| 0.15 | Team page shows 3 members | LIKELY_PASS | Fixed in Phase 47 (GAP-025) |
| 0.16 | Custom fields exist | LIKELY_PASS | Field packs seeded via vertical profile |
| 0.17 | Templates listed | LIKELY_PASS | Template packs seeded (GAP-008 fixed) |
| 0.18 | Automations listed | LIKELY_PASS | 4 accounting-za automation templates seeded |
| **Day 1** | | | |
| 1.1-1.2 | Login as Bob, navigate to Customers | LIKELY_PASS | Page exists |
| 1.3-1.5 | Create customer | LIKELY_PASS | Customer CRUD works |
| 1.6 | Lifecycle badge shows PROSPECT | LIKELY_PASS | `LifecycleStatusBadge` component |
| 1.7-1.8 | Transition to ONBOARDING | LIKELY_PASS | `LifecycleTransitionDropdown` component |
| 1.9-1.10 | Onboarding tab with FICA checklist | LIKELY_PASS | Checklist instantiation works (GAP-026 fixed) |
| 1.11-1.13 | Complete checklist items | LIKELY_PASS | `ChecklistInstancePanel` supports item completion |
| 1.14-1.18 | Create and send information request | UNCERTAIN | Requires portal contact (GAP-P48-009). Email sending depends on Mailpit integration. |
| 1.19-1.24 | Create and send proposal | LIKELY_FAIL | No proposal creation UI (GAP-P48-001) |
| 1.25-1.28 | Activate customer | LIKELY_PASS | Auto-transition on checklist completion works |
| 1.29-1.32 | Create project | LIKELY_PASS | Project CRUD works |
| 1.33-1.35 | Create retainer | LIKELY_PASS | `CreateRetainerDialog` exists on retainers page |
| 1.36-1.38 | Create tasks | LIKELY_PASS | Task creation on project detail works |
| **Day 2-3** | | | |
| 2.1-2.14 | Create 3 more customers, projects, tasks | LIKELY_PASS | Repeat of Day 1 flow (minus proposals) |
| **Day 7** | | | |
| 7.1-7.2 | My Work shows assigned tasks | LIKELY_PASS | My Work page exists |
| 7.3-7.4 | Task status transition | LIKELY_PASS | Task status update works |
| 7.5-7.8 | Log time with rate snapshot | LIKELY_PASS | Time logging fixed in Phase 47 (GAP-030) |
| 7.9-7.13 | Comments and time logging | LIKELY_PASS | Comment section on projects works |
| 7.14-7.16 | Alice logs time with rate | LIKELY_PASS | Rate snapshot mechanism works |
| 7.17-7.18 | Activity feed and My Work verification | LIKELY_PASS | `ActivityFeed` component on project detail |
| **Day 14** | | | |
| 14.1-14.5 | More time logging | LIKELY_PASS | Repeat of Day 7 pattern |
| 14.6-14.7 | Notifications and Mailpit check | LIKELY_PASS | Notification page exists |
| **Day 30** | | | |
| 30.1-30.2 | Navigate to Invoices, create invoice | LIKELY_FAIL | No "New Invoice" on invoices page (GAP-P48-002). Workaround: go via customer detail. |
| 30.3-30.8 | Add line items with tax, verify totals | UNCERTAIN | Must create via customer, then add lines on detail page. Tax rate selection exists. Math not verified. |
| 30.9 | Auto-generated invoice number | LIKELY_PASS | `InvoiceNumberService` exists |
| 30.10-30.12 | Retainer invoice | LIKELY_FAIL | No retainer-specific invoice flow (GAP-P48-003). Workaround: manual line items. |
| 30.13-30.16 | Invoice lifecycle (Approve, Send) | LIKELY_PASS | Approve, "Mark as Sent", and email trigger all exist |
| 30.17-30.19 | Budget set and tracking | LIKELY_PASS | `BudgetPanel` on project detail |
| 30.20-30.21 | Profitability page | LIKELY_PASS | Page exists with data |
| **Day 45** | | | |
| 45.1-45.4 | Record payment | LIKELY_PASS | `Record Payment` button and `InvoicePaymentForm` exist |
| 45.5-45.8 | Expense logging | LIKELY_PASS | `LogExpenseDialog` on project expenses tab |
| 45.9-45.12 | Ad-hoc engagement creation | LIKELY_PASS | Standard project + task + time logging |
| 45.13-45.14 | Resource planning and utilization | LIKELY_PASS | Both pages exist |
| **Day 60** | | | |
| 60.1-60.4 | February invoices | UNCERTAIN | Same flow constraints as Day 30 |
| 60.5-60.8 | BEE advisory invoice | UNCERTAIN | Same flow constraints |
| 60.9-60.11 | Reports with CSV export | LIKELY_PASS | Report pages with CSV/PDF export actions exist |
| **Day 75** | | | |
| 75.1-75.2 | Year-end project creation | LIKELY_PASS | Standard project + task creation |
| 75.3-75.6 | Information request with items | UNCERTAIN | Requires portal contact (GAP-P48-009) |
| 75.7-75.8 | Time logging on new project | LIKELY_PASS | Standard time logging |
| 75.9-75.10 | Multi-engagement per customer | LIKELY_PASS | Multiple projects per customer supported |
| **Day 90** | | | |
| 90.1-90.5 | Portfolio review, invoice filtering | LIKELY_PASS | Customer list + invoice list with status filters |
| 90.6-90.8 | Profitability and utilization | LIKELY_PASS | Both pages exist |
| 90.9-90.10 | Dashboard KPIs and activity | LIKELY_PASS | `KpiCardRow`, `RecentActivityWidget` |
| 90.11-90.13 | Document generation and PDF | LIKELY_PASS | `GenerateDocumentDropdown` on customer detail |
| 90.14-90.15 | Compliance dashboard | LIKELY_PASS | Compliance page with lifecycle distribution |
| 90.16 | Carol blocked from rates (permission) | UNCERTAIN | Returns 404 not permission error (GAP-P48-010) |
| 90.17 | Carol sees My Work | LIKELY_PASS | My Work page accessible to members |
| 90.18 | Bob admin access | LIKELY_PASS | Admin capability check passes |

---

## Fork Readiness Assessment

**Overall verdict: ~80% ready** (up from ~75% in Phase 47)

### Improvements since Phase 47
- **+14 gaps resolved** from the previous cycle
- Terminology overrides working (accounting-specific labels: Engagements, Clients, Engagement Letters)
- Currency correctly defaults to ZAR
- FICA checklists activatable
- Automation triggers fully wired (PROPOSAL_SENT, FIELD_DATE_APPROACHING, CUSTOMER_STATUS_CHANGED)
- Settings hub page functional
- Trust field pack created (though not yet wired into profile)

### Remaining blockers to "could a firm actually run on this?"
1. **Proposal workflow** (GAP-P48-001) — blocker. Engagement letters are core to accounting firm onboarding. No way to create, edit, view, or send proposals from the UI.
2. **Invoice creation UX** (GAP-P48-002, GAP-P48-003) — major. Firms need to create both hourly and retainer invoices. Current flow only supports unbilled-time-based generation. Manual line item invoices (retainer fees, disbursements, flat fees) require a convoluted workaround.
3. **Auto-seeding of rates/tax** (GAP-P48-005) — major. 10+ manual steps during Day 0 setup that could be eliminated.

### What works well
- Customer lifecycle (PROSPECT > ONBOARDING > ACTIVE) with FICA compliance
- Time tracking with rate snapshots and billable/non-billable classification
- Document generation with SA-specific templates
- Profitability and budget tracking
- Resource planning and utilization
- Reports with CSV/PDF export
- Compliance dashboard
- Role-based access control
- Terminology overrides for the accounting vertical
- Multi-project per customer
- Comments, notifications, activity feeds

### Recommended priority for next sprint
1. **GAP-P48-001** (Proposal UI) — blocker, M effort
2. **GAP-P48-002** (Invoice creation from invoices page + blank draft) — major, S effort
3. **GAP-P48-004** (Trust field pack registration) — major, S effort (10 min fix)
4. **GAP-P48-005** (Rate/tax auto-seeding) — major, M effort
5. **GAP-P48-003** (Retainer period close UI) — major, S effort
