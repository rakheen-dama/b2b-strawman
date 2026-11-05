# 634C — AFTER (post-fix) observation

Observed 2026-11-05 12:40–12:47 UTC by a QA observer agent using Playwright MCP (Chromium). No source was modified. No rebuild, reseed or teardown. All data was created through the UI: task, document uploads, comments. SQL was read-only.

## Stack / build

| Item | Value |
|------|-------|
| Stack | E2E mock-auth (frontend :3001, backend :8081, postgres :5433) |
| `docker inspect e2e-backend --format '{{.Created}}'` | `2026-11-05T12:37:59.782220804Z` (running, healthy) |
| `docker inspect e2e-frontend --format '{{.Created}}'` | `2026-11-05T12:37:59.873802179Z` (running, healthy) |
| Built from | PR #1735 branch `phase89/05C-notification-cluster` at `a8d5b86ae` ("fix(634C): address review findings — ring re-arm, documentId sync, sheet on empty list, mention pre-filter") |
| Tenant schema | `tenant_7d218705360b`, org `e2e-test-org` |
| Matter A (Carol is a member) | Task Test Project 8d2y `da7b62e0-2956-4c2f-940d-dc5d476efde3` (Internal Matter, no client) |
| Matter B (Carol NOT a member) | Website Redesign `67ad624d-ba2f-4b55-a258-0d0bfed72bc4` (Acme Corp) |

## DB facts (read-only SELECTs)

```
members ⨝ org_roles
 Alice Owner  afe09cd4-431b-4ee1-ba3a-ae2da62a9577  Owner
 Bob Admin    33e757a8-ebda-4a67-a5fc-40eac44b1ac9  Admin
 Carol Member 78e577dc-ece1-4399-91b7-18c705277de4  Member

project_members (matter A)       project_members (matter B, Website Redesign)
 Alice Owner  | lead              Alice Owner | lead     <- only member; Carol and Bob are not on it
 Carol Member | member

comments created during this walk (all through the UI as Alice)
 0f9e70d1-aa1e-4905-b847-bcf8e79b0cf3 TASK     215397bf-… (Companion Task 8d2y, A) "@Carol Member please review 634C-1440"
 5d83de0f-cd82-4155-b278-8e4165b94eed DOCUMENT 3ae11786-… (634C-doc-1440.txt, A)  "@Carol Member doc 634C-1440"
 58e330f2-04e5-4d44-8cb1-ec356c2f0870 TASK     37654681-… (C2 Mention Check 634C, B) "@Carol Member and @Bob Admin C2 check 634C-1440"
 68120c9e-8434-432f-9da3-4be4f526303d PROJECT  67ad624d-… (B, SHARED)  "@Bob Admin and @Carol Member matter-level 634C-1440"
 e9e6a601-4a4e-4771-867f-bba9287b7078 TASK     215397bf-… (Companion Task 8d2y, A) "@Carol Member please review 634C-1024"
 7d8787f4-377f-4d9f-8abb-0319bcaf733f DOCUMENT a469c581-… (634C-doc-1024.txt, A)  "@Carol Member doc 634C-1024"
 e9117d63-f5f2-4504-ac47-2157857dc84d TASK     37654681-… (C2 Mention Check 634C, B) "@Carol Member and @Bob Admin C2 check 634C-1024"

notifications (COMMENT_ADDED, this walk), every row carries reference_comment_id
 Carol Member | commented on task "Companion Task 8d2y"    | 0f9e70d1-…
 Carol Member | commented on document "634C-doc-1440.txt" | 5d83de0f-…
 Bob Admin    | commented on task "C2 Mention Check 634C" | 58e330f2-…
 Bob Admin    | commented on matter                       | 68120c9e-…
 Carol Member | commented on task "Companion Task 8d2y"    | e9e6a601-…
 Carol Member | commented on document "634C-doc-1024.txt" | 7d8787f4-…
 Bob Admin    | commented on task "C2 Mention Check 634C" | e9117d63-…

C2 query: notifications whose reference_comment_id is a comment on matter B (Website Redesign)
 33e757a8-… | Bob Admin | COMMENT_ADDED | Alice Owner commented on task "C2 Mention Check 634C" | e9117d63-…
 33e757a8-… | Bob Admin | COMMENT_ADDED | Alice Owner commented on matter                       | 68120c9e-…
 33e757a8-… | Bob Admin | COMMENT_ADDED | Alice Owner commented on task "C2 Mention Check 634C" | 58e330f2-…
 (3 rows: Bob only, zero rows for Carol)
```

