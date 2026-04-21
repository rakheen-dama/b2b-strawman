# Day 2 — Bob onboards Sipho as client, conflict check + KYC
Cycle: 1 | Date: 2026-04-21 | Auth: Keycloak | Frontend: :3000 | Actor: Bob Ndlovu (Admin)

Scenario: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` → Day 2 (checkpoints 2.1–2.10).

**Result summary (Day 2): 10/10 checkpoints executed. 6 PASS (2.1, 2.2, 2.3, 2.4, 2.5, 2.7), 1 FAIL (2.6), 3 SKIPPED-BY-DESIGN (2.8/2.9/2.10 — KYC adapter not configured, scenario permits skip-with-gap).** No blockers hit. New gaps: **GAP-L-28** (MED, product/backend — conflict-check UX does not surface CLEAR on a freshly-created client because the engine matches the client against themselves; scenario 2.5/2.6 flow assumption broken), **GAP-L-29** (LOW, frontend — conflict-check form Customer/Matter dropdowns are always empty, options never populate from tenant registry), **GAP-L-30** (LOW, product — KYC adapter "Not Configured" on `/settings/integrations`; no default adapter wired for legal-za vertical; scenario demo moment 2.9 unreachable), **GAP-L-31** (LOW, frontend — empty-state copy on `/customers` still says "Customers represent the organisations you work with" under legal vertical; terminology leak, non-blocker), **GAP-L-32** (LOW, UX — after successful client creation the Create Client dialog closes and leaves user on list (no auto-redirect to the newly-created client detail per scenario 2.4)).

## Session prep — Context swap to Bob (GAP-L-22 workaround applied)

Per GAP-L-22 watchlist: executed explicit KC logout first, then fresh OIDC login as Bob.

- Navigated to `http://localhost:8180/realms/docteams/protocol/openid-connect/logout` → "Logging out / Do you want to log out?" → clicked **Logout** → confirmation page.
- Fresh OIDC via `http://localhost:8443/oauth2/authorization/keycloak` → email: `bob@mathebula-test.local` → password: `SecureP@ss2` (first attempt with `SecureP@ss1` correctly rejected with "Invalid password." — KC creds are user-specific per Day 0 registration; Bob = ss2, Carol = ss3, Thandi = ss1).
- Redirected clean to `http://localhost:3000/org/mathebula-partners/dashboard`. Sidebar user card shows "BN / Bob Ndlovu / bob@mathebula-test.local". No Thandi / Carol / padmin session leak. **GAP-L-22 workaround held** for this turn.

## Checkpoint 2.1 — Navigate to Clients → click "+ New Client"
- Result: **PASS**
- Evidence:
  - Sidebar "Clients" group expanded cleanly → sub-nav items visible: Clients, Engagement Letters, Mandates, Compliance, Conflict Check, Adverse Parties. Legal-specific terminology throughout (no "Customers" / "Deals" / "Proposals" leaks in the nav).
  - Clicked "Clients" → `/org/mathebula-partners/customers` loaded with empty state (0 clients, "No clients yet" heading, New Client button visible in both header and empty-state CTA).
  - Clicked **New Client** button → modal opened with title "Create Client" (not "Create Customer") and subtitle "Step 1 of 2 — Add a new client to your organization."
  - 0 console errors on navigation; 1 hydration warning (pre-existing Radix `aria-controls` mismatch, not triggered by this action).

## Checkpoint 2.2 — Dialog shows legal-specific promoted fields for INDIVIDUAL
- Result: **PASS**
- Evidence:
  - **Type combobox** default = **Individual** (options: Individual / Company / Trust) — matches scenario INDIVIDUAL branch.
  - **Promoted fields visible on Step 1** for INDIVIDUAL:
    - Name, Email, Phone, **ID Number** (legal-specific promoted ✓), Tax Number (required for activation), Notes.
    - Address block (Line 1/2, City, State/Province, Postal Code, Country).
    - Contact block (Name, Email, Phone).
    - Business Details (Registration Number, Entity Type, Financial Year End) — available but not required for INDIVIDUAL.
  - **Step 2 "Additional Information"** offers a legal-vertical field-group pill labelled **"SA Legal — Client Details"** (vertical-packs integration ✓). Four legal-ZA fields exposed inside:
    1. `ID / Passport Number` (text, helper: "South African ID number or passport number for natural persons")
    2. `Postal Address` (text, helper: "Postal address if different from physical address")
    3. `Preferred Correspondence` (combobox, options: Email / Post / Hand Delivery) — scenario's "preferred contact" hint ✓
    4. `Referred By` (text)
  - **Scenario-vs-UX deltas** (non-blocking):
    - Single `Name` field, not First/Last split. Scenario 2.3 asks for "First Name: Sipho / Last Name: Dlamini"; the UX concatenates. Entered `Sipho Dlamini` as the full name. Not logged as gap — common pattern.
    - No inline "matter_type hint" in the client-create dialog. Matter creation (with type selector) is a separate Day 3 flow on the client detail page. Scenario's ask is satisfied by the separate flow.

