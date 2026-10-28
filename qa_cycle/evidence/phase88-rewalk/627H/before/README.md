# 627H BEFORE: G-B15 comment attachments (task 627.16)

Recorded 2026-10-28 (about 12:22–12:25 UTC) against the running e2e mock-auth stack: frontend :3001, backend :8081,
mock IDP :8090, `e2e-postgres` :5433, LocalStack :4567. Containers were created 2026-10-28T12:08Z.
Repo `$WT` @ `69a4d4e16` (= `main`). Tenant schema `tenant_7d218705360b` (org slug `e2e-test-org`, from
`public.org_schema_mapping`). No product code was changed.

Tokens were minted from the mock IDP. The token values are not recorded here.

```bash
curl -s -X POST localhost:8090/token -H 'content-type: application/json' \
  -d '{"userId":"user_e2e_alice","orgSlug":"e2e-test-org"}'   # -> .access_token, sent as Authorization: Bearer
```

## Ids created (left in place, reuse them in later observations)

| What | Id |
|---|---|
| Matter (existing) "Khumalo Property Holdings — Erf 1234 Sandton Transfer mvs2q3xe7e5d" | `9cf2159e-213a-44d7-b7c8-ef184544ddac` |
| Task (existing) "Close matter" on that matter | `f153f432-4a0b-4098-bbc8-f44b49cd0738` |
| **Document (created)** `masters-letter-627H.pdf`, application/pdf, 107 B, UPLOADED / INTERNAL / MANUAL / PROJECT | `ad27ffcc-b334-4e6c-97ac-46397e0fcc21` |
| **Comment (created)** TASK comment by Alice, INTERNAL | `870fba72-a20d-49ea-9311-0a3e1a977361` |

Terminology: the spec says "CONFIRMED". The backend status enum is `PENDING | UPLOADED | FAILED`
(`Document.java:216-220`), so "confirmed" means `status = UPLOADED`, which `POST /api/documents/{id}/confirm` sets.

## Steps

| Step | File(s) | Observed | Verdict |
|---|---|---|---|
| R1 Browser: Alice, matter → Work·Tasks → task "Close matter" sheet → Comments tab | `01-task-comment-form-no-file-control.png`, `01-task-comments-tab-snapshot.yml`, `01-dom-probe.json` | The form has only the textarea "Add a comment", the Visibility select (Internal only / Customer visible) and the "Post Comment" button. DOM probe: `input[type=file]` = **0** in the dialog and **0** on the page, and no button matches /attach\|file\|upload/. Source: `frontend/components/comments/add-comment-form.tsx` renders only `body` + `visibility`. The document-comment surface (`documents-panel.tsx:534`) mounts the same `CommentSectionClient`, which renders the same form. | **REPRODUCED** |
| R2 API: upload-init → presigned PUT → confirm | `02a-upload-init.response.txt` (signature/credential redacted), `02b-presigned-put.status.txt`, `02c-confirm.response.txt`, `02d-db-document-row.txt` | `POST /api/matters/{m}/documents/upload-init` returned 201. The presigned URL already targets `localhost:4567` (`AWS_S3_PRESIGNER_ENDPOINT`), so no rewrite was needed. PUT returned 200. `POST /api/documents/{id}/confirm` returned 200 with `status=UPLOADED`. DB row: `status=UPLOADED, visibility=INTERNAL, source=MANUAL, scope=PROJECT`. | Done (precondition) |
| R3a `POST /api/matters/{m}/comments` with `"attachmentDocumentIds":["ad27ffcc-…"]` | `03a-post-comment.request.json`, `03a-post-comment.status.txt`, `03a-post-comment.response.json` | **201 Created**. The response has no `attachments` key. Its fields are `id, entityType, entityId, matterId, authorMemberId, authorName, authorAvatarUrl, body, visibility, source, parentId, createdAt, updatedAt`. The unknown property is **silently ignored**: no 400 and no validation. `CreateCommentRequest` = `(entityType, entityId, body, visibility)` (`CommentController.java` DTOs). | **REPRODUCED** |
| R3b `PUT /api/matters/{m}/comments/{c}` with `attachmentDocumentIds` | `03b-put-comment.request.json`, `03b-put-comment.status.txt`, `03b-put-comment.response.json` | **200**. The body was updated, there is no `attachments` key, and the field was silently ignored. | **REPRODUCED** |
| R3c `GET /api/matters/{m}/comments?entityType=TASK&entityId={task}` | `03c-list-comments.status.txt`, `03c-list-comments.response.json` | **200**. One comment, no `attachments` key. `grep -c attachment` = 0 on all three response bodies. | **REPRODUCED** |
| R3d DB `comments` table + row | `03d-db-comments-table.txt`, `03e-db-comment-row.txt` | `comments` has no attachment column. The row persisted with no link to the document. | **REPRODUCED** |
| R4 DB schema on the e2e tenant | `04-db.txt` | `\d tenant_7d218705360b.comment_attachments` returns "Did not find any relation". `to_regclass` is NULL. No table in any schema has a name like `%attachment%`. `documents.source` values present: only `MANUAL` (9 rows). `documents` CHECKs: only `chk_documents_provenance` (AI_GENERATED ↔ ai_execution_id) and `chk_documents_scope_fks`. **No source-enum CHECK**, so `source` is varchar(30) DEFAULT 'MANUAL' and `COMMENT` needs no constraint change. `Document.Source` constants are `MANUAL, AI_GENERATED, TEMPLATE_GENERATED, EMAIL_INGEST` (`Document.java:267-273`), with no `COMMENT`. Tenant flyway top 3: V8 `phase88 retention comment attachments`, V7 `phase88 letterhead`, V6 `phase88 audit customer index`. The e2e tenant has V8 applied **but V8 contains no comment-attachment DDL**. | **REPRODUCED** |
| R5 Portal (code read + DB) | (cited below) | No attachment field anywhere on the portal comment path. `PortalCommentView` (`backend/.../customerbackend/model/PortalCommentView.java:6-13`) = `id, orgId, portalProjectId, authorName, content, createdAt, syncedAt`. `PortalCommentController.PortalCommentResponse` (`PortalCommentController.java:88-89`) = `id, authorName, content, createdAt`. Portal TS `PortalComment` (`portal/lib/types.ts:64-69`) = `id, authorName, content, createdAt`. `portal.portal_comments` has the same 7 columns, none for attachments. | **REPRODUCED** (code + schema read; not browser-driven) |
| R6 Migration number (OQ #9) | `migrations.md`, `06-kc-flyway-raw.txt` | KC dev `docteams`: both tenant schemas (`tenant_56399d06a773`, `tenant_5039f2d497cf`) have **V8** applied with checksum `163234837`, the same as the file. Worktree top: V6, V7, V8. | **max KC tenant = V8 → 627H creates V9** |

## Notes for the fix

- Jackson ignores unknown request properties on this backend. A request built against the new contract will therefore "succeed" on an
  old backend with no attachment. The after-observation must assert `attachments[]` in the **response**, not just a 2xx.
- The comment created here is INTERNAL. Portal-chip observation needs a SHARED comment, which the fix observation will create.
- The V8 header still claims 627H may append to it. That is now false (V8 is applied on KC). 627H should take V9 and
  may want to correct the V8 header comment. A comment-only edit still changes the Flyway checksum, so leave the
  V8 file byte-identical and record the correction elsewhere.
