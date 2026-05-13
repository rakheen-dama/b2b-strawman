# Day 30 -- Sipho pays fee note via PayFast sandbox -- PARTIAL (Payment blocked)

**Date**: 2026-05-14 (cycle 1, current)
**Actor**: Sipho Dlamini (portal `:3002`)
**Stack**: Keycloak dev stack -- portal :3002, backend :8080, Mailpit :8025
**Result**: PARTIAL -- Fee note detail renders correctly with all line items and totals. Payment flow blocked because mock payment integration was not seeded during tenant provisioning. Portal shows "Contact Mathebula & Partners to arrange payment" instead of "Pay Now" CTA.

---

## Pre-condition -- Sipho portal session

| ck | Step | Result |
|----|------|--------|
| 30.0a | Generate magic link via `POST /portal/dev/generate-link` for `sipho.portal@example.com` @ `mathebula-partners` | PASS -- link returned. |
| 30.0b | Exchange token at `http://localhost:3002/auth/exchange?token=...&orgId=mathebula-partners` | PASS -- redirected to portal `/projects` with Sipho session cookie. User identity confirmed as "Sipho Dlamini". |

## Step 1 -- Fee note email and portal navigation

| ck | Step | Result |
|----|------|--------|
| 30.1 | Mailpit: fee note email to `sipho.portal@example.com` with subject "Fee Note INV-0001 from Mathebula & Partners" | PASS -- email present (Mailpit ID `HSnLS7m7ZmFod9QDLEtuz2`). Subject uses "Fee Note" (correct terminology). Body contains View Fee Note link to `http://localhost:3002/invoices/f3babc90-f693-439e-8df2-4bf289c42c7b`. Amount shows ZAR 1437.50. |
| 30.1b | Navigate to `/invoices/f3babc90-f693-439e-8df2-4bf289c42c7b` via email link | PASS -- page renders. Header: `INV-0001 [SENT]`, `Issued: 14 May 2026`. |

## Step 2 -- Fee note detail verification

| ck | Step | Result |
|----|------|--------|
| 30.2 | Verify line items + totals | PASS -- 3 line items rendered correctly: |
| | | Line 1: "Initial RAF claim assessment & instructions -- 2026-05-14 -- Bob Ndlovu" qty 2.5, R 0,00 (TIME, no rate card) |
| | | Line 2: "File RAF1 claim form + supporting documents (within 3-year prescription) -- 2026-05-14 -- Bob Ndlovu" qty 1.5, R 0,00 (TIME, no rate card) |
| | | Line 3: "Sheriff fees: Sheriff service of summons on RAF (Sheriff Sandton, 2026-05-14)" qty 1, R 1,250.00 (EXPENSE) |
| | | Subtotal: R 1,250.00 |
| | | VAT -- Standard (15%): R 187.50 |
| | | **Total: R 1,437.50** (matches expected amount from Day 28) |
| 30.3 | Terminology consistency | PASS -- sidebar nav uses "Fee Notes", back link reads "Back to fee notes", Download button reads "Download INV-0001 as PDF". URL slug is `/invoices` (consistent with firm-side routing). No terminology leak. |
| 30.4 | Screenshot captured | PASS -- `qa_cycle/evidence/day-30/day-30-portal-fee-note-detail.png` |

## Step 3 -- Payment flow

| ck | Step | Result |
|----|------|--------|
| 30.5 | Pay Now button visible | **FAIL** -- No Pay Now CTA rendered. Page instead shows: "Contact Mathebula & Partners to arrange payment". This occurs because `invoice.paymentUrl` is null. |
| 30.6 | Complete payment via mock PSP | **BLOCKED** -- Cannot proceed; no payment URL generated. |
| 30.7 | Payment success page | BLOCKED -- depends on 30.6. |
| 30.8 | Receipt download | BLOCKED -- depends on 30.6. |
| 30.9 | Fee note status transitions to PAID | BLOCKED -- depends on 30.6. |

### Root cause analysis

The mock payment integration adapter was NOT seeded for this tenant:

1. **`TenantProvisioningService`** does NOT call `MockPaymentIntegrationSeeder.seedForTenant()` during initial tenant provisioning (only calls `standardReportPackSeeder` and `legalTariffSeeder`).
2. **`PackReconciliationRunner`** DOES call `mockPaymentIntegrationSeeder.seedForTenant()`, but it runs at backend startup. When the backend started, no tenant schemas existed yet (`"No tenant schemas found -- skipping pack reconciliation"`). By the time the tenant was provisioned (Day 0), the runner had already completed.
3. Database confirmation: `SELECT * FROM tenant_5039f2d497cf.org_integrations` returns **0 rows** -- no PAYMENT integration configured.
4. As a result, `IntegrationRegistry.resolve(PAYMENT)` returns the `NoOpPaymentGateway`, which returns `CreateSessionResult.notSupported()` from `createCheckoutSession()`.
5. `PaymentLinkService.generatePaymentLink()` checks `!result.supported()` and returns without setting `paymentUrl` on the invoice.
6. Portal frontend checks `invoice.paymentUrl` -- finds null -- shows fallback "Contact {orgName}" message.

**Fix required**: `TenantProvisioningService` should call `MockPaymentIntegrationSeeder.seedForTenant()` alongside the other seeders, OR `PackReconciliationRunner` should be triggered after each new tenant provision.

## Step 4 -- Isolation and list view

| ck | Step | Result |
|----|------|--------|
| 30.10 | Navigate to `/invoices` list | PASS -- Fee Notes page renders. Only INV-0001 visible (status SENT, R 1,437.50). No "Due" vs "Paid" filter tabs; single list view. |
| 30.11 | Passive isolation spot-check | PASS -- Only Sipho's INV-0001 visible. No Moroka fee notes present. Heading says "Fee Notes". |

Evidence: `qa_cycle/evidence/day-30/day-30-portal-fee-notes-list-isolation.png`

## Console errors

Zero JavaScript console errors during Day 30 navigation.

## Day 30 checkpoints

- [ ] PayFast sandbox / mock PSP payment completes end-to-end -- **BLOCKED** (mock payment integration not seeded; no `paymentUrl` on invoice; no Pay Now button rendered).
- [ ] Firm-side fee note reflects PAID -- **BLOCKED** (depends on payment completion).
- [ ] Receipt download works -- **BLOCKED** (depends on payment completion).
- [x] Isolation still holding -- **PASS** (only Sipho's INV-0001 visible on `/invoices` list; no Moroka fee notes).

## New gap

| Gap ID | Summary | Severity | Owner | Day | Notes |
|--------|---------|----------|-------|-----|-------|
| OBS-3001 | Mock payment integration not seeded during tenant provisioning -- portal shows "Contact firm to arrange payment" instead of Pay Now | HIGH | Dev | 30 | `TenantProvisioningService` does not call `MockPaymentIntegrationSeeder.seedForTenant()`. `PackReconciliationRunner` only runs at startup before tenant exists. Fix: add seeder call to provisioning flow or trigger reconciliation post-provision. Mandate note: PayFast as third-party PSP is exempt, but mock provider should work for dev/QA to exercise the payment lifecycle end-to-end. |

## QA Position

**Day 30**: PARTIAL. Fee note detail renders correctly (line items, totals, terminology all verified). Payment flow blocked by OBS-3001 (mock payment integration not seeded). Isolation holds. Advancing to next step requires OBS-3001 fix.

---

**Time on day**: ~15 min.
**Tool count**: ~40 calls.
