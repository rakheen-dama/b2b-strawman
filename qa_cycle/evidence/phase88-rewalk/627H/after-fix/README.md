# 627H AFTER-FIX: comment attachments backend (PR #1699)

**Observed on head f022281c3, fresh e2e stack.**

Recorded 2026-10-28 13:27–13:31 UTC against the e2e mock-auth stack: backend :8081, mock IDP :8090, `e2e-postgres`, LocalStack :4567. Tenant schema `tenant_7d218705360b`, org `e2e-test-org`.

- **Stack:** `compose/scripts/e2e-down.sh` removed all containers and volumes. `compose/scripts/e2e-up.sh` then rebuilt from `$WT` @ `f022281c3`. The worktree had no changes under `backend/`. Maven runs only inside the Docker builder stage, and `backend/target` is `.dockerignore`d, so the concurrent host `mvn verify` was not touched. The `e2e-backend` container was created at 13:27:05Z. Health was `UP`, and the seed completed.
- **Fresh seed:** one matter, "Website Redesign" `e120be4a-…`, whose client is Acme Corp `8cc8d8e1-…`. Acme has the portal contact `contact@acme.example.com`. The seed has no tasks, so one task was created via the API (`00-create-task.*`). TASK comments are used because matter-level PROJECT comments are always SHARED, and F4 needs to flip visibility.
- **Tokens:** staff tokens came from the mock IDP (`POST :8090/token {"userId":"user_e2e_alice","orgSlug":"e2e-test-org"}`). The portal token came from `POST /portal/auth/request-link` (the e2e response returns `magicLink`) followed by `POST /portal/auth/exchange`. **No token values are recorded.** In the presigned URLs the credential and signature are redacted.
- **No product code was changed.** The only raw SQL write was in F5, and it was rolled back.

File naming: `NAME.request.json` / `NAME.status.txt` (method + path, HTTP code + content-type) / `NAME.response.json`. `*-db*.txt` files are psql output. `*.upload-init.txt` / `*.confirm.txt` record the presign flow: init 201, S3 PUT 200, confirm 200.

## Results

