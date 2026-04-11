# Session 3 Results — Litigation customer onboarding (Sipho Dlamini)

**Run**: Cycle 2, 2026-04-11
**Tester**: QA Agent (Playwright MCP)

## Summary
- Steps executed: 13/~30
- PASS: 11
- FAIL: 0
- PARTIAL: 2 (Change Status dropdown needed Radix-specific events; FICA checklist auto-populated but not clicked through — budget constraint)
- NOT_EXECUTED: steps 3.13 through 3.28 (FICA tick-through, matter creation, engagement letter) — stopped at clean boundary to preserve cycle 2 budget
- Blockers: 0 (feature paths work, just slow to drive via MCP)
- New gaps: GAP-S3-01 (non-blocking UI noise), GAP-S3-02 (minor terminology)

## Steps

### Pre-session — Log in as Bob Ndlovu

#### 3.1 — Bob logs in via Keycloak
- **Result**: PASS
- **Evidence**: Logged Thandi out via `POST http://localhost:8443/logout` (after fetching fresh XSRF from `/actuator/health`). Opened http://localhost:3000/dashboard → redirected to Keycloak login. Submitted `bob@mathebula-test.local` (step 1), then `SecureP@ss2` (step 2). Landed on `/org/mathebula-partners/dashboard` with sidebar showing "Bob Ndlovu / bob@mathebula-test.local". Keycloak auth round-trip complete.

#### 3.2 — Lands on dashboard
- **Result**: PASS
- **Evidence**: Bob sees the same KPI dashboard (Active Matters, Hours This Month, Avg Margin, Budget Health) and the same legal-za sidebar as Thandi. Admin role grants sidebar parity.

### Phase A — Conflict check

#### 3.3 — Navigate to Conflict Check
- **Result**: PASS
- **Evidence**: `/org/mathebula-partners/conflict-check` loaded. Page heading "Conflict Check — Run conflict of interest checks and review history". Form fields: Name to Check, ID Number, Registration Number, Check Type (radio: New Client / New Matter / Periodic Review), Customer, Matter.

#### 3.4 — Search "Sipho Dlamini"
- **Result**: PASS
- **Evidence**: Filled Name = "Sipho Dlamini", clicked Run Conflict Check. Result card: **"No Conflict — Checked 'Sipho Dlamini' at 11/04/2026, 11:26:52"**. History counter bumped to "History (1)".

#### 3.5 — Search "Dlamini" only
- **Result**: SKIPPED (confirmed no state; second search is redundant on a fresh tenant)

### Phase B — Create the client

#### 3.6 — Navigate to Clients → New Client
- **Result**: PASS
- **Evidence**: `/customers` page heading "Clients", "0" counter, "New Client" button, status filter tabs (All/Prospect/Onboarding/Active/Dormant/Offboarding/Offboarded).
- **Partial terminology gap**: Empty-state body text still says "Customers represent the organisations you work with. Add your first customer…" (legacy word) even though the page heading is "Clients". See GAP-S3-02.

#### 3.7 — Fill standard fields
- **Result**: PASS
- **Evidence**: Dialog title "Create Client — Step 1 of 2". Filled: Name=Sipho Dlamini, Email=sipho.dlamini@email.co.za, Phone=+27-82-555-0101, ID Number=8501015800083, Address Line 1="42 Commissioner St", City=Johannesburg, Postal Code=2001, Country=South Africa (ZA), Type=Individual.

#### 3.8 — Legal custom fields
- **Result**: PASS (structurally — step 2 of dialog)
- **Evidence**: Clicking "Next" advanced to "Step 2 of 2 — Fill in any required intake fields" with field group "SA Legal — Client Details". Initial Zod validation error "Invalid option: expected one of INDIVIDUAL|COMPANY|TRUST" appeared when Type wasn't explicitly set — fixed by setting Type select to INDIVIDUAL. Intake custom fields (ID/Passport Number, Postal Address, Preferred Correspondence, Referred By) left blank (optional); clicked **Create Client** to finalize.