After the walk, every clicked notification has `is_read = t`.

Mention autocomplete: the comment box has **no** `@` autocomplete. Typing `@Car` showed no popup. Mentions are plain text resolved server-side (`NotificationService.MENTION_PATTERN`: `@First` or `@First Last`, case-insensitive). The display names are `Carol Member` and `Bob Admin`, and that is how they were typed.

## Results per gap

### G88-M6: Comment-notification deep link

#### Scenario 1, TASK comment: PASS at 1440 and 1024

Path: logged in as Carol → bell popover → click "Alice Owner commented on task "Companion Task 8d2y"".

| Check | 1440×900 | 1024×768 |
|---|---|---|
| Final URL | `/org/e2e-test-org/matters/da7b62e0-2956-4c2f-940d-dc5d476efde3?tab=tasks&taskId=215397bf-6ab5-4646-a0bc-ad08aadd08b0&commentId=0f9e70d1-aa1e-4905-b847-bcf8e79b0cf3` | `…?tab=tasks&taskId=215397bf-6ab5-4646-a0bc-ad08aadd08b0&commentId=e9e6a601-4a4e-4771-867f-bba9287b7078` |
| Popover closed | yes. The only `[role=dialog]` after the click is "Task Detail" and the bell has `aria-expanded="false"` | yes. The only dialog is "Task Detail" |
| Sheet open, active tab | Task Detail, **Comments** | Task Detail, **Comments** |
| URL changed / comment rendered | 37 ms / 834 ms after click | 31 ms / 828 ms |
| `#comment-<c>` rect vs viewport | top 638, bottom 682, innerHeight 900, so in view | top 506, bottom 550, innerHeight 768, so in view |
| Ring right after click (t≈0.84 s) | `flex gap-3 rounded-md transition-shadow ring-2 ring-teal-500 ring-offset-2 dark:ring-offset-slate-950` | same |
| Ring at t≈4.1 s | `flex gap-3 rounded-md transition-shadow` (ring gone) | same (ring gone) |
| Overflow `scrollWidth === clientWidth` | 1440 === 1440 | 1024 === 1024. The sheet itself is 575/575 |

- At 1024 the task has 2 comments (the 1440 one and the 1024 one). Only the **targeted** comment (634C-1024) gets the ring, so the highlight follows `commentId`. The sheet scrolled down past Custom Fields and Sub-items to bring the comment into view at both widths.
- Extra test of the review-fix "ring re-arm" (1024): with the sheet open on the same task, I pressed Escape, opened the bell and clicked the **older** notification (comment `0f9e70d1`). The URL switched to `commentId=0f9e70d1-…`, the sheet re-opened on Comments, and that comment had `ring-2` (top 450 of 768) 358 ms after the click. The ring re-arms when you navigate between comments on the same task.
- Screenshots: `after-1440-01-task-comment-deeplink.png` (ring visible), `after-1440-02-task-comment-ring-gone.png`, `after-1024-01-task-comment-deeplink.png` (ring visible), `after-1024-02-task-comment-ring-gone.png`

#### Scenario 2, DOCUMENT comment: PASS at 1440 and 1024

Document upload works on this stack. As Alice I uploaded `634C-doc-1440.txt` and `634C-doc-1024.txt` through the Work › Documents dropzone (status Uploaded), expanded the row and posted the comment. Then as Carol I clicked "Alice Owner commented on document …" in the bell.

