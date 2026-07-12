# Day 4 — Sipho first portal login + FICA uploads `[PORTAL]` — 2026-07-06

Context swap performed: cookies cleared, portal :3002 confirmed live. No Keycloak form used anywhere.

## Phase A — Magic-link landing

| Checkpoint | Result | Evidence |
|---|---|---|
| 4.1 locate magic-link email | PASS | Mailpit `Y962HNm9Fvt9gdkcfyTePQ` "Information request REQ-0001 from Mathebula & Partners" |
| 4.2 click link | PASS (route shape differs from scenario text) | Link is `http://localhost:3002/auth/exchange?token=…&orgId=mathebula-partners` (product's current shape; scenario's `/accept/[token]` is stale wording — same flow) |
| 4.3 exchange → authenticated | PASS | Landed authenticated (portal session cookie); initial landing `/projects`, `/home` fully accessible. Note: post-exchange redirect goes to `/projects` rather than `/home` — non-material |
| 4.4 /home pending request with matter context | PASS | "Pending info requests: 1"; /requests row: REQ-0001 · "Dlamini v Road Accident Fund" · SENT · 0/3 submitted (indexed by matter per OBS-401) |
| 4.5 firm branding | PASS | Mathebula logo (alt "Mathebula & Partners logo", S3 branding URL) renders in portal header |
| 4.6 identity | PASS | Header shows "Sipho Dlamini" |
| 4.7 screenshot | PASS | `day-04-portal-home-first-login.png` |

## Phase B — Upload FICA documents

| Checkpoint | Result | Evidence |
|---|---|---|
| 4.8 request detail renders | PASS | `/requests/ed20f923…`: header "REQ-0001 / Dlamini v Road Accident Fund / 0/3 submitted • status SENT" + per-item upload list. (First attempt hit a portal-side 500 — environment fallout from the Day-2 `pnpm install` under a running dev server, `Cannot find module './static-paths-worker'` in next dist; resolved by `svc.sh restart portal frontend`. Not a product bug — page renders correctly on healthy server) |
| 4.9 three labelled slots | PASS | ID copy (PDF/JPG/PNG), Proof of residence (≤ 3 months), Bank statement (≤ 3 months, PDF only) — all "required" |
| 4.10 upload 3 PDFs | PASS | fica-id.pdf / fica-address.pdf / fica-bank.pdf from `qa_cycle/test-files/` |
| 4.11 (removed per OBS-402) | N/A | Confirmed: no request-level cover-message textarea exists |
| 4.12 per-item Upload and submit | PASS | Counter advanced 1/3 → 2/3 → 3/3 submitted; each item "Submitted — status: SUBMITTED"; envelope "3/3 submitted • status IN_PROGRESS" (matches OBS-403 state machine — no auto-Submitted envelope state) |
| 4.13 /home pending count → 0 | PASS | "Pending info requests: 0" |
| 4.14 screenshot | PASS | `day-04-fica-submitted.png` |

## Day 4 exit checkpoints
- Magic-link login, zero Keycloak forms: PASS
- Uploads stored (firm verifies Day 5): submitted per-item, SUBMITTED ×3
- State machine Sent → IN_PROGRESS (3/3 submitted): PASS
- No firm-terminology leaks observed on portal (nav: Matters/Trust/Fee Notes/Engagement Letters/Requests — client-appropriate)
- Footer "Powered by Kazi" (never DocTeams): PASS

## Infra note (environment, resolved in-cycle — not a product gap)
- Portal dev server crashed on `/requests/[id]` with `MODULE_NOT_FOUND ./static-paths-worker` after the workspace `pnpm install` (Day 2) swapped next's pnpm dir under the running processes. `svc.sh restart portal frontend` fixed it. Recorded for infra hygiene: restart node dev servers after any dependency install.