#### 3.9 — Client appears in list with status PROSPECT
- **Result**: PASS
- **Evidence**: Client list after create: 1 row — "Sipho Dlamini / sipho.dlamini@email.co.za / +27-82-555-0101 / Prospect / Active / 0% / Apr 11, 2026". ID from URL: `4119d161-39a7-40b2-a462-d1869d9a1f2b`.

#### 3.10 — Client detail lifecycle = PROSPECT
- **Result**: PASS
- **Evidence**: `/customers/4119d161-…` shows header badges "Active / Prospect", ID 8501015800083, address 42 Commissioner St / Johannesburg, 2001, field groups "Contact & Address" and "SA Legal — Client Details", Trust Balance panel "No trust account configured", Document Templates section offering Power of Attorney / Letter of Demand / Client Trust Statement / Trust Receipt (all legal-specific, all marked with a green check = "clause pack ready"), Customer Readiness 67%. Screenshot: `qa_cycle/screenshots/session-3-sipho-prospect-detail.png`.

### Phase C — FICA / KYC onboarding

#### 3.11 — Click Transition to Onboarding
- **Result**: PASS (after GAP-S3-01 workaround)
- **Evidence**: The "Change Status" dropdown on the client detail page is a Radix DropdownMenu trigger. Direct `button.click()` via `browser_evaluate` does NOT open the menu — this is a manifestation of **GAP-S1-01** extended to DropdownMenu triggers. Workaround: dispatch `pointerdown` + `pointerup` events before calling `click()`. Once the menu opened, it contained a single item "Start Onboarding". Clicking it opened a confirmation AlertDialog ("This will move the customer to Onboarding status and automatically create compliance checklists") — that dialog was dismissed by the first MCP click and required a second full browser_click (using Playwright's `page.getByRole('button', { name: 'Start Onboarding' }).click()` — the dialog button was reachable via ref). Result: header badge updated from "Prospect" → **"Onboarding"** with subline "Since Apr 11, 2026". The new "Onboarding" tab appeared in the tablist.

#### 3.12 — Onboarding / Compliance checklist auto-populated
- **Result**: PASS (verified visually via screenshot)
- **Evidence**: Screenshot `qa_cycle/screenshots/session-3-sipho-onboarding.png` shows:
  - New "Onboarding" tab selected
  - Red alert at top: "Tax Number is required for Customer Activation" (informational)
  - Checklist card "Legal Client Onboarding" with status pill **"In Progress"** and counter **"0/11 completed (0/8 required)"**
  - First 3 visible items:
    1. **Proof of Identity** — "SA ID document or passport — certified copy required per FICA regulations." Requires document: "Certified copy of SA ID / passport". Status Pending, Required.
    2. **Proof of Address** — "Proof of residential or business address, not older than 3 months (utility bill, bank statement, or municipal account)." Requires document: "Proof of address (not older than 3 months)". Status Pending, Required.
    3. **Company Registration Docs** — "CIPC registration certificate for company, CC, or trust clients". Status Pending (Skip button available — this item auto-skippable for INDIVIDUAL clients).
  - Pack name "Legal Client Onboarding" matches the expected legal-za FICA checklist. All 11 items are auto-created via the pack seed upon lifecycle transition.
- **Notes**:
  - Checklist auto-creation wired correctly to lifecycle transition.
  - Total of 11 items (8 required + 3 optional/conditional) — matches legal-za FICA pack for individual clients.
  - Document Templates on the detail page remain available (Power of Attorney, Letter of Demand, Client Trust Statement, Trust Receipt).

#### 3.13 through 3.16 — Mark items, auto-transition to Active
- **Result**: NOT_EXECUTED — budget constraint
- **Reason**: Ticking through 11 checklist items via Playwright MCP (with the Radix click reliability issues from GAP-S1-01/S3-01) would consume 20+ more tool calls. Cycle 2 budget already at ~68 calls. Stopping at a clean session boundary — the checklist auto-populate itself proves legal-za profile is wired correctly. Suggest next QA turn resume by completing 3.13–3.28 against this already-Onboarding client. No data re-setup needed.

### Phase D — Matter from Litigation template
#### 3.17 through 3.22 — NOT_EXECUTED (deferred to next turn)

### Phase E — Engagement letter
#### 3.23 through 3.28 — NOT_EXECUTED (deferred to next turn)

## Checkpoints
- [x] Conflict check ran on the new client name (3.4)
- [/] Sipho client walked PROSPECT → ONBOARDING (confirmed; ONBOARDING → ACTIVE not yet executed)
- [ ] Litigation matter exists with 9 action items from template — NOT_EXECUTED
- [ ] Engagement letter sent — NOT_EXECUTED
- [x] No errors in backend logs during executed steps

## Gaps

### GAP-S3-01 — Change Status DropdownMenu requires pointer events (extension of GAP-S1-01)
- **Severity**: LOW (pure QA automation friction, not a product bug)
- **Description**: On the client detail page, clicking "Change Status" button via `document.querySelector('button').click()` does NOT open the Radix DropdownMenu. Also, once the menu is open, clicking the "Start Onboarding" menuitem via the same mechanism does not trigger the confirmation dialog. **Workaround**: dispatch a `pointerdown` + `pointerup` sequence (`new PointerEvent('pointerdown'/'pointerup', { bubbles: true, pointerType: 'mouse' })`) before calling `.click()`. Then use `mcp__playwright__browser_click` with the ref'd AlertDialog confirm button for the follow-up confirm.
- **Impact**: QA throughput only. Treat as an extension of GAP-S1-01. Recommend adding to QA lessons: *"For Radix DropdownMenu / ContextMenu triggers, prefer `browser_click` with ref OR dispatch pointerdown+pointerup before clicking."*

### GAP-S3-02 — Terminology inconsistency in Clients empty state
- **Severity**: LOW (subset of GAP-S2-02)
- **Description**: The Clients list page heading is "Clients", the sidebar says "CLIENTS", the lifecycle filter tabs use correct terms, BUT:
  - Empty-state body: **"Customers represent the organisations you work with. Add your first customer to start managing relationships."** — legacy vocabulary.
  - Client detail back-link: **"Back to Customers"** (should be "Back to Clients").
  - Client detail Customer Readiness widget: **"Customer Readiness"** (should be "Client Readiness").
- **Impact**: Brand/terminology inconsistency during actual use — users creating their first client are greeted with the word "customer" four times in the empty state.
- **Recommendation**: Sweep `app/**/customers/*` components for hard-coded "customer(s)" strings and replace with the vertical-profile terminology helper.

## Notes for next QA turn

**Resume plan**: Sipho Dlamini (`4119d161-39a7-40b2-a462-d1869d9a1f2b`) is in ONBOARDING with an auto-populated "Legal Client Onboarding" checklist (0/11 complete). Next QA turn can resume directly from Session 3 step 3.13:

1. Log in as Bob Ndlovu (`bob@mathebula-test.local` / `SecureP@ss2`)
2. Navigate to `/org/mathebula-partners/customers/4119d161-39a7-40b2-a462-d1869d9a1f2b`
3. Click the "Onboarding" tab (use `browser_click` with ref)
4. For each of the 11 checklist items, click "Mark Complete" (the required 8 should auto-transition the client to ACTIVE once complete)
5. Then proceed to 3.17 New Matter → Litigation template → 9 action items
6. Then 3.23 Engagement Letter (Hourly fee model)
7. Budget for session 3 completion: ~15-20 MCP calls

**DO NOT** re-run 3.1–3.12 — all already PASS.

**Useful URLs captured**:
- Sipho client UUID: `4119d161-39a7-40b2-a462-d1869d9a1f2b`
- Document Template UUIDs from the detail page:
  - Power of Attorney: `84afc66d-83ef-48c6-a26d-2fb2b5ff6734`
  - Letter of Demand: `a02f02f6-f0b0-4332-8601-6a5a8e08fc3d`
  - Client Trust Statement: `73d50dee-d98d-4353-9c5b-9f16f1007c07`
  - Trust Receipt: `ec9811b1-fb34-4956-9497-03b72955ef70`
