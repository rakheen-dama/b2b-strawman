# 648C observed gates — PR #1798 @ 4bb0d595f (2026-11-17, UTC)

All commands ran from the worktree `/Users/rakheendama/Projects/2026/worktree-epic-648C`, at HEAD
4bb0d595fda6b8c1ad5c55fca19636a438ded5d7.

**Tree state.** Before the run the tree held only this new `gates/` dir (`tree-before.txt`). After the run it was the
same (`tree-after.txt`). HEAD is unchanged, and nothing was committed or pushed.

| Gate | Result |
|---|---|
| E2E-LEGAL lifecycle | **PASS**: 105 passed / 0 failed / 0 skipped, first run (no re-run needed) |
| E2E-LEGAL depth-ii | **PASS**: 40 passed / 4 skipped (the baseline skips) / 0 failed, first run |
| OBS a — Investment, section 86(3) | **PASS** (UI = API = DB) |
| OBS b — Investment, section 86(4) | **PASS** (UI = API = DB) |
| OBS c — no basis select for General / Section 86; Investment → General hides it; General row has NULL basis, `S86_2` | **PASS** |
| OBS d — API refusal probes (API evidence) | **PASS**: 400 "Investment basis not allowed" and 400 "Investment basis required"; 0 rows added |
| OBS e — no raw enum on the settings tab or the dialog | **PASS**: 0 raw tokens; the known F-4 `ACTIVE` badge was recorded |
| OBS f — clip probe at 1280 / 1440 (1920 reference) | **PASS**: no overflow, nothing truncated. At 1280 the primary row's section label wraps, which is not a clip (`found-obs.md` W-1) |

Extra checks from the brief's OBS plan, folded into the runs above:
- Investment with no basis is refused inline, and **no POST** is sent: PASS.
- `GET /api/trust-accounts` carries `investmentBasis` and `section` on every account: PASS.
- The audit events `trust_account.created` carry `investment_basis`: PASS.
- Console errors 0, page errors 0: PASS.
- Backend log for the OBS window: 0 WARN, 0 ERROR.

**Ground rules kept:**
- No source edits, no commits, no pushes, no Maven. `pgrep -fl surefire` was empty before each stack and each e2e run.
- Exactly one e2e stack ran at a time. `e2e-down.sh` ran before each `e2e-up.sh`, and stack #2 was a fresh build after a
  full wipe.
- OBS changed state **only through the UI**. Alice (owner) signed in via `/mock-login`. The two (d) refusal probes are
  API calls labelled as API evidence: the UI cannot express them, and they created nothing.
- psql was used read-only, as evidence. The API used the browser context's own `mock-auth-token` against `:8081`.
  There were **no SQL writes** and no API writes that succeeded.
- Node: nvm 20.11.1 and `/opt/homebrew/bin/pnpm` by absolute path, with `NODE_OPTIONS=""` and
  `PLAYWRIGHT_BASE_URL=http://localhost:3001`. Flags were passed without `--`.
- The Playwright MCP profile was not touched. Every browser run is a headless library script from the worktree's
  `frontend/node_modules`.
- Long commands ran as `nohup` + rc marker + bounded poll. Timings (UTC) are in `timings.txt`. Every `*.log` is
  summarised in `logs-summary.txt`.
- An unrelated project's containers (`maestro-perf-*`, incl. a k6 load run) appeared on the Docker host at 21:05 UTC.
  They were not touched (`found-obs.md` E-1).

## Stack builds
| Stack | Built | Flyway / seed | Evidence |
|---|---|---|---|
| #1 (lifecycle + OBS) | "Building from source at: /Users/rakheendama/Projects/2026/worktree-epic-648C", rc 0 (20:58:12–21:00:08 UTC) | V22 success=t; 1 trust account (seeded GENERAL, basis NULL) | `stack0-down.log`, `stack1-build.log`, `stack1-proof.txt`, `stack1-backend.log`, `stack1-frontend.log`, `stack1-down.log` |
| #2 (depth-ii) | same source path, fresh after the wipe, rc 0 (21:08:54–21:09:30) | V22 success=t; same seed | `stack2-*` |

Proof that both stacks run this worktree's code (`vproof.sh`). The two outputs are identical apart from the Flyway
`installed_on` timestamps, as an empty diff shows.
- **Backend jar `/app/app.jar`:**
  - `trustaccounting/TrustAccountSection.class` exists (648C new) and contains `S86_2` / `S86_3` / `S86_4`.
  - `TrustAccountService.class` contains "Investment basis required", "Investment basis not allowed" and
    `investment_basis` (the audit detail).
  - `TrustAccountService$TrustAccountResponse.class` contains `investmentBasis` and `section`.
  - `TrustAccountService$CreateTrustAccountRequest.class` contains `investmentBasis`.
  - `TrustAccount.class` contains `investment_basis` and `getSection`.
  - The latest tenant migration in the jar is `V22__phase92_investment_account_basis.sql`. It contains
    `investment_basis IS NOT NULL` (the F-1 deviation).
