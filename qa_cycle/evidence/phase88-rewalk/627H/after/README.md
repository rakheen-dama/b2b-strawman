# 627H AFTER: comment attachments backend (PR #1699 @ `3f5380e8e`)

Recorded 2026-10-28 13:06–13:12 UTC against the e2e mock-auth stack. Backend :8081, mock IDP :8090, `e2e-postgres` :5433,
LocalStack :4567. Tenant schema `tenant_7d218705360b`, org `e2e-test-org`.

- **Build:** `e2e-backend` was rebuilt from `$WT` @ `3f5380e8e` with `compose/scripts/e2e-rebuild.sh backend`. Maven runs only inside the Docker builder stage, and `backend/target` is `.dockerignore`d. The container was created at 13:06:05Z and health was `UP`.
- **Migration:** tenant `flyway_schema_history` has **V9 `phase88 comment attachments`** applied at 13:06:25Z (success = t). `\d comment_attachments` shows PK `(comment_id, document_id)`, both FKs `ON DELETE CASCADE`, and a non-unique index on `document_id`. See `00-preflight.txt`.
- **Side effect:** the rebuild also recreated `e2e-localstack`. Objects uploaded before 13:06Z (for example the before-doc `ad27ffcc-…`) may no longer be in S3. The DB rows are unaffected, and no observation here downloads them.
- **Tokens:** minted from the mock IDP (`POST :8090/token {"userId":"user_e2e_<alice|bob|carol>","orgSlug":"e2e-test-org"}`). The portal token came from `POST /portal/auth/request-link`, whose e2e response returns `magicLink`, followed by `POST /portal/auth/exchange`. **No token values are recorded.** In the `*.upload-init.txt` files the presigned URL credential and signature are redacted.
- **No product code was changed.**

File naming: `NN-name.request.json` / `.status.txt` (HTTP code + content-type) / `.response.json`; `NN-*db*.txt` = psql output.

## Results

| O | Files | Observed | Verdict |
|---|---|---|---|
| O1 SHARED comment + fresh doc | `01-doc1.*`, `01-post-shared-comment.*`, `01-db.txt` | Presign upload: init 201, PUT 200, confirm 200 (`status=UPLOADED`, `visibility=INTERNAL`, `source=MANUAL`). Then `POST /api/matters/{M}/comments` TASK SHARED with `[D1]` returned **201**, `attachments:[{documentId,fileName:"o1-shared-attach-627H.pdf",contentType:"application/pdf",size:83}]`. DB: link row `(C1,D1,sort_order 0)`. Document is now **SHARED / COMMENT**. Audit has `document.visibility_changed {INTERNAL→SHARED}` and `comment.created … attachment_count:1`. | **PASS** |
| O2 INTERNAL → SHARED → INTERNAL | `02-doc2.*`, `02a..02c-*`, `02d-db-audit.txt` | POST INTERNAL with `[D2]` returned 201. D2 = INTERNAL/COMMENT. PUT `visibility:SHARED` (ids omitted) returned 200, attachments kept, D2 = **SHARED**. PUT `visibility:INTERNAL` returned 200, D2 = **INTERNAL**. The audit has two `document.visibility_changed` rows, `{INTERNAL→SHARED}` then `{SHARED→INTERNAL}`. | **PASS** |
| O3 failures | `03a..03h-*`, `03-db-before.txt`, `03f-db.txt` | Every case returned **422 `application/problem+json`, title "Attachment not allowed"**. Details: cross-matter doc `e7746434-…` gave "Document <id> is not a document on this matter". A random UUID gave **the same template**; only the id differs. 6 distinct real docs gave "A comment can have at most 5 attachments (got 6)". D1, already on C1, gave "… is already attached to another comment" on POST, and the same detail on a **PUT** to another comment (03h). SHARED D3 on an INTERNAL comment gave "… is shared with the client; attach it to a shared comment or unshare it first". Extra case: a PENDING doc gave "… has not finished uploading". The comment count on the matter was 3 before and 3 after, so no partial comments were persisted. `[D4,D4]` returned 201 with **1** link row. | **PASS** |
| O4 update semantics | `04a..04e-*` | C3 = `[D4]`. PUT with ids omitted kept `[D4]`. PUT `visibility:SHARED` with ids omitted kept `[D4]` and made D4 SHARED. PUT `[D5,D6]` replaced the set: links are D5→0, D6→1, removed **D4 still exists and stays SHARED/COMMENT (unchanged)**, and D5/D6 became SHARED/COMMENT. PUT `[D6,D5]` reordered the response. PUT `[]` returned `attachments:[]` with 0 link rows, and D4/D5/D6 rows were intact and unchanged. | **PASS** |
| O5 Carol (member) | `05-*`, `05a..05f-*` | Carol is not on matter `9cf2159e`: POST there returned **404 "Project not found"**, the existing view-access rule, before any attachment check (05e). On `a0f5fc91` (Carol = project `member`, not lead): attaching Alice's upload DA gave **422 "Only the uploader or a matter lead can attach document …"**, both on a DOCUMENT/INTERNAL comment and on a PROJECT/SHARED comment. Her own upload DC on a DOCUMENT/INTERNAL comment returned **201**, DC = INTERNAL/COMMENT. Her own upload DC2 on a **PROJECT (matter-level, always SHARED) comment** returned **201**, DC2 = **SHARED**/COMMENT. Control: Carol's direct `PATCH /api/documents/{DC}/visibility SHARED` returned **403** (PROJECT_MANAGEMENT). See the note below. | **PASS** (against the stated claims), with a note |
| O6 delete comment | `06a..06c-*` | `DELETE /api/matters/{M}/comments/{C2}` returned **204**. Comment rows for C2 = 0 and link rows = 0. Document D2 is intact (UPLOADED / INTERNAL / COMMENT) and appears in the `GET` documents list with `source:"COMMENT"` (07). | **PASS** |
| O7 documents list `source` | `07-*` | `GET /api/matters/{M}/documents` returned 200, 9 items, and **every item has `source`**. Values are MANUAL for the untouched docs (including PENDING and the PATCH-shared D3) and COMMENT for the attached ones. The confirm response also carries `source`. | **PASS** |
| O8 portal | `08a..08g-*` | Setup: C4 = INTERNAL TASK comment with `ad27ffcc` attached. C5 = PROJECT SHARED comment with D4, after which D4 was PATCHed to INTERNAL. Portal contact `khumalo.property.mvs2q3xe7e5d@example.com` went through request-link 200 and exchange 200 (client `ed0ff425-…` Khumalo). `GET /portal/matters/{M}/comments` returned **200** with 3 comments, all SHARED: C1 has `attachments:[D1 chip]`, C3 has `[]` (cleared), C5 has `[]` (doc no longer portal-visible, so it is filtered out). **INTERNAL C4 (with attachment) and the INTERNAL reproducer comment `870fba72` are absent.** Portal `presign-download`: D1 returned **200**, D4 returned **404**. | **PASS** |
| O9 regression | `09-*` | `GET /api/matters/{M}/comments?entityType=TASK&entityId={T}` returned 200, and every comment has the `attachments` key. The reproducer's `870fba72-…` (INTERNAL) lists with **`attachments: []`**. | **PASS** |

