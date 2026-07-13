# Day 5 — Firm reviews FICA submission `[FIRM]` — Cycle 2026-07-12

**Actor**: Bob Ndlovu (fresh Keycloak login on :3000 — two-step KC theme: email page → password page; synthetic events per harness note). Sipho's :3002 portal session intentionally preserved for the portal spot-check (different origins, prior-cycle precedent).

| Checkpoint | Result | Evidence |
|---|---|---|
| 5.1 matter Client > Requests | PASS | `?tab=requests` on RAF matter; all 7 `tab-group-*` testids present (details/overview/work/finance/client/schedule/activity) |
| 5.2 envelope In Progress, detail via /information-requests/{id} | PASS | Row: REQ-0001 / Dlamini v Road Accident Fund / Sipho Dlamini / **In Progress** / 0/3 accepted / 12 Jul 2026. Row link → `/org/mathebula-partners/information-requests/c0f67daa-9ceb-4280-a31b-53e2434adcee` (canonical route, OBS-501). Detail: Contact Sipho / sipho.portal@example.com, Reminder every 5 days, Sent 12 Jul, Due 19 Jul; 3 items **Submitted** with fica-id.pdf, fica-address.pdf, fica-bank.pdf attached; per-item Download/Accept/Reject |
| 5.3 per-item Download operational | PASS | Download click navigates to LocalStack presigned URL `docteams-dev/org/mathebula-partners/project/66451e87…/e4a3677d…`; curl on same presigned URL → HTTP 200, 626 bytes, valid PDF 1.4, **byte-identical to fica-id.pdf** (`cmp` match). Console: one cosmetic 404 (favicon.ico on :4566 origin), not the document |
| 5.4 Accept ×3 → envelope Completed | PASS | Counter 1/3 → 2/3 → 3/3 accepted; on 3rd Accept envelope auto-transitioned In Progress → **Completed**, "Completed on 12 Jul 2026" stamp |
| 5.5 Overview FICA card + activity trail | PASS | FICA card: "Done — Verified 12 Jul 2026", "View request" href = `/org/mathebula-partners/information-requests/c0f67daa…` (OBS-501 verified). Activity tab: "REQ-0001 completed — all items accepted", 3× "Bob Ndlovu accepted …", 3× "Sipho Dlamini submitted …" + 3× "Sipho Dlamini started uploading document …" (LZKC-019 friendly copy + LZKC-020 portal actor attribution hold), plus request sent/created events |
| 5.6 notification emails | PASS | Mailpit 21:03:30–32Z: "Item accepted — ID copy", "Item accepted — Proof of residence (≤ 3 months)", "Item accepted — Bank statement (≤ 3 months)", "Request REQ-0001 completed (Mathebula & Partners)" — 3+1 exactly |
| Portal post-completion spot-check (OBS-502) | PASS | Sipho on :3002 `/requests` row: "REQ-0001 / Dlamini v Road Accident Fund / COMPLETED / **3/3 accepted**" (not 0/3 submitted); detail header "3/3 accepted • status COMPLETED" |

## Day 5 exit checkpoints

- Three uploaded documents retrievable firm-side: PASS (presigned URL verified byte-identical)
- Lifecycle `Sent → IN_PROGRESS → Completed` via last per-item Accept (no separate Mark-as-Reviewed): PASS
- Matter FICA indicator updated; FICA card link canonical `/information-requests/{id}` (OBS-501): PASS
- Portal OBS-502 counters (COMPLETED, 3/3 accepted): PASS

Console: 0 product errors (only :4566 favicon 404, cosmetic).

## Gaps

- None new.