- **DB:**
  - `flyway_schema_history` top row is **22 "phase92 investment account basis" success = t**.
  - `trust_accounts.investment_basis` is `character varying(20)`, nullable.
  - `chk_trust_account_investment_basis` = `CHECK ((account_type = 'INVESTMENT' AND investment_basis IS NOT NULL AND
    investment_basis IN ('FIRM_DISCRETION','CLIENT_INSTRUCTION')) OR (account_type <> 'INVESTMENT' AND
    investment_basis IS NULL))`.
- **Frontend `.next`:** `trust-account-basis-select`, "the firm invests surplus trust money" and "invested on a client"
  are in `server/chunks/ssr/frontend_components_trust_AddTrustAccountButton_tsx_13c4uoj._.js` and the static chunks
  `3_hzooh3fzqt4.js` / `1wwiung7p17ns.js`. "Choose the investment basis" is in
  `frontend_lib_schemas_index_ts_0tntwfm._.js`. "Section 86(4) investment account" and "Invested at the firm" are in
  the SSR chunks `_13ydhih._.js` and others.
- **Runtime:** health is UP and `:3001` returns 200. There is one tenant schema, `tenant_7d218705360b`.

## Gate 1a — E2E-LEGAL lifecycle (stack #1, fresh): PASS (observed)
**105 passed / 0 failed / 0 skipped in 3.3 min**, rc 0 (21:00:19–21:03:42 UTC). This matches the baseline of 105, on
the first run. `lifecycle-last-run.json` reads `passed`.
- Log: `lifecycle.log`. Per-test list: `lifecycle-summary.txt`.
- Trust, investment and interest lines (46): `lifecycle-trust-tests.txt`. Among them: Day 60 "Place investment —
  Moroka R200,000 … (s86(3))" and "QuickCollect R30,000 … (s86(4))"; Day 90 "Investment Register with s86 basis".
  These are `trust_investments` rows, not INVESTMENT trust accounts.
- The lifecycle creates **no** INVESTMENT trust account. After the suite the tenant still had 1 trust account (the
  seeded GENERAL), so the V22 backfill had nothing to touch on this seed.
- Suite screenshots (16): `lifecycle-artifacts/`.

## Gate 1b — E2E-LEGAL depth-ii (stack #2, fresh): PASS (observed)
**40 passed / 4 skipped / 0 failed in 1.9 min**, rc 0 (21:10:07–21:12:05 UTC). This matches the baseline of 40/4. The
4 skips are exactly the baseline ones:
- `day-30-45-soa-writeoff.spec.ts:361` (fixme);
- `null-prefetch-probe.spec.ts:74` ×3 (opt-in).

Outputs:
- `depth-ii.log`, `depth-ii-summary.txt`, and `depth-ii-last-run.json` (`passed`).
- Trust and disbursement lines (22): `depth-ii-trust-tests.txt`.
- Screenshots (10): `depth-ii-artifacts/`.
- Trust accounts after the run: `depth-ii-trust-accounts.out` (1 GENERAL, basis NULL).

## Gate 2 — OBS (stack #1, after the lifecycle run)

### Runs
| Script | Window (UTC) | What |
|---|---|---|
| `obs.cjs` run 1 | 21:04:25–21:05:37, rc 1 | harness miss before any submit (`found-obs.md` H-1); count stayed 1; kept in `obs/run1-failed/` |
| `obs.cjs` run 2 (tag **6MHSR**) | 21:06:01–21:06:40, rc 0 | **run of record**: a–e, plus the brief's extras |
| `probe-clip.cjs` runs 1–2 | 21:06:59–21:07:37 | harness fixes (`found-obs.md` H-2); run 2 kept in `obs/probe-run2/` |
| `probe-clip.cjs` run 3 | 21:07:56–21:08:16, rc 0 | **run of record**: f (read-only; the dialog is cancelled) |

Where the outputs are:
- JSON: `obs/obs-result.json`, `obs/probe-clip.json`.
- SQL: `obs-sums.sql` / `obs-sums.out`.
- API: `obs/api-*.json`.
- Screenshots: `obs/NN-*-1440.png` / `-1280.png`, with `.txt` page text and `*-crop.png` crops.
- Raw-token scans: `obs/scan-*.txt`.

**Baseline:**
- DB: 1 account, `Mathebula & Partners Trust Account | GENERAL | NULL | primary`.
- API: `investmentBasis: null, section: "S86_2"`.
- The settings row reads "… Primary ACTIVE … **Section 86(2) trust banking account**", with no basis line
  (`01-baseline-*`).

