# Fix Spec: GAP-L-64 — PayFast prerequisite missing → portal Pay button + email "View Invoice" CTA broken

## Problem (verbatim from QA evidence)

Day 30 cycle-1 verify (`qa_cycle/checkpoint-results/day-30.md`,
2026-04-25 14:30 SAST) HALTED at checkpoint 30.5 per scenario hard rule. Two
defects on the same code path:

1. **Portal Pay button missing.** `Invoice.payment_url=NULL` on INV-0001
   (`tenant_5039f2d497cf.invoices.8f718728-5fb6-40fe-abf1-2cafc86c0f10`)
   because `tenant_5039f2d497cf.org_integrations` has **0 rows**.
   `IntegrationRegistry.resolve(IntegrationDomain.PAYMENT, PaymentGateway.class)`
   therefore falls back to `NoOpPaymentGateway`
   (`@IntegrationAdapter(domain=PAYMENT, slug="noop")` —
   `integration/payment/NoOpPaymentGateway.java`). NoOp's
   `createCheckoutSession()` returns `CreateSessionResult.notSupported(...)`.
   `PaymentLinkService.generatePaymentLink()` (lines 62–68) exits early with
   `if (!result.success() || !result.supported()) return;` — `payment_url`
   and `payment_session_id` stay `NULL` on Send. Portal render path
   `portal/app/(authenticated)/invoices/[id]/page.tsx:155-160`
   `{invoice.status === "SENT" && !invoice.paymentUrl && (...)}` shows the
   fallback panel "Contact Mathebula & Partners to arrange payment" instead
   of a Pay button → PayFast sandbox redirect cannot be triggered →
   exit-checkpoint E.12 (Day 28 generation + Day 30 PayFast payment) cannot
   be satisfied.

2. **Email "View Invoice" CTA has empty href.** Mailpit message
   `DB9hqMbLXzRzDdRgB7jQGh` ("Invoice INV-0001 from Mathebula & Partners"
   sent to `sipho.portal@example.com` 2026-04-25 12:12 UTC) — Pay-Now block
   is correctly suppressed (gated on `${paymentUrl}` —
   `templates/email/invoice-delivery.html:37`), but the **separate** "View
   Invoice" CTA at line 51 renders with `href=""`. The template uses
   `th:href="${portalUrl}"`, so the model is passing an empty `portalUrl`.
   This is independent of payment configuration — even with no PSP, the
   link should always go to the portal invoice URL. Currently broken UX in
   the no-PSP path.

## Scenario authority for the chosen approach

Scenario step 0.G (`qa/testplan/demos/legal-za-full-lifecycle-keycloak.md:105`)
reads verbatim:

> **0.G** Confirm PayFast sandbox credentials are configured in
> `OrgIntegration` seed data **OR set up a firm-side stub adapter for Day
> 30** — document which path is in use

The scenario explicitly authorises a stub-adapter path. We take it. Real
PayFast sandbox requires `merchant_id` / `merchant_key` / `passphrase` env
vars and a `notify_url` reachable from `sandbox.payfast.co.za` (ngrok tunnel
locally) — out-of-scope for an in-session QA cycle.

## Blast radius (why fix, not defer)

A defer would block these downstream scenario steps:

- **Day 60.5** closure gate report requires GREEN "no unpaid fee notes" —
  INV-0001 sitting at SENT (unpaid) fails this gate → matter cannot CLOSE
  through the happy path → Days 60–61 SoA generation blocked.
- **Day 61.4** Statement of Account contents must reconcile to "Day 30 fee
  note paid (amount)" — without a paid Day 30 invoice the SoA either omits
  or mis-renders that line.
- **Day 88.4** activity trail must show "fee-note paid (Day 30)" as one of
  six client-visible events.