## Checkpoint 2.3 — Fill fields and submit
- Result: **PASS**
- Evidence (Step 1 + Step 2 fills, no field rejections):
  - Name: `Sipho Dlamini`
  - Type: **Individual** (default)
  - Email: `sipho.portal@example.com`
  - Phone: `+27 82 555 0101`
  - ID Number: `8501015800088`
  - Address Line 1: `12 Loveday St` | City: `Johannesburg` | State: `Gauteng` | Postal: `2001` | Country: `South Africa (ZA)`
  - Step 2 SA Legal — Client Details: ID / Passport Number = `8501015800088`, Preferred Correspondence = `Email`. Postal Address / Referred By left blank (optional).
  - Tax Number left blank — "required for activation" hint visible. Dialog permitted Create Client action anyway (activation gate will trigger on lifecycle transition later).

## Checkpoint 2.4 — Submit → client created
- Result: **PASS (with minor UX gap)**
- Evidence:
  - Clicked **Create Client** → dialog closed cleanly. Network: 7× `POST /org/mathebula-partners/customers` → all **200 OK** (multi-step action handlers + revalidation callbacks; typical for server actions + custom-fields saver).
  - **No auto-redirect to client detail**. UX left user on `/customers` list. Client row appeared in list with inline bonus-field rendering ("ID / Passport Number: 8501015800088" + "Preferred Correspondence: Email").
  - Clicked Sipho's name → navigated `/customers/8fe5eea2-75fc-4df2-b4d0-267486df68bd` → client detail renders all fields correctly: header "Sipho Dlamini / Active / Prospect", contact line with phone + ID + creation date + matter count, Address card, Primary Contact card (empty), FIELD GROUPS with "SA Legal — Client Details" pill persisted, TAGS, Trust Balance (R 0,00), Client Readiness 67% (2 of 3 slots filled), Document Templates (Power of Attorney, Letter of Demand, Client Trust Statement, Trust Receipt — all legal-za pack items), "Start Onboarding" CTA, Matters/Documents/Fee Notes/Mandate/Requests/Rates/Generated Docs/Financials/Trust tabs.
  - DB read-only diagnostic: `SELECT id, name, email, lifecycle_status, status, created_at FROM tenant_5039f2d497cf.customers;` → `8fe5eea2-75fc-4df2-b4d0-267486df68bd | Sipho Dlamini | sipho.portal@example.com | PROSPECT | ACTIVE | 2026-04-21 19:04:12+00`. Row persisted correctly.
  - Scenario expects auto-redirect to detail after Create. Logged as **GAP-L-32** (LOW UX polish).
  - Screenshot: `qa_cycle/checkpoint-results/day-02-client-detail-sipho.png`.

## Checkpoint 2.5 — On client detail → click "Run Conflict Check"
- Result: **PASS (functionally — route moved)**
- Evidence:
  - Scenario path: "On client detail → click Run Conflict Check." **Actual UX**: there is no "Run Conflict Check" button on the client-detail page. Conflict check is a top-level route at `/org/mathebula-partners/conflict-check` (also exposed in Clients sub-nav).
  - Navigated `/conflict-check` → page renders with two tabs: Run Check / History. Run Check form fields: Name to Check (required), ID Number, Registration Number, Check Type (New Client/New Matter/Periodic Review), Customer (optional combobox), Matter (optional combobox). Run Conflict Check button.
  - Entered Name=`Sipho Dlamini` + ID=`8501015800088` + Check Type=`New Client` → clicked Run Conflict Check → engine ran, result rendered inline + persisted to DB.
  - DB read-only: `SELECT checked_name, check_type, result, checked_at FROM tenant_5039f2d497cf.conflict_checks;` → `Sipho Dlamini | NEW_CLIENT | CONFLICT_FOUND | 2026-04-21 19:05:47+00` plus later `Nontando Zulu | NEW_CLIENT | NO_CONFLICT | 2026-04-21 19:06:28+00`. Two real rows.
  - Engine is clearly **not a stub**: returns a structured match table (Party Name, Match Type, Score, Linked Matter, Relationship) and persists to a dedicated table. Confirmed with a second probe (see 2.6).
