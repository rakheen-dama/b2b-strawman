# 626A: re-observation of fix commit 99420af96 (after-fix)

Captured 2026-10-27, 19:22–19:25 UTC, on the E2E mock-auth stack (:8081 backend). Tenant `tenant_7d218705360b`, org `e2e-test-org`. The re-observer changed no product code and made no commits. Maven and `backend/target` were not touched. All SQL was read-only SELECTs, and every write went through the API.

## Stack
- Only the e2e backend was rebuilt: `SHELL=/bin/bash docker compose -p docteams-e2e -f compose/docker-compose.e2e.yml up -d --build --no-deps backend`, run from the worktree at **99420af9619354a88a7c2c73b99926b51e60be83** with `backend/src` clean. The Dockerfile compiles `src` inside the container, and `.dockerignore` excludes `target`.
- New image: **sha256:21fe1acb47ec7e5ee699ba4b21f60645f19b6628d99421a279e6b6eb8a27a32a**. The previous image was `sha256:618268dc9a9d…`, built from 03ae9c289. Health was UP about 15 s after the container started. See `00-e2e-backend-image.txt` and `00-docker-build.log`.
- Auth: mock-IDP `POST :8090/token` with `user_e2e_alice`/owner and `user_e2e_carol`/member (same recipe as `compose/seed/lib/common.sh`).
- Base payload: the current filled letterhead from `GET /api/settings/letterhead` (`01a`), with each field changed per check. The final payload is `04-letterhead-payload.json`, which is the same data with email `info@ndlovu-attorneys.co.za`.
- Each `.txt` request file holds the request body echo followed by the response. When grepping, use the part after `--- response:`, because the request echo comes first.

## Results

| # | Check | Result | Evidence |
|---|---|---|---|
| 0 | Image built from 99420af96: `sha256:21fe1acb47ec…`. The build log shows `COPY src src` followed by `mvnw clean package`. | **PASS** | `00-e2e-backend-image.txt`, `00-docker-build.log` |
| 1 | Alice PATCH with all fields filled and `"email": "   "` → 200. The response shows `letterhead.email: null`, GET shows `"email":null`, and the DB shows `letterhead_email IS NULL = t`. | **PASS** | `01a-…`, `01c-alice-patch-email-blank.txt`, `01d-…`, `01e-db-after-email-blank.txt` |
| 2 | `"email": " info@ndlovu-attorneys.co.za "` → 200. The response and GET both show `"info@ndlovu-attorneys.co.za"`. The DB value is `[info@ndlovu-attorneys.co.za]` with length 27, so no spaces were stored. | **PASS** | `02a-…`, `02b-…`, `02c-db-after-email-padded.txt` |
| 3 | `"email": "not-an-email"` → 400 with `fieldErrors[email] "email must be a valid email"`. `bankBranchCode` = 3 spaces + 20 digits + 3 spaces (26 raw, 20 trimmed) → 200, and the DB stores `[12345678901234567890]` with length 20. `bankBranchCode` of 21 digits → 400 with `"bankBranchCode must be at most 20 characters"`. The DB was unchanged after that 400 (same `updated_at`). | **PASS** | `03a-db-before.txt`, `03b-…`, `03c-…`, `03d-…`, `03e-…`, `03f-db-after-400s.txt` |
| 4 | PATCH #1 with the final payload restored `bankBranchCode` and wrote 1 audit row (`41b0befc…`, changed_fields `[bankBranchCode]`, 9 rows total). PATCH #2 had the same values with padding (`signatoryName: "   Thandiwe Khumalo  "`, `city: " Sandton\t"`) → 200. PATCH #3 was byte-identical → 200. The audit event ids are identical before and after (9 rows → 9 rows). The backend log shows `changedFields=[]` for both. | **PASS** | `04a-…`, `04b-audit-after-first.txt`, `04c-…`, `04d-…`, `04e-…`, `04f-audit-id-diff.txt`, `04g-db-after.txt`, `04h-backend-log-letterhead.txt` |
| 5 | Carol (member) PATCH → 403 `"Insufficient permissions for this operation"`. The DB row is identical before and after (diff empty). | **PASS** | `05a-…`, `05b-carol-patch-letterhead.txt`, `05c-…` |
| 6 | Final Alice PATCH → 200. GET equals the payload (`jq -S` diff empty) with 0 null fields. The DB row has all 16 columns filled (`num_nulls = 0`), including banking (FNB / 250655 / 62812345678 / Cheque), signatory Thandiwe Khumalo / Director, and email `info@ndlovu-attorneys.co.za`. The state is left filled for 626B/626C. | **PASS** | `06a-…`, `06b-alice-final-get.txt`, `06c-db-final.txt`, `06d-audit-final.txt` |

## Notes
- Audit trail for this run: 4 new `org_settings.letterhead_updated` rows (5 → 9), one for each changing PATCH: email cleared, email set, branch code set to 20 digits, branch code restored. The 400s, the 403 and the three no-op PATCHes (#2, #3 and the final restore) wrote none.
- A no-op PATCH writes no audit row but still bumps `org_settings.updated_at` (19:23:29.687 → .853 → .908, see `04g`). This does not violate any check and is recorded only for completeness.
- In `04c`, the request-body echo shows a raw TAB because zsh `echo` expanded the JSON `\t`. The bytes curl actually sent were valid JSON with a `\t` escape. A note about this is appended to the file.
- Presigned `logoUrl` query secrets in captured responses are redacted (`X-Amz-*=REDACTED`).

## Files
`00-docker-build.log`, `00-e2e-backend-image.txt`, `01a-alice-get-letterhead-before.txt`, `01b-audit-before.txt`, `01c-alice-patch-email-blank.txt`, `01d-alice-get-after-email-blank.txt`, `01e-db-after-email-blank.txt`, `02a-alice-patch-email-padded.txt`, `02b-alice-get-after-email-padded.txt`, `02c-db-after-email-padded.txt`, `03a-db-before.txt`, `03b-alice-patch-bad-email.txt`, `03c-alice-patch-branchcode-20-padded.txt`, `03d-db-after-branchcode-20-padded.txt`, `03e-alice-patch-branchcode-21.txt`, `03f-db-after-400s.txt`, `04-letterhead-payload.json`, `04a-alice-patch-first.txt`, `04b-audit-after-first.txt`, `04c-alice-patch-second-padded.txt`, `04d-alice-patch-third-identical.txt`, `04e-audit-after-second-third.txt`, `04f-audit-id-diff.txt`, `04g-db-after.txt`, `04h-backend-log-letterhead.txt`, `05a-db-before-carol.txt`, `05b-carol-patch-letterhead.txt`, `05c-db-after-carol.txt`, `06a-alice-final-patch-full.txt`, `06b-alice-final-get.txt`, `06c-db-final.txt`, `06d-audit-final.txt`
