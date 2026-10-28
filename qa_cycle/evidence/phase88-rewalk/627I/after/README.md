# 627I — Observed PASS (627.20 / U24): after

- **Code under test:** PR #1700, head `136ba30d7` (worktree `$WT`).
- **Stack:** E2E mock-auth stack. The frontend was rebuilt from `$WT` with `SHELL=/bin/bash bash compose/scripts/e2e-rebuild.sh frontend` (exit 0). Resulting image: `sha256:8100af99c42e…`, created 2026-10-28T15:05:13Z.
  - The rebuild recreated `e2e-backend` and `e2e-localstack`. The backend code is the same as main. Postgres volumes were kept, so all 627H data is still there.
  - **The LocalStack bucket `docteams-dev` came back empty.** The S3 bytes of the pre-existing f-a/f-b/f-c/f-d-627H.pdf are gone: their DB rows and links are intact, but downloading those four files would fail. Every file uploaded in this run has its bytes in S3.
- **Actor:** Alice (owner), signed in through `/mock-login`. Org `e2e-test-org`, tenant schema `tenant_7d218705360b`.
- **Matter:** "Website Redesign" `e120be4a-1c8b-4ed8-b993-74f1ee5de29a`. **Task:** `56344385-8956-4eeb-99f5-35b52e80c377`.
- **Browser:** Playwright MCP (Chromium). **SQL:** read-only `SELECT` through `docker exec e2e-postgres psql …` (`set search_path=tenant_7d218705360b`).
- **Server-action payloads:** captured from the browser POSTs (Next.js server-action request bodies = the action's argument array).

| # | Result | One-line evidence |
|---|--------|-------------------|
| O1 | PASS | Document-thread comment with masters-appointment.pdf. The doc was PENDING while staged; after Post it is UPLOADED / COMMENT / INTERNAL / PROJECT on the same project, with a link row |
| O2 | PASS | After reload, masters-appointment.pdf shows the "Comment" badge. The MANUAL regression-upload rows do not |
| O3 | PASS | Comment deleted, its link rows are gone (0), and the document is kept (UPLOADED / COMMENT) with the badge still in the list |
| O4 | PASS | Task comment with task-note-627I.pdf shows a chip. Clicking it sent a server-action POST (200) and then a GET of the LocalStack presigned URL for doc `04efac7f…` (200, `application/pdf`) |
| O5 | PASS | F4b shows 3 chips (f-a, f-b, f-c) and F6 shows 1 chip (f-d) |
| O6 | PASS | Visibility-only Save sends `attachmentDocumentIds` = `$undefined` and the link row is unchanged. Remove-chip Save sends `[9a3d…]`: link removed, doc kept. Add-file Save sends `[04ef…, 9a3d…]`: link appended |
| O7 | PASS | Removing a staged chip calls cancelUpload and the PENDING row is deleted. Cancelling the edit dialog does the same, and the comment's links are unchanged |
| O8 | PASS | Of 6 selected files, 5 were staged (5 PENDING rows, no limit-6) and the message appeared. The zone was disabled (no file chooser opened on click). Removing the chips deleted all 5 rows |
| O9 | N/A | The UI only offers new uploads (each one a new PENDING document on this matter), so it has no way to pick an already-attached or client-visible document |
| O10 | PASS | One click on the main dropzone gives `input[type=file].click()` ×1 and one MANUAL UPLOADED row, with no "Comment" badge and no console errors |
| O11 | PASS | The submit is still "Post Comment". The dropzone's accessible name is "Attach files (drop or click to browse)", which does not match `/send\|post\|submit\|comment/i` |

---

## O10 — regression: main Documents dropzone (run first, to create a MANUAL document)

**URL:** `/org/e2e-test-org/matters/e120be4a-…?tab=documents`

**Steps:**
1. Navigate to the URL.
2. Instrument `HTMLInputElement.prototype.click` (count file inputs) and `console.error`.
3. Click "Drag and drop files here, or click to browse" once.
4. Choose `regression-upload-627I-clean.pdf`.

**Result:**
- `{ fileClicks: 1, errs: [] }`.
- The new row reads `regression-upload-627I-clean.pdf 129 B Uploaded …`, with no "Comment" badge.

```
33be9843-1515-41ab-8a3b-7bd23fcd21d7 | regression-upload-627I-clean.pdf | UPLOADED | MANUAL | INTERNAL | PROJECT
```

**Observer artefact (not a product bug):** two earlier rows named `regression-upload-627I.pdf` (`0eb53617…`, `0d98307e…`) come from one file chooser being answered twice. My own `run_code` `setFiles` call answered it, and then the MCP's own file-chooser handler answered it again. The clean re-run above is the evidence that one click opens one chooser.

**Screenshot:** `o10-main-dropzone-upload-no-comment-badge.png`

## O1 — document-thread comment with an attachment (627.20 primary)

**Steps:**
1. On the Documents tab, expand `regression-upload-627I-clean.pdf`.
2. Type "Master's appointment received" with visibility Internal only.
3. Click "Attach files (drop or click to browse)" and choose `masters-appointment.pdf`.
4. The chip shows "masters-appointment.pdf 133 B ✓ (Ready) ×".

DB while staged, before Post:

```
7c77049f-a4bf-465d-8bf8-329d6849c8bb | masters-appointment.pdf | PENDING | MANUAL | INTERNAL | PROJECT | e120be4a-…
```

The PENDING document is not listed in the Documents table.

5. Click Post Comment. The posted comment renders a chip "masters-appointment.pdf 133 B".

DB after Post:

```
comment 3013841a-36a8-4dcb-b0ff-fd9f24421d3a | DOCUMENT | entity 33be9843-… | INTERNAL | Master's appointment received
  → link document 7c77049f-… | masters-appointment.pdf | UPLOADED | COMMENT | INTERNAL | PROJECT | project e120be4a-…
```

**Screenshots:** `o1a-document-thread-staged-chip.png`, `o1b-document-thread-posted-chip.png`

## O2 — "Comment" badge in the Documents list

After reloading `?tab=documents`, the first cells read:

```
regression-upload-627I-clean.pdf          (MANUAL, no badge)
f-b-627H.pdf Comment
f-d-627H.pdf Comment
regression-upload-627I.pdf                (MANUAL, no badge) ×2
masters-appointment.pdf Comment
f-a-627H.pdf Comment
f-c-627H.pdf Comment
```

**Screenshot:** `o2-documents-list-comment-badge.png`

## O3 — delete the comment; the document stays

**Steps:**
1. Expand the thread.
2. Click Delete, then confirm "Delete Comment — Delete this comment? This action cannot be undone." → Delete.

**Result:**
- The thread shows "No comments yet".
- `masters-appointment.pdf Comment` is still listed.

DB:

```
comments where id=3013841a… → 0
comment_attachments for comment 3013841a… or document 7c77049f… → 0
documents 7c77049f… → masters-appointment.pdf | UPLOADED | COMMENT | INTERNAL
```

The backend log shows `Deleted comment 3013841a-… from project e120be4a-…`.

**Screenshot:** `o3-after-delete-doc-kept-with-badge.png`

## O4 — task comment with an attachment; download the chip

**URL:** `?tab=tasks&taskId=56344385-…` → Comments tab.

**Steps:**
1. Type "627I O4 task comment with attachment".
2. Attach `task-note-627I.pdf`, wait for Ready, then click Post Comment.
3. The posted chip is a button named `Download task-note-627I.pdf` ("task-note-627I.pdf 130 B").

DB:

```
comment cb1ef9ea-2fcc-481c-b42f-3df7cfc98089 | TASK | INTERNAL
  → 04efac7f-1974-4395-9bb7-b71ca8bb3208 | task-note-627I.pdf | UPLOADED | COMMENT | INTERNAL | PROJECT
     s3_key org/e2e-test-org/project/e120be4a-…/04efac7f-…
```

**Chip click — network:**

```
POST http://localhost:3001/org/e2e-test-org/matters/e120be4a-…?tab=tasks&taskId=…  → 200   (getDownloadUrl server action)
GET  http://localhost:4567/docteams-dev/org/e2e-test-org/project/e120be4a-…/04efac7f-…?X-Amz-Algorithm=AWS4-HMAC-SHA256&…&X-Amz-Expires=3600&X-Amz-Signature=…  → 200
```

The browser then shows the PDF (`document.contentType = application/pdf`). The tab navigates to the presigned URL because `a.download` is ignored cross-origin. Clicking "Download …" in the Documents panel behaves the same way, because it uses the same anchor pattern (`documents-panel.tsx` `handleDownload`), so this is not new behaviour.

**Screenshots:** `o4-task-comment-posted-chip.png`, `o4-chip-click-presigned-localstack.png`

## O5 — existing 627H comments now show chips

On the task's Comments tab:
- F4b `dac70b66…` (Customer visible) shows chips f-a-627H.pdf, f-b-627H.pdf, f-c-627H.pdf (45 B each).
- F5 shows no chips.
- F6 `2695c0c4…` (internal) shows chip f-d-627H.pdf.

The "before" evidence showed no chips.

**Screenshot:** `o5-existing-comments-chips.png`

## O6 — edit dialog

All edits were made on comment `cb1ef9ea…` (the O4 comment).

1. **Existing chips shown.** Edit opens with body "627I O4 task comment with attachment", visibility INTERNAL, and the chip "task-note-627I.pdf 130 B" (`o6a-edit-dialog-existing-chip.png`).

2. **Visibility only.** I changed INTERNAL → SHARED and saved. The hint "Attachments on a client-visible comment are shared with the client." appeared (`o6b-edit-visibility-only-shared-hint.png`). The link row was unchanged: the same `document_id`, with `created_at` 15:08:39.355345 before and after. The document's visibility followed the comment to SHARED, which is the backend's documented rule on a visibility change (`CommentService` "A comment visibility change re-applies the rule").

   I then changed SHARED → INTERNAL and captured the action body:

   ```
   ["e2e-test-org","e120be4a-…","cb1ef9ea-…","627I O4 task comment with attachment","INTERNAL","$undefined"]
   ```

   `attachmentDocumentIds` is undefined, so the key is omitted from the PUT.

3. **Add a file.** Attach `edit-add-627I.pdf` → Save. Captured bodies:

   ```
   ["e2e-test-org","e120be4a-…","9a3d64c1-088a-44e6-bbab-143aa084a1e3"]                       (confirmUpload)
   ["e2e-test-org","e120be4a-…","cb1ef9ea-…","627I O4 task comment with attachment","INTERNAL",["04efac7f-…","9a3d64c1-…"]]
   ```

   DB: sort 0 = task-note-627I.pdf, sort 1 = edit-add-627I.pdf (UPLOADED, COMMENT, INTERNAL). Screenshot: `o6c-edit-dialog-kept-plus-new.png`.

4. **Remove a kept chip.** Remove `task-note-627I.pdf` → Save. Captured body:

   ```
   [..., "INTERNAL", ["9a3d64c1-…"]]
   ```

   No cancelUpload was sent for the kept document. DB: there is one link (edit-add-627I.pdf), and document `04efac7f…` task-note-627I.pdf is still UPLOADED / COMMENT. The posted comment now shows only the edit-add-627I.pdf chip. Screenshot: `o6d-edit-dialog-removed-kept-chip.png`.

## O7 — staged removal and edit cancel leave no orphan

**Composer:**
1. Attach `staged-remove-627I.pdf`. DB: `92b2ed84-bc31-47d6-8b5d-1379d24d9623 | PENDING | MANUAL`.
2. Click "Remove staged-remove-627I.pdf". Captured action body `["92b2ed84-…"]` (cancelUpload).
3. DB: `count = 0`.

Screenshot: `o7a-composer-staged-chip.png`

**Edit dialog:**
1. Attach `cancel-edit-627I.pdf`. DB: `f96a2b5e-2872-4efa-a1ea-ac5f85c1829b | PENDING`.
2. Click Cancel. Captured `["f96a2b5e-…"]` (cancelUpload).
3. DB: `count = 0`. The comment's link count is still 1.

Screenshot: `o7b-edit-dialog-staged-before-cancel.png`

## O8 — limit of 5

I selected limit-1…limit-6.pdf in one chooser.

**Result:**
- Five chips: limit-1…limit-5, each "128 B ✓".
- The message "A comment can have at most 5 attachments." is shown.
- DB: limit-1…5 are PENDING, and there is no limit-6 row.
- Zone state: class `cursor-not-allowed opacity-50`, `tabindex=-1`, inner `input[type=file]` disabled. A forced click opened no file chooser.

**Cleanup:** I removed all five chips. Afterwards the message is gone, `limit-%` docs = 0, and PENDING docs on the matter = 0. Nothing was posted.

**Screenshot:** `o8-limit-five-staged-message.png`

## O9 — 422 "Attachment not allowed" path

**N/A.** The composer and edit dialog only create new uploads, which are fresh PENDING documents on the comment's matter, INTERNAL by default. Kept attachments are excluded from re-validation on edit. No UI path lets the user pick an existing or client-visible document, so the 422 cannot be reached from the browser. It is covered by unit tests (`comment-actions-attachments.test.ts`, the edit and comment-section tests).

## O11 — Post button label and dropzone accessible name

- Task sheet: `button "Attach files (drop or click to browse)"`.
- Document thread: `button "Attach files (drop or click to browse)"`.
- Main zone: `button "Drag and drop files here, or click to browse Max 100 MB per file"`.

None of these matches `/send|post|submit|comment/i`.

Buttons on the page that do match the pattern:
- Task sheet: `Edit/Delete comment by Alice Owner` …, then `Post Comment`.
- Document page: `Collapse comments for <file>` (expanded row), then `Post Comment`, then the `Expand comments for <file>` buttons.

These row-toggle and edit/delete labels were not changed by 627I.

## Console

Over the whole session after the rebuild, there were **no application console errors** on :3001. The only error was a `404 http://localhost:4567/favicon.ico` on the LocalStack origin after O4 navigated there.

Three earlier entries (`ERR_CONNECTION_REFUSED` ×2 and one `404`) are stamped 15:05:24–15:05:39. They came from a page left open by the previous session, polling while the frontend container restarted, and they happened before this session's first navigation at 15:05:55. There was no "Maximum call stack size exceeded".

## Notes (not failures)

- **At limit:** the dropzone is disabled visually, by `tabindex=-1` and by the disabled input, but it has no `aria-disabled` on the `role=button` element. This is a minor a11y nit.
- **Posted chip click** navigates the current tab to the presigned URL, the same as the existing Documents "Download" button.

## IDs created

- **Comments:**
  - `3013841a-36a8-4dcb-b0ff-fd9f24421d3a` (DOCUMENT thread, later deleted in O3)
  - `cb1ef9ea-2fcc-481c-b42f-3df7cfc98089` (TASK; now INTERNAL with edit-add-627I.pdf)
- **Documents kept:**
  - `33be9843…` regression-upload-627I-clean.pdf (MANUAL)
  - `0eb53617…`, `0d98307e…` regression-upload-627I.pdf (MANUAL, observer double-set artefact)
  - `7c77049f…` masters-appointment.pdf (COMMENT, unlinked after O3)
  - `04efac7f…` task-note-627I.pdf (COMMENT, unlinked after O6)
  - `9a3d64c1…` edit-add-627I.pdf (COMMENT, linked to `cb1ef9ea`)
- **Created then cancelled** (rows deleted): `92b2ed84…`, `f96a2b5e…`, limit-1…5.
