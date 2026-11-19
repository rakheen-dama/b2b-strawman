# 652A — browser reproduction (task 652.1): REPRODUCED

- **Date:** 2026-11-19. **Stack:** e2e mock-auth stack on :3001 / :8081 (frontend image built from code identical to `main` for this page). **`main`:** `1d7686586856d6b5bc870bf41d0ed684b6fd067c`.
- **Script:** `repro-browser.cjs` (Playwright library, headless Chromium, run from the worktree's `frontend/`). **Raw result:** `repro-browser-result.json`. **Statements:** `s1-G1WLU.csv`, `s2-G1WLU.csv`.
- **User:** Alice (owner) via `/mock-login`. Every state change goes through the wizard UI. psql (:5433) is read-only evidence. The one exception is a setup API call: an earlier run had left the payment matched, so Alice unmatched it with `POST /api/bank-statement-lines/{id}/unmatch` (200). No SQL writes.

## How the calculate step was made to fail: a real server refusal

No interception was needed for steps 1–3. Since 646C, `calculateReconciliation` re-checks the period rules (`TrustReconciliationService.requireStatementFitsPeriodEnd`). One of those rules refuses when *another statement of the account has a line in (this statement's last line, period end]*. The steps:

1. **Tab A.** Wizard on the primary account with period end **16 Nov 2026**. Upload S1, which has two lines: 14 Nov "Unknown credit" +R700 and 15 Nov "Deeds office" −R1 250. Run auto-match (it only suggested the payment, so the line stayed Unmatched). Then Review. The first calculate **succeeds** (`repro-browser-0-review.png`).
2. **Tab B** (same session). A second wizard imports S2, which has one line dated **16 Nov**. From now on every calculate on Tab A's reconciliation is refused with "Statement s2-G1WLU.csv has movements between this statement's last line and 16 November 2026. Reconcile against the statement that covers the period end." This is captured from the calculate server action's own RSC response: `{"success":false,"error":…}`, HTTP 200.
3. **Tab A, three actions,** each saved by the backend and each followed by a refused calculate:

| action | screenshot | UI after the action | DB line (read-only) | stored reconciliation |
|---|---|---|---|---|
| Match line #2 to the R1 250 payment | `repro-browser-match.png` | line badge **Matched**, progress 1 / 2. **No banner.** The summary still reads "2 statement lines are neither matched, recorded nor excluded", plus the blocking flag "Statement lines not yet resolved". **No stale marking.** | `MANUALLY_MATCHED`, payment linked | unchanged (`updated_at` 11:05:31 from the first calculate) |
| Exclude line #1 | `repro-browser-exclude.png` | badge **Excluded**, progress 2 / 2. **No banner.** The summary still shows the old figures (2 unresolved, excluded total R0,00) | `EXCLUDED` | unchanged |
| Unmatch line #2 | `repro-browser-unmatch.png` | badge **Unmatched**, progress 1 / 2. **No banner.** Summary unchanged | `UNMATCHED`, no transaction | unchanged |

Calculate responses captured for the match and exclude calls: both `{"success":false,"error":"Statement s2-G1WLU.csv has movements …"}`. For the unmatch call's calculate the response event fired (HTTP 200), but Chromium had already discarded the body ("No data found for resource"). The stored row is unchanged (`updated_at` still 11:05:31.594), so no calculate succeeded.

**Observed:** the change is saved and shown, the refusal is swallowed, and the "As at" summary contradicts the pane (progress 2 / 2 against "2 statement lines are neither matched…"). Nothing on screen tells the user. This is the defect.

## The rejected-promise shape (route abort)

4. **Tab A.** Match line #2 again, and abort the calculate request with `page.route(…).abort("failed")`. The request is identified as a POST to the page URL with a `Next-Action` header and a body equal to `["<reconciliationId>"]` with no line id. The aborted request was Next-Action `40dda10e62c1f90f9dd5120518fd7665c7f13bebd5`, body `["5f57d266-7e13-436c-bc23-d80b6c6c40a5"]`.
   - Shape: the server-action promise **rejects** (`TypeError: Failed to fetch`). It does not resolve `{success:false}`.
   - UI (`repro-browser-match-calc-aborted.png`): the banner reads **"Failed to fetch"**. The outer `try/catch` in `handleMatch` (`catch (err) → setError(err.message)`) catches it. The line is **Matched** (DB `MANUALLY_MATCHED`), the figures are unchanged and there is **no stale marking**. No `pageerror` (no unhandled rejection). One console error, `net::ERR_FAILED` (the aborted request).
   - So the rejected shape is **not silent** in match / exclude / unmatch: all three wrap the calculate call in the same outer try. It is **misleading**, though. A bare "Failed to fetch" does not say the match was saved, and nothing marks the figures stale. In `handleRecord`, which has no try/catch, the same rejection would propagate to the Record dialog. Not exercised here.

## Residue left on the shared e2e stack (tenant `tenant_7d218705360b`)

The stack now holds 5 bank statements (two runs) and 3 DRAFT reconciliations for the primary account. The R1 250 payment is matched to S1-G1WLU line #2. Statements S2 have lines dated 16 Nov and 19 Nov. A new reconciliation of this account whose period end falls on or after such a line, with its own statement ending earlier, will be refused by the covering rule. OBS for 652A should run on a fresh stack (`e2e-down.sh && e2e-up.sh`).

Run 1 of the script used period end = today (19 Nov) and produced the same silent outcome for all three actions. Its screenshots were deleted by mistake before run 2, and only its console JSON was kept outside the repo. Run 2 (`PERIOD_END_OFFSET_DAYS=3`) is the evidence here.