| Check | 1440×900 | 1024×768 |
|---|---|---|
| Final URL | `…/matters/da7b62e0-…?tab=documents&documentId=3ae11786-4e07-4226-963d-02a0dd251cea&commentId=5d83de0f-cd82-4155-b278-8e4165b94eed` | `…?tab=documents&documentId=a469c581-99cb-46e1-8ea8-cc3c4eca11b6&commentId=7d8787f4-377f-4d9f-8abb-0319bcaf733f` |
| Tab | Work · Documents | Work · Documents |
| Row expanded | "Collapse comments for 634C-doc-1440.txt" `aria-expanded=true` | 634C-doc-1024.txt `aria-expanded=true`. The other row (634C-doc-1440.txt) stays `false`, so only the target row expands |
| Popover closed | yes (0 dialogs) | yes (0 dialogs) |
| Comment rect | top 441, bottom 485 / 900 | top 362, bottom 406 / 768 |
| Ring at ~0.82 s → ~4.08 s | `ring-2 ring-teal-500 …` → gone | `ring-2 ring-teal-500 …` → gone |
| Overflow | 1440 === 1440 | 1024 === 1024 |

- Screenshots: `after-1440-03-document-comment-deeplink.png`, `after-1024-03-document-comment-deeplink.png`

#### Scenario 3, PROJECT (matter-level) comment link shape: OBSERVED (landing on Overview is expected until 634E) at 1440 and 1024

- The only UI surface that creates a PROJECT comment is **Client › Client comments** (`?tab=customer-comments`). It is rendered only when the matter has a client, so it does not exist on matter A (Internal Matter). I used Website Redesign (B). Because of C2, Carol is never notified there, so the recipient I observed was **Bob** (Admin), mentioned in the same comment. The comment was posted with `visibility = SHARED` (the Client-comments box is the client thread).
- Bob's notification: "Alice Owner commented on matter", `reference_comment_id = 68120c9e-…`.
- Clicking it from the notifications page gave the single navigation `…/matters/67ad624d-ba2f-4b55-a258-0d0bfed72bc4?tab=comments&commentId=68120c9e-8434-432f-9da3-4be4f526303d` at both widths. The active tab was **Overview**, as expected until 634E ships the Comments tab. No overflow (1440/1440).
- Screenshots: `after-1440-08-bob-project-comment-landing.png`, `after-1024-08-bob-project-comment-landing.png`

### C2: `@mention` filtered by matter access: PASS at 1440 and 1024

- Matter B (Website Redesign) has only Alice in `project_members` (see DB facts). As Alice, on the UI-created task "C2 Mention Check 634C" (`37654681-2eff-4f45-9f28-5c6bc428aeef`), I posted `@Carol Member and @Bob Admin C2 check 634C-1440` and later `…634C-1024`. On the matter's Client comments I posted `@Bob Admin and @Carol Member matter-level 634C-1440`.
- DB: all 3 resulting notifications go to **Bob**. Carol has **0** rows for any comment on matter B.
- Browser, Carol:
  - Bell popover at 1440 and 1024: entries are the doc/task comments on matter A, uploads, the assignment and "added to matter". There is no "C2 Mention Check" and no "commented on matter" entry.
  - `/notifications` page at 1440 and 1024: same, and `hasC2 = false` on the page text. Carol's page shows "Load more" at 1024, but the C2 comments are the newest events, so they would be at the top if they existed.
- Browser, Bob: `/notifications` shows "commented on task "C2 Mention Check 634C"" (×2) and "commented on matter".
  - Clicking the task one at 1440 → `…/matters/67ad624d-…?tab=tasks&taskId=37654681-2eff-4f45-9f28-5c6bc428aeef&commentId=58e330f2-04e5-4d44-8cb1-ec356c2f0870`. Task Detail opened on Comments, the comment is in view (top 568/900), ring present at 0.85 s and gone at 4.1 s. Bob is Admin and can view the matter.
  - At 1024 Bob clicked the newest one (`e9117d63-…`): URL matched `commentId=`, Task Detail opened on Comments, the comment is in view (top 436/768), ring present at 0.84 s and gone at 4.09 s, overflow 1024/1024. I did not capture the exact URL string for this click; the `#comment-e9117d63-…` element rendering in the sheet proves the target.