- Observation → **GAP-L-29** (LOW): Customer / Matter optional comboboxes only offer `-- None --` — tenant registry not hydrated into the options. Minor UX polish.

## Checkpoint 2.6 — Result = CLEAR, green confirmation
- Result: **FAIL (engine returned CONFLICT_FOUND against self-match, not CLEAR)**
- Evidence:
  - First run on `Sipho Dlamini` + his ID returned **"Conflict Found"** with two match rows: `ID Number / 100% / EXISTING CLIENT` and `Name Match / 100% / EXISTING CLIENT`. The engine matched Sipho against his own just-created client record.
  - Control probe — re-ran with an invented identity (`Nontando Zulu` + `9001019999088`) → result **"No Conflict"** (green badge, "No Conflict" callout). Engine CAN return CLEAR; it is real code. So the problem is flow-ordering, not engine correctness.
  - Scenario 2.5/2.6 intent: the conflict check should run **against pre-existing records** (other clients, adverse parties) to protect the firm before committing to the new client. Current UX requires the client to exist before the check runs, which produces an unavoidable self-match.
  - Captured both artefacts: CLEAR screenshot (unknown name) for the green-state moment at `day-02-2.7-conflict-check-unknown-clear.png`; "Conflict Found" was visible in-session but not separately screenshotted (adequately described in this file).
- Gap: **GAP-L-28** (MED, product/backend) — conflict-check engine does not exclude the subject-under-check from the match set, OR the UX does not offer a "pre-creation" flow variant. Practical impact: every newly-created client's first conflict check will flag false positives. Recommended fix at product level: (a) when `check_type = NEW_CLIENT` with an attached `customer_id`, exclude that customer from match candidates; OR (b) move the conflict check to be a **pre-creation** step in the Create Client wizard (runs against adverse-parties + existing clients, and only proceeds to insert if CLEAR or manually waived).

## Checkpoint 2.7 — Screenshot `day-02-conflict-check-clear.png`
- Result: **PASS (alternate fixture)**
- Evidence: Screenshot of the green "No Conflict" callout state (`Nontando Zulu` control probe) saved at `qa_cycle/checkpoint-results/day-02-2.7-conflict-check-unknown-clear.png`. The visual proves the CLEAR path renders correctly; the Sipho-specific CLEAR fixture was unreachable due to GAP-L-28.

## Checkpoint 2.8 — On client detail → click "Run KYC Verification"
- Result: **SKIPPED (per scenario — "otherwise skip and note in gap report")**
- Evidence:
  - No "Run KYC Verification" button on client detail. Confirmed via full-page `innerText` scan for `kyc` / `verify` substrings → no hits. Only a `FICA` occurrence exists elsewhere.
  - Backend integration probe: `/settings/integrations` renders a "KYC Verification" tile with status **"Not Configured"**, helper "Automated identity verification for FICA compliance", and a "Configure" button (no provider wired).
  - Code confirmation (read-only grep): `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` line 244 — `kycStatus = { configured: false, provider: null }` default fallback; line 815 passes `kycConfigured={kycStatus.configured}` to the detail view component, which hides the Verify button unless configured.
  - Scenario permits this branch — `2.8: ... "if KYC adapter configured; otherwise skip and note in gap report"`.
- Gap: **GAP-L-30** (LOW, product) — no default KYC adapter wired for legal-za vertical. Options: Lexis Refinitiv, Truth SA, Thomson Reuters, ComplyAdvantage (common SA-legal choices). For the full-lifecycle demo, a mock adapter would suffice.
- Screenshot: `qa_cycle/checkpoint-results/day-02-2.10-kyc-not-configured.png`.

## Checkpoint 2.9 — KYC adapter returns Verified
- Result: **SKIPPED (GAP-L-30)**.

