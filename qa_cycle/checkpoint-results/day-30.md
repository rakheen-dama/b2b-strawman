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

## Decision: Day 30 PSP redirect — fix-in-cycle (Product triage 2026-04-25 SAST)

**GAP-L-64: FIX-IN-CYCLE.** Authority — scenario step 0.G verbatim (`qa/testplan/demos/legal-za-full-lifecycle-keycloak.md:105`):

> **0.G** Confirm PayFast sandbox credentials are configured in `OrgIntegration` seed data **OR set up a firm-side stub adapter for Day 30** — document which path is in use

The stub-adapter path is explicitly authorised. Chosen approach: dev-only `MockPaymentGateway` (slug=`mock`, `@Profile({local,dev,keycloak,test})`) auto-seeded into `org_integrations` on legal-za pack install via `PackReconciliationRunner` (Java-side, profile-gated; no Flyway migration). Bundled email-template fix: `portalUrl` always populated in `InvoiceDeliveryEmailService` regardless of `paymentUrl` state. Real PayFast sandbox deferred to Sprint 2 as **L-64-followup**. Full spec: `qa_cycle/fix-specs/GAP-L-64.md`.

**Why fix, not defer:** blast radius cascades through Day 60.5 closure gate ("no unpaid fee notes" GREEN required), Day 61.4 SoA reconciliation ("Day 30 fee note paid (amount)" line item), Day 88.4 activity trail ("fee-note paid (Day 30)"), and exit checkpoint E.12 — demo-blocking exit gate. Mock adapter exercises the full IntegrationRegistry resolution path, payment_url plumbing, portal Pay-button render, redirect roundtrip, and webhook reconciliation; only PayFast cryptographic signature verification is NOT covered (Sprint 2).

**Day 30 phase status pending L-64 fix:**
- 30.1 (email href) — re-walk after fix
- 30.2 (portal detail render) — already PASS
- 30.3 (terminology drift) — see GAP-L-65 deferral below
- 30.4 (screenshot) — already PASS
- 30.5 (Pay button) — re-walk after fix
- 30.6 (mock-payment success page) — re-walk after fix
- 30.7 (firm flip to PAID within 60s) — re-walk after fix
- 30.8 (receipt download) — re-walk after fix
- 30.9 (success screenshot) — re-walk after fix
- 30.10 (Sent→Paid filter) — re-walk after fix
- 30.11 (isolation) — already PASS

