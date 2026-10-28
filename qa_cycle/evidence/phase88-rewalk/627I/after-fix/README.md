# 627I — Re-observation after fix commit f943f72c5 (happy paths)

- **Code under test:** PR #1700, head `f943f72c5` (worktree `$WT`). This fix commit changed the error-path handling: `describeRejection` moved into the hook, `submittingRef` now guards the edit dialog's close, confirm/initiate/download calls got try/catch, and the dropzone got `aria-disabled`.
- **Stack:** E2E mock-auth stack. The frontend was rebuilt from `$WT` with `SHELL=/bin/bash bash compose/scripts/e2e-rebuild.sh frontend` (exit 0). Image `sha256:b875f2060ae19c3043ecb8ce9f5e324f4434b36de3eb3bfb2fb6fe54a6680657`, created 2026-10-28T15:23:24Z (after the commit, 15:20:10Z). `:3001` returned 200.
  - The rebuild resets the LocalStack bucket. Every file downloaded below was uploaded in this session.
- **Actor:** Alice (owner), signed in through `/mock-login`. Org `e2e-test-org`, tenant schema `tenant_7d218705360b`.
- **Matter:** "Website Redesign" `e120be4a-…`. **Task:** `56344385-…`.
- **Browser:** Playwright MCP (Chromium). **SQL:** read-only `SELECT` through `docker exec e2e-postgres psql`.
- **Server-action payloads:** captured from browser POSTs that carry a `next-action` header. The body is the action's argument array.
- **Baseline before the run:** 0 PENDING documents on the matter, and 5 `comment_attachments` rows.

| # | Result | One-line evidence |
|---|--------|-------------------|
| F1 | PASS | Document-thread comment `446f8f4f…` ("Master's appointment received", INTERNAL) with master-f1.pdf `afa9be14…`. While staged the doc was PENDING; after Post it is UPLOADED / COMMENT / INTERNAL / PROJECT, with a link row. The chip renders on the comment, and the row shows the "Comment" badge after reload |
| F2 | PASS | The comment was deleted: 0 comment rows and 0 link rows. Doc `afa9be14…` is kept (UPLOADED / COMMENT / INTERNAL) and the row still shows the "Comment" badge after reload |
| F3 | PASS | Task comment `1b7a8108…` with task-f3.pdf `4e2ab3e8…` shows a chip. Clicking it sent a server-action POST (200), then a GET of the LocalStack presigned URL (200, `application/pdf`), and the PDF opened in the tab |
| F4 | PASS | A visibility-only Save sent `attachmentDocumentIds` = `$undefined`, so the action omits the key; the link row is unchanged (`created_at` 15:25:47). Add-file Save sent `[4e2a…, e3d4…]`: edit-add-f4.pdf linked. Remove-chip Save sent `[e3d4…]`: the task-f3 link is gone and doc `4e2ab3e8…` is kept |
| F5 | PASS | Dropped edit-esc-f5.pdf into the edit dialog (PENDING `b277ea36…`), then pressed Escape. The dialog closed and the task sheet stayed open. cancelUpload POST `["b277ea36…"]` fired and the row is deleted. On re-open, the chips show only the saved set (edit-add-f4.pdf) |
| F6 | PASS | Dropped add-f6a.pdf (PENDING `2b2e08bd…`) and removed its chip: cancel POST fired and the row is deleted. Dropped 6 files: 5 staged (lim1–5, 5 PENDING rows, no lim6). The zone had `aria-disabled="true"` and `tabIndex=-1`, and the "A comment can have at most 5 attachments." message was visible. Removing all 5 sent 5 cancel POSTs, leaving 0 PENDING rows; `aria-disabled` went back to null and the message cleared |
| F7 | PASS (note) | Instrumented console, network, and backend log checks show no app errors attributable to F1–F6. The 7 entries in the browser's lifetime log come from a tab that was open before the rebuild, or from LocalStack (see below) |

