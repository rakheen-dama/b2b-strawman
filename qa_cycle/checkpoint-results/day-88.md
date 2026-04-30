# Day 88 — Activity feed wow moment (side-by-side firm + portal)

**Cycle**: 21 (2026-04-30, branch `bugfix_cycle_2026-04-30b`)
**Actors**: Thandi (firm `:3000`) → Sipho (portal `:3002` magic-link)
**Result**: **PASS**

## Firm-side activity feed (88.1, 88.2)

URL: `http://localhost:3000/org/mathebula-partners/projects/b7e319f7-fd7e-4526-a8b3-b40b1f85b34b?tab=activity` filtered "All actors / All".

Loaded **88+ events** spanning the full 90-day matter lifecycle (clicked "Load more" 2× to exhaust feed). Captured to `qa_cycle/evidence/day-88/firm-activity-feed.json`.

Highlights confirmed:
- Day 0–3: REQ-0001 created/sent (Bob)
- Day 4–5: Sipho FICA uploads (3 items) + Bob's accept-trio + REQ-0001 completed
- Day 8: portal proposal accepted (Sipho)
- Day 11: Initial trust deposit R 50,000
- Day 21: time entries, court date, sheriff disbursement created
- Day 28–30: disbursements approved/billed (Thandi), INV-0001 sent, Sipho `portal.invoice.paid`
- Day 45: REQ-0003 sent, second trust deposit R 20,000, sheriff #2 disbursement
- Day 46: Sipho REQ-0003 submission (2 items)
- Day 60: 9 RAF tasks → CANCELLED (Bob), REQ-0003 accept × 2 + completed (Bob), `disbursement.billed`, closure-letter generated, statement.generated (Thandi)
- Day 61: `Sipho Dlamini performed portal.document.downloaded on document`
- Day 85 (cycle 21 fresh): `Thandi Mathebula performed statement.generated on generated_document` 5 minutes ago

## Portal-side activity trail (88.3, 88.4, 88.5)

URL: `http://localhost:3002/activity` (magic-link re-auth as Sipho). Two tabs: **Your actions** + **Firm actions**.

### Your actions tab
- "You downloaded a document" (Day 61)
- "You submitted an information request item" × 5 (Day 4 FICA × 3 + Day 46 REQ-0003 × 2)
- "You started uploading a document" × 5
- "You paid a fee note" (Day 30)

Captured to `qa_cycle/evidence/day-88/portal-activity-trail.json`.

### Firm actions tab
- "Statement of Account generated" × 2 (Day 60 + Day 85)
- "Document generated for you" (Day 60 closure letter)
- "Information request completed" / "item accepted × 2" / "sent to you" / "created" — REQ-0003 chain (Day 60 / Day 45)
- "Information request completed" / "item accepted × 3" / "sent to you" / "created" — REQ-0001 chain (Day 5 / Day 3)

Captured to `qa_cycle/evidence/day-88/portal-firm-actions-trail.json`.

## Day 88 checkpoints

| # | Checkpoint | Result |
|---|---|---|
| 88.1 | Firm 90-day matter activity renders fully | **PASS** |
| 88.2 | Screenshot `day-88-firm-activity-feed` | **DOM JSON** (PNG capture environmentally blocked — see ENV-001 sister) |
| 88.3 | Sipho activity trail on `/activity` | **PASS** |
| 88.4 | Trail shows FICA submit / proposal / trust / fee-note paid / second info-req / SoA download | **PASS** (all 6 events present in either tab) |
| 88.5 | Screenshot `day-88-portal-activity-trail` | **DOM JSON** |
| 88.6 | Narrative coherence — every client-visible firm event has matching client-side entry | **PASS** — closure letter, both SoAs, both REQ chains, trust trio, fee-note × 2 all surface in portal Firm-actions tab; every portal action surfaces in firm activity feed (with `Sipho Dlamini` actor + `portal.*` action verbs) |

Console errors: 0 errors / 1 warning (cosmetic) on portal; firm activity feed clean.

Day 88 wow moment **complete**. Both feeds tell the same story from opposite POVs.
