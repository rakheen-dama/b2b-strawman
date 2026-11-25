# Take-on batch: FR-2, FR-3 and F-660A-1 (D-264)

Branch `fix/take-on-batch-fr2-fr3-signoff`, worktree `../wt-takeon-batch`, base `origin/main` @ `13f127226`.
For each item the failing test was committed first, then the fix.

## Item 1: FR-2 / F-655A-3. The email reserved by an earlier take-on now has a specific reason

- **Red** (`item1-red-run.txt`, test commit `7e335e14f`): `ClientsTakeOnIntegrationTest#anEmailAnEarlierClientWasTakenOnWith_failsTheRowWithTheSpecificReason`.
  Client R-1 is taken on with `reserved.address@…` and then moves to another email. A later take-on of a different client with the same address
  fails with the generic text: `but was: "This row could not be committed. Correct it in the previous system, then take it on in a new batch."`
- **Fix** (`5e47c4ed0`): `ClientsDatasetHandler.failureMessage` now maps a `uq_take_on_rows_created_key` violation to
  `EMAIL_RESERVED_MESSAGE`: "Another client was taken on with this email address in an earlier take-on. The address stays
  reserved for take-on, even if that client's email has since changed in Kazi. Take this client on with a different email
  address, or add them in Kazi directly." The text is fixed and never names the earlier client. A row's `failure_reason` is
  not redacted when a client is anonymised (`GenericTakeOnDatasetHandler.fixedFailureMessage` javadoc), so a client name
  stored there would outlive that client's anonymisation. The rule (RD-6) and duplicate handling are unchanged.
- **Green**: `item1-2-backend-green-run.txt`. The test also asserts that the message contains neither the earlier client's name nor the email,
  and that the rolled-back row created no client.

## Item 2: FR-3 / 653A F-2. A 3 MB server-action limit

- **Red** (test commit `ba3a21687`):
  - Frontend (`item2-frontend-red-run.txt`), 5 failing:
    - `bodySizeLimit` is `'11mb'`, not `'3mb'`.
    - A bank statement over 3 MB is still sent to the action.
    - The bank statement copy does not state 3 MB.
    - A take-on file of 3 000 001 bytes is accepted.
    - The take-on copy says 10 MB.
  - Backend (`item2-backend-red-run.txt`): a 3 000 001-byte take-on upload returns `201`, not `422 TAKE_ON_FILE_TOO_LARGE`.
- **Fix** (`502142c4f`):
  - `frontend/lib/server-action-upload.ts` holds the one limit:
    - body `3mb` (3 MiB = 3 145 728 bytes), used by `next.config.ts`;
    - file limit 3 MB = 3 000 000 bytes, so the multipart framing and the other form fields fit under the body limit;
    - `fileTooLargeError()` returns "File exceeds the 3 MB limit (3.1 MB)".
  - Take-on:
    - the browser refusal (`takeOnFileError`) and the upload copy now say 3 MB;
    - the backend `TakeOnFileReader.MAX_FILE_BYTES = 3_000_000` and the 422 text say 3 MB too.
  - Bank statements:
    - the server action's own 10 MB check is replaced by the shared check;
    - `BankStatementUpload` now refuses an over-limit file in the browser with that message. Before this, a body over the limit never reached the action, so its own size check could not run.
  - The upload card copy now says "of up to 3 MB".
  - The backend has no bank-statement-specific size check. Spring's global multipart limit of 10 MB is unchanged.
  - `XlsxTakeOnReaderTest`'s large-workbook case now builds a workbook just under the new limit: 10 000 rows × 6 columns, between 2.5 MB and 3 000 000 bytes.
- **Green**:
  - `item2-frontend-green-run.txt`: 45 files, 655 tests.
  - `item1-2-backend-green-run.txt`: UploadValidation 12, XlsxReader 16, CsvReader 21.

## Item 3: F-660A-1 / D-264. No path to Sign-off on a batch that can never be signed

- **Red** (test commit `a727c38d4`, `item3-vitest-red-run.txt`): 2 failing. The test names say what they check:
  - "a statutorily blocked trust batch offers no path to Sign-off and says why";
  - "a trust batch with an error but no block … (committed whole, D-191)".
