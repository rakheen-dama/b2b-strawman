# R1 — Matter membership and the `lead` / `LEAD` project-role casing

Root-cause dossier for capstone gaps **G-B3** (HIGH), **G-D11 fee-earner part** (MEDIUM), **G-D13** (LOW).
Read-only research on `main` @ `50b88ed5c` (2026-10-22). All paths relative to `/Users/rakheendama/Projects/2026/b2b-strawman`.
`BE=backend/src/main/java/io/b2mash/b2b/b2bstrawman`, `BT=backend/src/test/java/io/b2mash/b2b/b2bstrawman`, `FE=frontend`.

Legend: **CONFIRMED** = read in code; **HYPOTHESIS** = inferred, needs a reproduction.

---

## 0. Root cause statement

Two independent defects, one shared table.

**RC-1 (casing) — CONFIRMED.** `project_members.project_role` has no enum, no CHECK constraint, and a `varchar(50)` column (`V1__baseline.sql:1462`). The canonical value is `Roles.PROJECT_LEAD = "lead"` / `Roles.PROJECT_MEMBER = "member"` (`BE/security/Roles.java:18-19`, since Epic 19A `a3e29d235` 2026-02-08). But **template instantiation writes the literal `"LEAD"`** (`BE/projecttemplate/ProjectTemplateService.java:569,739`, since Epic 117A `4b4a63c36` 2026-02-20), and three *readers* were then written against that uppercase convention (`NotificationService.java:359,512,615`, `AssignMemberActionExecutor.java:25,94`, `CreateTaskActionExecutor.java:105`). Every other reader — access control (`ProjectAccessService.java:47`), lead transfer/removal (`ProjectMemberService.java:116,148`), save-as-template (`ProjectTemplateService.java:895`), the Diary fee-earner fallback (`ProjectMemberRepository.java:40`), the Matters list fee-earner column (`MatterListRowRepository.java:46`), the one-lead-per-matter partial unique index (`V1__baseline.sql:2171`, `WHERE project_role = 'lead'`), and the frontend (`matterRole === "lead"`, `page.tsx:115-116`, `types/member.ts:13`) — expect lowercase. So a matter created via **New from template with a Matter lead** gets a lead row the rest of the system does not recognise as a lead: no fee earner on the Matters list (G-D11), 403 on the per-member time breakdown for that lead if they are an org Member (G-D13), no canEdit in the UI for that lead, and `transferLead` by that lead fails 400 ("Not the project lead"). Conversely, a matter created via `POST /api/matters` (creator → `"lead"`) never triggers automation `PROJECT_OWNER` assignment nor lead notifications on task assignment / budget threshold / document generated, because those three readers look for `"LEAD"`.

**RC-2 (no membership path) — CONFIRMED.** Since Epic 599 (`9c3d9ec6a`, 2026-10-17) there is no reachable matter-team UI. The backend endpoints (`/api/matters/{id}/members` list/add/remove, `PUT …/members/{memberId}/role`) and the frontend panel + dialogs + server actions all still exist and are wired to each other, but nothing mounts the panel and `members` is not a tab id. Additionally, template instantiation adds **nobody** when no Matter lead is chosen (`ProjectTemplateService.java:566-570` is the only membership write on that path; there is no creator auto-add like `ProjectService.java:700-701`), so a template-created matter can have 0 members (walk evidence: `logs/walkB-db-day03-matter.txt` → `member_rows 0`). The task assignee picker is fed exclusively from `listMatterMembers` (`page.tsx:196`, `:537`), so with 0 members it offers only "Unassigned"; an org Member who is not on the matter is `ProjectAccess.DENIED` → 404 → error page (G-B3).

---

## 1. Where `project_members.project_role` is written