**GAP-L-65: DEFERRED (WONT_FIX this verify cycle) → Sprint 2 as L-65-followup.** Reasoning: exit checkpoint E.9 reads "Terminology sweep passed — zero `Project/Customer/Invoice` leaks **firm-side**; **portal terminology consistent within itself**". The portal IS internally consistent (renders "Invoice" everywhere on portal; no firm vocabulary leaks INTO portal). The firm↔portal seam divergence is not gated by E.9. Verify cycle scope is "re-validate 40+ shipped fixes" — propagating `legal-za` `terminology.invoice` override into portal layout + email templates is genuinely new functionality requiring a portal-side terminology resolver + email-template token expansion (M-effort, doesn't fit verify scope).

**Day 45 dispatch unblocked** — does not hard-depend on Day 30 PAID; firm-side flow can proceed in parallel while Dev implements L-64.

## Day 30 Re-walk after L-64 fix — Cycle 1

Date: 2026-04-25 (post-PR #1134 merge SHA `34493c79`) | Backend: :8080 PID 41837 (post-restart, V112 + MockPaymentGateway live) | Branch: `bugfix_cycle_2026-04-24`

### Verdict

**Day 30 cycle-1 re-walk: HALTED — Playwright MCP and Claude-in-Chrome bridge both unavailable this turn.** Pre-state DB confirmation completed (read-only SQL); browser-driven phases 30.1 / 30.5 / 30.6 / 30.7 / 30.8 / 30.10 NOT executed. No payment was faked. GAP-L-64 status remains **FIXED (code merged) / VERIFY-PENDING (browser-driven re-walk)**.

### Pre-state confirmation (read-only SELECT)

```sql
SELECT id, status, payment_url, payment_session_id
FROM tenant_5039f2d497cf.invoices
WHERE id='8f718728-5fb6-40fe-abf1-2cafc86c0f10';
-- → 8f718728-... | SENT | (NULL) | (NULL)

SELECT id, domain, provider_slug, enabled
FROM tenant_5039f2d497cf.org_integrations;
-- → 9775a954-96b8-4db1-89c6-ad5c7dff7fb9 | PAYMENT | mock | t
```

Confirms PR #1134 seeder ran successfully (mock PAYMENT row present, enabled=true), but the existing INV-0001 was created BEFORE the seeder, so its `payment_url`/`payment_session_id` are still NULL. Per Option A in dispatch instructions, browser-driven regeneration would be needed (firm-side "Resend Invoice" / detail-view trigger / new fee-note generation) — this is exactly the path the re-walk is meant to exercise.

### Halt cause: tooling unavailability

- `mcp__playwright__browser_*` returns `Target page, context or browser has been closed` on every call (navigate, snapshot, tabs, take_screenshot).
- `mcp__plugin_playwright_playwright__browser_*` returns `Browser is already in use for /Users/rakheendama/Library/Caches/ms-playwright/mcp-chrome-5d273ba` — wedged Chrome user-data dir from a prior session (Chrome PID 5426 still alive holding it; can't release without external intervention since killing user processes is out of QA-agent scope).
- `mcp__claude-in-chrome__tabs_context_mcp` returns `Browser extension is not connected. Please ensure the Claude browser extension is installed and running (https://claude.ai/chrome)`.

Same dual-bridge wedge that the previous turn (commit `b2bde0c8`, "status: pause-and-resume marker — chrome+playwright MCP both blocked this turn") recorded. Per HARD rule #4 of the Day 30 dispatch, **HALT** — UI checkpoints require real browser; REST/SQL substitutes are FORBIDDEN.

### What is preserved for the next turn

- Backend stack healthy: `/actuator/health` 200 (8080), 200 (8443), 3000 200, 3002 307 — services UP.
- L-64 fix already deployed (mock org_integrations row seeded; backend restarted post-PR #1134).
- Pre-existing INV-0001 still has NULL `payment_url`. Next turn's choices remain:
  - **Option A** (preferred): drive firm-side "Resend Invoice" / refresh-on-view / regenerate-link to populate `payment_url` for INV-0001 via the seeded MockPaymentGateway.
  - **Option B** (fresh fee note): generate a new disbursement-billing cycle so a fresh invoice is created post-seeder (clean test of the fix path, but loses INV-0001 lineage with Day 28 chain).
- Tab 0 (Bob firm) and Tab 1 (Sipho portal) state IS LOST — the wedged browser session is unusable. Next turn must re-login both: Bob via Keycloak (port 3000) and Sipho via portal `/login` magic-link (port 3002).

### Phases status post-halt

| Phase | Status |
|---|---|
| 30.1 (email href non-empty) | NOT_RE-WALKED — needs browser + Mailpit refresh on regenerated invoice |
| 30.2 (portal /invoices detail) | already PASS — no re-walk needed unless invoice payload changes |
| 30.4 (screenshot) | already PASS |
| 30.5 (Pay button render) | NOT_RE-WALKED — depends on payment_url populating; awaiting browser availability |
| 30.6 (mock-payment Complete click) | NOT_RE-WALKED |
| 30.7 (firm flip to PAID + payment_events row) | NOT_RE-WALKED |
| 30.8 / 30.10 | NOT_RE-WALKED |
| 30.11 (isolation) | already PASS |

### No state mutations this turn

Confirmed: no SQL writes, no REST writes, no faked payments, no clicks on `/dev/mock-payment`. Browser bridge unavailability has been respected.