- **Exit checkpoint E.12** ("Fee note + payment flow — Day 28 generation +
  Day 30 PayFast sandbox payment completes end-to-end; firm PAID status
  reflects within 60s") is a demo-blocking exit gate.

Conclusion: an unpaid INV-0001 cascades through 4 downstream scenario
steps. **Fix-in-cycle.**

## Chosen approach: dev-only `MockPaymentGateway` adapter + auto-enabled on legal-za pack install + email `portalUrl` always-populated

Two thin surfaces:

### Surface A — `MockPaymentGateway` (slug = `mock`) + auto-seed on legal-za install

A new adapter class `MockPaymentGateway` registered under
`@IntegrationAdapter(domain = PAYMENT, slug = "mock")` that simulates a full
PSP round-trip locally without any real network calls. On `legal-za` pack
install, an `OrgIntegration` row is seeded with `domain=PAYMENT,
provider_slug="mock", enabled=true` so `IntegrationRegistry.resolve` returns
the mock instead of NoOp.

**Behaviour spec for `MockPaymentGateway`:**

- `providerId()` → `"mock"`
- `createCheckoutSession(CheckoutRequest req)` →
  `CreateSessionResult.success(sessionId, redirectUrl)` where:
  - `sessionId = "MOCK-SESS-" + UUID.randomUUID()`
  - `redirectUrl = "{frontendBaseUrl}/dev/mock-payment?sessionId={sessionId}&invoiceId={req.invoiceId}&amount={req.amount}&currency={req.currency}&returnUrl={urlencoded(req.successUrl)}"`
- `handleWebhook(payload, headers)` → parse JSON `{sessionId, status,
  reference, amount, currency}` → return `WebhookResult(success=true,
  status=PAID, reference, sessionId, paymentDestination=null,
  metadata=Map.of("provider","mock"))` for `status=PAID`, else `FAILED`.
- `queryPaymentStatus(sessionId)` → `PaymentStatus.PAID` for any sessionId
  matching the `MOCK-SESS-` prefix that has been "paid" in-memory; else
  `PENDING`. Use a `ConcurrentHashMap<String,PaymentStatus>` keyed by
  sessionId.
- `recordManualPayment(req)` → mirror NoOp behaviour (generate
  `MOCK-MANUAL-` reference).
- `testConnection()` → `new ConnectionTestResult(true, "mock", null)`.

Mark the bean `@Profile({"local","dev","keycloak","test"})` so it never
loads in `prod`.

**Mock-payment dev page** — a tiny dev-only Next.js route at
`frontend/app/dev/mock-payment/page.tsx` (or a Spring controller — pick
whichever is cheaper given the existing `dev/` portal harness pattern) that
renders:

- Order summary (invoice ID, amount, currency)
- Two buttons: **"Simulate Successful Payment"** → POST to
  `/api/payments/mock/complete` with `{sessionId, status:"PAID"}` →
  backend invokes `PaymentWebhookService.handleWebhook(...)` synchronously
  with the mock payload → invoice flips SENT→PAID → redirect to
  `successUrl`. **"Simulate Failed Payment"** → analogous with
  `status=FAILED`.
- Profile-gated `@Profile({"local","dev","keycloak"})` — never reachable in
  `test` (where there's no Spring web context for portal pages anyway) or
  `prod`.

This exercises the **full IntegrationRegistry resolution path**, the
`payment_url` plumbing, the portal Pay-button render, the redirect
roundtrip, the webhook reconciliation, and the
SENT→PAID status flip with `payment_events` row insertion. The only thing
it does NOT exercise is real PayFast cryptographic signature verification —
acceptable for a verify cycle whose scope is "re-validate 40+ shipped
fixes", not "validate PayFast adapter".

**Auto-seed on legal-za install:** in
`PackReconciliationRunner` (or wherever
the legal-za vertical pack install hook fires —
`profile-loader/VerticalProfileService.installVerticalPack(...)` is the
likely call site; confirm during implementation), append a step that
upserts an `OrgIntegration` row when `domain=PAYMENT` is missing for the
tenant **and** the active profile env is `local|dev|keycloak`:

```
INSERT INTO {tenant_schema}.org_integrations
  (id, domain, provider_slug, enabled, config_json, created_at, updated_at)
VALUES (gen_random_uuid(), 'PAYMENT', 'mock', true, '{}'::jsonb, now(), now())
ON CONFLICT (domain) DO NOTHING;
```

Do **not** seed in `prod` profile — production tenants must configure a
real PSP through the integrations UI (whose existence/scope is a separate
question; not in this fix's scope).

### Surface B — email "View Invoice" CTA always populated

`InvoiceDeliveryEmailService` (find via
`grep -rn "invoice-delivery" backend/src/main/java`) already builds the
template model with `paymentUrl` (currently NULL when no PSP) and an
implicit `portalUrl`. The `portalUrl` is being passed empty.

Fix:

- Locate the model-building site and ensure `portalUrl` is always set to
  `{portalBaseUrl}/invoices/{invoiceId}` (`portalBaseUrl` resolved the same
  way as L-50 fix did for acceptance emails — likely
  `org_settings.portal_base_url` or app property
  `portal.base-url=http://localhost:3002`).
- Add a Vitest/Junit guard: model assertion that `portalUrl` is non-blank
  for every invoice-delivery email regardless of `paymentUrl` state.

This change is independent of Surface A and ships in the same PR for
atomicity.

## Files to modify / create

| Path | Change |
|---|---|
| `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/payment/MockPaymentGateway.java` | **NEW.** ~120 lines. `@IntegrationAdapter(domain=PAYMENT, slug="mock")`, `@Profile({"local","dev","keycloak","test"})`. Implements `PaymentGateway`. |
| `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/payment/MockPaymentController.java` | **NEW.** ~40 lines. `@Profile({"local","dev","keycloak"})` guarded. `POST /api/payments/mock/complete` calls `PaymentWebhookService.handleWebhook("mock", payload, Map.of())`. |
| `frontend/app/dev/mock-payment/page.tsx` | **NEW.** ~80 lines. Dev-only mock checkout page (gated server-side on `process.env.NODE_ENV !== "production"`). |
| `backend/src/main/resources/db/migration/tenant/V113__seed_mock_payment_integration.sql` | **NEW** (idempotent). Seeds `org_integrations` row only when `domain=PAYMENT` is missing AND the spring profile is non-prod. **Note:** Flyway can't read profiles directly — use a tenant-side migration that runs unconditionally and a separate prod-cleanup story (or skip Flyway altogether and do the seed in `PackReconciliationRunner` Java code, gated on profile). **Preferred:** Java-side seed in the runner, no migration. Reasoning: production tenants will have legal-za pack but must NOT have a `mock` adapter row. |
| `backend/.../packreconciliation/PackReconciliationRunner.java` (or wherever legal-za hook fires — confirm during implementation) | Add `seedDevPaymentIntegration(tenantSchema)` step gated on `Environment.acceptsProfiles("local", "dev", "keycloak")`. Idempotent insert. |
| `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceDeliveryEmailService.java` (or actual class — confirm) | Set `portalUrl = portalBaseUrl + "/invoices/" + invoiceId` unconditionally in the template model. Reuses portal-base-url resolution from L-50 fix. |
| `backend/src/test/.../MockPaymentGatewayTest.java` | **NEW.** Unit test on the gateway: `createCheckoutSession` returns success with `MOCK-SESS-` prefix; `handleWebhook` PAID payload returns `WebhookResult(status=PAID)`; `queryPaymentStatus` returns PAID after webhook fires for the same sessionId. |
| `backend/src/test/.../InvoiceDeliveryEmailIntegrationTest.java` (or extend existing) | **NEW assertion.** `portalUrl` is non-blank in the email model whether the tenant has a PSP configured or not. Run two cases: no `org_integrations` row + mock PSP row. Both must yield non-blank `portalUrl`. |
| `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` | **Add documentation note** under step 0.G: "Verify cycle uses the dev `mock` adapter (auto-seeded by `PackReconciliationRunner` on legal-za install)." |

**Estimated effort:** M (~3–4 hours) — most of the time is in the email
service fix + Java pack-reconciliation hook + dev-only mock-payment page.
The gateway class itself is mechanical (~120 lines following the NoOp +
PayFast templates).

**NEEDS_REBUILD: true** — Java + Spring profile config; backend must
restart after merge (frontend HMR picks up the dev page automatically).

## Migration needed?

**No Flyway migration.** The `org_integrations` row is seeded via Java in
`PackReconciliationRunner` because the seed must be profile-gated and
Flyway cannot read Spring profiles. The schema is already in place
(`V???` prior migration that created `org_integrations`).

## Verification plan (browser-driven, Day 30 re-walk)

After merge + backend restart:

1. **Pre-flight:** read-only SQL `SELECT * FROM
   tenant_5039f2d497cf.org_integrations` → expect 1 row
   `domain=PAYMENT, provider_slug='mock', enabled=true`.
2. **Pre-flight:** confirm INV-0001 still SENT in DB (no state mutation
   needed — the fix path triggers at portal click, not at DB-row
   creation). If invoice was created BEFORE the seed ran, payment_url is
   NULL — re-issue the invoice via firm UI Send-flow OR backfill via
   service-method that recomputes `payment_url`. **Recommended:** ship the
   spec with a `bin/backfill-mock-payment-url.sh` script that recomputes
   `payment_url` for any SENT invoice with NULL `payment_url` after the
   seed lands. (Optional — QA can re-issue manually as well.)
3. **30.1 (email):** open Mailpit, click into the INV-0001 email →
   "View Invoice" CTA href is `http://localhost:3002/invoices/<id>` (not
   empty) → click → portal renders.
4. **30.5 (portal Pay button):** Sipho portal `/invoices/<id>` → "Pay
   Now" button visible (was missing). Click → redirect to dev mock-payment
   page at `/dev/mock-payment?sessionId=MOCK-SESS-...`.
5. **30.6 (sandbox payment):** click "Simulate Successful Payment" →
   portal redirects back to `/invoices/<id>?paymentSuccess=1` → status
   flips to PAID within 5s (poll) → `payment_events` row inserted with
   `status=PAID, provider_slug=mock, reference=...`.
6. **30.7 (firm-side flip within 60s):** Tab 0 firm `/invoices/<id>` → status
   PAID, "Payment History" lists the mock event with timestamp.
7. **30.8 (receipt):** Download Receipt button visible on portal → PDF
   downloads cleanly.
8. **30.9 (screenshot):** capture `day-30-portal-payment-success.png`.
9. **30.10 (filter):** portal `/invoices?status=Paid` lists INV-0001;
   `?status=Sent` does not.
10. **30.11 (isolation re-check):** Sipho still cannot see Moroka invoices
    (no new attack surface).
11. **DB confirmation:**
    ```sql
    SELECT status, paid_at, payment_url, payment_session_id FROM
      tenant_5039f2d497cf.invoices WHERE id='8f718728-...';
    -- → PAID, <timestamp>, http://.../dev/mock-payment?..., MOCK-SESS-<uuid>
    SELECT count(*) FROM tenant_5039f2d497cf.payment_events
      WHERE invoice_id='8f718728-...';
    -- → 1
    ```

All UI checkpoints driven via Playwright MCP browser. No SQL or REST
mutations to fake state.

## Out of scope (do NOT expand into)

- Real PayFast sandbox wiring (merchant credentials, ngrok tunnel,
  signature verification). Tracked separately for **Sprint 2** as
  "L-64-followup: real PayFast sandbox integration test". Note this in the
  status.md log entry.
- Production payment-provider configuration UI (`/settings/integrations`).
  Out of scope for this verify cycle.
- Webhook signature verification on the `mock` adapter — not needed
  because the dev page is the only caller and is profile-gated.
- Refactoring `IntegrationRegistry.resolve` fallback semantics — the
  current NoOp fallback is correct production behaviour for tenants with
  no PSP; we only fix the seed gap on legal-za install.

## Status transitions

- GAP-L-64: OPEN → **SPEC_READY** (this commit).
- After Dev implements + merges: SPEC_READY → FIXED (NEEDS_REBUILD=true).
- After Infra restarts backend: Dev hands to QA.
- After QA re-walks Day 30 per verification plan: FIXED → VERIFIED.
