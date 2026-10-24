# 624G browser observer — PR #1676 @ 7cce247d2

Stack: e2e mock-auth. Frontend rebuilt from the 624G worktree (`e2e-rebuild.sh frontend`, exit 0, log `/tmp/624G-e2e-rebuild.log`; container created 2026-10-24T20:43:20Z, compose working_dir worktree-epic-624G). Backend left as is (built from 624F, created 19:39:26Z). Side effect: compose also recreated `e2e-localstack`, so S3 objects from before 20:43Z are gone. This is the known LocalStack loss. New generations upload again.
New build confirmed in the browser: the SoA intent checkboxes and the "Share with client" button render.
Matter dbebf31b-e0d0-4672-ab7e-2bf289b6c262 (tenant_7d218705360b). Viewport 1280x720. Evidence: `qa_cycle/evidence/phase88-rewalk/624G/after/`.

Mailpit timeline (`:8026`): 28 (start) → 28 (b) → **29** (c, `MipDC4frBpXTA3rfJLXedv`) → 29 (d) → **30** (e, `5qAWBcdnbJb24ysLsNRrwZ`) → 30 (d-bis, h setup). Before step c, the last mail was at 19:44Z, so no 5-minute dedup window applied.

## Verdict: PASS on function for every step a–i. One 624G-introduced layout defect (horizontal page overflow on the Generated tab). Details below.

| Step | Result | Evidence |
|---|---|---|
| a. SoA dialog intent | **PASS** | Both checkboxes render and are off by default. Summary reads "Save as internal draft — the client will not see it." Ticking Email ticks Share and disables it; the summary becomes "Save, share on the client portal and request an email to the client." Unticking Email re-enables Share and leaves it ticked, as designed (code comment plus vitest "unticking email re-enables share"); the summary becomes "Save and share … No email is sent now." The 5-minute caveat is present. Reopening the dialog resets both boxes to off. Screenshots `a1-soa-dialog-defaults.png`, `a2-soa-email-ticked-share-disabled.png`, `a3-soa-email-unticked-share-reenabled.png` |
| b. Save with nothing ticked | **PASS** | gd `c043e25b-4e18-4c5a-857f-227e4189a678` → doc `02f330e4…` **INTERNAL**. Mailpit 28 → 28. `b1-soa-internal-saved.png` |
| c. Save with Email ticked | **PASS** | gd `96f6e784-d104-48e8-8eb0-8bf8c4e1a1ec` → doc `65ecd9a5…` **PORTAL**. Mailpit 28 → 29, `MipDC4frBpXTA3rfJLXedv` "Document ready: statement-of-account-…pdf". Its only href is `http://localhost:3002/matters/dbebf31b-e0d0-4672-ab7e-2bf289b6c262?orgId=e2e-test-org` (no token). `c1-soa-email-saved.png` |
| d. Generated tab and Share without email | **PASS** (layout defect below) | Every row matches the DB. The 5 PORTAL SoAs show a "Client portal" badge and no Share button. The INTERNAL rows (2 SoAs, 2 engagement letters, Offer to Purchase) show the Share icon. The share dialog shows the title, the copy "The client will be able to see Statement of Account when they sign in to the client portal.", an unticked Email box and the caveat. Confirming on `c043e25b` gave toast "Shared with client" (captured by MutationObserver on the repeat run against `7d88390e`; on the first run the toast had already gone by the time I checked). The row flips to the "Client portal" badge and loses Share with no reload. DB `c043e25b` and `7d88390e` → PORTAL. Mailpit stays at 29, then 30 (after e). Focus after close lands on the `generated-documents-list` wrapper (tabIndex −1 fallback), not `<body>`. `d1-generated-tab-before-share.png`, `d2-share-dialog.png`, `d3-shared-toast-row-flipped.png`, `d4-shared-toast-no-email.png` |
| e. Share with email | **PASS** | Engagement Letter — Conveyancing `b80d17df-f9d4-4a36-9de9-34b5e31edf16` → PORTAL. Toast "Shared with client — email requested". Mailpit 29 → 30, `5qAWBcdnbJb24ysLsNRrwZ` "Document ready: engagement-letter-conveyancing-…pdf". Its href is `http://localhost:3002/matters/dbebf31b-…?orgId=e2e-test-org`. Not deduped: different document type, and no prior mail of that type in the last 5 minutes. `e1-share-dialog-email-ticked.png`, `e2-shared-email-requested-toast.png` |
| f. Declined badge and panel | **PASS** | `POST /api/portal/acceptance/ajutCysD…/decline` with `{"reason":"Fee too high — 624G observer"}` (body record `DeclineSubmission(reason)`) → 200 `{"status":"DECLINED"}`. Request `58d37957` is now DECLINED with the reason stored. On the Offer to Purchase row the badge reads "Declined — Fee too high — 624G observer". Text is truncated (span scrollW 243 > clientW 234, ellipsis visible) and the full text is in `title`. Colour is red (destructive). The detail panel shows Sent → Viewed (pending) → **Declined 24 Oct 2026**, then "Reason: Fee too high — 624G observer". There is no Accepted step, no "Expires" line, and no Remind/Revoke buttons. `f1-declined-reason-badge.png`, `f2-declined-detail-panel.png` |
| g. Closure dialog step 2 | **PASS** | The intent block renders, default off. Summary: "Save the closure letter and Statement of Account as internal drafts — the client will not see them." Unticking both Generate boxes disables both intent checkboxes; the summary becomes "No closure documents will be generated, so nothing is shared with or emailed to the client." At 1280x720 the dialog body scrolls (scrollHeight 826 / clientHeight 646). Before scrolling, Back and Close matter sit at y=706–742, below the dialog bottom (684). After scrolling the body they sit at y=623–659, visible. Cancelled with Escape; DB `projects.status` is still **ACTIVE**. `g1-closure-step2-intent.png`, `g2-closure-no-docs-intent-disabled.png`, `g3-closure-scrolled-footer-visible.png` |
| h. Carol (member) | **PASS** | Carol is not on matter dbebf31b, so she gets "This matter isn't on your list". I used matter `94499c18` (Ndlovu v RAF), where she is on the team. As Alice I generated an INTERNAL SoA there via the UI (`d7f6444a…`); Alice saw 1 Share button. As Carol the same row offers only "Download PDF": 0 Share buttons. `h1-carol-member-no-share.png` |
| i. Console | **PASS** | 0 errors and 0 warnings on every matter page I visited (dbebf31b overview and generated tab, 94499c18 as Alice and as Carol). The browser-session history still holds one older 500 on `GET :8081/api/portal/acceptance/Hln6OLgb…/pdf`. That came from a previous observer's accept page and is the known LocalStack S3 loss. It did not come from a matter page. |

