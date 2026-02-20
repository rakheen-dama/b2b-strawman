# Gap Analysis Fixes — Implementation Plan

Groups B, C, D from `accepted-gap-analysis.md`. Three independent, incrementally deployable parts.

---

## Part 1: Group C — Comment Visibility Enum Fix (Backend)

**Why first**: Security-relevant bug, smallest blast radius, zero frontend changes, fully testable in isolation.

### Changes

**1. `backend/src/main/java/io/b2mash/b2b/b2bstrawman/comment/CommentService.java`**
- [ ] Line 80: `"EXTERNAL"` → `"SHARED"` in the create privilege gate
- [ ] Line 159: `"EXTERNAL"` → `"SHARED"` in the update visibility validation

**2. `backend/src/test/java/.../comment/CommentServiceIntegrationTest.java`**
- [ ] Replace all `"EXTERNAL"` with `"SHARED"` (lines 235, 240, 256, 329)
- [ ] Add a new test: `createComment_memberCannotCreateSharedComment` — proves a regular member (ORG_MEMBER role, non-lead) sending `visibility: "SHARED"` gets 403. This is the key regression test — the bug was that this check was frontend-only.

**3. `backend/src/test/java/.../datarequest/DataAnonymizationServiceTest.java`**
- [ ] Line 446: `"EXTERNAL"` → `"SHARED"` (test creates a shared comment for anonymization testing)

### Verification
- [ ] `./mvnw test -Dtest=CommentServiceIntegrationTest -q`
- [ ] `./mvnw test -Dtest=DataAnonymizationServiceTest -q`
- [ ] Grep codebase for remaining `"EXTERNAL"` in comment context — should be zero

---

## Part 2: Group B — Invoice Creation Link Fix (Frontend)

**Why second**: Eliminates user-facing 404s, frontend-only, no backend changes.

### Changes

**1. `frontend/components/projects/overview-tab.tsx`** (line ~170-179)

The project overview's "Unbilled Time" ActionCard currently links to `/org/${slug}/invoices/new?projectId=${projectId}` (404).

Fix: Thread `customerId` through to the OverviewTab and redirect to the customer's Invoices tab.

- [ ] Add `customerId: string | null` to `OverviewTabProps`
- [ ] Change ActionCard `primaryAction.href` from the dead `/invoices/new` route to:
  ```
  `/org/${slug}/customers/${customerId}?tab=invoices`
  ```
- [ ] Guard: only show primaryAction when `customerId` is non-null (can't invoice without a linked customer)

**2. `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx`** (line ~417-429)

- [ ] Pass `customerId={customers.length > 0 ? customers[0].id : null}` to `<OverviewTab>`

**3. `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx`** (line ~495)

The customer overview's "Unbilled Time" ActionCard links to `/org/${slug}/invoices/new?customerId=${id}` (404). Since we're already on the customer page:

- [ ] Change `href` to `?tab=invoices` (same page, switch to Invoices tab where `InvoiceGenerationDialog` lives)

**4. Test updates**
- [ ] `frontend/__tests__/components/projects/overview-tab-setup.test.tsx` line 111: Update expected href
- [ ] `frontend/__tests__/components/customers/customer-setup-guidance.test.tsx` line 93: Update expected href
- [ ] `frontend/__tests__/components/action-card.test.tsx` line 36/42: Update test fixture href (optional — generic component test)

### Verification
- [ ] `pnpm test --run` (all frontend tests pass)
- [ ] `pnpm build` (no broken links at build time)
- [ ] Manual: click "Create Invoice" ActionCard on project overview → lands on customer Invoices tab
- [ ] Manual: click "Create Invoice" ActionCard on customer overview → switches to Invoices tab

---

## Part 3: Group D — Retainer UX Polish (Frontend)

**Why third**: UX enhancement, no backend changes, lowest urgency.

### Part 3a: Post-close invoice navigation (`close-period-dialog.tsx`)

- [ ] Add `useRouter` from `next/navigation` to the dialog
- [ ] After successful close, navigate to the generated invoice:
  ```tsx
  if (result.success && result.data?.generatedInvoice?.id) {
    onOpenChange(false);
    router.push(`/org/${slug}/invoices/${result.data.generatedInvoice.id}`);
  }
  ```
- [ ] The `slug` prop is already available in the component props

### Part 3b: Retainer consumption alert banners (`retainers/[id]/page.tsx`)

- [ ] Compute `consumptionPercent` from `retainer.currentPeriod.consumedHours / retainer.allocatedHours * 100`
- [ ] Add conditional alert banners above or inside the "Current Period" card:
  - At >= 100%: destructive variant — "Retainer fully consumed — additional hours are overage."
  - At >= 80% and < 100%: warning variant — "Retainer at X% capacity — approaching limit."
- [ ] Import `Alert` / `AlertDescription` from `@/components/ui/alert` and `AlertTriangle` icon
- [ ] Only show for HOUR_BANK type retainers (FIXED_FEE has no hour tracking)
- [ ] Only show when `retainer.currentPeriod` exists and status is OPEN

### Verification
- [ ] Manual: close a retainer period → browser navigates to the generated draft invoice
- [ ] Manual: log time until retainer hits 80% → amber alert appears on detail page
- [ ] Manual: log time past 100% → red alert appears
- [ ] `pnpm build` passes

---

## Summary

| Part | Group | Layer | Files Changed | Risk |
|------|-------|-------|--------------|------|
| 1 | C | Backend | 3 (service + 2 tests) | Low — string replacements + 1 new test |
| 2 | B | Frontend | 5 (2 production + 3 tests) | Low — href changes, one new prop |
| 3 | D | Frontend | 2 (dialog + detail page) | Low — additive UI, no existing behavior changes |

Each part is independently deployable and testable. A single agent can handle all three in sequence (they share no files), or they can be done in parallel by separate agents on separate branches.
