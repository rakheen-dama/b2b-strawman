# Day 45 Retry — QA Verification Cycle 18 (2026-04-30)

**Branch**: `bugfix_cycle_2026-04-30`
**Actor**: Thandi (Owner) for OBS-2103b/2104c2 verification on `:3000`; Bob (Admin) for Day 45 forward.
**Stack health (pre-test)**: backend 99563 / gateway 18539 / frontend 68198 / portal 18737 — all RUNNING + HEALTHY.

## Step 1 — Verify OBS-2103b (Edit + Archive both fire dialogs)

**Result: PASS — VERIFIED.**

PR #1242 / squash `f0845762` refactor (dialog owns trigger button — no Slot, no cloneElement, no consumer-supplied children) confirmed working end-to-end on both customers.

| Customer | Edit click | Archive click | Status |
|----------|-----------|---------------|--------|
| Sipho Dlamini (Active/Active/INDIVIDUAL) | Dialog opens — heading "Edit Customer" — form populated (Name=Sipho Dlamini, Type=Individual disabled, Email=sipho.portal@example.com, Phone=+27 82 555 0101, ID=8501015800088, Address=12 Loveday St) | AlertDialog opens — heading "Archive Customer" — body "Archive Sipho Dlamini? Their project links will be preserved but they will be hidden from active customer lists." | PASS |
| Moroka Family Trust (Active/Prospect/TRUST) | Dialog opens — heading "Edit Customer" — form populated (Name=Moroka Family Trust, Type=Trust disabled, Email=moroka.portal@example.com, Phone=+27 11 555 0102, Address=45 Helen Joseph St, Gauteng) | AlertDialog opens — heading "Archive Customer" — body "Archive Moroka Family Trust? Their project links will be preserved but they will be hidden from active customer lists." | PASS |

Both buttons render side-by-side in the action row on both customers. Click handlers fire correctly on BOTH dialogs on BOTH customers — no polarity stripping, no missing onClick. The structural refactor (dialog component owns the `<Button>`, exposes `triggerLabel`/`triggerVariant`/`triggerSize`/`triggerClassName`/`triggerIcon` props) eliminates BOTH the original Radix Slot adjacency collision (OBS-2103) AND the lazy/RSC cloneElement onClick-strip (OBS-2103b) in one structural change. Console clean (0 errors, 0 warnings on customer detail page navigation).

Evidence:
- `qa_cycle/evidence/day-45-retry/obs-2103b-sipho-edit-opens.png`
- `qa_cycle/evidence/day-45-retry/obs-2103b-sipho-archive-opens.png`
- `qa_cycle/evidence/day-45-retry/obs-2103b-moroka-edit-opens.png`
- `qa_cycle/evidence/day-45-retry/obs-2103b-moroka-archive-opens.png`

## Step 2 — Verify OBS-2104c2 (CherryPickStep no setState-in-render)

**Result: PASS — VERIFIED (post-reload).**

PR #1243 / squash `e87219e0` fix (resolve `isCurrentlyExpanded` synchronously OUTSIDE the `setExpandedIds` updater, then issue `loadCustomerData(itemId)` after committing the toggle) confirmed working post-reload.

Methodology: Initial test before reload showed the warning in console (stale chunk from previous build cache — frontend HMR had not fully replaced the bundle). After `location.reload()`, navigated step 1 → step 2 → step 3, then logged a `---MARKER-BEFORE-EXPAND---` console marker, then expanded the Sipho row. Console messages AFTER the marker contain ZERO occurrences of "Cannot update a component (Router) while rendering a different component (CherryPickStep)" / "setState in render" — the warning is gone.

Step 3 expanded Sipho row renders correctly:
- `<h3>Disbursements</h3>` heading
- Table columns: Include / Date / Description / Category / Supplier / Amount
- 30 Apr 2026 / "Day 45 verify-OBS-2104b/c second sheriff service" / SHERIFF_FEES / Sheriff Pretoria / R 500,00 / Include checkbox CHECKED
- Subtotal R 500,00
- "1 customer included" footer

Console post-reload + post-expand: 0 errors, only benign warnings (font preload, scroll-behavior smooth — both pre-existing harness noise).

Evidence: `qa_cycle/evidence/day-45-retry/obs-2104c2-cherrypick-clean-console.png`.

## Step 3 — Day 45 Forward Execution

**Result: PASS — Day 45 COMPLETE.**

**Actor switch**: Signed out as Thandi → signed in as Bob Ndlovu (`bob@mathebula-test.local` / `SecureP@ss2`) via Keycloak realm `docteams` (BFF gateway `:8443/oauth2/authorization/keycloak`). User menu shows "BN" / "Bob Ndlovu" / `bob@mathebula-test.local`.

### Day 45.1 — Second info request: PASS

Matter RAF-2026-001 (Dlamini v Road Accident Fund, id `b7e319f7-fd7e-4526-a8b3-b40b1f85b34b`) → Requests tab → `New Request` → Create Information Request dialog opened with Customer auto-selected (Sipho Dlamini) and Matter auto-selected (Dlamini v Road Accident Fund). Set Due Date `2026-06-21` (Day 52 mapping). Reminder Interval default 5 days. Added 2 ad-hoc items:
1. "Hospital discharge summary"
2. "Orthopaedic specialist report"

