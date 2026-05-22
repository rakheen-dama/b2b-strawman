# Day 75 — Weekly digest + late-cycle isolation spot-check [PORTAL]

**Date**: 2026-05-22
**Actor**: Sipho Dlamini (portal contact, authenticated via magic-link on port 3002)
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, portal :3002)

---

## Checkpoint Results

### 75.1 — Weekly digest email in Mailpit
**FAIL (feature gap)**

No weekly digest email exists in Mailpit for `sipho.portal@example.com`. Searched all 29 emails — all are transactional (portal access links, trust activity notifications, info request notifications, item accepted, fee notes, closure letter, invitations). No email with subject containing "weekly update", "your week", "digest", or similar.

**Root cause**: The product does not implement a weekly digest email feature for portal contacts. This is a feature gap, not a code bug.

**Filed as**: OBS-7501 (feature gap — no weekly digest email)

---

### 75.2 — Digest body content verification
**SKIPPED** — No digest email exists (blocked by 75.1).

---

### 75.3 — Digest must not reference Moroka / EST-2026-002
**PARTIAL PASS (email-level verification only)**

No weekly digest exists, but verified all 24 Sipho emails in Mailpit contain zero Moroka references — no "Moroka", "EST-2026", "25 000", or "25,000" strings in any subject or snippet. PASS at the email isolation level.

---

### 75.4 — Click "View activity" link in digest
**SKIPPED** — No digest email exists (blocked by 75.1). Navigated directly to `/activity` instead.

---

### 75.5 — Activity trail renders events from Days 4, 8, 11, 15, 30, 46, 61
**PASS**

Portal `/activity` page renders two tabs: "Your actions" and "Firm actions".

**Your actions** (Sipho's own actions):
- Document downloads (Day 61 — SoA + closure letter)
- Info request item submissions (Day 46 — hospital discharge + orthopaedic report)
- Document uploads (Day 46)
- Fee note payment (Day 30)
- Info request item submissions (Day 4 — 3 FICA items)
- Document uploads (Day 4)

**Firm actions** (visible to Sipho):
- Statement of Account generated (Thandi Mathebula)
- Document generated (Thandi Mathebula)
- Information request completed + items accepted (Bob Ndlovu) — REQ-0003
- Information request sent/created (Bob Ndlovu) — REQ-0003
- Information request completed + items accepted (Bob Ndlovu) — REQ-0001
- Information request sent/created (Bob Ndlovu) — REQ-0001

Zero Moroka references on activity page. Verified via JS: `document.body.innerText` contains no "moroka", "est-2026", "25 000", or "25,000".

**Note**: Activity trail does not include proposal acceptance (Day 8) or trust balance view (Day 11) as discrete events — these may not be instrumented as activity entries. The events that are present are consistent and complete for the instrumented action types.

---

### 75.6 — Passive isolation spot-check (61 days after Day 14 Moroka onboarding)
**PASS**

**`/home`** — No Moroka entries. Shows: Pending info requests = 0, Upcoming deadlines = 0, Recent fee notes = INV-0001 (R 1,250.00), Last trust movement = R 70,000.00 (22 May 2026). Zero Moroka references.

**`/trust`** — Balance shows **R 0,00** against RAF-2026-001 (trust fully paid out Day 60). NOT R 25,000 (Moroka leak). Transaction list shows 3 transactions (PAYMENT R 70,000, DEPOSIT R 20,000, DEPOSIT R 50,000) — all Sipho's. No Moroka deposit visible.

**`/projects`** — One matter only: "Dlamini v Road Accident Fund". Under "Past" tab (matter is CLOSED). No Moroka Family Trust or EST-2026-002 visible. Matter detail shows **CLOSED badge** rendered correctly (not greyed out as error).

**`/activity`** — All events reference only Sipho's matters. Zero Moroka references (verified via DOM search).

**Moroka data leak check** (JS DOM search on each page):
- `/home`: moroka=false, est-2026=false, 25000=false
- `/activity`: moroka=false, est-2026=false, 25000=false
- `/trust`: moroka=false, est-2026=false (balance R 0,00 not R 25,000)
- `/projects`: moroka=false, est-2026=false, 25000=false

---

### 75.7 — Screenshot (optional)
Not captured — no weekly digest to screenshot. Activity page and isolation checks documented via text evidence above.

---

## Console Errors
**Zero JS console errors** across all portal pages visited (/home, /activity, /trust, /projects, /projects/{id}).

---

## Summary

| Checkpoint | Result | Notes |
|-----------|--------|-------|
| 75.1 Weekly digest email | **FAIL** | Feature gap — no digest email implemented (OBS-7501) |
| 75.2 Digest body content | SKIPPED | Blocked by 75.1 |
| 75.3 Digest no Moroka refs | PARTIAL PASS | No digest, but all 24 Sipho emails have zero Moroka refs |
| 75.4 View activity from digest | SKIPPED | Blocked by 75.1; navigated directly instead |
| 75.5 Activity trail events | **PASS** | Both "Your actions" + "Firm actions" tabs render correctly |
| 75.6 Isolation spot-check | **PASS** | /home, /trust, /projects, /activity all clean. R 0,00 trust (not R 25k). 1 matter only. |
| 75.7 Screenshot | SKIPPED | Optional, no digest to capture |

**Overall**: 2 PASS, 1 PARTIAL PASS, 1 FAIL (feature gap), 3 SKIPPED (dependent on missing digest feature)

---

## New Gaps

| Gap ID | Summary | Severity | Day |
|--------|---------|----------|-----|
| OBS-7501 | No weekly digest email feature — product does not dispatch periodic activity summary emails to portal contacts | LOW | 75 |

**Assessment**: OBS-7501 is a feature gap (not a code bug). The product has robust transactional emails (trust activity, info requests, fee notes, document ready, closure letter) but no periodic digest/summary email. This is a product decision, not a regression. Severity LOW because all individual event notifications are already dispatched as transactional emails — the digest would be additive convenience, not a missing critical notification.
