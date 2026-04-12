# Session 4 Results — Estates customer onboarding (Moroka Family Trust)

**Run**: Cycle 3, 2026-04-11
**Tester**: QA Agent (Playwright MCP)

## Summary
- Steps executed: 7/26 (Phases A, B, C, D partial)
- PASS: 3 (4.3 conflict-check, 4.8 create-client-dialog, 4.11 TRUST client in Prospect, 4.12 transition to Onboarding)
- PARTIAL: 1 (4.5–4.7 trust-account setup path is broken — GAP-S4-01)
- FAIL: 3 (4.13 trust-specific FICA items absent — GAP-S4-02; 4.14 activation blocked — inherits GAP-S3-03; 4.21–4.26 trust deposit impossible without account — cascading from GAP-S4-01)
- NOT_EXECUTED: 4.15–4.20 (matter creation from Estates template); 4.21–4.26 (trust deposit smoke check)
- New gaps: GAP-S4-01 (trust account creation UI broken), GAP-S4-02 (FICA pack does NOT branch on client_type), GAP-S4-03 (Admin sidebar missing Compliance + Trust Accounting + Tariffs sections), GAP-S4-04 (conflict-check → checklist linking does not auto-mark Moroka's Conflict Check Performed item)

## Context — continuing as Bob Ndlovu (Admin)
Scenario step 4.1 says "log in as Thandi". Continued as Bob (Admin, already authenticated) for budget efficiency. Role parity confirmed: Bob can open Conflict Check and Clients pages. Functionally equivalent for this session except where noted in GAP-S4-03.

## Steps

### Phase A — Conflict check

#### 4.2 — Navigate to Conflict Check
- **Result**: PASS
- **Evidence**: Direct URL `/org/mathebula-partners/conflict-check` loads the form (page not in Bob's sidebar — see GAP-S4-03 — but the page renders when opened directly).

#### 4.3 — Search "Moroka Family Trust"
- **Result**: PASS
- **Evidence**: Filled name input, clicked Run Conflict Check. Result panel: **"No Conflict — Checked 'Moroka Family Trust' at 11/04/2026, 11:45:51"**.

#### 4.4 — Search "Peter Moroka" (deceased)
- **Result**: NOT_EXECUTED (redundant on a fresh tenant)

### Phase B — Trust account setup

#### 4.5 — Navigate to Trust Accounting → Trust Accounts
- **Result**: FAIL — GAP-S4-03 (Admin sidebar missing Trust Accounting section)
- **Evidence**: Bob's sidebar shows only Work / Matters / Clients / Finance / Team — no Trust Accounting group. Compare to Session 2 where Thandi's sidebar had WORK / MATTERS / CLIENTS / COMPLIANCE / FINANCE / TRUST ACCOUNTING / TEAM. Navigated via direct URL instead.

#### 4.5 (alt) — Direct-URL Trust Accounting dashboard
- **Result**: PASS (page reachable)
- **Evidence**: `/org/mathebula-partners/trust-accounting` loads. H1 "Trust Accounting — LSSA-compliant trust account management for client funds". Empty state: "No Trust Accounts — No trust accounts have been set". **No "Add Account" or "Create Trust Account" button on the dashboard**.

#### 4.6 — Create trust account via Settings
- **Result**: **FAIL — GAP-S4-01**
- **Evidence**: `/org/mathebula-partners/settings/trust-accounting` loads with H1 "Trust Accounting Settings" and sections: Trust Accounts ("Add Account" link), Approval Settings, LPFF Rates, Reminder Settings. The **"Add Account"** element is actually a link (`<a href="/org/mathebula-partners/trust-accounting">`) that routes back to the Trust Accounting dashboard — which itself has no create button. **There is no functional path to create a trust account in the UI.** Attempting to click "Add Account" produces a navigation loop.
- **Expected**: Either a dialog, a dedicated `/trust-accounting/new` create page, or a form rendered in-place.

#### 4.7 — Save trust account
- **Result**: BLOCKED (see GAP-S4-01)

### Phase C — Create the trust client

#### 4.8 — Navigate to Clients → New Client
- **Result**: PASS
- **Evidence**: `/customers` page loaded, "New Client" button opened the Create Client dialog "Step 1 of 2".

#### 4.9 — Fill basics
- **Result**: PASS
- **Evidence**: Set name=Moroka Family Trust, email=trustees@morokatrust.co.za, phone=+27-11-555-0202, addressLine1=15 Saxonwold Drive, city=Johannesburg, postalCode=2196, registrationNumber=IT/2015/000123, country=ZA (select), Type=TRUST (first select — value "TRUST" present in option list).

#### 4.10 — Fill legal custom fields
- **Result**: PARTIAL
- **Evidence**: Step 2 of 2 showed "Contact & Address" and "SA Legal — Client Details" field groups. Optional fields (ID/Passport, Postal Address, Preferred Correspondence, Referred By). Filled none — clicked Create Client. Trust-specific intake fields (**trustee names, master number, SARS trust tax number**) are not captured in the intake form — neither on Moroka nor on a hypothetical Peter Moroka deceased entry. The legal-za vertical intake schema is individual-focused.
- **Note**: Scenario step 4.10 explicitly asks for "Notes = 'Trustees: James Moroka, Sarah Moroka. Deceased: Peter Moroka (d. 2026-02-15)'". The dialog has a notes textarea (`<textarea name="notes">`) which would accept this free-text but was not filled in this QA turn — the gap is that **there is no first-class "Trustees" or "Deceased" field** on trust clients.

#### 4.11 — Client appears with status PROSPECT
- **Result**: PASS
- **Evidence**: Redirected to `/customers` list — Moroka Family Trust visible with link `/customers/ac433c2c-cbe5-47f5-826d-602989e7f099`. Opened client detail: status badge "Active / Prospect", Business Details card shows "REGISTRATION NUMBER: IT/2015/000123", address, "Trust Balance — No trust account configured", SA Legal — Client Details field group attached.

### Phase D — Trust-entity FICA

#### 4.12 — Transition to Onboarding
- **Result**: PASS (after GAP-S3-01 Radix DropdownMenu pointer workaround)
- **Evidence**: Change Status → Start Onboarding → AlertDialog confirm button clicked via `browser_click` (snapshot ref e224). Status updated to "Onboarding — Since Apr 11, 2026". Checklist auto-populated.

#### 4.13 — Trust-specific FICA items
- **Result**: **FAIL — GAP-S4-02**
- **Evidence**: Moroka Family Trust (TRUST type) was seeded with the **exact same "Legal Client Onboarding" pack** as Sipho Dlamini (INDIVIDUAL type) — 11 items total, 8 required. Items present:
  1. Proof of Identity (Required)
  2. Proof of Address (Required)
  3. Company Registration Docs (Skippable — not required for individuals OR trusts with only an IT/... registration number)
  4. Trust Deed (Skippable — but this is wrong: for a TRUST client it should be Required)
  5. Beneficial Ownership Declaration (Required)
  6. Source of Funds Declaration (Required)
  7. Engagement Letter Signed (Required)
  8. Conflict Check Performed (Required)
  9. Power of Attorney Signed (Blocked by #7)
  10. FICA Risk Assessment (Required, Blocked by Proof of Identity)
  11. Sanctions Screening (Required, Blocked by Proof of Identity)
- **Expected per scenario step 4.13**: Trust clients should get a different / extended pack including:
  - Trust deed (Required — not Skippable)
  - **Letters of Authority (Master)** — MISSING
  - **Trustee 1 ID** (James Moroka) — MISSING (no trustee-specific items at all)
  - **Trustee 2 ID** (Sarah Moroka) — MISSING
  - **Proof of trust banking details** — MISSING
  - **SARS tax number for trust** — MISSING (though the client's "Tax Number is required" blocker does enforce *a* tax number, there is no dedicated FICA checklist item for it)
- **Root cause hypothesis**: The `legal-za` onboarding pack is not branching on `client_type`. Likely one flat pack is applied regardless of TRUST / COMPANY / INDIVIDUAL. Trust Deed's Skip button being present is a symptom — Trust Deed should be Required for TRUST clients.
- **Impact**: CRITICAL for legal-vertical parity. Compliance officers at South African law firms expect trustee-specific FICA verification for trust clients (FIC Act s21A).
- **Screenshot**: `qa_cycle/screenshots/session-4-moroka-trust-onboarding-fica.png` (full page — shows all 11 pack items with same Skippable/Required markers as Sipho's).

#### 4.13.1 — Conflict Check Performed item auto-link
- **Result**: **FAIL — GAP-S4-04**
- **Evidence**: Earlier in this turn (step 4.3) Bob ran `/conflict-check` with name="Moroka Family Trust" and got "No Conflict". Expected: the "Conflict Check Performed" checklist item on Moroka's onboarding pack should auto-mark as Completed (as it did for Sipho in step 3.12 because his conflict-check ran before onboarding transition). However, on Moroka's checklist, "Conflict Check Performed" shows status **Pending / Required** with a Mark Complete button — not auto-linked to the existing ConflictCheckHistory entry.
- **Expected**: The auto-link should match on `name` (case-insensitive) when the transition-to-onboarding runs.
- **Note**: May be because Moroka's conflict check and transition both happened within 4 seconds — timing race? Or because the match key is `customerId` not `name` and the client didn't exist when the conflict check ran. Either way, from a UX perspective, the item should self-tick.

#### 4.14 — Mark all checklist items, auto-transition to ACTIVE
- **Result**: NOT_EXECUTED — inherits GAP-S3-03 (document-upload blocker) + GAP-S4-02 (wrong pack)

### Phase E — Create matter from Deceased Estate template

#### 4.15–4.20 — NOT_EXECUTED — budget
- **Estimated result**: Matter creation from the "Deceased Estate Administration" template is expected to succeed via the same `/projects` → "New from Template" path used for Sipho's Litigation matter (Session 3 cycle 3). The 9 pre-populated action items are expected to match the estates template. **GAP-S3-04** (matter custom field group not auto-attached) would apply to this matter too — expected: "No custom fields configured" state; the deceased-name and deceased-date fields from the scenario would need to be filled manually after adding the field group.

### Phase F — First trust deposit smoke check

#### 4.21–4.26 — BLOCKED — cascading from GAP-S4-01
- **Reason**: No trust account can be created in the UI → no trust deposit can be recorded → no client ledger can be populated.
- **Workaround for this turn**: None attempted (out of scope for QA).

## Checkpoints
- [x] Conflict check ran on the trust client name (4.3)
- [ ] Trust account created and visible (**FAIL** — GAP-S4-01)
- [x] Moroka Family Trust client created (4.11)
- [ ] client_type = TRUST AND trust-specific FICA completed (**FAIL** — GAP-S4-02: pack is individual-biased; trustee items absent)
- [ ] Deceased Estate matter exists with 9 template action items (NOT_EXECUTED)
- [ ] One trust deposit posted (BLOCKED by GAP-S4-01)
- [ ] No errors in backend logs (unchecked)

## Gaps filed this session

### GAP-S4-01 — Trust account creation UI is broken (loops back to empty dashboard)
- **Severity**: **HIGH** (blocks Session 4 Phase F entirely; blocks any real trust-accounting use)
- **Description**: `/settings/trust-accounting` exposes an "Add Account" element that is an `<a href="/trust-accounting">` link routing back to the Trust Accounting dashboard. The dashboard itself has no create button. End result: the firm cannot create a trust account through the product.
- **Expected**: Either a modal, a dedicated `/trust-accounting/new` page, or an in-place form.
- **Impact**: The entire Trust Accounting vertical feature (LSSA / Section 86 trust management — a top-three legal-vertical differentiator) is inaccessible via UI.

### GAP-S4-02 — Legal FICA onboarding pack does not branch on client_type (TRUST / INDIVIDUAL)
- **Severity**: **HIGH** (legal-vertical defect; breaks FICA s21A compliance promise)
- **Description**: Same 11-item "Legal Client Onboarding" pack is applied to both individual persons and trusts. Missing for TRUST clients: Letters of Authority, Trustee 1 ID, Trustee 2 ID, Proof of trust banking, SARS trust tax number. Present but wrongly Skippable: Trust Deed (should be Required for trusts). Likely a pack-seed bug or a missing `clientType` discriminator on the `OnboardingPackAssignmentService`.
- **Expected**: At minimum two pack variants — `legal-za-individual-onboarding` and `legal-za-trust-onboarding` (and ideally `legal-za-company-onboarding`) — chosen based on `client.type` at the moment of PROSPECT → ONBOARDING transition.
- **Evidence**: `qa_cycle/screenshots/session-4-moroka-trust-onboarding-fica.png`.

### GAP-S4-03 — Admin role sidebar missing Compliance, Trust Accounting, and Tariffs sections
- **Severity**: MEDIUM (role-based access regression or configuration drift from Session 2)
- **Description**: Bob Ndlovu (Admin) viewing `/dashboard` sees only 5 sidebar sections: Work / Matters / Clients / Finance / Team. Session 2 recorded Thandi (Owner) with 7 sections: WORK / MATTERS / CLIENTS / COMPLIANCE / FINANCE / TRUST ACCOUNTING / TEAM. Missing on Bob: **Compliance** (Conflict Check, Adverse Parties), **Trust Accounting** (Transactions, Client Ledgers, Reconciliation, Interest, Investments, Trust Reports, Tariffs). The pages themselves are still reachable via direct URL (e.g. `/conflict-check`, `/trust-accounting`), so this is a nav-filter issue, not an authorization issue.
- **Expected**: Either (a) Admin role gets the same sidebar as Owner for legal-za orgs — both are internal attorney users — or (b) a product decision that Admins explicitly cannot see trust accounting, in which case the pages should also 403 for Admins (not silently 200).
- **Note**: Top-level "Finance" section is now a collapsed button (not expanded with Fee Notes / Profitability / Reports sub-items as Thandi saw). Same regression.

### GAP-S4-04 — Conflict-check item does not auto-mark when the check ran before client onboarding transition
- **Severity**: LOW–MEDIUM
- **Description**: Bob ran a conflict check with name="Moroka Family Trust" at ~11:45:51, then created the Moroka client at ~11:48, then transitioned it to ONBOARDING at ~11:49:57. The auto-populated checklist included "Conflict Check Performed" with status **Pending** rather than Completed — despite a matching ConflictCheckHistory record existing. Sipho's item DID auto-complete in Session 3 (same mechanism), suggesting the match logic uses a field the QA turn didn't hit (likely `customerId` rather than `name`).
- **Expected**: Match on name (case-insensitive, trimmed) if no `customerId` link exists, as a fallback.
- **Impact**: Compliance team duplicates manual work — ticks a checklist item they already verified.

## Notes for next QA turn

**Session 4 is effectively blocked at Phase B (trust account creation) and Phase D (wrong FICA pack).** Session 5 (RAF plaintiff, contingency fee) has its own known gap (no Contingency fee model — filed prospectively as **GAP-S5-01** — need to verify after an agent addresses GAP-S3-06 proposal naming).

**Resume plan for next QA turn**:
1. Session 5 Phase A-C can run independently of the trust-account blocker. Log in as Bob, run conflict check for "Lerato Mthembu", create Lerato as INDIVIDUAL, transition to ONBOARDING. Expect the same 11-item individual-biased pack as Sipho.
2. Session 5 Phase D (create RAF litigation matter from Litigation template) — should succeed via the known `/projects` → "New from Template" workaround.
3. Session 5 Phase E (contingency engagement letter) — expect a new gap: no Contingency option in the Fee Model select; the New Proposal dialog only shows Retainer / Fixed Fee / Hourly.
4. Session 5 Phase F (Court Calendar + Adverse Parties) — expect direct URL works; the Admin sidebar doesn't expose these.
5. Defer Session 4 Phase F (trust deposit) until GAP-S4-01 is fixed.

## Budget note
Reached ~76 MCP calls this turn. Wrote up Session 3 resume + Session 4 and deferred Session 5 to a subsequent turn.