## Defect found (624G-introduced, layout): the Generated tab overflows the page horizontally
- At 1280x720 the page scrolls horizontally: `documentElement.scrollWidth` is 1364 after the shares in d/e, and 1471 after the decline in f, against a 1280 viewport. At 1440x900 it is 1471 against 1440. At 1536 there is no overflow. The last action icon (Delete) on INTERNAL rows is clipped at the right edge, and a horizontal scrollbar appears (`f1-declined-reason-badge.png`, `f2-…`, `i1-generated-tab-final-1280-overflow.png`).
- Attribution was proven in the live DOM by hiding only the 624G elements:
  - Collapse the decline-reason badge back to "Declined": 1471 → 1364.
  - Also hide the "Client portal" badges: → 1280.
  - Hiding only the badge on "Engagement Letter — Conveyancing" is enough to get 1280.
  - With all 624G additions hidden the table is 912px wide; with them it is 1045px or more.
- Hypothesis (not verified in code): `GeneratedDocumentsList.tsx:296` puts the badge inline (`ml-2`) in an unconstrained template-name cell. `AcceptanceStatusBadge.tsx:35` allows up to 16rem in the Acceptance column. Share adds a 4th action icon at `:366`. The shadcn table container (`components/ui/table.tsx:9`, `overflow-x-auto`) never constrains anything, because ancestor `main.flex-1` has `min-width:auto` and grows to fit the content. So the overflow lands on the page instead of the table. Likely fixes: `min-w-0` on the layout `main`, or let the name cell wrap the badge onto its own line and narrow the reason badge.

## Noted, out of scope (pre-existing)
- After Preview & Save, the SoA dialog grows horizontally around the preview iframe and clips its own content. This is pre-fix too (`before/06-soa-after-preview-and-save.png`).
- SoA period start defaults to tomorrow when an SoA already exists today (already known).

## Playwright (worktree frontend, `PLAYWRIGHT_BASE_URL=http://localhost:3001`, `--update-snapshots=all`, reused stack — not fresh)
- **legal-depth-ii**: 35 passed / 0 failed / 8 skipped. The skips are 5 `test.fixme` and 3 opt-in null-prefetch probes; 624G changes no e2e spec (`git diff main...HEAD -- frontend/e2e` is empty). The flagged screenshots `day-85-override-justification-entered` and `day-14-otp-acceptance-request-sent` render correctly, showing the new intent block and the Share icon (copies in `after/playwright/`).
- **legal-lifecycle**: 69 passed / 5 failed / 3 skipped / 20 did not run. Failures:
  - day-00 trust settings LPFF fill (A1)
  - day-30 Sipho fee note (A2)
  - day-45 bank reconciliation (A4)
  - day-90 profitability (A5)
  - day-60 interest LPFF
  The first four are the carried set. day-60 is the documented cross-suite ordering failure when lifecycle runs right after depth-ii on the same stack. Phase 88 memory records exactly 69/5/3/20 with the extra `day-60-interest:41`. None of these touch 624G code (trust, invoices, reconciliation, interest, profitability).
- Extra, default config: `documents/document-generation.spec.ts` + `audit-log/matter-closure-audit-tab.spec.ts` gave 2 passed / 3 runtime-skipped (data-dependent `test.skip`).
- Logs: `after/playwright/depth-ii.log`, `lifecycle.log`, `default-docs-closure.log`.

## Worktree cleanliness
- `git -C worktree-epic-624G status --short` is empty.
- The runs created 49 untracked screenshots under `documentation/screenshots/legal-vertical` and `frontend/e2e/screenshots`; I removed them with path-scoped `git clean -f`. No tracked files were modified. I also removed `frontend/test-results/`.
- Remaining ignored files: `.epic-brief.md`, plus `frontend/.next/` and `next-env.d.ts`, which predate this run.
- `.playwright-mcp/`: I deleted only my 44 `page-2026-10-24T20-4[3-9]-*.yml` snapshots. Screenshots were written straight into the evidence directory. Nothing committed.