| F | Files | Observed | Verdict |
|---|---|---|---|
| F1 Migration | `F1-migration.txt` | Tenant `flyway_schema_history`: V9 "phase88 comment attachments", success = t, installed 13:27:35Z, checksum 733466875. `pg_indexes` gives `CREATE UNIQUE INDEX idx_comment_attachments_document ON tenant_7d218705360b.comment_attachments USING btree (document_id)`. `\d` shows `"idx_comment_attachments_document" UNIQUE, btree (document_id)`. | **PASS** |
| F2 Happy path | `F2-doc-a.*`, `F2-post-shared-comment.*`, `F2-db.txt` | Doc a uploaded via presign. After confirm it was UPLOADED / INTERNAL / MANUAL. `POST /api/matters/{M}/comments` TASK SHARED with `[a]` returned **201** with `attachments:[{documentId:a, fileName:"f-a-627H.pdf", contentType:"application/pdf", size:45}]`. DB: a = **SHARED / COMMENT**, with link row `(C1, a, 0)`. Audit: `document.visibility_changed {INTERNAL→SHARED}` and `comment.created … attachment_count:1`. | **PASS** |
| F3 The fix (set-only edit keeps kept docs' visibility) | `F3-doc-b.*`, `F3a-*`, `F3b-*`, `F3-doc-c.*`, `F3c-*` | PUT with `[a,b]` (visibility omitted) returned 200, and b became SHARED / COMMENT. `PATCH /api/documents/{b}/visibility {"visibility":"INTERNAL"}` as Alice returned **200**, and b became INTERNAL. Doc c uploaded (INTERNAL / MANUAL). PUT with `[a,b,c]` (comment visibility unchanged, SHARED) returned **200** with 3 chips. DB: **a SHARED, b INTERNAL, c SHARED / COMMENT**, links at 0/1/2. b's audit trail: 13:28:44 `INTERNAL→SHARED` (attach), then 13:28:51 `SHARED→INTERNAL` (unshare). **No SHARED flip for b after the unshare.** The only new `visibility_changed` row at 13:28:52 is for c. | **PASS** |
| F4 Converse (comment visibility change re-applies to all) | `F4a-*`, `F4b-db-audit.txt`, `F4b-put-shared.*` | PUT with `visibility:INTERNAL` and the same `[a,b,c]` returned 200, and a, b, c were **all INTERNAL**. Audit has a→INTERNAL and c→INTERNAL; b was already INTERNAL, so it got no row. `comment.visibility_changed` was recorded. PUT back to `visibility:SHARED` with the same set returned 200, and a, b, c were **all SHARED**, b included. Audit at 13:29:01.89 has b/a/c `INTERNAL→SHARED`. This is the documented behaviour for a comment visibility change. | **PASS** |
| F5 UNIQUE index (DB + API) | `F5a-*`, `F5b-db-unique-rollback.txt`, `F5c-*`, `F5d-*`, `F5e-db-after.txt` | Second comment C2 (no attachments) was created via the API. psql in a single session ran `BEGIN; INSERT INTO comment_attachments (C2, a, 0)`, which failed with **`ERROR: duplicate key value violates unique constraint "idx_comment_attachments_document"` / `Key (document_id)=(a) already exists.`**, then `ROLLBACK`. After that, a still has exactly one link row, on C1. API: PUT C2 with `[a]` returned **422 `application/problem+json`, title "Attachment not allowed"**, detail "Document a is already attached to another comment". POST of a new comment with `[a]` returned the same **422**. The comment count on the task was 2 before and 2 after, and C2's body was unchanged, so nothing partial was persisted. | **PASS** |
| F6 Portal | `F6a-*`, `F6-doc-d.*`, `F6b-*`, `F6c-portal-exchange.summary.txt`, `F6d-*`, `F6e-portal-presign-download.txt`, `F6f-db.txt` | Setup: b was PATCHed to INTERNAL again (C1 still links a, b, c), and an INTERNAL comment C3 was posted with doc d. Portal request-link returned 200 with a magicLink, and exchange returned **200** for client Acme Corp `8cc8d8e1-…`. `GET /portal/matters/{M}/comments` returned **200** with 2 comments, both SHARED in the DB. **C1 has chips `[a, c]`; b (INTERNAL) is filtered out.** C2 has `[]`. **INTERNAL C3 (with d) is absent.** Portal `presign-download`: a **200**, c **200**, b **404**, d **404**. | **PASS** |

## Notes

1. In F3, b keeps `source=COMMENT` after the direct unshare, because the stamp was already applied on the first attach. Only the visibility is under test, and it is kept.
2. In F6, the portal read-model `content` for C1 reads the latest body ("F4b: back to SHARED same set"). The earlier observation's note 3 is unchanged: comment updates do not re-sync the content. Here the content was refreshed by the F4 `comment.visibility_changed` events.

## Ids created (left in place)

| What | Id |
|---|---|
| Matter (seeded, client Acme Corp `8cc8d8e1-78bc-4669-900e-81fd20100319`) | `e120be4a-1c8b-4ed8-b993-74f1ee5de29a` |
| Task (created here) | `56344385-8956-4eeb-99f5-35b52e80c377` |
| Doc a `f-a-627H.pdf` (SHARED, on C1) | `0a1da2a2-eaca-40da-8992-e3898aaf14f8` |
| Doc b `f-b-627H.pdf` (INTERNAL, on C1) | `12597af6-92a0-4a95-b247-5080b0975148` |
| Doc c `f-c-627H.pdf` (SHARED, on C1) | `e26e743f-af0f-493e-bf45-a91c85764a55` |
| Doc d `f-d-627H.pdf` (INTERNAL, on C3) | `e1db1bb6-5d74-460e-b63b-fabd95a5f255` |
| C1 TASK SHARED `[a,b,c]` | `dac70b66-b73d-4426-bb37-60d7e3fe45ac` |
| C2 TASK SHARED `[]` | `65ccf1a8-8195-46b3-8700-43454165e3da` |
| C3 TASK INTERNAL `[d]` | `2695c0c4-e6a3-406c-b1f8-db4ca9f5d472` |