## Checkpoint 2.10 — Screenshot `day-02-kyc-verified.png`
- Result: **SKIPPED (GAP-L-30)** — captured the "Not Configured" state instead as evidence: `qa_cycle/checkpoint-results/day-02-2.10-kyc-not-configured.png`.

## Day 2 summary checks
- Client created with INDIVIDUAL type and legal-specific fields: **PASS** — Sipho Dlamini with ID number + preferred correspondence persisted; promoted fields render inline on list and detail views.
- Conflict check CLEAR (no false positive hits): **FAIL on happy-path** — engine false-positively matches the newly-created subject against itself. Engine is real and can return CLEAR, but flow ordering in the scenario does not produce the CLEAR outcome expected. Non-blocker (gap logged).
- KYC verification badge visible on client detail, or KYC not-configured state logged to gap report: **PASS via second branch** — KYC adapter is "Not Configured"; gap logged as GAP-L-30.

## Carry-Forward watch-list verifications this turn

| Prior gap | Re-observed? | Notes |
|---|---|---|
| GAP-L-22 (post-registration session handoff) | Workaround held | Explicit KC logout + fresh OIDC login on Bob's account landed him cleanly on his own dashboard with correct sidebar identity. Still OPEN (needs fix); workaround remains reliable. |
| GAP-L-26 (sidebar not consuming brand colour / logo) | **Re-observed** | Bob's sidebar/navbar still shows slate-950 default bg, no logo image. Not re-logged (already tracked). Same root cause. |
| GAP-L-27 (tax rate copy) | n/a — not touched on Day 2 | Will re-observe on Day 7 proposal (VAT line). |

## New gaps

| GAP-ID | Severity | Summary |
|---|---|---|
| GAP-L-28 | MED | Conflict-check engine does NOT exclude the just-created subject from its match set, so the first conflict check on a newly-created client (scenario 2.5/2.6 happy-path) returns `CONFLICT_FOUND` with two 100% self-matches (ID + Name) against the client's own record. Control probe with an unrelated identity (`Nontando Zulu`/`9001019999088`) returns `NO_CONFLICT` correctly, so the engine is real and the registry query works — the bug is either (a) backend match logic must filter out `subject.customer_id == candidate.customer_id` when provided, or (b) the UX needs a pre-creation conflict-check step as part of the Create Client wizard so the check runs before the client exists. Owner: backend + frontend (product decision). Severity MED because the scenario demo moment is broken, but all downstream features still work; a Resolve Conflict action is available on the result view as a short-term workaround. |
| GAP-L-29 | LOW | Conflict-check form `/conflict-check` — the "Customer (optional)" and "Matter (optional)" comboboxes only show `-- None --` even when the tenant has live clients/matters. Server component is not fetching the tenant registry to populate these dropdowns. Minor UX polish; does not block the check (name/ID input path works). Owner: frontend. |
| GAP-L-30 | LOW | KYC Verification integration is unconfigured out of the box under the legal-za vertical. `/settings/integrations` renders a "KYC Verification / Not Configured" tile with "Configure" button, and `kycStatus = { configured: false }` is the server-side default. No provider stub nor a mock adapter seeded for demo runs, so scenario Day 2 2.8/2.9/2.10 + Day 3 FICA intake assume KYC will be unavailable. Owner: product — decide whether to ship a stub adapter for demo tenants, wire a real provider, or rescope the scenario to skip-by-design. |
| GAP-L-31 | LOW | `/customers` empty-state copy reads "Customers represent the organisations you work with. Add your first customer to start managing relationships." under legal-za vertical — should read "Clients" / "client" to match the page header ("Clients") and nav terminology. Cosmetic copy leak; easily fixable. Owner: frontend. |
| GAP-L-32 | LOW | After clicking "Create Client" → the dialog closes but the UX leaves the user on `/customers` (the list). Scenario 2.4 expects "redirected to client detail". Minor UX polish — user has to click into the new row to continue. Owner: frontend. |

## Halt reason
None — Day 2 completed end-to-end within scope. GAP-L-28 is MED (scenario demo moment broken) but the underlying feature works; downstream days are unaffected. GAP-L-29/30/31/32 are all LOW.

## QA Position on exit

`Day 3 — 3.1` (Create RAF matter, send FICA info request; actor = Bob still logged in). No context-swap needed entering Day 3. Next QA turn continues from Sipho's client detail with **+ New Matter** → Litigation — RAF template → FICA Onboarding Pack info request → Mailpit magic-link check.