- **Same commit, capstone spec**: `take-on.spec.ts` step 4 changes from `toBeVisible()` on `take-on-to-sign-off` to `toHaveCount(0)`. It also checks that the stepper's Sign-off is disabled and that the reason text appears. The checks on the Sign-off step itself and the FINDING annotation are dropped. Step 5 (the EQ-1/EQ-2 blocks) asserts `toHaveCount(0)` too.
- **Fix** (`3266a852f`):
  - `takeOnBlocksCommit(batch)` in `lib/types/take-on.ts` mirrors the backend's `TakeOnBatch.blocksCommit()`. That predicate is read by every sign-off and commit guard, and is true when either:
    - `blocks > 0`, or
    - the batch has errors and is committed whole (new trait `commitsWhole`, trust position only).
  - When it is true, the wizard:
    - drops "Continue to sign-off";
    - disables the stepper's Sign-off step;
    - shows `take-on-sign-off-unavailable`. With blocks: "This take-on cannot be signed off while the statutory blocks above remain. …". With errors only: "A trust take-on is committed whole, so it cannot be signed off while any error remains. …"
- **Green**:
  - `item3-vitest-green-run.txt`: 15 files, 200 tests.
  - E2E take-on, step 4 observed in the browser: `04-trust-debit-block.png` shows Sign-off greyed and the reason under the table. `05-trust-r10-block.png` shows the same for step 5.

## Item 4: FR-3 follow-up. The other server-action file uploads now follow the 3 MB limit

Lowering the shared body limit to 3 MB would have regressed every other upload that goes through a server action. A file over 3 MB would fail with Next's unreadable body-limit error, and the DOCX screens still said 10 MB.

**Every server action that takes a file** (grep of `"use server"` files for `File` / `formData.get("file")`):
- Take-on (`take-on-actions.ts`): done in item 2.
- Bank statement (`reconciliation/actions.ts`): done in item 2.
- DOCX template upload (`UploadDocxDialog.tsx` → `uploadDocxTemplateAction`): fixed here.
- DOCX template replace (`template-docx-sections.tsx` `ReplaceFileDialog` → `replaceDocxFileAction`): fixed here.
- Disbursement receipt on the detail page (`detail-client.tsx` → `uploadReceiptAction`): fixed here.
- Firm logo (`general-settings-form.tsx` → `uploadLogoAction`): already refused above 2 MB in the browser, which is under 3 MB, so it is unchanged.
- The receipt picked in the *create*-disbursement dialog does not go through a server action. It is uploaded straight to S3 through a presigned URL (`initiateUpload`), so it is left as it is.

**Red** (test commit `6d0c75467`, `item4-vitest-red-run.txt`): 3 failing in the new `__tests__/server-action-uploads-3mb.test.tsx`.
- DOCX upload: the copy says Max 10 MB, not Max 3 MB.
- DOCX replace: the copy says Max 10 MB, not Max 3 MB.
- Receipt: no "up to 3 MB" copy, and a 3 000 001-byte receipt is sent to the action.

**Fix** (`41b2ab7e1`):
- All three use `fileTooLargeError`, so a file over the limit gets "File exceeds the 3 MB limit (x MB)".
- All three take their copy from `SERVER_ACTION_FILE_MAX_LABEL`.
- The receipt dialog refuses the file before calling the action.
- The existing `UploadDocxDialog.test.tsx` case that asserted "File size exceeds 10 MB." now asserts the shared message (`63ca9b13e`).
  The first full vitest run at `41b2ab7e1` caught that stale assertion: `fe-test-41b2ab7e1-stale-docx-test.txt`, 1 failed.

**Green**: `item4-vitest-green-run.txt`, 20 files and 197 tests (the new file and `__tests__/legal`); then the full frontend gate below.

## Gates

Item 4 changed only the frontend. The backend and E2E rows below are at `3266a852f`. Item 4 changes no file those runs exercised except the DOCX and receipt dialogs, which no E2E suite uploads over 3 MB.

