# Day 2 — Onboard Sipho as client, conflict check + KYC — Cycle 2026-06-13

**Executed**: 2026-06-13 (branch `bugfix_cycle_2026-06-13`)
**Actor**: Bob Ndlovu (Admin) — context swap performed: Thandi's Day 1 session signed out via user menu, fresh Keycloak login as `bob@mathebula-test.local` / SecureP@ss2 at `:8180` realm `docteams`; landed on dashboard with sidebar identity "Bob Ndlovu"
**Driver**: QA agent via Playwright MCP against Keycloak dev stack (frontend :3000, gateway :8443, KC :8180)
**Result**: 7/10 checkpoints PASS, 1 PARTIAL (exempt), 2 SKIPPED (exempt) + 2/3 day-summary PASS, 1 PARTIAL (exempt). **Zero new gaps filed.**

## Checkpoints

| Checkpoint | Result | Evidence | Gap |
|---|---|---|---|
| 2.1 Navigate to Clients → + New Client | PASS | Sidebar Clients group expanded (legal terminology: Clients, Engagement Letters, Mandates, Compliance, Conflict Check, Adverse Parties) → `/org/mathebula-partners/customers` — "Clients" heading, count 0, New Client button. Clicked → "Create Client" dialog (Step 1 of 2). | — |
| 2.2 Dialog shows legal-specific promoted fields for INDIVIDUAL | PASS | Step 1: Name, Type (Individual/Company/Trust), Email, Phone, Tax Number, Notes, Address, Contact, Business Details. Step 2 "Additional Information": **SA Legal — Client Details** field group with ID / Passport Number ("South African ID number or passport number for natural persons"), Postal Address, Preferred Correspondence (Email/Post/Hand Delivery), Referred By. | — |
| 2.3 Fill Sipho's details | PASS | Step 1: Type=Individual, Name="Sipho Dlamini" (product uses single Name field, not First/Last — same as prior cycles, not a gap), Email=sipho.portal@example.com, Phone=+27 82 555 0101, Address Line 1=12 Loveday St, City=Johannesburg, Postal Code=2001, Country=South Africa (ZA). Step 2: ID/Passport=8501015800088, Preferred Correspondence=Email. | — |
| 2.4 Submit → client created, redirected to client detail | PASS | Redirected to `/org/mathebula-partners/customers/2211a80a-5523-4a6d-8f96-0d638dff88f6`. Heading "Sipho Dlamini", badges Active + Prospect, contact line "sipho.portal@example.com · +27 82 555 0101". Overview: Client Readiness 67%, 4 legal-za Document Templates (Power of Attorney, Letter of Demand, Client Trust Statement, Trust Receipt), FICA Verification (AI) section. | — |
| 2.5 Run Conflict Check from client detail | PASS | More actions → "Run Conflict Check" → `/conflict-check?customerId=2211a80a-…&checkedName=Sipho%20Dlamini`. Name pre-filled "Sipho Dlamini", Check Type "New Client", Client pre-selected "Sipho Dlamini". Clicked Run Conflict Check. | — |
| 2.6 Result = CLEAR, green confirmation renders | PASS | **"No Conflict"** heading + green check icon, "Checked \"Sipho Dlamini\" at 13/06/2026, 01:22:58", green "No Conflict" badge. History tab incremented to (1). | — |
| 2.7 📸 `day-02-conflict-check-clear.png` | PASS | Captured: `qa_cycle/checkpoint-results/day-02-conflict-check-clear.png` (green No Conflict result, Bob's session visible in sidebar). | — |
| 2.8 Run KYC Verification (if adapter configured) | PARTIAL (exempt) | Client Overview FICA Verification section present with "AI" badge; **"Verify with AI" button disabled** — KYC/FICA adapter not configured in this stack. Expected PARTIAL per mandate carry-over exemption; noted, not filed. Evidence: `day-02-kyc-not-configured.png`. | — (exempt) |
| 2.9 KYC returns Verified, green badge + timestamp | SKIPPED | Adapter not configured (2.8). Scenario explicitly allows skip-and-note. | — (exempt) |
| 2.10 📸 `day-02-kyc-verified.png` | SKIPPED | Not applicable — KYC not available. Not-configured state captured instead (`day-02-kyc-not-configured.png`). | — (exempt) |

## Day 2 summary checkpoints

| Checkpoint | Result | Evidence |
|---|---|---|
| Client created with INDIVIDUAL type and legal-specific fields | PASS | Clients list row: "Sipho Dlamini — ID / Passport Number: 8501015800088, Preferred Correspondence: Email — Prospect / Active — created 13 Jun 2026". Details tab: address 12 Loveday St, Johannesburg, 2001, ZA. Fields tab (`?tab=fields`): SA Legal — Client Details group; DOM input-value check confirmed ID/Passport `8501015800088` and Preferred Correspondence select value `EMAIL` persisted. |
| Conflict check CLEAR (no false positive hits) | PASS | "No Conflict" green result; zero hits (no pre-existing clients/matters/adverse parties in clean-slate tenant). Check recorded — History (1). |
| KYC badge visible, or not-configured state logged | PARTIAL (exempt) | FICA Verification section renders with AI badge; Verify with AI disabled — adapter not wired in dev stack. Logged here per scenario instruction; exempt per mandate (KYC integration). |

## Console / log health

- Frontend console: the only errors across the whole day were **OBS-201 class** `GET /api/assistant/invocations?contextEntityType=customer&contextEntityId=2211a80a-…&status=PENDING_APPROVAL&size=10` → 404 on each client-detail load (WONT_FIX-EXEMPT carry-over; not re-filed). No other JS/hydration/render errors. Two in-page fetch 404s during QA evidence-gathering were the QA agent probing API routes, not product errors.
- `.svc/logs/backend.log`: 0 ERROR lines. `.svc/logs/frontend.log`: no errors/exceptions.

## Gaps filed

None. (KYC not-configured + OBS-201 assistant-invocations 404 both fall under known carry-over exemptions.)

## Entity IDs (for downstream days)

- **Sipho Dlamini client ID**: `2211a80a-5523-4a6d-8f96-0d638dff88f6`
- **Client URL**: `/org/mathebula-partners/customers/2211a80a-5523-4a6d-8f96-0d638dff88f6`
- Lifecycle: Prospect / Active · Conflict check history: 1 (No Conflict, 13/06/2026 01:22:58)
