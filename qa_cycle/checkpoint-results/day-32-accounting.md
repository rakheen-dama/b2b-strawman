# Day 32 — 4th client creation + VAT Return engagement (accounting cycle)

**Date**: 2026-05-15
**Actor**: Thandi Thornton (Owner)
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, KC :8180)
**Scenario**: `qa/testplan/demos/accounting-za-90day-keycloak-v2.md`, checkpoint 32.1

---

## Checkpoint 32.1 — Add 4th client (Pty Ltd, compressed happy-path onboarding) + create VAT Return engagement

### Step 1: Client creation

| ck | Step | Result | Evidence |
|----|------|--------|----------|
| 32.1a | Create 4th client "Mathole Engineering (Pty) Ltd" | **PASS** | Client created via New Client dialog. All promoted fields filled inline: Entity Type = Pty Ltd (Private Company), Registration Number = 2019/654321/07, Tax Number = 9876543210, Financial Year End = 2026-06-30, Contact: Thabo Mathole. Client ID: `29b90b29-9a51-4e73-9157-b2d3622ed29b`. |
| 32.1b | Client appears in Prospect list | **PASS** | Verified in Clients list filtered by PROSPECT. |
| 32.1c | Step 2 custom fields (SA Accounting -- Client Details) | **PASS** | SARS Tax Reference filled: 0987654321. FICA Verified: Not Started. |

### Step 2: Compressed onboarding (PROSPECT -> ONBOARDING -> ACTIVE)

| ck | Step | Result | Evidence |
|----|------|--------|----------|
| 32.1d | Transition to ONBOARDING via "Change Status" > "Start Onboarding" | **FAIL** | Clicked "Change Status" dropdown > "Start Onboarding" menu item three separate times. Each time the menu closes but the lifecycle badge remains "Prospect". No backend log entry for lifecycle transition of customer `29b90b29`. Network monitoring shows a POST 200 to the page URL (Next.js Server Action / RSC), but no PATCH/POST to the backend `/api/customers/{id}/lifecycle` endpoint. The frontend is not issuing the backend API call. |
| 32.1e | Complete FICA/KYC checklist | **BLOCKED** | Cannot proceed without ONBOARDING state (checklist only appears after transition). |
| 32.1f | Transition to ACTIVE | **BLOCKED** | Depends on 32.1e. |
| 32.1g | Create VAT Return engagement from template | **BLOCKED** | Not attempted yet -- client needs to be ACTIVE first (engagement creation works on any status, but scenario specifies compressed happy-path to ACTIVE first). |

### Root cause hypothesis

The "Start Onboarding" menu item click is handled by a Next.js Server Action that returns 200, but the backend lifecycle transition API call is either:
1. Not being made (frontend Server Action does not call the backend)
2. Failing silently (error swallowed by Server Action error handler)
3. Intercepted by the gateway/BFF layer

Backend log shows zero lifecycle transition attempts for customer `29b90b29`. The frontend console shows no JavaScript errors related to the transition (only 404s for `/api/assistant/invocations` which are unrelated).

### New gap

| Gap ID | Summary | Severity | Owner | Day | Notes |
|--------|---------|----------|-------|-----|-------|
| OBS-4009 | "Start Onboarding" lifecycle transition via Change Status dropdown does not execute | HIGH | Dev | 32 | Frontend "Change Status" > "Start Onboarding" click returns 200 OK (Server Action) but no backend API call is issued. Client remains in PROSPECT. Observed 3 separate attempts, all identical. Backend log has no transition entry for the customer. This may be a Next.js Server Action bug or gateway routing issue. Blocks client onboarding. |

---

## Day 32 — PARTIAL (BLOCKED)

Client creation succeeded. Lifecycle transition to ONBOARDING blocked by OBS-4009. VAT Return engagement creation deferred until client onboarding issue is resolved.
