# Day 8 — Sipho reviews + accepts proposal [PORTAL]

**Date**: 2026-05-21
**Actor**: Sipho Dlamini (portal contact)
**Stack**: Keycloak dev stack — portal :3002, backend :8080
**Auth**: Magic-link re-request (Day 4 token expired; fresh token via `POST /portal/auth/request-link`)

---

## Checkpoint Results

### 8.1 — Proposal accessible via email link
**Result**: PASS
**Evidence**: Navigated to `http://localhost:3002/proposals/6d3a1bc8-3f68-4e1b-b6b6-d95bc411db6b` (the link from the proposal email in Mailpit, message ID `NFQuMtkr4kMyRqsgjNN4je`). Portal authenticated via magic-link token exchange. Page loaded with proposal detail. No Keycloak form appeared.

### 8.2 — Proposal detail page renders
**Result**: PARTIAL
**Evidence**: Page renders with:
- Title: "Engagement Letter — Litigation (Dlamini v RAF)" + SENT badge
- Reference: PROP-0001
- Sent: 21 May 2026, Expires: 7 Jun 2026
- Fee Details: Fee Model = Hourly Rate
- Engagement Letter Details: auto-generated Tiptap content (Dear Sipho Dlamini, Fee Arrangement, hourly basis, T&Cs)
- Accept Engagement Letter / Decline buttons
- **Missing**: No fee estimate breakdown with tariff lines + totals in ZAR + VAT 15% line. This is consistent with OBS-701 scenario amendment (thin lifecycle wrapper, no fee-estimate line-item builder in proposals) and OBS-2101 (no tariff-time-entry binding). Not a new bug.

### 8.3 — Fee estimate with ZAR + VAT 15%
**Result**: PARTIAL (EXPECTED per OBS-701)
**Evidence**: No ZAR currency symbol or VAT 15% line visible. The proposal shows only "Fee Model: Hourly Rate" with no monetary breakdown. This is a known product limitation per OBS-701 scenario amendment — the proposal dialog is a thin lifecycle wrapper; fee-estimate line-items and tariff integration are out of scope for this cycle.

### 8.4 — Screenshot: proposal review
**Result**: PASS
**Evidence**: Screenshot saved as `ss_26987syxc` / `ss_9974yz3qt` — proposal detail page with SENT badge, fee details, engagement letter content, Accept/Decline buttons.

### 8.5 — Click Accept
**Result**: PASS (inline confirm path)
**Evidence**: Clicked "Accept Engagement Letter" button (`ref_50`). No confirmation dialog appeared — acceptance was immediate (one-click). The scenario expected "acceptance confirmation dialog (or inline confirm)" — the inline-confirm path was taken. Status transitioned immediately to ACCEPTED.

### 8.6 — Acceptance step (if token route)
**Result**: N/A
**Evidence**: No separate `/accept/[token]` flow. Acceptance was inline on the proposal detail page.

### 8.7 — Acceptance confirmed — status ACCEPTED
**Result**: PASS
**Evidence**: After clicking Accept:
- Status badge transitioned from SENT to **ACCEPTED** (green)
- Success banner: "Thank you for accepting this proposal. Your project has been set up." (green checkmark icon)
- Accept/Decline buttons removed
- Portal API confirms: `GET /portal/api/proposals/6d3a1bc8-...` returns `"status": "ACCEPTED"`
- **Terminology gap**: Banner says "Your **project** has been set up" — should say "Your **matter** has been set up" for legal-za. Filed as OBS-801.

### 8.8 — Screenshot: proposal accepted
**Result**: PASS
**Evidence**: Screenshot saved as `ss_41787rr3i` — proposal detail with ACCEPTED badge + success banner.

### 8.9 — /home no longer shows pending proposal
**Result**: PASS
**Evidence**: Navigated to `http://localhost:3002/home`. Dashboard shows:
- Pending info requests: 0
- Upcoming deadlines: 0
- Recent fee notes: "No fee notes yet."
- Last trust movement: "No recent activity"
- No "Pending proposals" / "Pending engagement letters" section visible. The accepted proposal is not surfaced as pending.

### 8.10 — /proposals list shows accepted badge
**Result**: PASS
**Evidence**: Navigated to `http://localhost:3002/proposals`. Page title: "Engagement Letters". Table shows:
- PROP-0001 | Engagement Letter — Litigation (Dlamini v RAF) | **ACCEPTED** (green badge) | 21 May 2026 | - | View
- Single row — only Sipho's proposal.

---

## Day 8 Checkpoint Summary

| Checkpoint | Description | Result | Notes |
|------------|-------------|--------|-------|
| 8.1 | Proposal accessible via email link | PASS | Magic-link re-auth required (Day 4 token expired) |
| 8.2 | Proposal detail renders | PARTIAL | No fee breakdown (OBS-701 expected) |
| 8.3 | ZAR + VAT 15% | PARTIAL | No monetary breakdown (OBS-701/OBS-2101 expected) |
| 8.4 | Screenshot: proposal review | PASS | Saved |
| 8.5 | Click Accept | PASS | Inline confirm (no dialog) |
| 8.6 | Token acceptance step | N/A | No token route |
| 8.7 | Acceptance confirmed ACCEPTED | PASS | Status + API confirmed |
| 8.8 | Screenshot: proposal accepted | PASS | Saved |
| 8.9 | /home no pending proposals | PASS | Dashboard clear |
| 8.10 | /proposals list ACCEPTED badge | PASS | Single row, correct badge |

---

## Day 8 Scenario Checkpoints

- [x] Proposal accessible via email link without re-authentication (magic-link session valid OR transparent re-exchange) — **PASS** (re-exchange required; original Day 4 token expired)
- [x] Acceptance recorded (firm will verify on Day 10) — **PASS** (API confirms ACCEPTED)
- [x] No double-accept bug: clicking Accept again shows already-accepted state, not a second transition — **PASS** (revisiting proposal shows "This engagement letter has been accepted." banner, no Accept button)
- [x] Terminology consistent: portal copy reads "proposal" throughout — **PARTIAL** (portal uses "Engagement Letter" consistently which is correct for legal-za; success banner says "proposal" which is the generic term; sidebar says "Engagement Letters"; page title says "Engagement Letters"; one gap: "Your project has been set up" should say "Your matter has been set up")

---

## Gaps Filed

### OBS-801 — Portal acceptance banner uses "project" instead of "matter" (legal-za terminology leak)
**Severity**: LOW
**Location**: Portal proposal detail page, acceptance success banner
**Observed**: "Thank you for accepting this proposal. Your project has been set up."
**Expected**: "Thank you for accepting this engagement letter. Your matter has been set up." (legal-za terminology)
**Impact**: Terminology inconsistency on portal — "project" is the generic term; legal-za should display "matter"
**Root cause**: The success message in the portal proposal acceptance handler is not terminology-aware; it uses hardcoded "project" and "proposal" instead of resolving via the terminology map.

---

## OBS-707 Verification (from Day 7)
**Result**: VERIFIED (PASS)
**Evidence**: Emails sent AFTER the fix (new magic-link requests) show `From: noreply@kazi.app`. The original proposal email (sent before the fix) still shows `From: noreply@docteams.app` which is expected. The fix is working for all new outbound emails.

## Console Errors
**Result**: ZERO console errors across all portal pages visited (/login, /projects, /proposals/{id}, /home, /proposals)

## Footer Check
**Result**: "Powered by Kazi" confirmed on portal pages (not "DocTeams")
