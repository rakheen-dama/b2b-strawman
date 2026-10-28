# 627I final-head re-observe (PR #1700 @ 1e4e5a71c)

Scope: the commit that made the comment forms' body and visibility controlled state and moved both forms
(`add-comment-form.tsx`, `edit-comment-dialog.tsx`) from `<form action>` to `onSubmit` + `preventDefault` + `startTransition`.

## Stack
- The e2e frontend was rebuilt from `$WT` (worktree HEAD 1e4e5a71c) with `compose/scripts/e2e-rebuild.sh frontend`. The `next build` step ran fresh (not cached).
  The commit is from 15:37:02Z. The image was created at 2026-10-28T15:40:14Z and the container at 15:40:15Z. `:3001` returned 200.
- Browser: Playwright MCP, a fresh single tab, mock-login as Alice. Tenant schema is `tenant_7d218705360b`. All SQL was SELECT-only.

## Results

| # | Result | Evidence |
|---|--------|----------|
| G1 | PASS | I opened the thread on `regression-upload-627I-clean.pdf` (doc `33be9843…`), typed "Master's appointment received", switched visibility to Customer visible and back to Internal only, attached `master-g1.pdf`, and clicked Post Comment. The comment and its chip rendered. Afterwards the textarea value was `""` and visibility was `INTERNAL`. DB: comment `8500fb12-3b60-49a9-9751-00a7fe43932a` (DOCUMENT, INTERNAL), a `comment_attachments` row pointing at doc `d3fa7212-d059-4f97-a93f-e4bb744a89fa`, and that doc is UPLOADED / COMMENT / INTERNAL. After a fresh load the Documents list row reads "master-g1.pdf Comment 193 B". Screenshots: `g1-before-post.png`, `g1-after-post.png`, `g1-documents-badge.png`. |
| G2 | PASS | Neither the old form nor the new one has a keyboard-submit handler (no `onKeyDown`/Enter handling in `components/comments/`), so I clicked Post Comment. I posted on the task Comments tab with **Customer visible** and no attachment. The task comment count went from 5 to 6, so exactly one row was created: `59325724-4c72-40d2-abe5-c82bf5f09268`, SHARED, 0 attachments. The textarea cleared and visibility **reset to INTERNAL**: both the DOM `value` and the selected option text were "Internal only", so the controlled select did not desync. The server-action args were `[..., "627I final G2 task comment no attachment", "SHARED", []]`. `lib/actions/comments.ts:97` leaves `attachmentDocumentIds` out of the backend body when the list is empty. Screenshot: `g2-task-after-post.png`. |
| G3 | PASS | I edited `59325724…`. On open the dialog showed the saved body and SHARED. I changed the body to "627I final G3 edited body" and visibility to Internal only, then clicked Save. DB: body updated, visibility INTERNAL. Re-opening showed the saved values. I then typed a draft, set Customer visible and clicked Cancel. Re-opening showed the saved body and "Internal only", so the draft was discarded, and the DB row was unchanged (`updated_at` still 15:43:55Z). Screenshots: `g3-edit-before-save.png`, `g3-reopen-after-cancel.png`. |
| G4 | PASS | In the edit dialog a whitespace-only body showed "Comment cannot be empty.", the dialog stayed open, and nothing was saved. In the add form a whitespace-only body with Customer visible showed "Comment cannot be empty.", and **the draft was kept**: body `"   "`, visibility SHARED / "Customer visible". With a truly empty body, native `required` blocked the submit ("Please fill in this field."). The task comment count stayed at 6. Confirm and 422 failures are covered by unit tests (`comment-section.test.tsx`, `edit-comment-dialog.test.tsx`) and were not driven in the browser. Screenshots: `g4-edit-whitespace-error.png`, `g4-add-whitespace-error-draft-kept.png`. |
| G5 | PASS | I deleted G1 comment `8500fb12…` using the confirm dialog. The comment row and link row counts are both 0. Doc `d3fa7212…` is still there (UPLOADED / COMMENT / INTERNAL), and the list still shows the "Comment" badge. Screenshot: `g5-after-delete-doc-kept.png`. |
| G6 | PASS | The browser console had 0 errors and 0 warnings for the whole session (`all: true`, level warning). That covers fresh loads of the Documents tab and the task sheet, plus opening and closing the edit dialog. There were no React controlled/uncontrolled warnings. `docker logs e2e-frontend` since 15:40Z has no error, warn or "controlled" lines. |

## Ids created
- Comment `8500fb12-3b60-49a9-9751-00a7fe43932a` (G1): deleted in G5.
- Document `d3fa7212-d059-4f97-a93f-e4bb744a89fa` (`master-g1.pdf`, COMMENT/INTERNAL): kept.
- Comment `59325724-4c72-40d2-abe5-c82bf5f09268` (task, G2/G3): now INTERNAL with body "627I final G3 edited body".