### Investment with no basis: refused inline, no POST (brief b)
**What the dialog showed after choosing Investment:**
- The "Investment basis" select appears with the placeholder "Select the basis".
- The FormDescription reads "Interest on a section 86(3) investment belongs to the Legal Practitioners' Fidelity Fund. On
  a section 86(4) investment the client receives 95% and the Fund 5%."
- The listbox options (`05-b0-*`, `05-b0-basis-listbox-crop.png`) are exactly:
  - "Section 86(3) — the firm invests surplus trust money"
  - "Section 86(4) — invested on a client's instruction"

**The submit.** Name, bank, branch and number were filled in, primary was unticked, and Create Account was clicked
with no basis chosen. Result:
- The inline message "**Choose the investment basis**" appeared, and the field turned red (`06-b1-dialog-no-basis-crop.png`).
- **0 POST requests** were sent during the submit (network log).
- `trust_accounts` count stayed 1 → 1, and there is 0 rows named "OBS no-basis 6MHSR".

### a. Investment, section 86(3): PASS (`07`, `08`; `api-a-*`)
The dialog read Investment / "Section 86(3) — the firm invests surplus trust money", with primary **unticked**
(`aria-checked=false`). Create Account sent one `createTrustAccount` server-action POST, and the dialog closed.

| Check | UI (settings row) | API | DB |
|---|---|---|---|
| OBS s86(3) Investment 6MHSR | "**Section 86(3) investment account**" / "**Invested at the firm's discretion**" (`08-a2-settings-row-crop.png`) | list and `GET /{id}` 200: `accountType INVESTMENT`, `investmentBasis FIRM_DISCRETION`, **`section S86_3`**, `isPrimary false` | `INVESTMENT \| FIRM_DISCRETION \| f`; count 1 → 2 |

### b. Investment, section 86(4): PASS (`09`, `10`; `api-b-*`)
| Check | UI | API | DB |
|---|---|---|---|
| OBS s86(4) Investment 6MHSR | "**Section 86(4) investment account**" / "**Invested on a client's instruction**" (`10-b2-settings-row-crop.png`) | `investmentBasis CLIENT_INSTRUCTION`, **`section S86_4`**, `isPrimary false` (list and by id) | `INVESTMENT \| CLIENT_INSTRUCTION \| f`; count 2 → 3 |

### c. No basis for non-investment types: PASS (`02`, `03`, `11`–`13`; `api-c-list.json`)
**Basis select by type:**

| Type | Basis select in the DOM | "Investment basis" label | Screenshot |
|---|---|---|---|
| General (the default) | **0** | 0 | `02-c1-*` |
| Section 86 Trust Account | **0** | 0 | `03-c2-*` |
| Investment, with "Section 86(4) — invested on a client's instruction" picked | 1 | 1 | `11-c3-*` |
| switched back to General | **0** | 0 | `12-c4-*` |

**After switching back to General**, OBS created "OBS General 6MHSR" (primary unticked; one server-action POST; count
3 → 4):
- UI: the row reads "**Section 86(2) trust banking account**" with **no basis line** (`13-c5-settings-general-row-crop.png`).
- DB: `GENERAL | NULL | f`.
- API: `investmentBasis: null`, **`section: "S86_2"`**.

The 86(4) choice made before the switch was not carried into the request.

### d. API refusal probes (API evidence, labelled as such): PASS (`api-d1-*.json`, `api-d2-*.json`)
Both probes were `POST http://localhost:8081/api/trust-accounts` with `Authorization: Bearer <Alice's mock-auth-token
from the browser context>`. Common fields in each body: `bankName`, `branchCode`, `isPrimary:false`,
`requireDualApproval:false` and `openedDate`.

| Body | Status | ProblemDetail (`application/problem+json`) |
|---|---|---|
| `accountType:"GENERAL"`, `investmentBasis:"FIRM_DISCRETION"` | **400** | title "**Investment basis not allowed**"; detail "Only an investment account carries an investment basis. A trust banking account reports under section 86(2)." |
| `accountType:"INVESTMENT"`, no `investmentBasis` | **400** | title "**Investment basis required**"; detail "An investment account needs its basis: section 86(3) (the firm invests surplus trust money) or section 86(4) (invested on a client's instruction)." |

DB: count **4 before = 4 after**, and 0 rows named "OBS refused …". The backend log has no WARN or ERROR for either
probe (`obs-window-backend-lines.txt`: 12 INFO scheduler lines only).

### e. No raw enum on screen: PASS (`obs/scan-*.txt`)
**Method.** The tokens `FIRM_DISCRETION`, `CLIENT_INSTRUCTION`, `S86_2`, `S86_3`, `S86_4`, `S86_`, `INVESTMENT`,
`SECTION_86` and `GENERAL` were scanned for case-sensitively, as whole tokens. Each scan checked both `innerText`
(visible, after CSS text-transform) and `textContent` (source text).

