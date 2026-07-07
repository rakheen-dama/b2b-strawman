# Day 5 — Firm reviews FICA submission `[FIRM]` — 2026-07-06

Actor: Bob Ndlovu (fresh Keycloak login on :3000; Sipho's :3002 portal session intentionally preserved for the portal spot-check — different origins, no cross-contamination).

| Checkpoint | Result | Evidence |
|---|---|---|
| 5.1 matter Client > Requests | PASS | `?tab=requests` on RAF matter |
| 5.2 envelope In Progress, detail via /information-requests/{id} | PASS | Row: REQ-0001 / In Progress / 0/3 accepted. Detail at `/org/mathebula-partners/information-requests/ed20f923…` (canonical route, OBS-501); 3 items Submitted with fica-id.pdf, fica-address.pdf, fica-bank.pdf attached |
| 5.3 per-item Download operational | PASS | Download click navigates to LocalStack presigned URL `org/mathebula-partners/project/272be4f8…/3d55673f…`; object verified: curl on same presigned URL → HTTP 200, downloaded bytes are valid PDF 1.4 (626 B, matches fica-id.pdf). (Browser console showed one 404 — favicon.ico on the :4566 origin, cosmetic, not the document) |
| 5.4 Accept ×3 → envelope Completed | PASS | Counter 1/3 → 2/3 → 3/3 accepted; on 3rd Accept envelope auto-transitioned In Progress → **Completed**, "Completed on 6 Jul 2026" stamp |
| 5.5 Overview FICA card + activity trail | PASS | FICA card: "Done — Verified 6 Jul 2026", "View request" href = `/org/mathebula-partners/information-requests/ed20f923…` (OBS-501 fix verified). Activity feed: "REQ-0001 completed — all items accepted", 3× "Bob Ndlovu accepted …", 3× "portal.request_item.submitted" + 3× upload_initiated (Sipho), request sent/created events |
| 5.6 notification emails | PASS | Mailpit: "Item accepted — ID copy", "Item accepted — Proof of residence (≤ 3 months)", "Item accepted — Bank statement (≤ 3 months)", "Request REQ-0001 completed (Mathebula & Partners)" — 3+1 exactly as specified |
| Portal post-completion spot-check (OBS-502) | PASS | Sipho on :3002: /requests row "REQ-0001 / COMPLETED / **3/3 accepted**" (not 0/3 submitted); detail header "3/3 accepted • status COMPLETED" |

Day 5 exit checkpoints: 3 docs retrievable firm-side ✓; lifecycle Sent → IN_PROGRESS → Completed via last per-item Accept ✓; FICA indicator updated + canonical route ✓; OBS-502 portal counters ✓. Zero gaps.