| # | Site | Literal written | Status |
|---|---|---|---|
| W1 | `BE/project/ProjectService.java:700-701` `createProject` → creator becomes lead | `Roles.PROJECT_LEAD` = **`"lead"`** | CONFIRMED |
| W2 | `BE/member/ProjectMemberService.java:66` `addMember` | `Roles.PROJECT_MEMBER` = **`"member"`** | CONFIRMED |
| W3 | `BE/member/ProjectMemberService.java:163-164` `transferLead` | `"member"` / `"lead"` via constants | CONFIRMED |
| W4 | `BE/projecttemplate/ProjectTemplateService.java:566-570` `instantiateTemplate` (request path, `request.matterLeadMemberId()`) | **`"LEAD"`** literal | CONFIRMED |
| W5 | `BE/projecttemplate/ProjectTemplateService.java:736-740` `instantiateFromTemplate` (recurring-schedule path, `projectLeadMemberId`) | **`"LEAD"`** literal | CONFIRMED |
| W6 | `BE/demo/seed/LegalDemoDataSeeder.java:151` (all 4 demo members on every demo matter) | **`"CONTRIBUTOR"`** (neither lead nor member — demo matters have no lead at all) | CONFIRMED |
| W7 | Take-on (Phase 84) | not built; no write site exists | CONFIRMED (grep `projectMemberRepository.save` → only W1–W6) |
| W8 | Tests writing rows directly | `"LEAD"`: `BT/automation/AutomationEndToEndTest.java:102`; `"lead"`: `BT/projecttemplate/ProjectTemplateControllerTest.java:152`, `ProjectTemplateServiceTest.java:448`; `Roles.PROJECT_LEAD`: `BT/automation/AutomationActionExecutorTest.java:100`, `AutomationActionExecutorExtendedTest.java:120`, `BT/project/ProjectCustomFieldIntegrationTest.java:293`; `"MEMBER"`: `BT/notification/NotificationServiceIntegrationTest.java:405,408` | CONFIRMED |

Constants: `BE/security/Roles.java:17-19` — `PROJECT_LEAD = "lead"`, `PROJECT_MEMBER = "member"` (comment: "project_members.project_role values").

Schema (`backend/src/main/resources/db/migration/tenant/V1__baseline.sql`):
- `:1458-1470` — `project_role character varying(50) NOT NULL`; **no CHECK constraint, no enum**; `UNIQUE (project_id, member_id)`.
- `:2171` — `CREATE UNIQUE INDEX idx_project_members_unique_lead ON project_members (project_id) WHERE project_role = 'lead'` (from `6c29ff8fc` 2026-02-08). **Lowercase only** → uppercase `"LEAD"` rows are not covered by the one-lead invariant.
- Tenant migrations present: V1, V2 (`add_project_template_work_type`), V3 (`normalise_work_types`). **Next free tenant migration = V4.** No later migration touches `project_role`.
- `backend/src/test/resources/schema/tenant-baseline.snapshot` is pinned to `target("1")` by `BT/provisioning/BaselineSchemaParityTest.java` (javadoc: "Do NOT re-record the snapshots for a feature migration"); a V4 DML/DDL does not disturb it.

The JPA entity (`BE/member/ProjectMember.java:26-27,37-43,61-63`) is a plain `String projectRole`; no normalisation in ctor/setter.

## 2. Every READ / compare site of `project_role`