**Scope.** 10 scans:
- the settings card at baseline and at the end;
- the whole settings `main` at the end;
- the dialog as General, as Section 86, as Investment, filled for a, filled for b, and switched to General;
- the open basis listbox.

**Result:** **0 raw tokens in all 10.** The only enum-like token is the known **`ACTIVE`** status badge (F-4, recorded,
not a failure). The type select shows "General" / "Investment" / "Section 86 Trust Account", and the settings rows show
the section and basis in words.

### f. Clip probe 1280 / 1440 (1920 reference): PASS (`obs/probe-clip.json`, `obs/f-probe-*`)
**Trust Accounts card (4 rows):**
- No descendant outside the card or the viewport; none truncated; card `scrollWidth = clientWidth`.
- At 1280 the card is 544–1256, each row 569–1231, and the section column 999–1231 / 1011–1231.
- At 1440 the card is 544–1416 and the rows are 569–1391.
- Every section and basis line has `scrollWidth = clientWidth`.
- At 1280 the primary row's section label wraps to two lines. That is a wrap, not a clip (W-1).

**Dialog, Investment, basis select open:**
- At 1280 the dialog is 384–896 (512 px, inside the viewport), with 0 offenders and 0 truncated.
- The listbox is 410–871, inside the dialog and the viewport.
- Both options are 415–866, one line each, `scrollWidth = clientWidth`, so not truncated. 1440 gives the same at
  +80 px.

**After "Section 86(4) — invested on a client's instruction" is picked:** the trigger is 409–871, inside the dialog, and
its value span is 317/317, not truncated (`f-probe-dialog-basis-picked-*-crop.png`).

**Console errors:** 0 in run 3.

### Audit (brief h): PASS (`obs-sums.out` "final: audit")
OBS added 3 `trust_account.created` events:
- s86(3) `details.investment_basis = "FIRM_DISCRETION"`;
- s86(4) `details.investment_basis = "CLIENT_INSTRUCTION"`;
- General: **no** `investment_basis` key.

The seeded GENERAL account's event also has none.

### Final state (`api-z-final-list.json`)
| Account | type | investmentBasis | section | primary |
|---|---|---|---|---|
| Mathebula & Partners Trust Account | GENERAL | null | S86_2 | true |
| OBS s86(3) Investment 6MHSR | INVESTMENT | FIRM_DISCRETION | S86_3 | false |
| OBS s86(4) Investment 6MHSR | INVESTMENT | CLIENT_INSTRUCTION | S86_4 | false |
| OBS General 6MHSR | GENERAL | null | S86_2 | false |

Every account in the list carries the `investmentBasis` and `section` keys.

### Logs
- **OBS window** (21:04:25–21:08:20 UTC): backend **12 INFO, 0 WARN, 0 ERROR** (scheduler fan-out only). Frontend: 0 `⨯`
  lines.
- **Whole stacks:** stack #1 341 INFO / 151 WARN / **0 ERROR**; stack #2 273 INFO / 199 WARN / **0 ERROR**.
- **WARN kinds:** a normalised diff against 647D's stack #1 and #2 breakdowns finds **no new kind**
  (`warn-kinds-vs-647D.txt`). They are the known noise (PDFBox fallback fonts, etc.).
- **Frontend `⨯`:** all 22 `The destination stream closed early.` lines on stack #1 fall inside the lifecycle run
  (21:00:30–21:03:27). Stack #2 has 1, during depth-ii.
- **Browser:** OBS run 2 had 0 console errors and 0 page errors.

## Found items
See `found-obs.md`.
- **W-1:** the section label wraps at 1280 on the primary row. Cosmetic, not a clip.
- **P-1 / P-2:** F-3 (primary ticked by default for Investment) and F-4 (raw `ACTIVE` badge), both seen live.
- **P-3:** Approval Settings lists the investment accounts. Pre-existing; hand-off note for 648A/648E.
- **N-1:** the backend has no access log. The 201s are evidenced by the network log, the DB, the audit trail and the API.
- **H-1 / H-2:** harness misses, explained above.
- **E-1:** unrelated `maestro-perf-*` containers on the Docker host.

## Cleanup
- After each legal run I copied the curated screenshots (16 and 10). I then ran
  `git clean -fd -- documentation/screenshots/legal-vertical`. No tracked file was modified by either run
  (`git diff --stat` empty). `frontend/test-results/` is gitignored.
- Both stacks were torn down (`stack1-down.log`, `stack2-down.log`). 0 `e2e-*` containers are running.
- `git status --short` shows only `?? qa_cycle/evidence/phase92/648C/gates/` (`tree-after.txt` = `tree-before.txt`).
  HEAD is 4bb0d595f, and nothing was committed.
