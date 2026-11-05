# Slice 634A / G88-F3: browser observation

- Date: 2026-11-05
- Observer: browser observer subagent. No source edits, commits or pushes.
- Stack: E2E mock-auth stack (frontend :3001, backend :8081), Alice (owner), org e2e-test-org, tenant schema `tenant_7d218705360b`
- PR #1733, branch `phase89/05A-field-group-membership`, head a175d28f1
- Page: /org/e2e-test-org/settings/matters?tab=custom-fields, then Matters sub-tab, then Field Groups

## Phase 1: BEFORE (stack built from main, without the fix), 1440x900. Result: REPRODUCED

1. Created group "Obs 634A Before" through Add Group with Fields = Case Number selected. Clicked Create Group. DB showed **0 members**, so the create path also dropped the selection.
2. Edit on "Obs 634A Before": the Fields picker showed "Select fields..." with nothing pre-selected. Added Court and clicked Save Changes. The dialog closed with no error. DB still showed **0 members**. The card showed no member list.
   - before-1440-01-edit-dialog-court-selected.png (dialog with Court badge before save)
   - before-1440-02-cards-after-save.png (cards: no membership info anywhere)
3. Edit on "Matter Info" (pack group, DB member = Category): the Fields picker showed "Select fields..." with **no pre-selection**. Cancelled.
   - before-1440-03-matter-info-edit-no-preselect.png
- Audit at main: `field_group.updated` for Obs at 06:37:14 with no added/removed counts and no member events.
- Conclusion: G88-F3 reproduced at main. Membership edits are silently dropped and the dialog does not show current members.

## Phase 2: Rebuild from the worktree. Result: OK

- `SHELL=/bin/bash bash compose/scripts/e2e-rebuild.sh backend frontend` was run from worktree-epic-634A (nohup + marker). It exited 0. Log: /tmp/e2e-rebuild-634A.log
- New images: backend 2026-11-05T06:44:36Z, frontend 06:38:57Z. Both containers were healthy. :8081/actuator/health and :3001 returned 200.
- Fix confirmed in the running build: group cards list member labels, which come from `fieldDefinitionIds`. The order matched DB `sort_order` exactly. Audit details now carry added/removed counts.
- `git status --short` in the worktree was clean after the rebuild. **Nothing to restore.**

## Phase 3: AFTER, 1440x900

1. The cards list member labels. SA Conveyancing shows 16 members, SA Legal 7, Matter Info "Category", and Obs "No fields in this group yet." `scrollWidth 1440 === clientWidth 1440`. Each member list has scrollWidth equal to clientWidth, so there is no overflow. Chips wrap cleanly. Screenshot: after-1440-01-cards-list-members.png. **PASS**
2. Edit on Obs: added Court and Case Number, then saved. The card lists "Court, Case Number". Screenshot: after-1440-02-card-after-add.png. **PASS**
3. Reloaded: still "Court, Case Number". Screenshot: after-1440-03-card-after-reload.png. Reopened Edit: "2 fields selected" with Court and Case Number badges pre-selected. Screenshot: after-1440-04-edit-dialog-preselected.png. **PASS**
4. Removed Court and saved. The card shows "Case Number" only. After reload it still shows only "Case Number". Screenshots: after-1440-05-card-after-remove.png, after-1440-06-card-after-remove-reload.png. **PASS**
5. Matter Info: opened Edit ("1 field selected", Category) and saved with no changes. The card still shows Category and the DB still has 1 member. Audit: `field_group.updated` with added 0 / removed 0. Screenshot: after-1440-07-matter-info-unchanged-save.png. **PASS** (no wipe)
6. Bonus, pack-seeded group "SA Legal — Matter Details" (slug legal_za_matter): opened Edit with 7 members pre-selected. Added Category and saved. After reload the card shows 8 members ending in Category (after-1440-08-pack-group-category-added-reload.png). Then removed Category and saved. After reload the card is back to the original 7 (after-1440-09-pack-group-restored-reload.png). Audit shows added 1, then removed 1. **PASS**