- This is also the deep-link path from the full `/notifications` page, not only the bell.
- Screenshots: `after-1440-04-carol-bell-no-c2-entry.png`, `after-1440-05-carol-notifications-page-no-c2.png`, `after-1440-06-bob-notifications-page-c2.png`, `after-1440-07-bob-c2-task-deeplink.png`, `after-1024-04-carol-bell-no-c2-entry.png`, `after-1024-05-carol-notifications-page-no-c2.png`, `after-1024-06-bob-notifications-page-c2.png`, `after-1024-07-bob-c2-task-deeplink.png`

### Scenario 5, overflow: PASS

`document.documentElement.scrollWidth === clientWidth` on every sheet, documents and matter view: 1440/1440 and 1024/1024. The Task Detail sheet at 1024 is 575/575.

## Console / logs

- `browser_console_messages` (level warning, `all: true`), checked after the Alice session, the Carol session and the Bob session at both widths: **0 errors, 0 warnings**.
- `docker logs e2e-backend --since 12:40Z | grep ERROR|WARN|Exception`: 6 × WARN `Forbidden: path=/api/matters/da7b62e0-…/time-summary/by-member … restricted to matter leads, org admins, and org owners` for `user_e2e_carol`. This fires every time Carol loads matter A, a background fetch whose 403 is handled. The gate is intentional (Carol is a plain member) and unrelated to 634C. It is log noise, not a user-visible error.
- `docker logs e2e-frontend --since 12:40Z`: one `⨯ Error: The destination stream closed early.` at 12:45:37.15Z. That is the same instant my script posted the 1024 doc comment and **immediately** called `page.goto(...)` to another matter. The aborted RSC stream was most likely caused by that navigation, so it is probably an observer artifact. It did not reproduce on any other step.

## Cosmetic / other notes (not FAILs)

- No `@`-mention autocomplete in the comment composer. You have to know the member's display name; `@Carol` alone also resolves via the first-name rule. This is pre-existing and not part of 634C, but it is a UX gap for C2 (users get no hint that a mentioned non-member was skipped).
- At 1024 the newly posted 1024 task comment showed "just now" about 1.5 min after posting, while the doc comment posted about 20 s later showed "1 minute ago". This is a minor relative-time inconsistency and was not investigated.
- When a matter-level comment mentions a non-member (Carol), the author gets no feedback that Carol was not notified. This works as designed for C2 and is only noted.

## Verdict summary

| Gap / scenario | 1440×900 | 1024×768 |
|---|---|---|
| G88-M6 TASK comment deep link | PASS | PASS (plus the ring re-arm check) |
| G88-M6 DOCUMENT comment deep link | PASS | PASS |
| G88-M6 PROJECT comment link shape | OBSERVED: `?tab=comments&commentId=` → Overview (expected until 634E) | same |
| C2 mention filtered by matter access | PASS (DB + bell + /notifications) | PASS |
| Overflow | PASS | PASS |

## Housekeeping

- The upload fixtures `634C-doc-1440.txt` and `634C-doc-1024.txt` are kept here. During the walk they were temporarily copied into `b2b-strawman/.playwright-mcp/`, because the upload tool only allows the repo root (that directory is gitignored). Those copies were deleted.
- Screenshots were written to `b2b-strawman/.playwright-mcp/` and moved here with `mv`. This session's 24 snapshot `.yml` files were removed. The main checkout `git status` is unchanged (only the pre-existing `.claude/markers/*`, `portal/next-env.d.ts` and `tasks/insights/latest.stats.json` modifications).
- Data left on the stack, all created through the UI: task "C2 Mention Check 634C" on Website Redesign, 2 uploaded documents on Task Test Project 8d2y, and 7 comments (one is a SHARED client-thread comment on Website Redesign).
