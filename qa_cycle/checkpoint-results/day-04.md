# Day 4 — Sipho first portal login + FICA uploads `[PORTAL]` — Cycle 2026-07-12

Context swap performed: cookies + localStorage cleared, fresh navigation; portal :3002 confirmed live (curl 307→login). No Keycloak form used anywhere.

**Actor**: Sipho Dlamini via magic-link (Mailpit msg `mbGJM7DAwdk3GrYs6A4SDZ`).

## Phase A — Magic-link landing

| Checkpoint | Result | Evidence |
|---|---|---|
| 4.1 locate magic-link email | PASS | Mailpit `mbGJM7DAwdk3GrYs6A4SDZ` "Information request REQ-0001 from Mathebula & Partners" (Mailpit API — standing REST exception) |
| 4.2 click link | PASS (route shape note carried) | Link is `http://localhost:3002/auth/exchange?token=…&orgId=mathebula-partners` — product's current shape; scenario's `/accept/[token]` remains stale wording (same as prior cycle, non-gap) |
| 4.3 exchange → redirect | PASS | Token exchange succeeded; redirected to `/home` (this run lands on `/home` directly — prior cycle landed `/projects`) |
| 4.4 /home pending request with matter context | PASS | Home card "Pending info requests: 1"; `/requests` row: REQ-0001 · "Dlamini v Road Accident Fund" · SENT · 0/3 submitted (indexed by matter per OBS-401) |
| 4.5 firm branding | PASS | `img alt="Mathebula & Partners logo"` renders in portal header |
| 4.6 identity | PASS | User menu shows "Sipho Dlamini" |
| 4.7 screenshot | PASS | `day-04-portal-home-first-login.png` |

## Phase B — Upload FICA documents

| Checkpoint | Result | Evidence |
|---|---|---|
| 4.8 request detail renders | PASS | `/requests/c0f67daa-9ceb-4280-a31b-53e2434adcee`: header "REQ-0001 / Dlamini v Road Accident Fund / 0/3 submitted • status SENT" + per-item upload list |
| 4.9 three labelled slots | PASS | ID copy (PDF/JPG/PNG), Proof of residence (≤ 3 months) (PDF/JPG/PNG), Bank statement (≤ 3 months) (PDF only) — all marked required, full FICA descriptions |
| 4.10 upload 3 PDFs | PASS | fica-id.pdf / fica-address.pdf / fica-bank.pdf from `qa_cycle/test-files/` staged via file inputs; per-item "Upload and submit" enabled after staging |
| 4.11 (removed per OBS-402) | N/A | Confirmed: no request-level cover-message textarea exists |
| 4.12 per-item Upload and submit | PASS | Counter advanced 1/3 → 2/3 → 3/3 submitted; each item "Submitted — status: SUBMITTED"; envelope "3/3 submitted • status IN_PROGRESS" (OBS-403 state machine — no auto-Submitted envelope state) |
| 4.13 /home pending count → 0 | PASS | "Pending info requests: 0" |
| 4.14 screenshot | PASS | `day-04-fica-submitted.png` |

## Day 4 exit checkpoints

- Magic-link login, zero Keycloak forms: PASS
- Uploads stored (firm verifies Day 5): submitted per-item, SUBMITTED ×3
- State machine Sent → IN_PROGRESS (3/3 submitted): PASS
- No firm-terminology leaks on portal (nav: Matters/Trust/Deadlines/Fee Notes/Engagement Letters/Requests — client-appropriate; no "task"/"ticket"): PASS
- Footer "Powered by Kazi" (never DocTeams): PASS

Console: 0 errors across all Day 4 navigations (only benign Next.js `scroll-behavior: smooth` dev warning).

Observation (non-gap, transient): first paint of `/requests` briefly renders default nav labels ("Invoices"/"Proposals", no firm logo) before the org profile loads and swaps in legal-za labels ("Fee Notes"/"Engagement Letters") + logo — loading-state flash, settles within ~1s, no hydration error.

## Gaps

- None new.

## IDs for later days

- Matter: `66451e87-4723-49c4-b363-e696b68ff6b0`
- Customer (Sipho): `d0c7daf5-7085-4560-afb9-e9e937db5abc`
- REQ-0001: `c0f67daa-9ceb-4280-a31b-53e2434adcee`
- Deal DEAL-0001: `3aad1c89-b8b8-4d27-a94d-687be0682180`
