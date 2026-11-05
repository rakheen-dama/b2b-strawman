# Rulings index (added in the 636B review-fix commit, 2026-11-06)

The walker brief (`.epic-brief.md`) is uncommitted. This index lets a reader reconstruct every binding ruling from committed files. 0.R3 and the 0.R4 reconciliation are quoted verbatim; the 0.R4 base ruling and 0.R5 are reproduced in full further down this file.

- **0.R — 2026-11-05 ~21:45 SAST, before session 1.** Answers the scout's decisions D-1…D-8:
  - **D-1 → A.** Fresh tenant via the scenario's own Session 0, after a `pg_dump` backup of the old tenant. No write beyond the 629A recipe.
  - **D-2 → accepted.** Portal `/deadlines` (P-75.2–75.3) is narrated as Lerato, the owner of the RAF matter. Sipho's `/deadlines` was observed empty. This is a ruled walk deviation, not a scenario edit.
  - **D-3 (a)–(g) → authorised as NOTE rows.** Covers: client emails; mention placement; C2 negative comment; MC-1 matter comment; judge and firm note on the 80.4a court date; a prescription tracker at today − 3 y + 20 d; a POPIA scratch client.
  - **D-4 → accepted.** G88-F38, F46, F48 and F49 are suite-covered by 636A. F48/F49 are boundary-only and not walked.
  - **D-5 → accepted.** E.1 is scored over SCORED rows only, and SKIP rows are listed. E.11a/b/d are scored by the byte-identical verified heads (#1735 / #1739 markers).
  - **D-6 → accepted, with a hard clock rule.** No walking 00:00–02:00 SAST, and pause at 23:45. If Day 30 and Day 75 fall on different SA dates, F33 is scored on uuid-segmented `s3_key`s.
  - **D-7 → accepted.** Browser budget: ≤ 200 calls in session 1 and ≤ 250 per later session.
  - **D-8 → authorised.** `e2e-down.sh` before the walk.
  - **Severity rule:** walkers propose severities; the orchestrator confirms every HIGH/MEDIUM.
- **0.R2 — 2026-11-05 ~23:00 SAST, after session 1.**
  - **S1-78 (45.6–45.8) → option (a).** Create one extra approved, unbilled disbursement on Sipho's matter as NOTE row D-3(h), then score S1-78…S1-80.
  - **Numbering.** 636A-c1/c2/c3 become G89-F1…F3. The session-1 candidates become F4–F6. Session 2 continues from F7.
- **0.R3 — 2026-11-05 ~23:35 SAST, after session 2.** Quoted verbatim:
  > - **S2-13 blocker → option (c)**: NO correcting trust entry (memory `feedback_no_workarounds_in_qa` — blockers are fixed at root cause, never bypassed). S2-13 (75.2–75.8), S2-14 and every row whose observation requires Sipho's matter to be CLOSED (S2-50…S2-52 and any other row the walker finds depends on it) are scored **DEFERRED — blocked by G89-F12 (HIGH), owner: follow-up PR re-opening the owning trust epic**. Do not substitute another matter.
  > - **Severities confirmed by orchestrator** (code read): **G89-F11 HIGH** (`InterestService.createInterestRun` only rejects end<start; no elapsed-period guard → future-dated interest credits on the trust ledger; realistic early month-end run), **G89-F12 HIGH** (`TrustTransactionService.performApprovalCompletion:870` checks only the client ledger card; client-level interest credit carries no matter → matter sub-ledger in debit, closure blocked on the main Day-75 path), **G89-F13 MEDIUM** (ledger "Total Payments" excludes refunds → columns don't reconcile). F1–F10 LOW as proposed.
  > - **Exit verdict is therefore FAIL** regardless of the remaining rows; 636B.3 still walks every remaining non-blocked row so the register is complete.

  **Option lettering.** The 636B.2 HANDOFF below offered three options: (a) a corrective R 1,34 entry; (b) score S2-13/S2-14 against a different matter **or DEFERRED**; (c) treat G89-F12 as blocking until fixed. 0.R3 *called* its choice "option (c)". It implemented that choice as the DEFERRED branch of (b), with no substitute matter.
- **0.R4 — the governing ruling for 636B.3.** It has three parts:
  1. **The 23:50 SAST ruling.** "ORCHESTRATOR RULING 0.R4 … for 636B.3", reproduced in full below. Its items:
     - item 1: F12 confirmed HIGH;
     - item 2: F11 confirmed HIGH, "Founder may downgrade to MEDIUM in 636C if ruled an input-validation edge; record it HIGH now";
     - item 3: S2-13 → option (a), "S2-13 is scored on its own steps (75.2–75.8) after the NOTE";
     - item 4: the time rule;
     - item 5: everything else stands.
  2. **The founder amendment (00:4x SAST).** It lifts item 4, so 636B.3 may run inside 00:00–02:00 SAST, and date anomalies are product signals.
  3. **The orchestrator reconciliation (00:45 SAST).** Brief §0.R4, quoted verbatim:
     > ### 0.R4 Orchestrator reconciliation for 636B.3 (2026-11-06 00:45 SAST) — BINDING, supersedes 0.R3 bullet 1
     > - `HANDOFF.md` § "ORCHESTRATOR RULING … for 636B.3" (written 23:50, after 0.R3; founder amended only its item 4) is the governing ruling. **S2-13 → option (a)**: ONE corrective R 1,34 trust entry on Sipho's matter through the product UI, reference `CORR/G89-F12`, recorded as a NOTE prerequisite caused by G89-F12 (before/after matter balance −R 1,34 → R 0,00 + audit row). S2-12 stays FAIL (G89-F12). S2-13/S2-14/S2-50…S2-52 are then scored on their own steps. The 0.R3 "option (c) DEFERRED" bullet is superseded; record the supersession in `steps/days-46-90.md`.
     > - Time rule lifted by the founder: 636B.3 runs inside 00:00–02:00 SAST; record it; date-sensitive anomalies are product signals. S2-14/S2-16 use the D-6 fallback.
     > - 0.R3's severities (F11 HIGH, F12 HIGH, F13 MEDIUM, F1–F10 LOW) and "exit verdict FAIL" stand.
     > - Session budget: ≤ 250 browser calls per continuation session; at the cap append a HANDOFF block, commit+push, end.

  **Effect on 0.R3.** 0.R4 superseded 0.R3's first bullet as a whole: both the "(c)" label and the DEFERRED scoring. 0.R3's severities and its FAIL verdict stand.

  **S2-13 as a scored row.** 0.R4 item 3 makes S2-13 a SCORED row. This overrides the brief §4.2 table, which lists S2-13 as NOTE. The review-fix commit therefore moved S2-13 into the scored table of `steps/days-46-90.md`, and CORR stays its NOTE prerequisite.
- **0.R5 — 2026-11-06 ~01:20 SAST, after 636B.3.** The full text is the last section of this file and is repeated in `gaps.md` §1. Confirmed:
  - G89-F14, F16, F17, F18 stay MEDIUM.
  - G89-F22 is raised to HIGH.
  - G89-F23 is raised to MEDIUM.
  - Register: 3 HIGH · 6 MEDIUM · 17 LOW; exit FAIL.

**Final counts (after the review fixes; these supersede the 636B.3 block's "Totals" line):**
- Walk rows: 106 scored → PASS 95 · FAIL 8 · PARTIAL 3 · DEFERRED 0 (+2 N/A rows).
- Exit checkpoints:
  - legal E: PASS 6 · FAIL 2 · PARTIAL 1 · N/A 2;
  - portal P-E: PASS 3 · FAIL 2 · PARTIAL 3 · N/A 2, with one DEFERRED sub-item (P-E.8 Day-7 digest; cron Monday 08:00 SAST not reached).
- Register: 27 ids — HIGH 3 · MEDIUM 6 · LOW 18. Next free id: G89-F28.
- Closed-gap re-verification: 29 VERIFIED · 5 PARTIAL · 2 FAIL · 2 suite-covered.
- *Counts updated 2026-11-06 after the CodeRabbit review of PR #1742: S1-65 PASS → PARTIAL, S2-41 PASS → FAIL as written (new G89-F27 LOW scenario mismatch, P-3.4 request state), S2-56 PASS → FAIL as written (G89-F24 root); G88-F16 and G88-F29 VERIFIED → PARTIAL. Exit-checkpoint tallies unchanged.*

---

# 636B HANDOFF — written by 636B.1 walker at 2026-11-05T22:52:43+0200 (SAST)

- **Build:** `$MAIN` HEAD `86e0fd5deca903225f959723b1a43e99c4b32e3b` (= origin/main at 21:34:55) · KC-STACK 21:35:36–21:37:08 (clean compile, builder prune, pnpm install, `svc.sh start all`), backend restarted 21:38:43 for the D-1 reset · services: backend :8080 **76758** (start 21:38:43) · gateway svc 75499 / :8443 **75625** (21:36:58) · frontend svc 75638 / :3000 **75682** (21:37:03, next dev 16.3.8) · portal svc 75689 / :3002 **75733** (21:37:08, **next dev** — prod swap is S2-00, not done). `$MAIN` status = pre-walk (`portal/next-env.d.ts`, `tasks/insights/latest.stats.json` only).
- **Flyway:** tenant_5039f2d497cf **V11** (fresh, installed 21:45:00–21:45:02) · tenant_56399d06a773 **V11** (21:36:53) · public **V3**.
- **Tenant:** org "Mathebula & Partners", slug `mathebula-partners`, schema `tenant_5039f2d497cf` (D-1 option **A**), KC org `2cc9bcfd-fe7a-4f45-a343-0adddbd5ee28`, provisioned 2026-11-05T19:45:00Z. Old tenant backup: `/tmp/p88-tenant-backup-202611052137.sql` (818 575 B) + `/tmp/p88-tenant-backup-202611052137-portal-rows.sql`.
- **Users:** thandi@ / bob@ / carol@mathebula-test.local (SecureP@ss1 / SecureP@ss2 / SecureP@ss3), padmin@docteams.local / password — **all registered: yes** (KC two-step login; padmin single-step).
- **Clients (name · id · email · lifecycle):** Sipho Dlamini · 79546474-0f5b-4e2d-ade4-0263d70d3eeb · sipho.dlamini@email.co.za · ACTIVE | Moroka Family Trust · bafd05f9-2a27-4d89-b004-60dea4ee1a55 · moroka.trust@email.co.za · ACTIVE | Khumalo Property Holdings · 11c84e42-ab71-4b2a-a79e-baa61351cc87 · khumalo.holdings@email.co.za · ACTIVE | Lerato Mthembu · ec988df3-932f-4ef2-b68e-1d7c3c99f258 · lerato.mthembu@email.co.za · ACTIVE | (Nomvula not created — S2-04)
- **Matters (name · id · lead · team · status):** "Sipho Dlamini v. Standard Bank (civil)" · cd0ef789-214e-4001-978f-90c0b240ed95 · Bob · Bob, Carol · ACTIVE (case JHB/CIV/2026/001, court "Gauteng High Court, Johannesburg") | "Estate Late Peter Moroka" · c441d229-47c5-41d2-a686-68551583937f · Thandi · Thandi, Bob, Carol · ACTIVE (fee estimate 40 h / R 80 000) | "Khumalo Property Holdings — Erf 1234 Sandton Transfer" · a9f6a004-9107-4850-acd3-111ba4c05809 · Thandi · Thandi only · ACTIVE (OTP saved) | "Lerato Mthembu — RAF Claim (MVA 2025-11-03)" · e0f25d3f-f195-42fe-ba95-842e79de983d · Bob · Bob, Carol · ACTIVE (case RAF/2026/0042; adverse party Road Accident Fund, Opposing Party)
- **Tasks of note:** Bob's Sipho task "Initial consultation & case assessment" e1369071-36bd-47b8-95d5-4d422b96d6d7 (6.2 comment 9b84ef00-05c7-48e1-9b6f-ba3f2205c70c); Carol's "Letter of demand" d6e1f462-554b-46bf-879d-fec9fa37c8bd; RAF comment task "Prescription monitoring (3-year claim, 5-year for damages)" (33.1); Khumalo task "Close matter" (C2-neg). Sipho matter: 9 tasks all **Open** (74.1 will need all 9 moved). Estate: "Obtain death certificate & will" Done, 8 Open. RAF: 4 Done, 5 Open.
- **Comments:** MC-1 matter comment (SHARED) e90b7c2d-8ea1-4d5d-b22f-f4d8c468eb1f "@Bob please review the particulars" on the Sipho matter (P-60.2 expects it).
- **Trust:** account "Mathebula & Partners Trust Account" (GENERAL, Standard Bank 051001 / 0123456789, primary, single approval) · balance **R 53 750,00** · ledgers Sipho **R 3 750,00** (DEP/SIPHO-001 R 5 000 in, DEEDS/SIPHO-001 R 1 250 out, approved), Moroka **R 50 000,00** ("Initial fund for estate admin") · LPFF **6.50 %** effective **05 Nov 2026** (LPFF share 0.00 %).
- **Fee notes:** INV-0001 Sipho **SENT** R 5 335,00 (533aceec-93b9-4361-804e-f2fb1e47e461; Bob 1.5 h + Carol 2 h + sheriff R 750 + deeds R 1 250) · INV-0002 Moroka **SENT** R 12 224,50 (c7340009-63b9-462c-9e15-5933cabae4d9; tariff 3(a) × 4 printed last).
- **Documents:** Day-30 SoA doc **0693a6f6-8d0f-4395-9fc5-213ccee73010** visibility **PORTAL** size **5 758 B** sha1 **401aeb367813f9513f6efa131161837e4345a999** (file `logs/30.6-soa.pdf`); OTP doc 50d32e2e-dae0-40ff-9cee-d98699f36d4a; engagement letter 32dd3129-03d4-45e5-bb0c-d2335e36d4e1 (Accepted).
- **Requests:** REQ-0001 (Sipho, client-level, 1 item "Certified copy of ID document", due 2026-11-12) **Sent**.
- **Court dates:** RAF **Case Management 2026-12-20** (today + 45), Gauteng High Court. No court date / prescription on Sipho's matter.
- **Disbursements:** sheriff 1bf0cfb3-a942-4702-a820-b5005c8dbb65 R 750 and deeds d3d8939c-96b6-493f-994e-f4c9925e6147 R 1 250 (trust-linked to payment a1dccacc-4152-41b7-b267-f1c393c7d9ef) — both **Approved + Billed** (on INV-0001).
- **Mailpit:** total **12** at 22:49 SAST; last id 4gXPU2wZfqGSj4EWa8eVdt (Fee Note INV-0002 to moroka.trust@). Portal tokens minted for sipho.dlamini@: acceptance link 20:06:36Z (used 20:07), REQ-0001 email 20:18:28Z, INV-0001 email 20:44:52Z (fee-note emails may also carry a portal link) — mind the 3 tokens / 5 min per contact cap; S2-33 is well past it. Moroka: INV-0002 email 20:48:44Z.
- **Browser:** last actor **Thandi** (cookies + storage NOT cleared at the stop), viewport 1440×900, single Playwright context. **Tooling gotcha:** the MCP Chromium can stop delivering trusted input to the page (all clicks/keys silently dropped, even window capture listeners see nothing) — every `browser_run_code_unsafe` batch starts with `await page.bringToFront()`; that cleared it every time.
- **Browser calls used: 177 / 200.**
- **Last row done:** S1-77 (45.5) · **NEXT row: S1-78** (45.6–45.8), then S1-79 (45.9), S1-80 (45.10), then S2-00.
- **Blocking conflict for S1-78…S1-80 (needs an orchestrator ruling, not a walker call):** brief S1-72 says the Day-36 fee note takes "all unbilled time + **disbursements**"; I followed it, so both Sipho disbursements are **Billed** on INV-0001. Scenario 45.5 then needs "one APPROVED/UNBILLED disbursement from Sipho's matter (pre-state from Day 5 flow)" — none exists, and the brief's S1-80 also expects the written-off disbursement to be absent from a new fee-note selection. Options: (a) authorise creating one extra approved office-account disbursement on Sipho's matter as a D-3-style NOTE prerequisite, then run 45.6–45.10; (b) score S1-79/S1-80 DEFERRED. Note S1-80's "Outstanding fee notes = INV-0001 total" + "Trust balance R 3 750,00" can still be observed regardless.
- **Open findings so far** (`findings-session1.md`): G89-F1 LOW — 5.3 "Client comments" vs product "Comments" (scenario mismatch, design proven `matter-tab-groups.ts:70`) · G89-F2 LOW — matter Overview "HOURS LOGGED 0.0h" stale after logging time until reload (G88-M9 family) · G89-F3 LOW — Trust Receipt / Client Trust Statement print "— Section 78(1) Trust Account" (repealed Attorneys Act; `template-packs/legal-za/trust-receipt.json:122`, `client-trust-statement.json:122`). Numbering may shift +3 if `gaps.md` seeds 636A-c1…c3 as F1–F3.
- **Deviations / incidents:** (1) machine on **battery** throughout (no AC), 64 % → 39 %; (2) unrelated Kafka/postgres testcontainers from another project running on the machine (not touched); (3) Playwright input-freeze (above), not a product issue; (4) order shifts recorded as NOTE: 6.2 before 6.1; 28.1a at Day 22; 8.1 before Bob's MC-1 landing; (5) S1-02 "Submitting..." transient not captured (PARTIAL, harness); (6) S1-76 observed via HTML Preview, banking never configured (PARTIAL); (7) Day 30 rows ran on **2026-11-05 SAST** → for D-6, S2-16 / 75.x on 2026-11-05 keeps the same-day F33 precondition, otherwise use the fallback.
- **caffeinate** pid 74574 (`-dimsu -t 14400`, started 21:34 → expires ~01:34); power **battery**, lid open.

---

# 636B HANDOFF — appended by 636B.2 walker at 2026-11-05T23:25:56+0200 (SAST) — **paused for SAST boundary (23:45 rule)**

- **Build:** unchanged `86e0fd5de` (= origin/main at 22:54). backend :8080 **76758**, gateway :8443 **75625**, frontend :3000 **75682** (next dev). **Portal is now the PROD BUILD**: svc portal stopped 23:13; `pnpm run start` pnpm **99092** → next-server (v16.3.8) **99129** on :3002 (`logs/portal-build.log` exit 0; `localhost:8080` in 4 static files, `:8081` in 0). Left running for the portal block. **Restore (S2-65) still to do:** `kill 99129 99092` (exact PIDs, never pkill), `svc.sh start portal`, then `cp /tmp/636B-next-env-before.d.ts $MAIN/portal/next-env.d.ts` (the prod build reset that file to its committed content; the pre-walk dirty content + diff are in `/tmp/636B-next-env-before.d.ts` / `/tmp/636B-next-env-before.diff`). `$MAIN` status now: only `tasks/insights/latest.stats.json` dirty.
- **Power:** battery 37 % → 34 % discharging, then **charging (52 %) from ~23:22**; caffeinate 74574 (expires ~01:34).
- **Browser calls used this session: ≈109 / 250** (session 1: 177). Contexts: default (firm) — last actor **Thandi**, 1440×900, cookies not cleared at the stop; isolated context **"sipho"** (portal tab 1) still signed in as Sipho (portal_jwt in localStorage; magic link used 21:13:52Z).
- **Rows done this session:** S1-78 (NOTE), S1-79, S1-80, D-3(h) NOTE; S2-00…S2-12 (S2-13 BLOCKED); portal S2-30…S2-41, S2-54 (NOTE), S2-61 — driven early in the Sipho Day-68 sign-in (order deviation recorded in `steps/portal.md`).
- **NEXT row: S2-13 (75.2–75.8) — BLOCKED, needs an orchestrator ruling before 636B.3 continues.** Sipho matter trust balance is **-R 1,34**: S2-12 refunded the displayed *client* balance R 3 751,34 (R 3 750,00 + R 1,34 S2-03 interest, brief §9.10) against the matter, but the interest credit is client-level (no matter). Closure gate fails "Matter trust balance is -R1,34". Options: (a) authorise a corrective R 1,34 trust entry (e.g. a deposit/transfer-in on the matter) as a recorded NOTE prerequisite, then run S2-13/S2-14; (b) score S2-13/S2-14 (75.9) against a different matter or DEFERRED; (c) treat G89-F12 as blocking until fixed. S2-14 (75.9 closure letter + SoA s3_key) and S2-50/S2-51/S2-52 (P-60.x on the Closed matter) depend on this.
- **After S2-13/14:** S2-15 (C5), S2-16 (80.4a — Day 30 ran 2026-11-05 SAST; if 636B.3 runs after midnight, S2-14/16 use the D-6 fallback), S2-17…S2-29, then portal S2-42…S2-53, S2-55…S2-60, S2-62…S2-65 (Moroka sign-in for S2-46…S2-49 in a new isolated context; Lerato for S2-55…S2-60; Sipho again for S2-42…S2-45, S2-50…S2-53, S2-63/64 — Sipho's last token 21:13:52Z), then E-checkpoints.
- **New entities:** disbursement 69aa43bf-a010-4bb4-80d9-930b708449da (Sipho, Court Fees R 500, Approved → **Written off**); INV-0001 **PAID** (EFT/SIPHO-001, receipt "INV-0001/R1", `logs/55.2-INV-0001-R1.pdf` sha1 803720640b3856ec60eadaf64ed98c4fa902a5c2); interest run 9eb81568-a056-4d62-bf26-380615f66582 **POSTED** (05→06 Nov, R 19,15: Sipho 1,34 / Moroka 17,81, credits dated 2026-11-06); client **Nomvula Khoza** 5a174623-766f-4d7b-9745-36eeb6dc9a6b ACTIVE + matter "Nomvula Khoza — Debt Recovery" e691e213-4f64-4d55-88f3-8d69f47dccdd (Thandi lead; Carol, Bob members); Moroka PAY/MOROKA-001 R 12 000 **APPROVED** (Moroka ledger R 38 017,81); Sipho matter tasks 9/9 **DONE**, matter **COMPLETED**; Sipho REFUND/SIPHO-001 R 3 751,34 **APPROVED** (client ledger R 0,00, matter -R 1,34); REQ-0001 item **Submitted** by Sipho on the portal (request "In progress").
- **Trust now:** account balance R 38 017,81 (Moroka only). LPFF 6.50 % from 05 Nov 2026.
- **Mailpit:** total **15** (unread 9) at 23:25; latest: htwGiQjhaVeFCiBCedYZTP 2026-11-05T21:23:24.372Z Mathebula & Partners: Trust account activity → sipho.dlamini@email.co.za. Sipho has a "Trust account activity" email (refund) for P-21.1 / P-21.2.
- **Open candidates (session 2, `findings-session2.md`):** G89-F7 LOW Overview 4th KPI tile wraps alone · G89-F8 LOW Settings › Documents OTHER table 1621/856 · G89-F9 LOW 1024 Payment History wraps + 9 px scroll · G89-F10 LOW interest wizard shows client UUIDs · **G89-F11 HIGH (proposed)** interest run with a future period end accepted + posted, credits dated tomorrow · **G89-F12 HIGH (proposed)** matter-linked refund may exceed the matter's trust balance (matter -R 1,34; interest credit client-level) — blocks S2-13 · G89-F13 MEDIUM (proposed) ledger "Total Payments" excludes refunds.
- **Deviations:** portal swap moved from S2-00 to just before S2-06; Sipho portal rows P-0…P-3.4 + /deadlines + /profile run in the Day-68 sign-in; isolated-context default viewport was 1698 px — first 68.2 frame retaken at 1440; P-0.1 screenshot discarded (dev-mode link shows a live token).

---

# ORCHESTRATOR RULING 0.R4 — written by the phase orchestrator at 2026-11-05T23:50+0200 (SAST), for 636B.3 (adopted as governing by the 00:45 SAST reconciliation; see the Rulings index)

Context: the 636B.2 walker paused for the 23:45 SAST rule and asked for a ruling on S2-13. The `claude -p` invocation
for 636B was stopped by the orchestrator at 23:42 (it was idle, waiting past 02:00, beyond the runner's 200-min cap);
`./scripts/run-phase.sh 89 636B` is scheduled to resume at 02:05 SAST. The worktree and this branch carry every
session-1 and session-2 commit; nothing was lost. **636B.3 starts here, after 02:00 SAST, with this ruling applied.**

1. **G89-F12 — CONFIRMED HIGH, stays open.** A matter-linked REFUND must not exceed the matter's trust balance
   (`TrustTransactionService.recordRefund` / approval checks the client ledger-card balance only; interest credits are
   client-level). It is a trust money-path product bug; it is NOT a scenario mismatch and §9.10 is NOT amended.
   Disposition: G89 register (636C) → fix in the post-Phase-89 bug batch; do not fix in 636B.
2. **G89-F11 — CONFIRMED HIGH** (interest run with a future period end accepted and posted; credits dated tomorrow).
   Founder may downgrade to MEDIUM in 636C if ruled an input-validation edge; record it HIGH now.
3. **S2-13 / S2-14 and the dependent portal rows (S2-50…S2-52): option (a).** Before S2-13, make ONE corrective trust
   entry of **R 1,34 on Sipho's matter through the product UI** (a transfer-in / deposit as the firm would do to
   correct the matter ledger; reference it `CORR/G89-F12`). Record it as a **NOTE prerequisite caused by G89-F12**,
   with the before/after matter balance (−R 1,34 → R 0,00) and the audit row, in `steps/days-46-90.md`. Then run
   S2-13…S2-14 and the P-60.x rows against the product as it is. Score honestly: S2-12 is the row that FAILS
   (G89-F12); S2-13 is scored on its own steps (75.2–75.8) after the NOTE — never "PASS with gap"; split rows if a
   step is mixed. No SQL, no API shortcut: the corrective entry goes through the UI like any firm action.
4. **Time boundary:** do not start 636B.3 before 02:00 SAST. S2-14 / S2-16 use the D-6 fallback as the 636B.2 note
   says, because Day 30 ran on 2026-11-05 SAST and 636B.3 runs on 2026-11-06.
5. Everything else in the 636B.2 "After S2-13/14" plan stands. Browser-call budget and the Playwright input-freeze
   gotcha from session 1 still apply.

This ruling is the orchestrator's; the founder can override it before 02:05 by editing this section.

**Amendment (founder, 2026-11-06 00:4x SAST):** ruling item 4 is lifted. The founder authorised 636B.3 to start now,
inside the 00:00–02:00 SAST window, on the basis that `KaziTime` (PR #1716) makes every business "today" SA time
regardless of the UTC process clock. 636B.3 must record in `steps/days-46-90.md` that it ran inside the window, and
any date-sensitive anomaly (closure date, retention date, SoA period, portal dates) is a product signal to be read and
filed, not a zone artefact to be explained away.

---

# 636B HANDOFF — appended by 636B.3 walker at 2026-11-06T01:19:59+0200 (SAST) — **walk COMPLETE; gaps.md + markers.md written**

- **Clock:** ran 00:42–01:25 SAST 2026-11-06, inside 00:00–02:00 under the founder's amendment; recorded in `steps/days-46-90.md`. Every in-window date printed by the product was the SA date (no zone anomaly observed).
- **Build:** unchanged `86e0fd5de` (= origin/main, fetched 00:42). backend :8080 76758, gateway :8443 75625, frontend :3000 75682. **Portal restored (S2-65):** prod build 99129/99092 killed by exact PID; svc portal 19714 / next-server **19757** (`next dev`); `portal/next-env.d.ts` back to its pre-walk content. `$MAIN` status = pre-walk (`portal/next-env.d.ts`, `tasks/insights/latest.stats.json`).
- **Ruling 0.R4 applied:** corrective deposit **R 1,34 `CORR/G89-F12`** on Sipho's matter via Trust tab › Record Deposit (txn 73f9baff-…, matter -1,34 → 0,00, audit `trust_deposit.recorded`) as a NOTE; S2-12 re-scored **FAIL (G89-F12)**; 0.R3 option (c) supersession recorded.
- **Rows done this session:** CORR, S2-13…S2-29 (firm), S2-42…S2-65 (portal, restore incl.), E.1–E.11 + P-E.1–P-E.11 (`steps/exit-checkpoints.md`). Nothing left to walk. **NEXT: none — orchestrator review of `gaps.md` (confirm proposed MEDIUMs F14, F16, F17, F18, F22; founder §6 on F4, F24, F25), then the evidence PR.**
- **Totals:** 105 scored rows → PASS 97 · FAIL 6 · PARTIAL 2 · DEFERRED 0 (+2 N/A rows). Register 26 ids: HIGH 2 · MEDIUM 6 · LOW 18 → **exit FAIL**. Markers 26/26 exit 0. *(As written at 01:19; superseded by 0.R5 and by the review-fix recount (106 scored, S2-13 scored) — see "Final counts" in the Rulings index.)*
- **New this session (G89-F14…F26):** F14 MEDIUM matter Trust tab shows client ledger · F15 LOW 1024 documents table · F16 MEDIUM closure SoA counts written-off disbursement, "owing R500" · F17 MEDIUM reconciliation list always fails · F18 MEDIUM Section 35 pack hollow (re-opens G88-F40, S2-25 FAIL) · F19 LOW "trustaccount" placeholder · F20 LOW DSAR raw status · F21 LOW closure-letter ISO date · F22 MEDIUM portal trust ≠ firm client ledger (S2-45 FAIL) · F23 LOW portal activity announces internal drafts · F24/F25 LOW scenario mismatches (P-75.1, P-75.6) · F26 LOW "Matter type: LITIGATION" on portal.
- **Entities added:** trust txn CORR 73f9baff-4c04-4833-9f35-e345b854a1d8; Sipho matter CLOSED (closure letter + SoA INTERNAL); estate CLOSED (override); Hearing 3346fe45-… 2026-11-13 + prescription 45ae1185-… 2026-11-26 on RAF; client "POPIA Scratch p89rw" 1078b1c6-… (anonymised); DSAR 10e61cab-… COMPLETED; INV-0002 PAID (card, receipt INV-0002/R1).
- **Mailpit:** total 19. Magic links: Sipho Cf4e45QLfW3BmKYhh34oVJ, Moroka cbTnG3q9AL74oq8Rv5TPDz, Lerato QvyZWKT9uQNG4qhtmaTtnS.
- **Browser calls this session: 88 / 250.** Default context last actor Thandi (not cleared); isolated contexts sipho3 / moroka3 / lerato3 signed in.
- **Power:** AC, 100 %; caffeinate 74574 expires ~01:34, run-phase caffeinate 10620 still held.

---

# ORCHESTRATOR RULING 0.R5 — 2026-11-06 ~01:20 SAST, after 636B.3 (walk complete)
- Confirmed MEDIUM: G89-F14, F16, F17, F18 (code read at `86e0fd5de`). Raised: **G89-F22 → HIGH** (portal trust balance ≠ firm client ledger; trust money path), **G89-F23 → MEDIUM** (internal-draft generation surfaced on the client's activity feed). Register now 3 HIGH · 6 MEDIUM · 17 LOW; exit **FAIL**. Full text in `gaps.md` §1.
- 636B is complete as an evidence slice; HIGH/MEDIUM go to the post-Phase-89 follow-up batch (never fixed inside 636). 636C proceeds with the deck caveat that Sipho's portal trust frames show G89-F12 artefacts.