## Phase 3: AFTER, 1024x768

1. Cards list members. `scrollWidth 1024 === clientWidth 1024`, so there is no page-level horizontal scroll. **However, the per-card layout does NOT wrap cleanly** (see Defect D1). Screenshots: after-1024-01-cards-crop.png (crop of after-1024-01-cards-list-members-fullpage.png). **FAIL (layout)**
2. Edit on Obs: the dialog opens with "1 field selected" and the Case Number badge pre-selected (after-1024-02-edit-dialog-preselected.png). The dialog is max-h 85vh and scrolls internally, so Save stays reachable. Added Court and saved. The card lists "Case Number, Court" (after-1024-03-card-after-add.png). **PASS**
3. Reloaded: still "Case Number, Court" (after-1024-04-card-after-reload.png). **PASS**
4. Removed Case Number and saved. The card shows "Court" (after-1024-05-card-after-remove.png). After reload it still shows "Court" (after-1024-06-card-after-remove-reload.png). **PASS**
5. SA Conveyancing (16 members): opened Edit with "16 fields selected" and all 16 badges pre-selected (after-1024-07-conveyancing-edit-16-preselected.png, which only captures the visible part of the scroll area; the text dump confirmed all 16). Saved with no changes. After reload the card still shows 16 in the original order. Audit: added 0 / removed 0. **PASS** (no wipe)

## DB corroboration (read-only SELECT)

- db-final.txt: Matter Info 1 (Category). Obs 634A Before 1 (Court). SA Conveyancing 16 (unchanged). SA Legal 7 (restored to the original).
- audit-final.txt: per-member added/removed events plus one `field_group.updated` per PUT, with counts that match every UI action.

## Defects

### D1 (new in this PR, cosmetic/layout, 1024x768): long member chips overflow the group card and are hidden under the neighbouring card

- Steps: viewport 1024x768, then Settings > Matters > Custom fields > Field Groups.
- Expected: member chips wrap or truncate inside the card.
- Actual: the card grid is `md:grid-cols-2`. Inside the settings sub-nav layout this gives a content column of about 102px. The Badge uses `white-space: nowrap`, so the long labels overflow:
  - "Seller ID / Passport Number" extends 30px past the card's right edge.
  - "Purchaser ID / Passport Number" extends 56px past the card's right edge. It is visibly cut off at "Purchaser ID / Passport Nu", painted over by the SA Legal card.
  - Lists with scrollWidth > clientWidth: SA Conveyancing 207 vs 102, SA Legal 131 vs 102.
  - Page-level scrollWidth is unaffected (1024 === 1024).
- Location: `frontend/app/(app)/org/[slug]/settings/matters/_tabs/custom-fields-content.tsx:59-67` (GroupMemberList `<ul className="mt-2 flex flex-wrap gap-1.5">` / `<Badge variant="neutral">`). The grid is at about line 278.
- At 1440x900 every list fits (scrollWidth equals clientWidth).
- Not fixed (observer only).

### Pre-existing, unrelated (noted, not caused by this PR)

- Console error on every dialog open, at both main and the PR: `Pattern attribute value ^[a-z][a-z0-9_-]*$ is not a valid regular expression ... /v: Invalid character in character class`. It comes from the Slug input `pattern` (`-` unescaped under the `v` flag).
- After a dialog closes, the card's "..." dropdown menu sometimes re-opens (Radix focus return). This was seen at main and in the PR.
- The badge remove (x) buttons in the Fields picker have no accessible name.

## Verdict

Functional behaviour of G88-F3 is fixed and observed at both viewports:
- adds and removes persist across reload
- the dialog pre-selects current members
- an unchanged save does not wipe membership
- a pack-seeded group is editable

The new card member list has a layout overflow defect at 1024x768 (D1).

Strict verdict against the observer checklist: Phase 3 step 1 at 1024 ("card layout wraps cleanly with no horizontal overflow") failed. Everything else passed.