## Notes (for the orchestrator, not FAILs against the PR's claims)

1. **A member can make their own upload client-visible through a matter-level comment.** PROJECT-level comments are forced SHARED, and the pre-existing rule lets any matter member post them. The new attach rule (uploader **or** edit access) then sets the document SHARED. Observed: Carol has no PROJECT_MANAGEMENT. A direct visibility PATCH gives her 403, but attaching DC2 to a matter-level comment flipped it to SHARED. This matches the arch §13.3.22 text ("uploaded by the author (or the author has edit access) … SHARED"). It is still a new path around the document-visibility capability gate. Product/RBAC call. Matter `a0f5fc91` has no client, so nothing actually reached a portal here.
2. **Removed attachments stay client-visible.** Per the claim ("removed documents unchanged"): after `[D4]` was replaced/cleared on a SHARED comment, D4/D5/D6 remain SHARED with `source=COMMENT` and no comment link. They stay portal-visible as standalone documents. As designed, worth a conscious sign-off.
3. **Pre-existing, unrelated to this PR:** the portal read-model `content` for C3 still reads "body edited, ids omitted" (the body when it went SHARED), not the current "cleared". `PortalEventHandler` listens for comment created / visibility-changed / deleted, but not `CommentUpdatedEvent`. The PR does not touch `customerbackend/handler`.
4. "Missing and cross-matter share one detail": the template is identical ("Document <id> is not a document on this matter", `documentId` property set). Only the echoed id differs, so there is no existence oracle.

## Ids created (left in place)

| What | Id |
|---|---|
| Matter (existing, client Khumalo `ed0ff425-…`) | `9cf2159e-213a-44d7-b7c8-ef184544ddac` |
| Task (existing) | `f153f432-4a0b-4098-bbc8-f44b49cd0738` |
| Matter (existing, Carol = member) "Ndlovu v RAF nknb" | `a0f5fc91-7bb3-4c46-b89d-e95813ae0f33` |
| D1 `o1-shared-attach-627H.pdf` (SHARED, on C1) | `01e3174c-cb62-47fe-82b4-34800153b44c` |
| D2 `o2-internal-attach-627H.pdf` (INTERNAL, comment deleted) | `f81ca6e7-9804-458a-96c3-a07b893c9544` |
| D3 `o3-doc3-627H.pdf` (PATCH-shared, MANUAL, unattached) | `6c0fe675-9a75-4851-bcb2-149511fe3209` |
| D4 `o3-doc4-627H.pdf` (on C5, PATCHed INTERNAL) | `fccd0c3f-6202-480a-a28c-dc0977b0fbf0` |
| D5 / D6 (SHARED, unattached after clear) | `9a84d270-5bc7-495d-8ce7-fb888834a35f` / `8eb81163-022a-4d80-bc79-80e6f4305e10` |
| PENDING doc (never PUT/confirmed) | `e5d13940-e7dd-4474-a0ae-e422faed0e67` |
| DA (Alice, on `a0f5fc91`) / DC / DC2 (Carol, on `a0f5fc91`) | `14dadb73-386e-46d6-8b35-842c2f13b450` / `1c47b8c4-649b-436b-8ec4-b03a8e941270` / `1b1abb3f-8b65-4944-a0db-798cc026687f` |
| C1 TASK SHARED `[D1]` | `303b7d5a-3da8-46a0-9fea-a30d93bcb0b1` |
| C2 TASK INTERNAL `[D2]`, **deleted** in O6 | `22810258-a949-45e3-b5e4-39eb132f887d` |
| C3 TASK, now SHARED `[]` | `d574e017-8c79-4613-827d-804641543c6e` |
| C4 TASK INTERNAL `[ad27ffcc]` | `47ec1062-20b6-4adf-b613-43fa5e4a3bc9` |
| C5 PROJECT SHARED `[D4]` | `bb64b946-d93d-4a29-9188-f0fd91387615` |
| Carol comments on `a0f5fc91`: DOCUMENT INTERNAL `[DC]` / PROJECT SHARED `[DC2]` | `b8901fcc-c9aa-4299-8951-1716ff576a39` / `0cfa753d-ec0b-4b7a-abaf-19f098e194fd` |