| # | Site | Expects | Mechanism | Status |
|---|---|---|---|---|
| R1 | `BE/member/ProjectAccessService.java:47` `Roles.PROJECT_LEAD.equals(pm.getProjectRole())` | `"lead"` | Java equals, case-sensitive → `isLead` drives `canEdit`/`canManage` for org Members | CONFIRMED |
| R2 | `BE/member/ProjectMemberService.java:116` (removeMember guard), `:148` (transferLead precondition) | `"lead"` | Java equals | CONFIRMED |
| R3 | `BE/projecttemplate/ProjectTemplateService.java:895` save-as-template permission | `"lead"` | Java equals | CONFIRMED |
| R4 | `BE/member/ProjectMemberRepository.java:37-43` `findLeadsByProjectIdIn` JPQL `pm.projectRole = 'lead'` (613A; used by `BE/diary/DiaryService.java:283-291`) | `'lead'` | JPQL literal | CONFIRMED |
| R5 | `BE/project/MatterListRowRepository.java:46` native `pm.project_role = 'lead'` (615A; Matters list "Fee earner") | `'lead'` | SQL literal | CONFIRMED |
| R6 | `BE/timeentry/TimeEntryService.java:424-431` `getProjectTimeSummaryByMember` → `access.canEdit()` else 403 "Per-member time breakdown is restricted to project leads…" (endpoint `BE/timeentry/ProjectTimeSummaryController.java:34` `GET /api/matters/{matterId}/time-summary/by-member`) | derives from R1 → `"lead"` | — | CONFIRMED (G-D13 log `logs/walkC2-62.1-carol-403.txt`) |
| R7 | `BE/task/TaskService.java:462-466,633-636,655-659,763-768` edit/assign/delete gates `access.canEdit()` | derives from R1 | — | CONFIRMED |
| R8 | `BE/notification/NotificationService.java:359` (task assigned), `:512` (budget threshold), `:615` (document generated) `findByProjectIdAndProjectRole(projectId, "LEAD")` | **`"LEAD"`** | Spring Data derived query, case-sensitive | CONFIRMED |
| R9 | `BE/automation/executor/AssignMemberActionExecutor.java:25,94` (`PROJECT_LEAD_ROLE = "LEAD"`, comment "Matches DB convention (uppercase)") and `CreateTaskActionExecutor.java:105` — `PROJECT_OWNER` assignment | **`"LEAD"`** | derived query | CONFIRMED |
| R10 | `BE/automation/executor/SendNotificationActionExecutor.java:105` `PROJECT_OWNER` recipient | `Roles.PROJECT_LEAD` = `"lead"` (inconsistent with R9 in the same package) | derived query | CONFIRMED |
| R11 | `BE/template/ProjectContextBuilder.java:154` `"lead".equalsIgnoreCase(...)` (document-template `{{lead}}`) | either | **case-insensitive** (the only tolerant reader) | CONFIRMED |
| R12 | `BE/project/ProjectRepository.java:12-46` three JPQL projections return `pm.projectRole` raw → `MatterWithRole` → `MatterResponse.matterRole` (`BE/project/dto/MatterResponse.java:34,92,136`), unchanged casing; `ProjectService.java:483,602,895` | passes through | — | CONFIRMED |
| R13 | `BE/mcp/dto/McpMatterDto.java:14,29-45` passes `projectRole` raw (javadoc says "LEAD/MEMBER/…" — doc drift) | passes through | — | CONFIRMED |
| R14 | `BE/setupstatus/ProjectSetupStatusService.java:60-61` `teamAssigned = findByProjectId(...).size() >= 2` (comment assumes creator auto-added as lead — false on the template path) | role-agnostic | count | CONFIRMED |
| R15 | `FE/app/(app)/org/[slug]/matters/[id]/page.tsx:115-117` `project.matterRole === "lead"` → `canEdit`, `isCurrentLead`, `canManage`; `FE/lib/types/member.ts:13` `MatterRole = "lead" \| "member"`; `FE/components/projects/project-members-panel.tsx:166` `member.matterRole === "lead"`, `:29-36` badge map keyed lowercase | `"lead"` | strict equality | CONFIRMED |
| R16 | `FE/lib/api/matters.ts:327` `transferMatterLead` sends `{ role: "lead" }` (body ignored server-side; controller uses the caller as current lead, `ProjectMemberController.java:60-68`) | `"lead"` | — | CONFIRMED |

Not touching role (membership only): `ProjectRepository.findProjectsForMember` (`DashboardService.java:822`, `ProjectService.selectBase` `:481-485`), `AccessService` member branch `findByProjectIdAndMemberId`.

Net: **13 lowercase readers vs 4 uppercase readers**; one writer (`ProjectTemplateService`, both paths) and one seeder are the only producers of non-lowercase values.

## 3. Recommended root fix

### Options