## Detail

### F1 — document thread (Documents tab, expand `regression-upload-627I-clean.pdf`)

```
staged: afa9be14-e595-499c-984c-fda4413ba646 | master-f1.pdf | PENDING | MANUAL | INTERNAL | PROJECT | e120be4a-…
posted: comment 446f8f4f-b6b2-4acf-ac1e-6e89c2028a56 | DOCUMENT | entity 33be9843-… | INTERNAL | Master's appointment received
        → afa9be14-… | UPLOADED | COMMENT | INTERNAL | PROJECT
row after reload: "master-f1.pdf  Comment  81 B  Uploaded  28 Oct 2026"
```

Screenshots: `f1a-document-thread-staged-chip.png`, `f1b-document-thread-posted-chip.png`, `f1c-documents-list-comment-badge-after-reload.png`

### F2 — delete the comment

Delete → confirm dialog "Delete this comment? This action cannot be undone." → Delete.

- `comments` row count 0.
- `comment_attachments` rows for that comment or document: 0.
- Doc `afa9be14…` is UPLOADED / COMMENT / INTERNAL.
- After reload the row reads `master-f1.pdf Comment 81 B Uploaded`.

Screenshot: `f2-after-delete-doc-kept-with-badge.png`

### F3 — task comment and chip download

```
comment 1b7a8108-45f4-4c83-9c91-ccce8c47a005 | TASK | 56344385-… | INTERNAL → 4e2ab3e8-961a-43a9-8964-c4b5c4305ae3 | task-f3.pdf | UPLOADED | COMMENT
chip click: POST :3001 (server action) 200 → GET :4567/docteams-dev/org/e2e-test-org/project/e120be4a-…/4e2ab3e8-…?X-Amz-… 200 application/pdf
```

The chip uses `<a download>`. The presigned URL is cross-origin, so Chromium ignores the `download` attribute and opens the PDF inline in the current tab. This behaviour was already there before the fix commit.

Screenshots: `f3a-task-comment-posted-chip.png`, `f3b-chip-click-opens-presigned-pdf.png`

### F4 — edit dialog on the F3 comment

1. **Visibility only:** INTERNAL → SHARED, then Save.
   - Payload: `["e2e-test-org","e120be4a-…","1b7a8108-…","627I re-observe F3 task comment","SHARED","$undefined"]`.
   - `updateComment` spreads `attachmentDocumentIds` only when it is defined, so the backend PUT omits the key.
   - The link row is unchanged (same `document_id`, `created_at` 15:25:47.85).
   - The document's visibility follows the comment and is now SHARED. That is backend 627H behaviour.
2. **Add a file:** Edit → attach edit-add-f4.pdf → Save.
   - initiateUpload returned `e3d43692…`.
   - Payload: `[…,"SHARED",["4e2ab3e8-…","e3d43692-…"]]`.
   - DB: two links, and the new one is created at 15:27:29.
3. **Remove a chip:** Edit → remove the task-f3.pdf chip → Save.
   - Payload: `[…,"SHARED",["e3d43692-…"]]`.
   - DB: a single link (edit-add-f4.pdf). Doc `4e2ab3e8…` is still UPLOADED / COMMENT.

Screenshots: `f4a-edit-visibility-only.png`, `f4b-edit-kept-plus-new.png`, `f4c-edit-removed-kept-chip.png`

### F5 — Escape closes the edit dialog and releases the staged upload

1. Dropped edit-esc-f5.pdf onto the dialog's dropzone with a real `DataTransfer` drop event. The chip reached Ready and the DB shows `b277ea36… | PENDING | MANUAL`.
2. Pressed Escape (no Save). The Edit dialog closed and the Task Detail sheet stayed open. The server action `["b277ea36-…"]` (cancelUpload) was POSTed.
3. DB: 0 rows for `b277ea36…` and 0 PENDING rows. The comment's links are unchanged (`e3d43692…` only).
4. Re-opened the dialog: chips show only `edit-add-f4.pdf 83 B`. Closed it with Cancel.