Clicked Send Now → dialog dismissed → Requests tab list now shows:
- REQ-0001 (Day 3 FICA) — Completed — 3/3 accepted
- REQ-0003 (Day 45 medical evidence) — Sent — 0/2 accepted

Note: REQ-0002 sequence number consumed by Day 14 Moroka onboarding (per status carry-over). Sequencing: REQ-0001 (Sipho FICA) → REQ-0002 (Moroka L&D) → REQ-0003 (Sipho medical) — correct shared-counter behavior.

Scenario amend (minor): Scenario Day 45.1 specifies title "Supporting medical evidence" with 2 items (hospital discharge summary, orthopaedic report) — current dialog shape is "ad-hoc items list" with no parent "Title" field. The "Hospital discharge summary" + "Orthopaedic specialist report" two items effectively cover the scenario intent; the request as a whole is identified by code (REQ-0003) rather than user-supplied title. Triaged: scenario-amend WONT_FIX. Title-as-aggregator is a UX nuance, not a bug.

### Day 45.2 — Mailpit second magic-link email: PASS

Mailpit GET `/api/v1/messages?limit=5` confirms newest message:
```
2026-04-30T17:09:47.415Z | sipho.portal@example.com | Information request REQ-0003 from Mathebula & Partners
```

Email subject "Information request REQ-0003 from Mathebula & Partners" delivered to `sipho.portal@example.com` — magic-link flow consistent with REQ-0001 prior delivery.

### Day 45.3 — Trust deposit R 20,000: PASS

Matter Trust tab → Record Deposit dialog → Client/Matter pre-locked to Sipho Dlamini / Dlamini v Road Accident Fund (cannot edit — correct portal-context guard). Set Amount=20000, Reference=`DEP/2026/003`, Description="Top-up per engagement letter", Transaction Date=2026-04-30 (default today). Submit → dialog dismissed → matter Trust tab refreshes:
- Funds Held: R 71 000,00 (was R 51 000,00 — increment of R 20,000 ✓)
- Deposits: R 71 000,00
- Payments: R 0,00
- Fee Transfers: R 0,00

Evidence: `qa_cycle/evidence/day-45/trust-balance-71000.png`.

### Day 45.4 — Approve / client ledger R 71,000: PASS

No dual-approval required for trust deposits in this build (single-actor flow). Navigated Finance → Trust Accounting → Client Ledgers (`/trust-accounting/client-ledgers`):
- Sipho Dlamini: Trust Balance R 71 000,00 / Total Deposits R 71 000,00 / Last Transaction 30 Apr 2026
- Moroka Family Trust: Trust Balance R 25 000,00 / Total Deposits R 25 000,00 / Last Transaction 30 Apr 2026

R 71,000 = R 51,000 (Day 11 R 50k portal-visible deposit + Day 14 R 1k cycle-15 OBS-1101 verify deposit) + R 20,000 (Day 45 today). Scenario expectation R 70,000 amended to R 71,000 to reflect Day 14 carry-over.

Isolation: Moroka R 25,000 separate, no aggregation with Sipho's ledger. Day 14 OnD test still holds at Day 45.

Evidence: `qa_cycle/evidence/day-45/client-ledger-71k.png`.

### Day 45.5 — Matter Trust tab balance R 71,000: PASS (covered by 45.3 above).

**Day 45 checkpoints:**
- [x] Second info request dispatched (REQ-0003 Sent, 0/2 accepted)
- [x] Trust balance reconciles to R 71,000 on client ledger AND matter trust tab (R 70,000 scenario expected, R 71,000 actual due to Day 14 R 1k carry-over — amended)

### Scenario Amendments (Day 45)
- Scenario Day 45 expected ledger total R 70,000 (R 50k + R 20k); actual R 71,000 due to Day 14 R 1,000 OBS-1101 verify deposit. Amend `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` line 732 to read **R 71,000 (R 51,000 + R 20,000)** to capture cycle-15 carry-over.
- Scenario Day 45.1 specified title "Supporting medical evidence"; current Create Information Request dialog has no parent title field (request identified by code REQ-XXXX). The 2 individual items capture intent; triaged WONT_FIX.

## Step 4 — Day 60 Forward Execution

**Result: PARTIAL — Closure UI WIRED + VERIFIED, happy-path cannot complete due to demo-flow Day 22-46 prep gaps (not bugs).**

Navigated to RAF-2026-001 → Close Matter button → closure dialog Step 1 (gate report) opens correctly. The gate report is FULLY wired and renders truthful state:

| Gate | Actual State | Required for Clean Close |
|------|--------------|--------------------------|
| Matter trust balance | R 71,000 (BLOCK) | Must transfer to client/office |
| Disbursements approved | All approved (PASS) | — |
| Approved disbursements unbilled | 1 unbilled (BLOCK) | Must bill (DRP-002 R 500) |
| Final bill issued | 0h unbilled time + 1 unbilled disbursement (BLOCK) | Final fee note must be issued |
| Court dates today/later | 1 court date (BLOCK) | Must reschedule/complete (Pre-Trial 2026-05-14) |
| Prescription timers | None running (PASS) | — |
| Tasks open | 9 tasks (BLOCK) | Must close all tasks |
| Info requests outstanding | 1 (BLOCK) | REQ-0003 just sent — Day 46 Sipho responds |
| Document acceptances pending | None (PASS) | — |