**A. Normalise at write + one-off data migration (recommended).**
1. `ProjectTemplateService.java:569,739` → `Roles.PROJECT_LEAD`.
2. `NotificationService.java:359,512,615`, `AssignMemberActionExecutor.java:25`, `CreateTaskActionExecutor.java:105` → `Roles.PROJECT_LEAD`.
3. `LegalDemoDataSeeder.java:151` → `Roles.PROJECT_MEMBER`, and make the first/primary member `Roles.PROJECT_LEAD` (optional; today demo matters have no lead).
4. Defensive guard at the single choke point: `ProjectMember` ctor/setter `projectRole = projectRole.toLowerCase(Locale.ROOT)` — or, more elegant, a `ProjectRole` enum with `@Enumerated(STRING)` is NOT advisable (column stores lowercase, would need a converter). A `@PrePersist/@PreUpdate` lowercasing is cheap and makes the invariant self-enforcing. Keep it minimal.
5. Tenant migration **`V4__normalise_project_role_case.sql`**: `UPDATE project_members SET project_role = lower(project_role) WHERE project_role <> lower(project_role);` — then optionally `ALTER TABLE project_members ADD CONSTRAINT chk_project_members_role CHECK (project_role IN ('lead','member'))`. **Caveat for the CHECK:** `"CONTRIBUTOR"` rows from `LegalDemoDataSeeder` exist in any tenant that ran the demo seeder; the migration must map them (`UPDATE … SET project_role='member' WHERE project_role NOT IN ('lead','member')`) before the CHECK, or the CHECK is omitted. **Caveat for the partial unique index:** a matter can already hold one `'lead'` (creator, from `ProjectService`) *and* one `'LEAD'` (template) only if both paths ran on one matter — they don't (template path never adds the creator), but `transferLead` on a template matter is impossible today, so collisions are unlikely; still guard the UPDATE with `WHERE NOT EXISTS (another 'lead' on the same project_id)` or demote duplicates to `'member'` keeping the oldest. Note the migration must be in a form the Flyway tenant runner applies to every schema (as V2/V3 are).
6. Per `backend/db/BASELINE.md` §5 the parity snapshot is pinned to V1; V4 needs its own targeted test (e.g. provision a tenant, insert `'LEAD'` via JDBC, run the migration path — or simpler, an integration test that instantiates a template and asserts `"lead"` + Matters-list `feeEarnerId` populated).

**B. Case-insensitive compare everywhere.** Touch R1–R10, R15 (`lower(pm.project_role)` in R4/R5, `equalsIgnoreCase` elsewhere, `UPPER`/`LOWER` in derived queries → custom JPQL), plus the partial unique index (`WHERE lower(project_role) = 'lead'`). ~12 sites, spreads the tolerance, leaves the data dirty and the "CONTRIBUTOR" rows unaddressed. Not recommended.

**C. Only fix the writer (no migration).** Leaves every existing template-created matter (incl. the walked tenant: 4 `LEAD` rows) broken. Not acceptable for the demo tenant unless it is re-seeded from scratch.

**Recommendation: A** (writer + readers to the constant, entity-level lowercasing guard, V4 data migration with CONTRIBUTOR→member mapping, CHECK constraint optional/founder call). It is the "find the root cause, no symptom patch" option and shrinks the codebase to one literal source (`Roles`).

