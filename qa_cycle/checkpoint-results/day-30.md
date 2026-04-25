# Day 30 — Sipho portal pays fee note via PayFast sandbox  `[PORTAL]`

Cycle: 1 turn 1 (verify) | Date: 2026-04-25 14:30 SAST | Auth: portal magic-link (Sipho) on :3002 + Keycloak (Bob admin) on :3000 | Backend: :8080 PID 80950 | Actor: Sipho Dlamini (portal) → Bob Ndlovu (firm sanity-check)

Scenario: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` → Day 30 (checkpoints 30.1–30.11).

## Verdict

**Day 30: 6/11 PARTIAL — portal route works, invoice renders correctly, isolation holds; PayFast redirect step BLOCKED by HIGH GAP-L-64 (no PaymentGateway adapter configured on Mathebula tenant → portal renders "Contact Mathebula & Partners to arrange payment" instead of Pay button; firm-side email "View Invoice" link is also empty href). Halted per scenario rule — did not fake payment via SQL/REST.**

Two related defects surfaced on the same code path:

1. **Portal Pay button missing** — `Invoice.payment_url=NULL` because `PayFastPaymentGateway` requires `merchant_id`/`merchant_key` from `SecretStore`, and `tenant_5039f2d497cf.org_integrations` has **0 rows**. `IntegrationRegistry.resolve(PAYMENT, ...)` therefore returns `NoOpPaymentGateway` whose `createCheckoutSession()` returns `notSupported`. `PaymentLinkService.generatePaymentLink` then exits early without setting `payment_url`. Portal render path `invoice.status === "SENT" && !invoice.paymentUrl` shows the fallback panel "Contact {orgName} to arrange payment" (`portal/app/(authenticated)/invoices/[id]/page.tsx:155-160`).

2. **Email "View Invoice" link is empty href** — `curl /api/v1/message/<id>` on the INV-0001 email shows the Pay-Now block (gated on `paymentUrl`) is correctly omitted, BUT a separate "View Invoice" CTA still renders with `href=""` (empty string). This is independent of payment configuration: the link should always go to the portal invoice URL, even when no payment provider is configured. Logged as part of GAP-L-64.

## Pre-flight

- Sipho's portal JWT (Tab 1) had expired — bounced to `/login`. No unexpired magic-link in Mailpit (REQ-0004 link expired; REQ-0005 was for Moroka).
- **Re-issued** by typing `sipho.portal@example.com` into portal `/login?orgId=mathebula-partners` → "Send Magic Link" → portal returned a new dev-mode magic-link inline → clicked → exchanged for a fresh portal JWT → landed on `/projects`.
- Bob's firm session (Tab 0) preserved untouched.

## Checkpoint execution

| Checkpoint | Result | Evidence |
|---|---|---|
| 30.1 | **PARTIAL — portal route works, but email "View Invoice" CTA has empty href** | Mailpit shows email subject "**Invoice INV-0001 from Mathebula & Partners**" sent to `sipho.portal@example.com` at 2026-04-25 12:12 UTC. Body lists Invoice Number `INV-0001`, Due Date `N/A`, Amount Due `ZAR 1250.00`. **However**, the "View Invoice" CTA renders with `href=""` (HTML inspected via Mailpit GET — only the S3 logo URL is present; no portal link). The Pay-Now block is correctly suppressed (commented `<!-- Pay Now button (only shown if paymentUrl is non-null) -->` and the conditional renders an empty wrapper). Subject contains "Invoice" rather than "Fee Note" — minor terminology drift. Portal `/invoices` reachable via the sidebar after fresh magic-link login. |
| 30.2 | **PASS** | Portal `/invoices/8f718728-...` renders header `INV-0001` SENT chip, Issued `25 Apr 2026`, Due empty (matches DB `due_date=NULL`). Line item "Sheriff fees: Sheriff service of summons on RAF — Day 21 cycle-1 verify (Sheriff Pretoria, 2026-04-25), Qty 1, R 1 250,00, Zero-rated 0%, R 1 250,00". Subtotal R 1 250,00; Zero-rated (0%) R 0,00; Total R 1 250,00. Snapshot `day-30-cycle1-portal-invoice-detail.{png,yml}`. |
| 30.3 | **OBSERVATION — terminology drift** | Portal copy reads "**Invoices**" (sidebar), heading "INV-0001", "Back to invoices", body "Contact Mathebula & Partners to arrange payment". Firm side reads "**Fee Note**" / "fee notes" / "Generate Fee Note" / "New Fee Note". The terminology override does not propagate to portal renderings. Email subject also says "Invoice", not "Fee Note". URL path `/invoices/[id]` is consistent both sides. Logged as **GAP-L-65 (LOW — portal terminology override does not apply)**. |
| 30.4 | **PASS** | Screenshot `day-30-cycle1-portal-invoice-detail.png` saved (full page). |
| 30.5 | **FAIL — BLOCKER (GAP-L-64)** | **No "Pay" / "Pay Now" button on the portal invoice detail page.** Page shows fallback panel: "Contact Mathebula & Partners to arrange payment". Source: `portal/app/(authenticated)/invoices/[id]/page.tsx:155-160` `{invoice.status === "SENT" && !invoice.paymentUrl && (...)}`. DB confirms `payment_url=NULL` and `payment_session_id=NULL`. Root cause: `tenant_5039f2d497cf.org_integrations` has 0 rows → `IntegrationRegistry.resolve(PAYMENT, PaymentGateway.class)` falls back to `NoOpPaymentGateway` (`@IntegrationAdapter(domain=PAYMENT, slug="noop")`), whose `createCheckoutSession()` returns `notSupported`. `PaymentLinkService.generatePaymentLink` (line 62-68) then exits early without setting `payment_url`. **Cannot proceed to PayFast sandbox.** |
| 30.6 | **BLOCKED** | No Pay button → no PayFast sandbox redirect → cannot complete sandbox transaction. Per scenario hard rule: "If PayFast not configured / sandbox unreachable, log as gap and HALT — don't fake payment via SQL or REST." |
| 30.7 | **BLOCKED** | Cannot reach status flip without successful sandbox round-trip. Invoice still SENT in DB. |
| 30.8 | **N/A — alternative observed** | Receipt download not applicable until paid. **However**, "Download PDF" button is available on the portal invoice detail (top-right). Not exercised in this turn (out of Day 30 scope; covered as Day 28 acceptance). |
| 30.9 | **N/A** | No success state to capture. |
| 30.10 | **N/A** | Cannot transition Sent → Paid filter without a successful payment. |
| 30.11 | **PASS — passive isolation holds** | Portal `/invoices` list shows exactly one row: `INV-0001 SENT R 1 250,00`. **No Moroka invoices visible.** Tenant `tenant_5039f2d497cf` has no other invoices for any other customer at this point in the lifecycle, so this is partly a passive observation. Sipho's portal scope correctly grants access only to his own invoice (snapshot `day-30-cycle1-portal-invoices-list.{png,yml}`). Direct-URL probe to Sipho's invoice ID succeeds; portal correctly resolves portal JWT to PortalContact → Customer → Invoice path. |

## Day 30 summary checks

- [ ] PayFast sandbox payment completes end-to-end (webhook-driven reconciliation works) → **BLOCKED** by GAP-L-64
- [ ] Firm-side fee note reflects PAID within 60s → **N/A** — invoice still SENT firm-side; "Payment History — No payment events yet"; "Record Payment" / "Void" actions available manually but not used (would bypass scenario-mandated PayFast flow)
- [ ] Receipt download works → **N/A** (no payment to receipt)
- [ ] Isolation still holding — no Moroka fee notes visible → **PASS**

## DB confirmation (read-only SELECT)

```sql
-- Invoice state
SELECT id, invoice_number, status, total, currency, paid_at, payment_url, payment_session_id
FROM tenant_5039f2d497cf.invoices
WHERE id='8f718728-5fb6-40fe-abf1-2cafc86c0f10';
-- → INV-0001 | SENT | 1250.00 | ZAR | (paid_at NULL) | (payment_url NULL) | (payment_session_id NULL)

