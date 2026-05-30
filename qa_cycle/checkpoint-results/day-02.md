# Day 2 — Checkpoint Results (Cycle 2026-05-30)

**Date**: 2026-05-30
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, KC :8180, Mailpit :8025)
**Executed by**: QA Agent
**Scenario**: legal-za-full-lifecycle-keycloak.md (Mathebula & Partners)
**Actor**: Bob Ndlovu (Admin — realistic assignment for intake)

---

## Pre-check: Login as Bob

Signed out residual Thandi session from Day 1. Navigated to `/dashboard`, redirected to Keycloak login at `:8180`. Entered `bob@mathebula-test.local` / `SecureP@ss2`. Logged in successfully, landed on `/org/mathebula-partners/dashboard`. Sidebar shows `BN`, org name "Mathebula & Partners", user "Bob Ndlovu" (bob@mathebula-test.local). Zero console errors after login.

---

## Day 2 — Onboard Sipho as client, run conflict check + KYC `[FIRM]`

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 2.1 | Navigate to Clients -> click + New Client | **PASS** | Expanded Clients nav group (legal terminology confirmed: Clients, Engagement Letters, Mandates, Compliance, Conflict Check, Adverse Parties). Clicked "Clients" link -> `/org/mathebula-partners/customers`. Page shows "Clients" heading with count "0" and "New Client" button. Clicked "New Client" -> "Create Client" dialog opened (Step 1 of 2). |
| 2.2 | Verify dialog shows legal-specific promoted fields for INDIVIDUAL | **PASS** | Step 1: standard fields (Name, Type=Individual, Email, Phone, Tax Number, Notes, Address, Contact, Business Details). Step 2 ("Additional Information"): shows **SA Legal -- Client Details** field group with 4 promoted fields: (1) ID / Passport Number ("South African ID number or passport number for natural persons"), (2) Postal Address, (3) Preferred Correspondence (combobox: Email/Post/Hand Delivery), (4) Referred By. Legal-specific promoted fields present for INDIVIDUAL type. |
| 2.3 | Fill client form with Sipho's details | **PASS** | Step 1: Type=Individual, Name="Sipho Dlamini", Email=sipho.portal@example.com, Phone=+27 82 555 0101, Address="12 Loveday St", City=Johannesburg, Postal Code=2001, Country=South Africa (ZA). Step 2: ID/Passport Number=8501015800088, Preferred Correspondence=Email. |
| 2.4 | Submit -> client created, redirected to client detail | **PASS** | Clicked "Create Client" -> redirected to `/org/mathebula-partners/customers/d74963c8-4527-41b8-bd67-a2ca3ed6a3cf`. Heading: "Sipho Dlamini". Status badges: Active + Prospect. Contact: sipho.portal@example.com, +27 82 555 0101. Overview tab shows: Client Readiness 67%, Document Templates (4 legal-za: Power of Attorney, Letter of Demand, Client Trust Statement, Trust Receipt), FICA Verification section (AI). |
| 2.5 | On client detail -> Run Conflict Check | **PASS** | Clicked "More actions" overflow menu on client detail -> "Run Conflict Check" menu item. Navigated to `/org/mathebula-partners/conflict-check?customerId=d74963c8-..&checkedName=Sipho%20Dlamini`. Name pre-filled "Sipho Dlamini", Check Type "New Client", Client "Sipho Dlamini" pre-selected. Clicked "Run Conflict Check". |
| 2.6 | Result = CLEAR (no pre-existing records) — green confirmation state renders | **PASS** | Result: **"No Conflict"** heading with green checkmark icon. Detail: "Checked 'Sipho Dlamini' at 30/05/2026, 16:29:48". Status badge "No Conflict" in green. History tab updated to "(1)" confirming check recorded. |
| 2.7 | Screenshot: day-02-conflict-check-clear.png | **PASS** | Screenshot captured: `day-02-conflict-check-clear.png` showing Conflict Check page with "No Conflict" result badge. |
| 2.8 | On client detail -> Run KYC Verification | **PARTIAL** | Navigated back to client detail Overview tab. FICA Verification section visible with "AI" badge. "Verify with AI" button is **disabled** (greyed out). No KYC adapter is configured in this dev stack — the AI provider is not wired for FICA verification. |
| 2.9 | KYC adapter returns Verified — KYC badge renders green | **SKIPPED** | KYC/FICA adapter not configured (button disabled). This is an expected condition per scenario: "if KYC adapter configured; otherwise skip and note in gap report". Logged as expected skip. |
| 2.10 | Screenshot: day-02-kyc-verified.png | **SKIPPED** | Skipped — KYC verification not available (no adapter configured). |

---

## Day 2 Summary Checkpoints

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| Client created with INDIVIDUAL type and legal-specific fields | **PASS** | Sipho Dlamini created as INDIVIDUAL with SA Legal promoted fields: ID/Passport Number (8501015800088), Preferred Correspondence (Email). Address: 12 Loveday St, Johannesburg, 2001, ZA. Client ID: d74963c8-4527-41b8-bd67-a2ca3ed6a3cf. |
| Conflict check CLEAR (no false positive hits) | **PASS** | "No Conflict" result with green badge. Zero false positives — no pre-existing clients, matters, or adverse parties matched. Check recorded in History (1). |
| KYC verification badge visible on client detail, or KYC not-configured state logged | **PARTIAL** | FICA Verification section present on client Overview tab with "AI" badge. "Verify with AI" button exists but is **disabled** — KYC adapter (AI provider) not configured in this dev stack. This is an expected limitation per the scenario (KYC and Payments integrations are exempt per mandate). |

---

## Console Errors

2x 404 errors for `/api/assistant/invocations?contextEntityType=customer&contextEntityId=...&status=PENDING_APPROVAL&size=10` — this is the AI assistant feature probing for pending AI invocations. Non-critical: the route returns 404 when the AI assistant module has no registered invocations endpoint. Does not affect core client creation, conflict check, or KYC flows. Not a blocker.

No JavaScript/hydration/rendering errors observed during Day 2 execution.

## Gaps Filed

| Gap ID | Summary | Severity | Notes |
|--------|---------|----------|-------|
| OBS-201 | `/api/assistant/invocations` returns 404 on client detail page | LOW | AI assistant feature endpoint not registered. Fires on every client detail page load (2x per navigation). Cosmetic — no user-facing impact. Non-cascading. |
| — | KYC/FICA adapter not configured | EXEMPT | Per mandate: KYC and Payments integrations are not yet wired in. Expected skip. |

## Entity IDs (for downstream days)

- **Sipho Dlamini Client ID**: `d74963c8-4527-41b8-bd67-a2ca3ed6a3cf`
- **Client URL**: `/org/mathebula-partners/customers/d74963c8-4527-41b8-bd67-a2ca3ed6a3cf`