| Gate | Head | Result | Evidence |
|---|---|---|---|
| Targeted backend (`takeon.**`, `trustaccounting.takeon.**`) | `3266a852f` | 405 testcases, 0 failures, 0 errors | `backend-targeted-takeon.txt` |
| Full backend `bash scripts/verify.sh -q` | `3266a852f` | exit 0; 933 report files, **8 214 testcases, 0 failures, 0 errors, 55 skipped**, counted from the `<testcase>` elements. Main at #1860 had 8 213; the +1 is the new FR-2 test. The console log (13 MB) is trimmed to its result lines and tail. Surefire printed its "kill self fork JVM … after System.exit(0)" notice once, after the run had finished. | `backend-full-verify.txt`, `verify-sha.txt` |
| Frontend lint | `63ca9b13e` | 0 errors (76 warnings, none in changed files) | `fe-lint.txt` |
| Frontend format:check | `63ca9b13e` | clean | `fe-format-check.txt` |
| Frontend build (`timeout 900`) | `63ca9b13e` | exit 0 | `fe-build.txt` |
| Frontend vitest (full) | `63ca9b13e` | 508 files, **4 980 passed, 2 skipped** (at `3266a852f`: 507 files, 4 975 passed / 2 skipped) | `fe-test.txt` |
| E2E take-on (stack 1, fresh, alone) | `3266a852f` | **17 passed** (2.8 min) | `e2e-take-on.txt`, `stack-1-containers.txt` |
| E2E legal-lifecycle, attempt 1 (stack 2, fresh) | `3266a852f` | **FAILED**: 2 failed, 32 passed, 71 did not run (10-min suite timeout) | `e2e-legal-lifecycle-attempt1-FAILED.txt`, `attempt1-artifacts/`, `stack-2-containers.txt` |
| E2E legal-lifecycle, attempt 2 (stack 3, fresh) | `3266a852f` | **105 passed** (3.5 min) | `e2e-legal-lifecycle.txt`, `stack-3-containers.txt` |
| E2E legal-depth-ii (stack 3, after lifecycle) | `3266a852f` | **40 passed, 4 skipped** (2.0 min; the known 4 skips) | `e2e-legal-depth-ii.txt` |

The take-on project runs alone on its own fresh stack (its step 0 checks that nothing ran before it; see `frontend/e2e/playwright.legal-lifecycle.config.ts`).
Lifecycle and depth-ii share one fresh stack (stack 3), as in the Phase 93 exit (`qa_cycle/evidence/phase93-exit/suites/README.md`).
All runs used nvm node v20.11.1 and pnpm 10.26.1.

## Lifecycle attempt 1: a failure that was not reproduced (recorded, not dismissed)

**What happened** (stack 2, fresh):
- `day-01-03-onboarding.spec.ts:108` "Bob: Create client Sipho Ndlovu (Individual)" ran for 5.0 min.
  - After the client was created, the client detail page never got past its loading skeleton: the breadcrumb reads "Client" and `main` is empty.
  - See `attempt1-artifacts/day01-create-sipho-stuck-skeleton.png` and `day01-error-context.md.txt`.
- `day-07-14:62` then failed as a consequence: Sipho's matter did not exist.
- The suite then hit its 10-minute global timeout, so 71 tests did not run.
- Backend log (`attempt1-artifacts/stack2-backend-log.txt`): no ERROR, and only the known WARN kinds.
- Frontend log: 19 × `⨯ Error: The destination stream closed early.`

**Can this diff cause it? I conclude it cannot.**
- *`bodySizeLimit`*. In `next@16.3.8`, `experimental.serverActions.bodySizeLimit` is read only in two places:
  - `server/app-render/action-handler.js` caps an incoming server-action body; above the cap it throws `ApiError(413)`.
  - `build/templates/app-page-runtime.js` sets the action-body budget for a PPR resume in minimal mode, also a 413.

  Nothing reads it for RSC or flight responses, page rendering, or proxy bodies: `clonableBody` uses the separate `proxyClientMaxBodySize`. A smaller limit can only turn an oversized request into a 413. It cannot hold a page on its skeleton. The client-create request body is a few hundred bytes.
- *The rest of the diff*. Nothing in it touches the client create dialog, its action, or the client detail page (`app/(app)/org/[slug]/clients/[id]/`). None of those import a changed module, except through the `lib/types` and `lib/schemas` barrels, which only gain a pure function, constants and a trait field.

**Has this been seen on main?**
- "The destination stream closed early" is long-standing noise in passing runs on main-era code:
  - 657B, 657C and 657D OBS logs;
  - the phase93-exit run of record (8×, `gap-report.md`);
  - 647C (36× during a 105/105 lifecycle).
- The passing attempt 2 on stack 3 logged it 30 times (`stack3-frontend-log.txt`), more often than the failing attempt did. It does not mark the failure.
- I found no earlier record of a stuck client-detail skeleton after create. There is an earlier case of a non-deterministic page hang that reproduced on main code: `qa_cycle/evidence/g89-fixes/pr1749-e2e/README.md`, `day-14-conveyancing.spec.ts:354`.

**Reproduction attempt.**
- A fresh stack (stack 3) ran the whole lifecycle at the same head: 105/105, including this test.
- Re-running only `day-01-03` 3× on stack 3 afterwards was **not a valid probe**. Its conflict checks expect CLEAR, and the clients already existed from the full run (`e2e-probe-day01-03-INVALID-used-stack.txt`). A valid repro needs one fresh stack per attempt. I did not spend more fresh stacks on it.

**Status: root cause unknown, not reproduced. I do not consider it caused by this PR.** It is listed under "Found, not fixed" in the PR body.