Screenshots: `f5a-edit-dialog-dropped-staged.png`, `f5b-reopen-chips-reset-to-saved.png`

### F6 — add-form staging, removal, and the 5-file cap

1. Dropped add-f6a.pdf. The DB shows `2b2e08bd… | PENDING`.
2. Removed its chip. cancelUpload `["2b2e08bd-…"]` was POSTed and the DB shows 0 rows.
3. Dropped 6 files at once (lim1–lim6). lim1–lim5 reached Ready; lim6 was not staged and has no DB row. DB: 5 PENDING rows, lim1–lim5.
4. Dropzone attributes: `{ ariaDisabled: "true", tabIndex: -1, role: "button", class: "… cursor-not-allowed opacity-50" }`. The message "A comment can have at most 5 attachments." was visible according to `isVisible()`. It sits below the viewport in the screenshot.
5. Removed all 5 chips. This sent 5 cancelUpload POSTs (`967e26f3…`, `eeb048b0…`, `738c0448…`, `431a4cba…`, `b5964820…`).
6. Final state: `aria-disabled` is null, the message is not visible, and the DB shows 0 PENDING rows and 0 `lim%.pdf` rows.

Screenshots: `f6a-add-form-dropped-staged.png`, `f6b-five-staged-dropzone-aria-disabled.png`, `f6c-all-removed-zone-enabled.png`

### F7 — console and logs

The browser's lifetime console log (`all: true`) holds 7 error entries:

- **2× `ERR_CONNECTION_REFUSED` and 4× 404 at `?tab=documents`:** these come from the Playwright tab left open by the earlier observer session.
  - The connection-refused errors can only have happened while the frontend container was down during the rebuild, which was before 15:23:26Z.
  - The frontend log shows 3× `Failed to find Server Action "00e3962f…"` at 15:23:31, 15:23:41 and 15:23:51 (a 10 s poll). That action id is not in the new build. All three came before this session's first navigation (15:23:57), so they are the old bundle's poll hitting the new deployment.
- **1× 404 for `localhost:4567/favicon.ico`:** this is LocalStack, after opening the presigned PDF. It is not app code.

Fresh check: I re-attached `console` and `requestfailed`/`response ≥ 400` listeners, then reloaded the Documents tab, expanded a thread, and opened the task sheet's Comments tab. The result was 0 console errors and warnings and 0 responses ≥ 400. The only failures were `ERR_ABORTED` RSC prefetches cancelled by navigation, which is normal.

The backend log since 15:20Z has no 4xx, WARN or ERROR lines.

## IDs created this session

| Kind | ID | Note |
|------|----|------|
| Comment (DOCUMENT thread) | `446f8f4f-b6b2-4acf-ac1e-6e89c2028a56` | deleted in F2 |
| Document master-f1.pdf | `afa9be14-e595-499c-984c-fda4413ba646` | UPLOADED / COMMENT, kept |
| Comment (TASK) | `1b7a8108-45f4-4c83-9c91-ccce8c47a005` | now SHARED, linked to e3d43692 |
| Document task-f3.pdf | `4e2ab3e8-961a-43a9-8964-c4b5c4305ae3` | UPLOADED / COMMENT / SHARED, unlinked in F4 |
| Document edit-add-f4.pdf | `e3d43692-91a6-4240-8c12-691b421ee669` | UPLOADED / COMMENT / SHARED, linked |
| PENDING docs released | `b277ea36…`, `2b2e08bd…`, `967e26f3…`, `eeb048b0…`, `738c0448…`, `431a4cba…`, `b5964820…` | all deleted |

## Final state

On the matter: UPLOADED/COMMENT 10, UPLOADED/MANUAL 3, PENDING 0. `comment_attachments` has 6 rows.