-- Payment events
SELECT count(*) FROM tenant_5039f2d497cf.payment_events
WHERE invoice_id='8f718728-5fb6-40fe-abf1-2cafc86c0f10';
-- → 0

-- Org integrations (root cause)
SELECT count(*) FROM tenant_5039f2d497cf.org_integrations;
-- → 0
```

Note: Database has table `payment_events` (not `invoice_payments` as referenced in the prompt). Schema columns: `id, invoice_id, provider_slug, session_id, payment_reference, status, amount, currency, payment_destination, provider_payload, created_at, updated_at`.

## New gaps opened

| GAP_ID | Severity | Summary |
|--------|----------|---------|
| GAP-L-64 | **HIGH** | Mathebula tenant has no `org_integrations` row for `domain=PAYMENT` → `IntegrationRegistry.resolve` falls through to `NoOpPaymentGateway` → `Invoice.payment_url=NULL` after Send → portal shows "Contact Mathebula & Partners to arrange payment" instead of Pay button → email "View Invoice" CTA also has `href=""`. Day 30 PayFast sandbox flow is unreachable end-to-end on the dev stack. Two fixable surfaces: (a) seed PayFast sandbox `OrgIntegration` row on the legal-za pack install (or via `/settings/integrations` UI if it exists) so `payment_url` populates on Send; (b) email template's "View Invoice" link should always render the portal invoice URL even when `paymentUrl` is null (current behaviour: empty href is broken UX even in the correct no-PSP path). Scenario step 0.G acknowledges this prerequisite ("Confirm PayFast sandbox credentials are configured in OrgIntegration seed data OR set up a firm-side stub adapter for Day 30") — confirming this is a known scenario prerequisite that was never satisfied. |
| GAP-L-65 | LOW | Portal terminology override does not apply: portal still says "Invoices" / "INV-0001" / "Back to invoices" while firm side correctly uses "Fee Note" / "fee notes". Email subject also says "Invoice INV-0001 from Mathebula & Partners". The `legal-za` vertical defines `terminology.invoice = "Fee Note"` (per Day 28 firm-side observation) but portal + email rendering bypass the override. |

## Tab state preserved

- Tab 0 — Bob firm session (Mathebula) on `/invoices/8f718728-...` — alive.
- Tab 1 — Sipho portal session (port 3002) on `/invoices/8f718728-...` — alive (fresh JWT issued this turn). Re-usable for Day 46.

## Browser-driven confirmation

All UI checkpoints driven via Playwright MCP browser tabs. Mailpit GET used only for invoice email content inspection (legitimate per QA cycle rules). Read-only SQL SELECT used for DB confirmation. No state mutations via REST or SQL. PayFast sandbox redirect not faked.