### Tests that pin current behaviour (will change / must be updated)
- `BT/projecttemplate/InstantiateTemplateIntegrationTest.java:277` — asserts `getProjectRole()` **`isEqualTo("LEAD")`** → flip to `Roles.PROJECT_LEAD`. This is the one test that actively pins the bug.
- `BT/automation/AutomationEndToEndTest.java:102` — seeds `"LEAD"`; its flows use `assignTo: TRIGGER_ACTOR` (`:131,:312`), not `PROJECT_OWNER`, so it stays green either way; change the seed to the constant for hygiene.
- `BT/automation/AutomationActionExecutorTest.java:100,462-490` — seeds `Roles.PROJECT_LEAD` and tests `SendNotification PROJECT_OWNER` (R10, already lowercase) → green. **No test covers `AssignMember`/`CreateTask` `PROJECT_OWNER` against a real lead row** (`AutomationEntityTest.java:145,438` only parse config). Add one: after the fix a `"lead"` row must resolve.
- `BT/notification/NotificationServiceIntegrationTest.java` — no test asserts leads are notified on task-assigned / budget / document-generated (grep `lead` → none; `BudgetAlertNotificationTest.java:40` javadoc claims it but seeds via `ProjectService.createProject` → `"lead"`, so lead recipients were silently empty). Add assertions after the fix.
- `BT/member/ProjectMemberIntegrationTest.java:80,261`, `BT/project/ProjectIntegrationTest.java:166`, `ProjectAccessIntegrationTest.java:178,261,318`, `MatterListRowIntegrationTest.java:303,328` — pin `matterRole == "lead"` on API responses (lowercase wire contract) → unchanged, confirm they stay green.
- `BT/audit/ProjectMemberAuditTest.java:227,265,277` — audit JSON `{"role":"lead"}`, `from/to` — unchanged.
- `BT/template/ProjectContextBuilderTest.java:240` — `"lead"` — unchanged.
- `BT/diary/DiaryServiceTest.java:616` — mocked `findLeadsByProjectIdIn` with `"lead"` — unchanged.
- `BT/setupstatus/ProjectSetupStatusServiceTest.java:63-96` — count-based — unchanged.

## 4. Matter team UI (G-B3)

**Orphan components — CONFIRMED.**
- `FE/components/projects/project-members-panel.tsx` (9.0 kB) and `FE/components/projects/add-member-dialog.tsx` (4.6 kB), plus `FE/components/projects/transfer-lead-dialog.tsx`, exist. Importers of the panel/dialog: only their own tests (`project-members-panel.test.tsx`, `add-member-dialog.test.tsx`). **Mounted nowhere.** No `components/matters/` equivalent exists (`matters/` holds only list/table/tabs files).
- Panel props (`project-members-panel.tsx:67-82`): `canManage: boolean`, `currentUserId: string`, members list; `:112` Add-member button gated by `canManage`; `:166` `isLead = member.matterRole === "lead"`; `:200-216` remove (non-lead) and transfer-lead (only shown when the viewer is the current lead).
- Server actions still exist and are correct post-rename: `FE/app/(app)/org/[slug]/matters/[id]/member-actions.ts` → `fetchProjectMembers`, `fetchOrgMembers` (`GET /api/members`), `addProjectMember`, `removeProjectMember`, `transferLead`, all via `FE/lib/api/matters.ts:312-327` (`matterPath(id,"members")` etc.).

**Backend endpoints — CONFIRMED, still live** (`BE/member/ProjectMemberController.java:21-68`, base `/api/matters/{matterId}/members`):
- `GET` list → `MatterMemberResponse` (fields incl. `matterRole`, `orgRole`).
- `POST {memberId}` → add as `"member"`. **No `@RequiresCapability` and no project-access check** — any authenticated org member can add anyone to any matter (`ProjectMemberIntegrationTest.java:313 memberCanAddMemberToProject` pins this; `memberCanListProjectMembers` `:301` likewise).
- `DELETE /{memberId}` → `removeMember(…, orgRole)`; `orgRole` parameter is **unused** in the service (`ProjectMemberService.java:106-135`); refuses to remove the lead (400, `:116`).
- `PUT /{memberId}/role` → `transferLead(matterId, caller, memberId)`: **the caller must be the current lead** (`ProjectMemberService.java:138-152`, `"lead"` compare). Owners/Admins who are not the lead get 400 "Not the project lead". On a template-created matter (`"LEAD"`) even the lead gets 400.
- The controller class has no capability gating at all; the only guard is JWT auth.

