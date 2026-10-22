# HANDOFF-B → Walker C (617C capstone, legal-za 90-day, Keycloak)

**Last completed step: 21.1** (doc line 363). Walker C starts at **Day 22, step 22.1** (doc line 368).
Walker B totals: Days 1–7 = 59 steps → 43 PASS · 14 FAIL · 2 BLOCKED, plus one back-reference row (0.50) PASS; Days 8–21 = 39 steps → 31 PASS · 8 FAIL. Gaps G-B1…G-B19 in `gaps.md`.

## Stack state (left RUNNING, 15:56Z)

Unchanged from HANDOFF-A: services from the **main checkout** (HEAD `dac048f12`), same PIDs all walk long — backend 60278 · gateway 60449 · frontend 60581 · portal 60640, all healthy (`bash /Users/rakheendama/Projects/2026/b2b-strawman/compose/scripts/svc.sh status`). Docker dev infra up; e2e stack DOWN. `/tmp/.e2e-keycloak-mode` still exists (leave until Part C ends). caffeinate PID 41495 alive. Main checkout still has the dev-artefact edit to `portal/next-env.d.ts` (do not commit; restore after services stop).
Browser at handoff: logged in as **Bob** on the estate matter Fees › Fee estimate. Clear cookies before switching.

## Still holds from HANDOFF-A

Org `mathebula-partners` (`http://localhost:3000/org/mathebula-partners/`), KC org `2c4034d3-f76d-4323-a172-7eabd018657d`, tenant schema `tenant_5039f2d497cf`; users Thandi (Owner, `SecureP@ss1`), Bob (Admin, `SecureP@ss2`), Carol (Member, `SecureP@ss3`), padmin/`password` — all logged in fine via the two-step KC form during Walker B. Rates (billing 2500/1200/550, cost 1000/500/200), VAT 15%, 6 matter templates, trust account `b53ed14e-1944-4941-bb63-dfa18d8d138a` (LPFF 6.5%). Never run `keycloak-bootstrap.sh`.

## Created by Walker B (all observed in the UI; IDs from the URL / read-only SELECTs)

| Kind | Name | ID | State |
|---|---|---|---|
| Client | Sipho Dlamini (INDIVIDUAL) | `ce83c576-9bd9-4c7a-80b9-39100ade80d7` | ACTIVE; email sipho.dlamini@email.co.za |
| Matter | Sipho Dlamini v. Standard Bank (civil) (LITIGATION) | `458bc331-d51e-4a94-99d8-32ecad3de00e` | ACTIVE; 9 tasks all Unassigned; **0 members**; case_number JHB/CIV/2026/001, court_name Gauteng High Court, Johannesburg |
| Engagement letter | generated_document `9c8491ef-1c45-4bf5-84d7-f21c9bc565a7` | acceptance_request `858d721a-fc74-42d3-bff5-56c77a7bb8a7` | **ACCEPTED** by Sipho 15:35:54Z (a duplicate unsaved row `e8c23d6f…` also exists, G-B6) |
| Time | Bob 1.5 h "Drafting particulars of claim" on Sipho task "Issue summons / combined summons" | — | R1,800, unbilled |
| Disbursement | Sheriff fee — service of process, R750, OFFICE_ACCOUNT, zero-rated | `a5aafdc4-20be-4bc8-a940-e77172002cca` | APPROVED, UNBILLED. **No deeds-office R1,250 disbursement** (5.9 FAIL) |
| Docs (Sipho matter) | engagement letter PDF, "Particulars of Claim — draft v1.pdf" | — | Uploaded |
| Client | Moroka Family Trust (TRUST) | `24b98ee9-f65e-47bb-ba8e-55acae27ec34` | ACTIVE; email moroka.trust@email.co.za (walker test value; dialog requires email) |
| Matter | Estate Late Peter Moroka (ESTATES) | `bb2ce7c7-17d1-4121-8ac1-ee907863a7be` | ACTIVE; lead/only member Thandi; 3 tasks DONE (death cert, report to Master, Letters of Executorship); 18.0 h = Thandi 11 h + Bob 7 h = R35,900 unbilled; fee estimate 40 h / R80,000, 45% On Track |
| Trust deposit | "Initial fund for estate admin", Moroka / estate matter | `aff6ea56-411a-4226-a3e2-8f5b9178387b` | DEPOSIT R50,000 **RECORDED**; trust balance R 50 000,00 |
| Client | Khumalo Property Holdings (INDIVIDUAL) | `e1229036-88c4-49ae-9ade-9d773d0f72fc` | ACTIVE; email khumalo.holdings@email.co.za (test value) |
| Matter | Khumalo Property Holdings — Erf 1234 Sandton Transfer (CONVEYANCING) | `0d9bf92c-d6b0-4993-bcf6-5dcbf2ffe547` | ACTIVE; lead Thandi; property_address / erf 1234 / deeds_office JOHANNESBURG |
| OTP | generated_document `e2ab5daa-4569-4cbb-b0da-5b62b704f6f8` | acceptance_request `ce2f21b7-946a-4271-8189-9f56ecfd3722` | **VIEWED, not accepted**, expires 2026-10-29 |