Clicked Continue → closure dialog Step 2 (form) opens correctly:
- Reason dropdown: Concluded / Client terminated / Referred out / Other (default Concluded)
- Notes (optional) textarea
- **Generate closure letter** checkbox (default CHECKED) — "A PDF closure letter will be attached to this matter"
- **Generate Statement of Account** checkbox (default CHECKED) — "A PDF Statement of Account (Section 86 ledger reconciliation) will be attached to this matter" — Phase 67 Epic 491 surfaced as separate flag exactly as scenario 60.8 expects ✓
- **Override failing gates** checkbox (unchecked) — "This matter has failing gates. Override requires justification and will be logged"
- Back / Close matter buttons

**Phase 67 Epic 489 verified-wired**: gate report renders 9 gates with truthful state (3 PASS green-check, 6 BLOCK red-x with "Fix this" links).
**Phase 67 Epic 491 verified-wired**: SoA generation surfaced as separate checkbox alongside closure letter.

Evidence: `qa_cycle/evidence/day-60/closure-gate-report.png`, `qa_cycle/evidence/day-60/closure-step2-form.png`.

**Why happy-path doesn't complete**: The matter has 6 legitimate blocking gates from Day 22-46 demo-flow steps that the cycle skipped:
- Day 22-27 are scenario stubs (no checkpoints) — 9 tasks never advanced
- Day 21 Pre-Trial court date 2026-05-14 still scheduled (Phase B Day 21 PASS but never marked completed/cancelled)
- Day 28-30 generated INV-0001 PAID (covered) but Day 45 disbursement DRP-002 R 500 still unbilled
- Day 45.1 REQ-0003 just sent, Day 46 portal-Sipho response not yet executed
- Day 45.3 R 71,000 trust never transferred out (scenario Day 60.1-60.3 prep)

These are NOT product bugs; they're scenario-flow prep gaps. The Phase 67 closure UI is fully wired and renders correctly. Triaged: Day 60 UI = VERIFIED-WIRED, happy-path completion deferred to a full lifecycle run that includes Days 22-46 (not a separate bug-fix cycle).

**Day 60 checkpoints:**
- [x] Closure gate report renders (Phase 67 Epic 489)
- [x] Closure form renders Reason + closure letter + SoA + Override (Phase 67 Epic 489 + Epic 491)
- [ ] Matter closes cleanly on the happy path — **PARTIAL** (UI wired; happy-path requires Days 22-46 prep work outside cycle scope)
- [ ] Statement of Account PDF generated and attached to matter Documents — not exercised
- [ ] Mailpit notification email — not exercised

## Step 5 — Day 75 Forward Execution

**Result: SKIPPED.** Day 75 portal expectations (`/projects` shows RAF-2026-001 as CLOSED, weekly digest references closure events) presuppose Day 60 happy-path closure landed. With matter still ACTIVE, Day 75 cannot exercise its specific assertions. Late-cycle isolation already exhaustively validated at Day 15 (20 checkpoints PASS, zero leaks across list / direct-URL / API / activity / email layers).

## Summary Table

| Step | Verification | Outcome |
|------|--------------|---------|
| OBS-2103b | Edit + Archive both fire dialogs on Sipho + Moroka | **PASS** — VERIFIED (PR #1242) |
| OBS-2104c2 | CherryPickStep no setState-in-render warning | **PASS** — VERIFIED post-reload (PR #1243) |
| Day 45.1-45.5 | Bob: 2nd info request + R 20k trust top-up + ledger reconcile | **PASS** — REQ-0003 sent, R 71k ledger total (scenario amend R 70→71) |
| Day 60 | Phase 67 closure UI wired (gate report + Step 2 form + SoA flag) | **PARTIAL** — UI VERIFIED-WIRED, happy-path completion blocked on Days 22-46 prep gaps (not bugs) |
| Day 75 | Late-cycle portal isolation + closed-matter rendering | **SKIPPED** — Day 60 closure not landed; isolation already validated Day 15 |

## QA Position
- **Day**: 45 — **PASS, Day 45 COMPLETE.**
- 2 of 2 cosmetic re-verifications PASS (OBS-2103b + OBS-2104c2 both fixed).
- Day 60 closure UI fully wired and renders correctly (Phase 67 Epic 489 + Epic 491). Happy-path completion deferred — needs Days 22-46 demo-flow prep that's outside cycle 18 scope.
- Stack remains healthy.
- Carry-over entities: REQ-0003 (Sipho, sent, 0/2 accepted, due 2026-06-21); DEP/2026/003 R 20,000; trust balance R 71,000 / R 25,000 (Sipho / Moroka).
- New entity counts: 1 info request, 1 trust deposit. No new bugs filed.
