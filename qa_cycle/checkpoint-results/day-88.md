# Day 88 — Activity feed wow moment (firm + portal side-by-side) `[FIRM → PORTAL]` — cycle 2026-07-12 (run 2026-07-13)

**Actors**: Thandi (firm :3000, standing session), Sipho (portal :3002 — standing session expired mid-day; re-authenticated via fresh magic link `fknmXxp3xjCAiL2j7WfktG` → `/auth/exchange` → `redirectTo=/activity` honoured, zero Keycloak forms).

## Checkpoints

| # | Result | Evidence |
|---|--------|----------|
| 88.1 | PASS | Firm matter Activity tab @ 90-day lookback, All actors, Load more to exhaustion → **49 events**, full lifecycle end-to-end: `project.created_from_template` + `project.updated` (Day 3) → REQ-0001 created/sent → Sipho FICA uploads+submits ×6 → Bob downloaded fica-id.pdf + accepts ×3 + REQ-0001 completed (Days 4–5) → time logged 2h30m + 1h30m (Day 21) → disbursement created→submitted→approved(Thandi)→billed → court_date.created → invoice.sent (Day 28) → "Sipho Dlamini paid fee note INV-0001" (Day 30) → REQ-0003 created/sent/submits/accepts/completed (Days 45/46/60) → task transitions (2 DONE, 7 CANCELLED — zero Follow-up spawns, LZKC-013 shape holds) → "Thandi Mathebula closed the matter" → closure letter generated from template + statement of account generated → Sipho downloaded both (Day 61) |
| 88.2 | PASS | 📸 `day-88-firm-activity-feed.png` |
| 88.3 | PASS | Portal `/activity` — "A timeline of actions on your matter", tabs **Your actions / Firm actions** (fresh magic-link session) |
| 88.4 | PASS — **LZKC-020 fix HOLDS (this day's re-verification target)** | **Your actions** (14 items, all attributed "You"): FICA submits+uploads ×6 (Day 4), **"Engagement letter accepted — You" (Day 8)** — the prior-cycle defect (attributed to "System" under Firm actions, absent from Your actions) is resolved: acceptance now sits in the client's own trail with contact attribution, and **zero "System" attributions exist in either tab**; fee note paid (Day 30), REQ-0003 submits+uploads ×4 (Day 46), document downloads ×2 (Day 61). "First trust balance view (Day 11)" N/A — page views aren't audited (content-model precedent). Known payload-level `actor_name` residual is a logged observation, not re-filed |
| 88.5 | PASS | 📸 `day-88-portal-activity-trail.png` |
| 88.6 | PASS | Narrative coherence — every client-visible firm event has a portal "Firm actions" counterpart at matching times: REQ-0001/0003 created/sent/item-accepted/completed ×2 cycles (Bob), "Fee note sent to you" (Bob, = firm `invoice.sent`), "Matter closed" (Thandi), "Document generated for you" + "Statement of Account generated" (Thandi). Sipho's actions mirror into the firm feed as named portal events. Internal-only events (time, disbursements, court date, task transitions) correctly absent from the client view. Single-day compressed cycle → ≤1-day delay trivially satisfied |

## Day 88 day-level checkpoints

- Firm and portal activity feeds each internally complete: **PASS**
- Semantic match across POVs — no client-visible firm event missing from portal: **PASS** (and the prior cycle's one attribution defect is now fixed)

## Observations (not new gaps)

- Firm feed retains "performed X on Y" fallback copy for `invoice.sent` / `disbursement.*` / `court_date.created` / `project.updated` / `project.created_from_template` — same raw-copy family as the carried `project.created` observation (LZKC-019 fixed the closure/generation/download/task/time/request verbs; these residual verbs unchanged from prior cycle, already tracked, not re-filed).
- Portal Firm-actions attribution now shows real names (Thandi Mathebula / Bob Ndlovu) — improvement consistent with LZKC-020-class fixes.

## Console

0 errors on either side during Day 88 flows (one Next.js smooth-scroll WARNING on portal, framework advisory).

## Gaps

None new.
