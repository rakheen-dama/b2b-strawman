# Batch G brief: comments (634E(1)(2)(3)(5), 634C, new G89-F31)

**Branch:** `fix/g89-g-comments`, cut from fresh `origin/main` after C2 has merged.
**PR title:** `fix(comments): load all pages, PROJECT⇒SHARED on edit, portal author names, member-only access-filtered recipients, @-mention autocomplete (634E, 634C, G89-F31)`

**Authorisation.** Founder D1/D2 cover the §4 follow-ups 634E(1)(2)(3)(5) and 634C. G89-F31 is new: the scout found it while tracing 634C. Its fix is the 634C recipient filter, so it is bundled here as the same fix. It must be reproduced first.

**Gates:**
- backend full verify (orchestrator)
- frontend gate and portal gate
- two bound reviews and CodeRabbit
- browser-observed on the Keycloak stack:
  - a matter with more than 50 comments shows the newest, and a deep link to #51+ highlights
  - a portal-authored comment shows the contact's name, not "Unknown"
  - @ autocomplete inserts a member name, and the mentioned member gets a notification after a client has already commented

`BE` = `backend/src/main/java/io/b2mash/b2b/b2bstrawman`. Lines are from scout reads at `e0a44ba72`.

## 634E(1) + 634C: the list loads the oldest 50 only

This is worse than first filed: once a matter has more than 50 comments, the **newest comments disappear**, including ones just posted.

**Cause.**
- Backend: `CommentController.listComments:54-65` takes `page=0` and `size=50` by default, returns a plain `List`, and sorts oldest first (`CommentRepository:14-36`).
- Frontend: `fetchComments` (`frontend/lib/actions/comments.ts:37-43`) → `listMatterComments` (`frontend/lib/api/matters.ts:371-376`) passes neither page nor size.
- A single `CommentSectionClient` serves the matter tab, the task sheet and the documents panel.

**Fix (option A, frontend only):**
- Add `page` to `ListMatterCommentsParams` (`matters.ts:117-121`).
- `fetchComments` requests `size=100` pages until a page comes back short, with a hard cap of 10 pages.
- Concatenate the pages and de-duplicate by id.
- The deep-link highlight (`comment-section-client.tsx:33,81`, `comment-item.tsx:44-61`) then works unchanged.
- Delete the dead `components/comments/comment-section.tsx` (it is never imported).
- Add a vitest: 150 comments across 2 pages → all rendered; target #120 highlighted.
- Update `__tests__/lib/api/matters.test.ts:170` and `matter-client-path-callers.test.ts:64`.

## 634E(2): updateComment lacks the PROJECT⇒SHARED guard

**Cause.** `CS.updateComment` (around `:285-436`) only checks `INTERNAL|SHARED` (`:316-319`). Create rejects a PROJECT comment unless it is SHARED (`:187-197`).

**Fix.** After the load (around `:299`), apply the same guard to `comment.getEntityType()`. Reuse one private helper for both create and update.

**Test (reproduce first).** `CommentServiceIntegrationTest.updateMatterComment_toInternal_rejected400`, sent as a PUT through the controller.

## 634E(3): duplicated `visibilityLocked`

**Where.** `add-comment-form.tsx:42` and `edit-comment-dialog.tsx:74`.

**Fix.** Extract `isVisibilityLocked(entityType)` (or a constant) into `frontend/lib/comments.ts` (or the nearest existing module), and use it in both places.

## 634E(5): portal-authored comments show "Unknown"

**Cause.** Portal comments store the contact id in `author_member_id`, with `source='PORTAL'` (`PortalCommentService:119-121`). `CS.resolveAuthors` (`:716-734`) only reads `memberRepository`, so the frontend shows "Unknown" (`comment-item.tsx:55`).

**Fix.**
- Split the ids by `source`.
- Resolve PORTAL ids with `portalContactRepository.findAllById` → `AuthorInfo(displayName, null)`. Precedent: `activity/ActivityService.java:105-133`.
- Any remaining id (the customer-id fallback, `PortalCommentController:63-68`) resolves through the customer name, else "Client".

**Test (reproduce first).** `portalAuthoredComment_listedWithContactName`.

## G89-F31 (new, reproduce first): notifications may break after a client comments

This also covers the 634C "recipients not access-filtered" item.

**Hypothesis** (scout, code-read only):
- `NS.handleCommentCreated` (`notification/NotificationService.java:270-334`) adds prior commenters via `findDistinctAuthorsByEntity` (`:302-306`).
- Those can include portal contact ids, but `notifications.recipient_member_id` has a foreign key to `members` (`V1__baseline.sql:1055`).
- The handler runs in one REQUIRES_NEW transaction and the caller catches and logs errors.
- So once a client has commented on a matter, **every later comment there may produce no notifications at all, mentions included.**
- Separately, assignee, uploader and prior-commenter recipients are not filtered by matter access; only mentions are (`:895-897`, `canViewMatter :922-926`).

**Reproduce first.** `CommentNotificationRecipientsIntegrationTest`:
- `afterPortalComment_memberComment_stillNotifiesMentionedMember`. Expected red on main. **If it is green, stop and report:** the hypothesis is wrong; drop the F31 part and keep only the access filter.
- `priorCommenterWithoutMatterAccess_notNotified`
- `assigneeWithoutMatterAccess_notNotified` (if reachable)

**Fix.** One step after `:313` that keeps only ids that are existing members **and** pass `canViewMatter`. It applies to every recipient source.

## 634C: @-mention autocomplete

**Today.** No mention UI exists. Mentions are parsed in the backend from `@First Last` or `@First` (`NS.resolveMentionedMemberIds :855-920`, `MENTION_PATTERN :835-836`).

**Fix (frontend only):**
- `CommentSectionClient` fetches candidates once with SWR: matter members (`fetchProjectMembers`) ∪ org owners/admins (`fetchOrgMembers`, filtered by `orgRole`). Both actions are in `app/(app)/org/[slug]/matters/[id]/member-actions.ts:18-24`.
- Pass the candidates to `AddCommentForm` (Textarea `:106`) and `EditCommentDialog` (Textarea `:181`).
- When `/(^|[^\w.+-])@(\w*)$/` matches the text before the cursor, open a `Popover` + `Command` list filtered by that prefix. Pattern: `components/tasks/assignee-selector.tsx`.
- Selecting a candidate replaces the token with `@First Last `.
- Make it keyboard accessible: arrow keys, Enter, Escape.
- Add a vitest that types `@Bo`, picks "Bob Ndlovu", and checks the textarea reads `@Bob Ndlovu `.
- **Do not** add a mention extension to Tiptap.

## Out of scope: file as new ids in `gaps.md` (Batch F), do not fix

- **G89-F32:** comment edits never reach the portal. No listener exists for `CommentUpdatedEvent`.
- **G89-F33:** SHARED task and document comments appear in the portal matter thread (`PEH.onCommentCreated :567` does not check entity type).
- **G89-F34:** portal optimistic append is at the bottom while the list is newest first, so a new comment jumps.
- **G89-F35:** `GET /api/matters/{id}/members` has no access check (`ProjectMemberController:30-37`). It needs verifying before it is filed.

## Process

As in `batch-B-brief.md` "Process":
- check `pwd`
- commit each red test before its fix
- targeted tests: `comment/**`, `notification/**`, `customerbackend/**`, `activity/**`, `architecture/**`, plus importers
- run the frontend and portal gates
- open the PR; no full verify, no merge
- report the PR number, head sha, counts, test changes and judgment calls
