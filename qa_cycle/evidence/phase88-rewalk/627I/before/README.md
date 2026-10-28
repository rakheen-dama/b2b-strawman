# 627I — G-B15 reproduction (BEFORE fix)

- Date: 2026-10-28 ~14:33Z
- Stack: E2E mock-auth (frontend :3001, backend :8081, mock IDP :8090), built from main @ b0a8631ba (627H merged)
- User: Alice (owner), org `e2e-test-org`, tenant schema `tenant_7d218705360b`
- Browser: Playwright MCP (Chromium)
- No source changes. No DB writes. The Edit Comment dialog was opened and cancelled without saving.

## Fixture ids (left by the 627H observer)

| Thing | id |
|---|---|
| Matter "Website Redesign" | `e120be4a-1c8b-4ed8-b993-74f1ee5de29a` |
| Task "627H after-fix observation task" | `56344385-8956-4eeb-99f5-35b52e80c377` |
| Comment F4b (SHARED, 3 attachments) | `dac70b66-b73d-4426-bb37-60d7e3fe45ac` |
| Comment F5 (SHARED, no attachments) | `65ccf1a8-8195-46b3-8700-43454165e3da` |
| Comment F6 (INTERNAL, 1 attachment) | `2695c0c4-e6a3-406c-b1f8-db4ca9f5d472` |

All three comments are `entity_type = TASK` on the single task. There are no PROJECT-level comments in the DB, and the matter page has no matter-level comment surface. Comments show up in two places: the task detail sheet's Comments tab, and the comments that expand under each row of the Documents tab (`entity_type = DOCUMENT`).

## Step 1: comment form has no file control (REPRODUCED)

URL: `http://localhost:3001/org/e2e-test-org/matters/e120be4a-1c8b-4ed8-b993-74f1ee5de29a?tab=tasks&taskId=56344385-8956-4eeb-99f5-35b52e80c377`, then the **Comments** tab in the sheet.

The "Add a comment" form has three controls, read from the DOM:

```
TEXTAREA:textarea
SELECT:select-one   (Internal only / Customer visible)
BUTTON:submit       Post Comment
```

`input[type=file]` inside the task sheet: **0**. On the whole page: **0**. There is no attach button, paperclip or dropzone.

Screenshot: `01-task-comments-form-no-file-control.png`

The document-row comment form behaves the same way (`?tab=documents`, expand `f-a-627H.pdf`). Its form is also TEXTAREA + SELECT + Post Comment. The only `input[type=file]` on that tab is the matter Documents upload dropzone ("Drag and drop files here…"), which sits outside the comment form (`inForm: false`). Screenshot: `05-document-row-comments-expanded.png`

## Step 2: Edit Comment dialog has no attachment control (REPRODUCED)

On the same sheet, I clicked **Edit** on comment F4b (`dac70b66…`, which has 3 attachments in the DB).

The dialog text reads "Edit Comment / Update your comment. / Comment / Visibility / Cancel / Save". Its controls are TEXTAREA, SELECT, Cancel, Save and Close. `input[type=file]`: **0**. The dialog does not list the existing attachments and has no way to add or remove one.

Screenshot: `02-edit-comment-dialog-no-attachment-control.png` (I clicked Cancel afterwards, so nothing was saved.)

## Step 3: existing attachments are not rendered (REPRODUCED)

DB (`docker exec e2e-postgres psql -U postgres -d app`):

```
select * from tenant_7d218705360b.comment_attachments;
              comment_id              |             document_id              | sort_order
--------------------------------------+--------------------------------------+-----------
 dac70b66-b73d-4426-bb37-60d7e3fe45ac | 0a1da2a2-eaca-40da-8992-e3898aaf14f8 |          0
 dac70b66-b73d-4426-bb37-60d7e3fe45ac | 12597af6-92a0-4a95-b247-5080b0975148 |          1
 dac70b66-b73d-4426-bb37-60d7e3fe45ac | e26e743f-af0f-493e-bf45-a91c85764a55 |          2
 2695c0c4-e6a3-406c-b1f8-db4ca9f5d472 | e1db1bb6-5d74-460e-b63b-fabd95a5f255 |          0
```

The backend API returns them. Read-only GET with a mock-IDP token for Alice: `GET /api/matters/{matterId}/comments?entityType=TASK&entityId={taskId}`

```
dac70b66-… SHARED   [0a1da2a2-… f-a-627H.pdf, 12597af6-… f-b-627H.pdf, e26e743f-… f-c-627H.pdf]
65ccf1a8-… SHARED   []
2695c0c4-… INTERNAL [e1db1bb6-… f-d-627H.pdf]
attachment shape: {"documentId": "...", "fileName": "f-a-627H.pdf", "contentType": "application/pdf", "size": 45}
CommentResponse keys: attachments, authorAvatarUrl, authorMemberId, authorName, body, createdAt, entityId, entityType, id, matterId, parentId, source, updatedAt, visibility
```

The UI does not show them. The task sheet's Comments tab shows the three comments with body, author, time and the "Customer visible" badge, plus Edit and Delete. None of `f-a/f-b/f-c/f-d-627H.pdf` appears anywhere in the sheet text (DOM check: `fileNamesShown: []`), and there are no chips.

Screenshot: `03-comments-with-attachments-no-chips.png` (the same view as 01)

## Step 4: Documents tab has no "Comment" source label (REPRODUCED)

```
select id, file_name, status, scope, visibility, source from tenant_7d218705360b.documents;
 0a1da2a2-eaca-40da-8992-e3898aaf14f8 | f-a-627H.pdf | UPLOADED | PROJECT | SHARED   | COMMENT
 12597af6-92a0-4a95-b247-5080b0975148 | f-b-627H.pdf | UPLOADED | PROJECT | INTERNAL | COMMENT
 e26e743f-af0f-493e-bf45-a91c85764a55 | f-c-627H.pdf | UPLOADED | PROJECT | SHARED   | COMMENT
 e1db1bb6-5d74-460e-b63b-fabd95a5f255 | f-d-627H.pdf | UPLOADED | PROJECT | INTERNAL | COMMENT
```

The API `GET /api/matters/{matterId}/documents` returns `source: "COMMENT"` for all four.

URL: `http://localhost:3001/org/e2e-test-org/matters/e120be4a-1c8b-4ed8-b993-74f1ee5de29a?tab=documents`

The columns are File, Size, Status, Uploaded and actions. Each row reads only `<file> 45 B Uploaded 28 Oct 2026` plus Download and Review with AI. A DOM check of the active tabpanel found no "Comment" word (`hasCommentLabel: false`) and no source or "from comment" text. Nothing on the list shows that a document came from a comment. (There is also no visibility indicator for the SHARED vs INTERNAL documents, which is outside G-B15 scope.)

Screenshot: `04-documents-tab-no-comment-source-label.png`

## Step 5: rendering components (from DOM plus a frontend grep)

- `frontend/components/comments/comment-section-client.tsx` is used by `components/tasks/task-detail-sheet.tsx` (Comments tab) and `components/documents/documents-panel.tsx` (expanded document row)
- `frontend/components/comments/add-comment-form.tsx` renders the textarea, the visibility select and Post Comment
- `frontend/components/comments/edit-comment-dialog.tsx` renders the Edit Comment dialog
- `frontend/components/comments/comment-item.tsx` renders the comment row (no attachment rendering)
- No `data-testid` on the comment surfaces (the task sheet only has `tag-input` and `custom-field-section`; the documents tabpanel has none)