G88-F3: FAIL — membership persistence/pre-selection/no-wipe PASS at 1024 + 1440, but the new card member chips overflow the card at 1024x768 (D1, custom-fields-content.tsx GroupMemberList: nowrap Badge in ~102px column)

## Layout fix re-observation

Fix commit: `a97a51d24` on `phase89/05A-field-group-membership`, pushed. Frontend e2e container was rebuilt from the worktree at that head (`/tmp/e2e-rebuild-634A-fe.log`, exit 0). The worktree had no tracked files to restore.

### Root cause (measured at 1024x768, before the fix)

- Each card is 212px wide. The grid is `md:grid-cols-2` inside the settings sub-nav layout, which leaves about a 440px content column.
- How the 212px card is used:
  - p-4 padding plus the border bring the row down to 178px.
  - The row is `justify-between` with the 32px "..." button, so the left column is 146px (flex `0 1 auto`). The left column already fills the free space, so a missing `flex-1` is NOT the cause.
  - The 32px icon and 12px gap then leave a 102px text column.
- The shared `@b2mash/ui` Badge base classes include `whitespace-nowrap shrink-0 w-fit`. Each `<li>` is a flex item of the `flex-wrap` `<ul>`, and its min-content was therefore the full unbreakable label width:
  - "Purchaser ID / Passport Number" is 207px.
  - "Seller ID / Passport Number" is 181px.
- A flex item cannot shrink below its min-content, so these items overflowed the 102px list: 30px and 56px past the card's right edge.

### Fix (custom-fields-content.tsx only; shared Badge untouched)

- `<li className="max-w-full min-w-0">`
- `<Badge variant="neutral" className="max-w-full text-left wrap-break-word whitespace-normal">`. tailwind-merge drops the base `whitespace-nowrap`. `wrap-break-word` is the canonical Tailwind v4.2 name of `break-words`, and the built CSS emits `.wrap-break-word{overflow-wrap:break-word}`.
- Labels wrap onto 2 or 3 lines. They are never truncated.
- Vitest pins the classes on every member li and chip, and asserts that the chip no longer has `whitespace-nowrap`. The test fails without the fix (red verified) and passes with it.

### Measurements after the fix (rebuilt :3001 stack)

1024x768: page scrollWidth 1024 === clientWidth 1024.

| Card | card right | max chip right | worst overhang | ul scrollWidth / clientWidth |
|---|---|---|---|---|
| SA Conveyancing (16 chips) | 756 | 707 | -49 | 102 / 102 |
| SA Legal (7) | 984 | 935 | -49 | 102 / 102 |
| Obs 634A Before (1) | 756 | 660 | -96 | 102 / 102 |
| Matter Info (1) | 984 | 908 | -76 | 102 / 102 |

- "Seller ID / Passport Number" and "Purchaser ID / Passport Number" are each 102px wide and 54px tall (3 lines), with their right edge at 707, inside the card's 756.
- All chips have `white-space: normal`.

1440x900: page scrollWidth 1440 === clientWidth 1440. Cards are 420px wide. All chips are inside their cards, with a worst overhang of -80 / -81 / -304 / -284. No chip wraps (single line, same as before the fix).

Edit check (1440): SA Legal "..." > Edit opens "Edit Field Group" with "7 fields selected". These are the same 7 members shown on the card: Case Number, Court, Opposing Party, Opposing Attorney, Advocate, Date of Instruction, Estimated Value. The dialog was closed with Cancel, without saving.

Screenshots: `fix-1024-cards.png`, `fix-1440-cards.png`. The grey band in the 1024 element capture is the app's sticky top header overlapping the stitched element screenshot. It is not part of the card layout.

Remaining note (not fixed, product/layout call): at 1024 the 2-column grid makes cards only 212px wide, so many chips wrap to 2 lines. They are readable and contained. A wider breakpoint (e.g. `xl:grid-cols-2`) or a container query would give roomier cards, but that is a separate layout decision.

G88-F3 layout D1: PASS at 1024 and 1440 (observed).