Lerato Mthembu does not exist yet (Walker C creates her on Day 22). No fee notes, no diary events, no adverse parties yet.

## Portal

| Contact | Email | ID | Notes |
|---|---|---|---|
| Sipho Dlamini | sipho.dlamini@email.co.za | `ac43d200-cffd-4a30-91b8-f0796db96383` | auto-created from the client email when the engagement letter was sent (GENERAL, ACTIVE) — needed for 68.1 |
| Moroka Family Trust | moroka.trust@email.co.za | `7d49d818-3e9e-4cce-94b4-cc1326734ad6` | auto-created (trust deposit) |
| Khumalo Property Holdings | khumalo.holdings@email.co.za | `ca957639-a8ad-4172-b725-c16181572c70` | auto-created (OTP send) |

How the portal was used: only the **public** acceptance links (`http://localhost:3002/accept/<token>` from the Mailpit email Text), opened after `context().clearCookies()` + `localStorage/sessionStorage.clear()` on :3002. **No magic-link portal login was performed** by Walker B, so the 3-per-5-min magic-link budget for Sipho is untouched for 68.1. Afterwards cleared cookies and re-logged into :3000.

## Mailpit (dev :8025) — total 8, do NOT delete

| ID | To | Subject |
|---|---|---|
| `NTJgYA4eAEgkt8R3YE4NZP` | sipho.dlamini@ | Mathebula & Partners -- Please review engagement letter for acceptance: engagement-letter-litigation-…pdf |
| `A7HfxBpigZkmFBiGzL42iw` | sipho.dlamini@ | Confirmed: You have accepted engagement-letter-litigation-…pdf |
| `FrRPJuefbwR9sXbWDHWXBF` | moroka.trust@ | Mathebula & Partners: Trust account activity |
| `j6q5Ymg3wDEu4SwYyDiC9f` | khumalo.holdings@ | Mathebula & Partners -- Please review engagement letter for acceptance: offer-to-purchase-…pdf (OTP; subject says "engagement letter", G-B16) |
(+ the 4 Walker A messages listed in HANDOFF-A)

## Gotchas (read before starting)

1. **No way to add matter members (G-B3, HIGH).** Template matters get 0 members unless you pick a **Matter lead** in "New from Template — Configure"; the lead becomes the only member and receives the template's task assignments. So for Lerato's RAF matter pick a lead (Bob is the Day 22 actor). Consequences you will hit: task Assignee only offers the lead; **Carol (Member) sees no matters** ("You're not on any matters yet") and gets "Something went wrong" on any matter URL — every Carol step (time, comments, @mentions, My work) will FAIL. Bob (Admin) and Thandi (Owner) can open and log time on any matter.
2. **Tab deep-links pin the panel (G-B14).** If a matter URL carries `?tab=…`, clicking other groups/sub-tabs does not switch the panel. Navigate without `?tab` (or to `?tab=<target>` directly: `fields`, `details`, `tasks`, `documents`, `generated`, `budget`, `overview`; client pages: `fields`, `details`, `onboarding`, `documents`, `matters`, `audit`).
3. Task detail sheet: open with `?tab=tasks&taskId=<id>` (taskId alone does nothing). Status Done is only offered from In Progress (Open → In Progress → Done).
4. Time is logged per task ("Log Time" in the task row); Fees › Time recording is a read-only summary. The Overview "Hours logged" card can be stale until reload.
5. Client "New Client" requires an Email even when the doc gives none; Walker B used `<name>@email.co.za` test values.
6. Generate Document preview: **do not click "Download PDF" before "Save to Documents"** — it persists an extra generated-document row (G-B6).
7. File uploads: use `browser_click` on the drop zone then `browser_file_upload`. Do not also `waitForEvent('filechooser')` inside `browser_run_code_unsafe` (the file uploads twice). Test PDFs live in `/Users/rakheendama/Projects/2026/b2b-strawman/.playwright-mcp/*.pdf` (tiny one-page PDFs; create more there if needed — the MCP rejects paths outside the main checkout).
8. Portal `/accept/<token>` never shows the PDF (X-Frame-Options DENY, G-B7) and has no Decline (G-B19); typing the name + "I Accept" works.
9. Trust deposit dialog has no "source" field; put it in Description. The trust-link for TRUST_ACCOUNT disbursements finds nothing (G-B11) — relevant if Walker C needs trust-linked disbursements.
10. Disbursement category choice auto-sets VAT (Sheriff Fees → Zero-rated pass-through).
11. Matter Activity feed omits acceptance events and prints raw keys like "performed project.created_from_template on project" (G-B8) — expect this on Day 88.
12. KC login script used every switch: `clearCookies → goto /dashboard → fill #username → submit → waitFor #password → fill → submit → waitForURL /org/`.

## Exact last step

21.1 — as Bob, estate matter Fees › Fee estimate showed 18h / 40h (45%) and R 35 900,00 / R 80 000,00 (45%), "On Track" (screens/21.1-fee-estimate-45pct.png). Evidence files: `days-01-07.md`, `days-08-21.md`, `gaps.md` (G-B1…G-B19), `index.md` (B rows filled; 0.50 / CP0.5 closed as PASS).
