# ADR-346: Matter Visibility for Org Members Stays Membership-Only; Owner, Admin or the Current Lead Manage the Team; the Creator Is Auto-Added on Template Instantiation; Team Is a Sub-Tab Under Details

**Status**: Accepted

**Context**:

The capstone's HIGH gap G-B3 ("Carol can't") hides eight FAILs and two DEFERREDs behind one missing path: since Epic 599 (`9c3d9ec6a`) nothing mounts the matter-team UI. `project-members-panel.tsx`, `add-member-dialog.tsx`, `transfer-lead-dialog.tsx`, `member-actions.ts` and `lib/api/matters.ts:312-327` all still exist and wire to live endpoints (`ProjectMemberController.java:21-68`, base `/api/matters/{matterId}/members`), but `matter-tab-groups.ts` has 7 groups / 21 tabs with no `members`/`team` id (pinned at "21" by `matter-tab-groups.test.ts:11-13` and `matter-tabs.test.tsx:252`), and the Overview setup step links to `?tab=members`, which `validTabIds` ignores (R1 §4).

The second half is upstream: `ProjectTemplateService.instantiateTemplate` adds **nobody** unless a Matter lead is chosen (`:566-570`), unlike `ProjectService.createProject:700-701` which makes the creator the lead — the walk's Day 3 matter had `member_rows 0`. The visibility rule (`ProjectAccessService.java:24-51`) is: Owner and Admin see every matter; an org **Member** sees a matter only with a `project_members` row, otherwise `requireViewAccess` throws `ResourceNotFoundException` (404 by design, `:53-56`), which the matter page turns into an error boundary. Latent (N-3): `POST …/members` has no capability or project-access gate (`memberCanAddMemberToProject` pins this), and `transferLead` requires the caller to be the current lead (`ProjectMemberService.java:138-152`), so an Owner/Admin cannot transfer. Founder decision D1 (`tasks/phase88-gap-register.md` §4) authorises the rules this ADR records.

**Options Considered**:

1. **Membership-only visibility for Members; Owner/Admin/current lead manage the team; creator auto-added on instantiation; Team sub-tab under Details (CHOSEN).**
   - Pros:
     - Matches the scenario's intent (Carol is *added*, not granted blanket access) and the existing `ProjectAccessService` contract that every list, dashboard and MCP query already implements (`findProjectsForMember`, `DashboardService:822`, `MatterTools:91`).
     - Closes the 0-member state at its source, mirroring the one path that already works.
     - Restores working UI rather than building new UI.
     - One tab id, two pin bumps.
     - A law firm's confidentiality posture (conflict walls, counsel-only files) is the membership model.
   - Cons:
     - Members still cannot discover matters they are not on.
     - A firm that wants "everyone sees everything" has no switch (deferred, see Consequences).
     - `transferLead` and `POST/DELETE …/members` gain gates that two existing tests pin the absence of.
2. **All org Members read every matter; membership only governs edit.**
   - Pros:
     - Carol's error disappears without a team UI.
     - No auto-add needed.
   - Cons:
     - Changes the security model of every list, Home widget, MCP tool and portal-adjacent query in one move, in a phase scoped to closing capstone gaps.
     - Contradicts the legal product's confidentiality expectation and the membership-only pins in `ProjectAccessIntegrationTest`.
     - Does not fix the 0-member matter (task assignee picker still offers only "Unassigned").
3. **Per-matter opt-in ("visible to all staff" flag on the matter).**
   - Pros:
     - Flexible.
     - Firms choose per file.
   - Cons:
     - New column, new DTO field, new filter branch in every accessible-set query, new UI — a feature, not a gap closure.
     - Still needs the team UI for the non-opted matters.
     - Two visibility mechanisms to reason about in a phase that is removing dual mechanisms (FICA, formatters, renderers).

**Decision**: Option 1 — Members keep membership-only visibility; `POST`/`DELETE …/members` and `PUT …/members/{id}/role` are gated to callers with `ProjectAccess.canManage` (Owner, Admin, or the current lead), `transferLead` accepts an Owner/Admin caller who is not the lead; template instantiation auto-adds the creator (lead when no lead is chosen, member otherwise); a **Team** sub-tab is added to the Details group mounting the existing panel and dialogs, with `members → team` aliased in `legacy-tab-ids.ts`.

**Rationale**:

1. **Visibility is a security contract, not a UI gap.** The 404-on-denied design (`ProjectAccessService:53-56`) and the accessible-set joins are pinned by integration tests across `project/`, `dashboard/`, `mywork/` and `mcp/`; widening them is an architectural decision outside the capstone's remit and the demo-partner timeline. Recording the rule here ends the recurring question (R1 §8 Q3).
2. **Mirror the path that works.** `createProject` already encodes "creator is on the matter"; instantiation diverged in Epic 117A. Auto-adding the creator is the smallest change that makes every matter have at least one member and makes `ProjectSetupStatusService:60-61`'s assumption true.
3. **Owner/Admin already have `canManage`**; the member endpoints simply never checked it. Gating them to `canManage` and letting an Owner/Admin transfer lead removes the latent inconsistency (N-3) with the same predicate the rest of the matter uses.
4. **Details is the group with headroom** (2 tabs) and team composition is set-up metadata like custom fields; aliasing `members` keeps old bookmarks and the setup step working.

**Consequences**:

- Positive:
  - G-B3's eight FAILs and two DEFERREDs, N-3, and the Carol chain (list → open → log time → reply to mention → notification link) close.
  - `day-01-07` Carol steps are un-skipped.
  - The task assignee picker (fed from `listMatterMembers`) offers real people on template-created matters.
  - A `not-found.tsx` under `matters/[id]` may give non-members a clean page instead of the error boundary (the slice reproduces which boundary rendered "Something went wrong" first, per the register).
- Negative:
  - `ProjectMemberIntegrationTest:301,313` (`memberCanListProjectMembers`, `memberCanAddMemberToProject`) change: a non-lead Member can list but cannot add.
  - New cases "admin can transfer", "member cannot add", "lead can add".
  - Tab pins bump 21 → 22 in `matter-tab-groups.test.ts` and `matter-tabs.test.tsx`.
  - `overview-tab-setup.test.tsx:28-30,132` expects `?tab=team`.
  - Depends on [ADR-345](ADR-345-canonical-project-role-values.md): the panel's lead badge, `canManage` and transfer-lead read `matterRole === "lead"`.
  - An "all staff can read" firm setting is explicitly deferred.
  - If a design partner asks, it is a new ADR, not a widening of `hasCapability` or `ProjectAccessService`.
- Related:
  - [ADR-345](ADR-345-canonical-project-role-values.md), [ADR-342](ADR-342-audit-and-entity-tag-strings-stable-under-renamed-wire.md) (`project_member.added/removed` audit literals unchanged), [ADR-344](ADR-344-qa-scenario-amendment-authorisation-phase87.md) (no scenario amendment is needed for G-B3 — the product moves to the scenario), `tasks/phase88-gap-register.md` D1.