**Tab groups — CONFIRMED.** `FE/lib/constants/matter-tab-groups.ts:21-88`: 7 groups / 21 sub-tabs — Details(details, fields) · Overview(overview) · Work(tasks, documents, correspondence, generated) · Fees(time, disbursements, budget, rates, financials, statements, trust) · Client(clients, requests, customer-comments, adverse-parties) · Diary(court-dates) · Activity(activity, audit). No `members`/`team` id. Pinned by `FE/__tests__/lib/constants/matter-tab-groups.test.ts:11-13` ("7 groups and 21 tabs") and `FE/__tests__/components/matters/matter-tabs.test.tsx:252` (21). `FE/components/matters/matter-tabs.tsx:60-82` `validTabIds` (21 ids, `?tab=` not in the set is ignored `:110`; `:196-198` falls back to `"overview"`); the `MatterTabsProps` (`:12-34`) has no `membersPanel`. `legacy-tab-ids.ts` aliases only customers/projects/expenses.
- Epic 599 (`9c3d9ec6a`) changed `project-tab-groups.ts` (6 lines) and `project-tabs.tsx`; 615E (#1631) deleted the orphan mount (gaps.md attribution row for G-B3).
- **Where a Team sub-tab would go:** the natural home is the **Details** group (`{ id: "team", label: "Team" }` after `fields`) — it is matter set-up metadata like custom fields, and Details is the group with headroom (2 tabs). Alternative: Overview group. Either way: add the id to `MATTER_TAB_GROUPS`, `validTabIds`, `TabId`, a `teamPanel` prop + `Tabs.Content`, bump the two "21" pins to 22, and point the setup step at `?tab=team` (or alias `members → team` in `legacy-tab-ids.ts`, which also fixes old bookmarks).

**Overview setup checklist today — CONFIRMED.** `page.tsx:147-150` step "Team members added" → `actionHref: "?tab=members"`; `teamAssigned` comes from `ProjectSetupStatusService:60-61` (≥2 rows). Clicking it: `aliasLegacyTabId("members")` passes through unchanged → not in `validTabIds` → `urlTab = null` → Overview re-renders. Pinned by `FE/__tests__/components/projects/overview-tab-setup.test.tsx:28-30,132` (expects `?tab=members`) → update with the new id.

**Assignee picker — CONFIRMED.** `page.tsx:196` `members = await listMatterMembers(id)` (non-fatal catch) → `:537` `TaskListPanel members={…}` → `CreateTaskDialog`/`TaskDetailSheet` → `FE/components/tasks/assignee-selector.tsx:17-38` renders only the passed `members` plus "Unassigned". It is **matter membership**, not org membership. Backend `TaskService` does not validate that an assignee is a matter member (grep `isProjectMember|existsByProjectIdAndMemberId` in `TaskService.java` → none), so the restriction is UI-only.

## 5. Visibility rule / Carol's error

`BE/member/ProjectAccessService.java:24-51` — CONFIRMED:
- **Owner**: any matter in the tenant → `ProjectAccess(view, edit, manage, delete=true, role)` (`:26-32`).
- **Admin**: any matter → view/edit/manage, no delete (`:34-40`).
- **Member (org role)**: only if a `project_members` row exists for (matter, member) (`:43-50`); `canEdit = canManage = isLead` where `isLead` is the case-sensitive `"lead"` compare (`:47`). No row → `ProjectAccess.DENIED`.
- `requireViewAccess` (`:58-64`) throws `ResourceNotFoundException` → **404** (deliberate security-by-obscurity; comment `:53-56`). `requireEditAccess` → 403 `ForbiddenException`.
- Listing: `ProjectService.selectBase` `:481-485` → non-admin accessible set = `findByMemberId(actor)` keys; `listProjects(actor)` → `findProjectsForMember` (`ProjectRepository.java:12-21`, inner join on membership). Hence "0 matters — You're not on any matters yet" for Carol. Dashboard uses the same (`DashboardService.java:822`); MCP `MatterTools.java:91` uses `listProjects`.
- G-D13 chain (Carol = org Member with a `"LEAD"` row): membership row found → view OK (page renders) → `isLead=false` → `canEdit=false` → `TimeEntryService.java:424-431` 403 ×4 (one per time-summary widget fetch) → `security.access_denied` audit rows. CONFIRMED by code + `logs/walkC2-62.1-carol-403.txt`.
- G-B3 chain (Carol not a member): `GET /api/matters/{id}` → `ProjectService.getProject` `:598-603` → `requireViewAccess` → 404 → `FE/lib/api/client.ts:155` `ApiError(404)` → `page.tsx:109-113` catch → `handleApiError` `:223-225` `notFound()`.
  - **HYPOTHESIS on the rendered page:** `notFound()` should render Next's default 404 (there is **no** `not-found.tsx` anywhere under `FE/app`), yet the walk saw "Something went wrong" (`days-01-07.md:61,63`). The nearest boundary is `FE/app/(app)/org/[slug]/matters/error.tsx` ("Something went wrong" / "Unable to load this page. Please try again."); the narrated sub-copy "An unexpected error occurred while loading this page." is not in the repo (walker paraphrase likely). Either `notFound()` is being swallowed into the error boundary in this Next 16 layout, or a different call failed first. The fix agent must reproduce as Carol on :3001 (reproduce-before-fix) — but the *cause* (non-member → 404) is CONFIRMED and the fix (give Carol a membership path) is the same either way. A secondary polish: add a `not-found.tsx` under `matters/[id]` so a non-member gets a clean "Matter not found / you're not on this matter" page instead of an error boundary.
- Notification link (6.3) routes to `/matters/{id}?tab=tasks` → same page → same 404 path. CONFIRMED.
- Bob (Admin) can open and log time on matters he is not on: Admin branch `:34-40`. CONFIRMED (matches walk).

## 6. Edit / create dialogs

- `FE/components/projects/edit-project-dialog.tsx` — **no** lead/member/team field (grep `lead|member|team` → none). CONFIRMED.
- `FE/components/projects/create-project-dialog.tsx` — no lead picker; `POST /api/matters` makes the **creator** the `"lead"` (`ProjectService.java:700`, `ProjectController.java:112` returns `matterRole: "lead"`). CONFIRMED.
- `FE/components/templates/NewFromTemplateDialog.tsx:87,185,369-388` — "Matter lead (optional)" `<select>` over `orgMembers` (prop, from `GET /api/members`), default "Unassigned"; sends `matterLeadMemberId` (`FE/lib/api/templates.ts:104`) to `POST /api/matter-templates/{id}/instantiate` (`templates.ts:159`) → backend W4 writes **`"LEAD"`** for that one person; template tasks with `assigneeRole = PROJECT_LEAD` are pre-assigned to them (`ProjectTemplateService.java:960-968`). If left "Unassigned": **zero members** and tasks unassigned. The creator is never added. CONFIRMED (walk Day 3 = 0 rows; Day 9/14 = one `LEAD` row).

## 7. Related gaps sharing the root

- **G-D11 (fee earner)**, **G-D13** — RC-1 directly.
- **G-B3** — RC-2 (plus RC-1 for the UI's lead badge/`canManage` once a team tab exists).
- Latent, not yet filed (same RC-1): (a) lead notifications on task-assigned / budget-threshold / document-generated never fire for `ProjectService`-created matters (R8 reads `"LEAD"`); (b) automation `PROJECT_OWNER` for ASSIGN_MEMBER/CREATE_TASK resolves to nobody on those matters (R9), while SEND_NOTIFICATION `PROJECT_OWNER` (R10) works — inconsistent within one feature; (c) `transferLead` and "Save as template" are impossible for a template-chosen lead who is an org Member; (d) one-lead invariant not enforced for `"LEAD"` rows; (e) Diary fee-earner fallback (613A) shows "—" for template-created matters (same query as R4); (f) demo-seeded tenants (`"CONTRIBUTOR"`) have no lead anywhere.
- Not this root: G-D11 outstanding/FICA parts and G-D21 (debtor rule / FICA projection) — separate cluster.

## 8. Product questions for the founder

1. **Who may manage a matter's team?** Today: anyone can add (no gate), nobody but the lead can transfer lead, Owner/Admin cannot transfer or demote. Proposed: Owner/Admin or current lead may add/remove/transfer (`canManage`), and `transferLead` should accept an Owner/Admin caller who is not the lead. Confirm.
2. **Should the creator of a template-instantiated matter be auto-added** (as lead when no Matter lead is chosen, as member otherwise), mirroring `POST /api/matters`? This alone removes the 0-member state.
3. **Should org Members see all matters (read) or only their own?** Current rule is membership-only for Members; Admin/Owner see all. The walk's scenario expects Carol to be *added*, not to see everything, so the fix is a team UI, but confirm the visibility rule stands (ADR-level).
4. **Lead picker in Edit Matter?** Minimal: a Team tab with Add / Remove / Transfer lead. Extended: "Matter lead" select in Edit Matter. Which?
5. **Enforce `project_role IN ('lead','member')` with a CHECK** in V4 (requires mapping `CONTRIBUTOR` → `member` in demo-seeded tenants)? Recommend yes.
6. Tab placement: Team under **Details** (recommended) vs Overview group; and keep `?tab=members` as a legacy alias?

## 9. Tests to run (blast radius)

Backend (full `./backend/scripts/verify.sh` before merge; targeted inner loop):
- `member/ProjectMemberIntegrationTest`, `project/ProjectAccessIntegrationTest`, `project/ProjectIntegrationTest`, `project/MatterListRowIntegrationTest`, `project/ProjectCustomFieldIntegrationTest`, `project/ProjectLifecycleIntegrationTest`, `project/ProjectServiceTest`
- `projecttemplate/InstantiateTemplateIntegrationTest` (**:277 flips**), `ProjectTemplateControllerTest`, `ProjectTemplateServiceTest`
- `automation/AutomationActionExecutorTest`, `AutomationActionExecutorExtendedTest`, `AutomationEndToEndTest`, `AutomationEntityTest`
- `notification/NotificationServiceIntegrationTest`, `NotificationEventHandlerIntegrationTest`, `budget/BudgetAlertNotificationTest`
- `timeentry/ProjectTimeSummaryIntegrationTest` (+ the rest of `timeentry/*`), `task/TaskIntegrationTest`, `TaskCreateWithAssigneeIntegrationTest`, `TaskClaimIntegrationTest`, `TaskLifecycleIntegrationTest`, `TaskServiceTest`
- `diary/DiaryServiceTest`, `DiaryControllerIntegrationTest`; `dashboard/DashboardPersonalIntegrationTest`, `DashboardProjectIntegrationTest`; `mywork/MyWorkIntegrationTest`
- `audit/ProjectMemberAuditTest`, `SecurityAuditTest`; `template/ProjectContextBuilderTest`; `setupstatus/ProjectSetupStatusServiceTest`; `demo/seed/DemoDataSeederIntegrationTest`; `mcp/tool/CatalogueBatch1ToolsTest`, `mcp/MatterClientResourceTest`
- If V4 added: `provisioning/BaselineSchemaParityTest` (must stay green — snapshot pinned to V1) + a new V4 targeted test.

Frontend (`pnpm lint && pnpm format:check && pnpm build && pnpm test`, full vitest):
- `components/projects/project-members-panel.test.tsx`, `add-member-dialog.test.tsx`
- `__tests__/components/matters/matter-tabs.test.tsx` (21 pin), `__tests__/lib/constants/matter-tab-groups.test.ts` (7/21 pin), `__tests__/components/projects/grouped-tab-bar.test.tsx`, `overview-tab-setup.test.tsx` (`?tab=members` pin)
- `__tests__/components/tasks/assignee-selector.test.tsx`, `create-task-dialog.test.tsx`, `task-detail-sheet.test.tsx`, `task-list-panel*.test.tsx`
- `__tests__/lib/api/matters.test.ts`, `__tests__/lib/matters-list.test.ts`, `__tests__/components/projects/matter-sidebar.test.tsx`
- Playwright (E2E stack :3001, `--config` legal configs, run in a worktree): `e2e/tests/projects/project-tasks.spec.ts`; legal-lifecycle + legal-depth-ii suites (UI change rule); no existing Playwright spec covers add-member/transfer-lead (grep → only `e2e/AUTOMATION-NOTES.md`) — add one for the new Team tab.
