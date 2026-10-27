# 625B observer: AFTER evidence (task 625.6, Observed PASS for U5 sentences)

**Verdict: 625.6 PASS.** On :3001 the matter Activity feed and Home Recent Activity show the same legal sentences, and no row contains "performed". G-D10 (feed) is closed as observed.

Observed 2026-10-27 ~12:22–12:25 UTC on the e2e mock-auth stack (frontend :3001, backend :8081). Read-only: no product data was created and no source file was modified.

## Backend build proof

- Built from worktree `/Users/rakheendama/Projects/2026/worktree-epic-625B`, HEAD `132f60ae2c7de3f9fd4edb6452af1439f2d0f128` (PR #1680, branch `phase88/08B-legal-formatter-home`), with a clean working tree (0 modified files).
- Command: `SHELL=/bin/bash bash compose/scripts/e2e-rebuild.sh --no-deps backend`, run 12:12:21Z → 12:21:36Z, exit 0. Full log: `rebuild.log`.
  - The image compiles inside Docker. `backend/Dockerfile` builder stage does `COPY src src` + `./mvnw clean package -DskipTests`, and `.dockerignore` excludes `target`. The host `backend/target`, where a concurrent `mvn verify` was running, was not read or touched. Log step `#15 [builder 7/10] COPY src src` ran fresh (not CACHED).
  - `--no-deps` was added so that postgres, localstack and mock-idp were not recreated. Their containers belong to the 624I/625A worktrees with different bind paths, and their data was preserved.
- Image `docteams-e2e-backend` sha256:9593c0619792c1db119deb2de570b84177509f87b9c0a4619f86159d54928d5f, created 2026-10-27T12:21:34Z. Container `e2e-backend` runs that image. Its compose label working_dir is `/Users/rakheendama/Projects/2026/worktree-epic-625B/compose`. "Started BackendApplication" at 12:21:50Z, and `/actuator/health` is UP.
- 0 ERROR lines in the backend log since restart.
- New code is live: `/api/dashboard/activity` and `/api/matters/{id}/activity` return the legal sentences below. Before the rebuild they returned "performed …" (see `../before/`).

## Counts

| Surface | Rows | "performed" | raw keys | raw entity types / "invoice"/"project" wording | Before (FALLBACK/raw) |
|---|---|---|---|---|---|
| Matter Activity, estate matter `9a27cd08-…` (all rows, after one "Load more") | 28 | 0 | 0 | 0 | 23 / 28 |
| Home → Recent Activity (limit 15) | 15 | 0 | 0 | 0 | 15 / 15 |

- In the browser, the rendered DOM text equals the API `message` / `description` field row for row on both surfaces (script check True; `dom-*.json` vs `api-*-alice.json`).
- Home ↔ feed: all **15 of 15** Home rows are identical to the same audit event, matched by event id, in that matter's feed, sentence and actor both. Rows 1–4 and 10–15 are on the estate matter. Rows 5–9 are on matter `8ca3c73d-…` "625A observer closure override", whose feed was also observed in the browser. **0 mismatches.**
- `acceptance.declined` actor: Home now shows **Sipho Ndlovu mvmzw78f31c9**. Before, it showed "System". The feed shows the same name.
- Raw-key check: regex `\b[a-z]+[._][a-z_.]+\b`, run after stripping quoted file names and template names. Entity-word check: `invoice|Invoice|project|trust_transaction|acceptance_request|time_entry`.

## Carol (member) redaction observation

- DB (`db-carol-capabilities.txt`): Carol has role `member` with capabilities AI_EXECUTE, GENERATE_STATEMENT_OF_ACCOUNT, MANAGE_DISBURSEMENTS, VIEW_LEGAL, VIEW_TRUST. She has **no FINANCIAL_VISIBILITY** and no member overrides.
- Carol is **not** on the estate matter's team. She is on only "Ndlovu v RAF tlsg" and "Team spec mvmzvy87a7be". In the browser the estate matter shows "This matter isn't on your list" (`carol-estate-matter-not-on-list.png`), and the API returns 404 (`api-matter-activity-carol.json`).
- Carol's Home Recent Activity has 8 rows (`carol-home-recent-activity.png`, `dom-carol-home-recent-activity.json` = `api-dashboard-activity-carol.json`). All are matter-team, matter-opened or from-template events. **None of them is a fee-note, part-payment or trust row, so fee-note amount redaction could not be observed for a member.** No data was created to force it, as the brief instructed. The "owner sees amounts" half was observed: Alice sees R2 000,00 / R5 000,00 part payments and R amounts plus trust references. Carol's 8 sentences also contain 0 "performed" and 0 raw keys:
  1. Carol Member removed a member from the matter team
  2. Carol Member added a member to the matter team
  3. Bob Admin made a team member the matter lead
  4. Bob Admin changed a matter team member's role to member
  5. Alice Owner added a member to the matter team
  6. Alice Owner opened the matter from template "Litigation (Personal Injury / General)"
  7. Alice Owner added a member to the matter team
  8. Alice Owner opened the matter "Ndlovu v RAF tlsg"

## Notes / surprises (not 625.6 failures)

- The trust approval sentence has no reference: "Bob Admin approved the trust payment of R2 500,00". The recorded row carries "(PAY/625A-FIX/KHU)". This reads fine, but it is asymmetric.
- "Alice Owner created a draft fee note" has no number or amount. This is expected, because the `invoice.created` details carry no `invoice_number` (see before/README).
- Acceptance view/decline/accept rows say "a document sent for acceptance" without naming the document. The sent/created rows do name the file.
- "Certificate of acceptance generated" is the only actorless sentence (system actor). It reads acceptably.
- The Home widget truncates long sentences visually with an ellipsis (rows 5, 6, 9). The full text is in the DOM.
- Out of scope (not Recent Activity): the Home **Sensitive events** widget still shows raw tokens, e.g. "Matter Closure Override Used / matter_closure · 013caa57-…" (`home-full.png`).

## Files

- `matter-activity.md`: 28 rows, before → after verbatim, matched by event id
- `home-recent-activity.md`: 15 rows, before → after verbatim, plus the same event's feed sentence and actor
- `matter-activity-all.png`, `home-recent-activity.png`, `home-full.png`, `matter-activity-closure-override-matter.png`
- `carol-home-recent-activity.png`, `carol-estate-matter-not-on-list.png`, `db-carol-capabilities.txt`
- Raw JSON: `api-matter-activity-alice.json`, `api-dashboard-activity-alice.json`, `api-matter-activity-closure-override-matter-alice.json`, `api-dashboard-activity-carol.json`, `api-matter-activity-carol.json` (404). These were fetched with a mock-IDP token for the same user (`POST :8090/token`), the same method used for `../before/`. The browser makes these calls server-side, so they do not appear in browser network requests.
- DOM captures: `dom-matter-activity.json`, `dom-home-recent-activity.json`, `dom-matter-activity-closure-override-matter.json`, `dom-carol-home-recent-activity.json`
- `rebuild.log`: docker build/recreate output
