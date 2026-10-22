# R9 — Misc behaviour gaps: root-cause dossier (G-C2, G-D4, G-D5, G-D12, G-A1, G-C1)

Read-only research on `main` @ 50b88ed5c (2026-10-22). Paths are repo-relative to
`/Users/rakheendama/Projects/2026/b2b-strawman`. Each finding is tagged CONFIRMED (read in code) or
HYPOTHESIS (not provable from code alone — needs a reproduce run).

---

## 1. G-C2 — Adverse-party registry › "Link to Matter" crashes (`Cannot read properties of undefined (reading 'map')`)

### Findings
- **CONFIRMED** `frontend/app/(app)/org/[slug]/legal/adverse-parties/actions.ts:136-142` — `fetchProjects()` casts the
  result of `listMatters({ size: 200 })` to `PaginatedResponse<…>` and returns `result.content`. The cast is
  `as unknown as PaginatedResponse`, with the comment `FIXME(607D follow-up): /api/matters is a flat list; .content is
  undefined (pre-existing bug, not fixed in 610B)`.
- **CONFIRMED** `GET /api/matters` returns a flat list, not a Page:
  `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectController.java:62-77` —
  `@GetMapping public ResponseEntity<List<MatterListRow>> listMatters(...)` (615A `MatterListRow` projection via
  `projectService.listMatterRows(new MatterListQuery(...))`). There is no `Page<>` anywhere on this endpoint.
- **CONFIRMED** the matters list page uses the typed client: `frontend/lib/api/matters.ts:137-147` —
  `listMatters(params): Promise<Matter[]>` and `listMatterRows(params): Promise<MatterListRow[]>` both call
  `api.get<…[]>(`${MATTERS_PATH}${toQuery(params)}`)`. `frontend/app/(app)/org/[slug]/matters/page.tsx:102` uses
  `listMatterRows(listParams)`. Both return arrays; `.content` on an array is `undefined`.
- **CONFIRMED** the dialog does `Promise.all([fetchProjects(), fetchCustomers()])` then `setProjects(p)` with
  `p === undefined` (`frontend/components/legal/link-adverse-party-dialog.tsx:72-82`); `projects.map(...)` at
  `:138` throws inside the `FormField` render for `projectId`. The `.catch` at `:78` does not fire because the promise
  resolves (with `undefined`), it does not reject. Matches the console trace in the gap row exactly.
- **CONFIRMED** `fetchCustomers()` does NOT have the bug: `actions.ts:144-151` already handles both shapes
  (`Array.isArray(result) ? result : (result.content ?? [])`). `GET /api/clients` is also a flat list
  (`frontend/lib/api/clients.ts:84-86` `listClients(): Promise<Client[]>`).
- **CONFIRMED** the `size: 200` query param is harmless: `ProjectController` has no `size` binding; it lands in
  `allParams`, and `backend/.../view/CustomFieldFilterUtil.java:112` only extracts keys matching `customField[...]`;
  `TagFilterUtil` only reads `tags`. The backend ignores `size`.
- **CONFIRMED** other callers of the two actions from this module:
  - `frontend/components/legal/link-adverse-party-dialog.tsx:26-29` — imports `fetchProjects` + `fetchCustomers` (the broken path).
  - `frontend/components/legal/link-adverse-party-to-project-dialog.tsx:30,82` — imports only `fetchCustomers`
    (`Promise.all([fetchAdverseParties(), fetchCustomers()])`) → this is why the matter-side path works.
  - `frontend/components/legal/adverse-party-dialog.tsx:31` and `project-adverse-parties-tab.tsx:18` import other
    actions from the same file (not `fetchProjects`).
  - No other consumer of this module's `fetchProjects`. (The identically named `fetchProjects` in
    `conflict-check/actions.ts:107-113`, `legal/disbursements/actions.ts:304-309`, `lib/actions/court-dates.ts:199-202`
    are separate, already-correct implementations.)

### Root cause
`fetchProjects()` in `legal/adverse-parties/actions.ts` was written against a paginated envelope that this endpoint
never returned (at the pre-Phase-87 baseline `GET /api/projects` was already `List<ProjectResponse>`). Returns
`undefined`; the dialog maps it.

### Recommended fix (exact)
Replace `actions.ts:136-142` with the same defensive shape the sibling modules use (or simply return the array):

```ts
export async function fetchProjects(): Promise<{ id: string; name: string }[]> {
  // GET /api/matters returns a flat List<MatterListRow> (ProjectController.listMatters) — no envelope.
  const result = await listMatters();
  return Array.isArray(result) ? result : [];
}
```
`listMatters()` already resolves to `Matter[]` with `id`/`name`, so the cast and `PaginatedResponse` import go away
(`size: 200` can be dropped — the backend ignores it). Optionally harden the dialog: `setProjects(p ?? [])`.
Reproduce first (CLAUDE.md §4): open registry → row Actions → Link to Matter on the e2e stack.

### Tests
- Frontend unit: `frontend/__tests__/legal/adverse-parties.test.tsx` (mocks `fetchProjects` → `[]` at `:38-39`,
  so it never exercised the real action — add a test that asserts `fetchProjects()` returns an array when
  `listMatters` resolves to an array; mock `@/lib/api/matters`). Also `__tests__/legal/legal-wire-bodies.test.ts`
  and `__tests__/legal/project-legal-tabs.test.tsx` import/mock this module.
- Playwright: `frontend/e2e/tests/legal-lifecycle/day-07-14-work-trust.spec.ts`, `day-75-complex.spec.ts`,
  `demo-full-90day.spec.ts` touch adverse parties (confirm whether any of them clicks the registry's Link to Matter;
  if not, add that step).
- Backend untouched (no change). `MatterListRowIntegrationTest` documents the flat-list contract.

---

## 2. G-D4 — Report export "Failed to export CSV/PDF" (nothing reaches the backend); Profitability has no export

### Findings
- **CONFIRMED** export path: `frontend/components/reports/report-runner.tsx:60,77` →
  `exportReportCsvAction` / `exportReportPdfAction` (`frontend/app/(app)/org/[slug]/reports/[reportSlug]/actions.ts:36-64`)
  → `exportReportCsv` / `exportReportPdf` (`frontend/lib/api/reports.ts:113-168`).
- **CONFIRMED** `lib/api/reports.ts:117-122` and `:146-151`:
  ```ts
  let token: string;
  try { token = await getAuthToken(); } catch { redirectToReLogin(await resolveServerReturnTo(), "expired"); }
  ```
  then `fetch(`${BACKEND_URL}/api/report-definitions/${slug}/export/csv?…`, { headers: { Authorization: `Bearer ${token}` } })`
  with `BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8080"` (`:101`).
- **CONFIRMED** `getAuthToken()` throws unconditionally in Keycloak/BFF mode:
  `frontend/lib/auth/server.ts:24-27` dispatches on `AUTH_MODE`; `frontend/lib/auth/providers/keycloak-bff.ts:121-125`:
  `throw new Error("getAuthToken() is not available in BFF mode. API calls should route through the gateway.")`.
  Only the mock provider (`lib/auth/providers/mock/server.ts:73`) returns a token.
- **CONFIRMED** the thrown error is swallowed: the `catch` calls `redirectToReLogin(...)` which is
  `redirect(buildSignInUrl(...))` (`frontend/lib/auth/expiry.ts:59-61`). Next's `redirect()` throws a `NEXT_REDIRECT`
  error. That error is not an `ApiError`, so the server action's generic branch returns
  `{ data: null, error: "Failed to export CSV." }` (`actions.ts:41-46`, `:57-62`). This explains the 2–25 ms
  return, no `ApiError` message, no redirect, and zero backend log lines
  (`qa_cycle/evidence/phase87-capstone/logs/walkC2-80.4-export.txt`: "backend.log: lines mentioning
  report-definitions/export … 0").
  - Secondary defect even if a token existed: the call targets `BACKEND_URL` (8080) directly, not the gateway, so
    in BFF mode it could never carry the SESSION cookie.
- **CONFIRMED** why it "worked" before: in mock mode (`:3001` e2e stack) `getAuthToken()` returns the mock JWT and
  the backend on 8081 accepts bearer tokens. The capstone was the first Keycloak-mode run of this path.
- **CONFIRMED** the working download pattern (how every other BFF-mode download succeeds):
  `getAuthFetchOptions(method)` + `API_BASE` from `frontend/lib/api/client.ts:16-52` — in keycloak mode it
  forwards the `SESSION` cookie (and `X-XSRF-TOKEN` for mutations) and `API_BASE` = `GATEWAY_URL` (8443); in mock
  mode it attaches the bearer token and `API_BASE` = `BACKEND_URL`. Used by:
  - `frontend/lib/api/audit-events.ts:241-270` `exportAuditPdf` — byte-for-byte the same job as the report PDF
    export (base64 back to the client), already correct.
  - `frontend/lib/api/generated-documents.ts:73-104` `downloadGeneratedDocument` (fee-note / SoA / engagement PDFs
    are generated documents) and `:106-` `downloadDocxGeneratedDocument`.
  - `frontend/lib/api/document-templates.ts:183`, `legal-disbursements.ts`, `settings.ts`.
  - Route-handler variant: `frontend/app/api/invoices/[id]/preview/route.ts:23-35` (gateway proxy with the same helper).
- **CONFIRMED blast radius of the same helper**: `frontend/app/(app)/org/[slug]/trust-accounting/client-ledgers/actions.ts:141-153`
  `generateStatementPdf()` calls `exportReportPdf("client-ledger-statement", …)` — the trust client-ledger
  statement PDF is broken in BFF mode for the same reason (not exercised in the capstone — no evidence row).
- **CONFIRMED** backend endpoints exist and are fine:
  `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/ReportingController.java:61-83` —
  `GET /api/report-definitions/{slug}/export/pdf` and `/export/csv`, `@RequiresCapability("FINANCIAL_VISIBILITY")`.
- **Profitability (second half) — CONFIRMED**: the `/reports/profitability` page
  (`frontend/app/(app)/org/[slug]/reports/profitability/page.tsx:45-48`) reads bespoke endpoints
  `GET /api/reports/utilization` and `GET /api/reports/profitability`
  (`backend/.../report/ReportController.java:61`), which have **no export endpoint**. Separately, a
  *report-definition* named `project-profitability` exists (`StandardReportPackSeeder.java:57,280`) and runs through
  the generic runner, so it gets CSV/PDF export for free once G-D4 is fixed. The bespoke Profitability page is a
  different surface with no export control and no backend export.

### Root cause
`lib/api/reports.ts` export functions are the only API code still built on the pre-BFF bearer-token contract
(`getAuthToken()` + direct `BACKEND_URL`). In BFF mode the token getter throws by design, and the `redirect()` thrown
inside the catch is masked by the action's generic catch.

### Recommended fix (exact pattern)
Rewrite `exportReportCsv` / `exportReportPdf` on the `exportAuditPdf` template (`audit-events.ts:241-270`):
```ts
import { api, ApiError, API_BASE, getAuthFetchOptions, resolveServerReturnTo } from "./client";
...
const auth = await getAuthFetchOptions("GET");           // throws → let redirectToReLogin handle, as generated-documents.ts:74-78 does
const response = await fetch(`${API_BASE}/api/report-definitions/${encodeURIComponent(slug)}/export/csv?${qs}`,
  { headers: auth.headers, credentials: auth.credentials });
```
Drop `getAuthToken` and the local `BACKEND_URL`. This also fixes `generateStatementPdf`. Keep the base64/text
return contract so `report-runner.tsx:60-90` is unchanged. Reproduce in Keycloak mode first (`svc.sh start all`,
Timesheet → Run → Export CSV) and confirm a backend `export/csv` log line after the fix.
Product question: should the bespoke Profitability page gain an export (new backend endpoint), or should the
scenario step point at the `project-profitability` report definition instead? (Not an agent call.)

### Tests
- Frontend unit: `frontend/__tests__/reports-api.test.ts` (does not currently mock `getAuthToken` — add cases that
  mock `getAuthFetchOptions`/`API_BASE` and assert the URL/headers), `__tests__/report-runner.test.tsx`,
  `__tests__/trust-investments-reports.test.tsx`, `reports-page.test.tsx`, `report-results.test.tsx`.
- Playwright: `frontend/e2e/tests/finance/reports.spec.ts` (mock mode — will stay green either way; the regression
  is BFF-only, so the observed check must be a Keycloak-mode browser run).
- Backend: no change; `reporting/ReportingControllerTest.java` covers the endpoints.

---

## 3. G-D5 — My work shows no upcoming court dates ("Next deadline" is tasks-only; "Today's diary" is today-only)

### Findings
- **CONFIRMED** "Next deadline" = `PersonalDashboard.upcomingDeadlines[0]`:
  `frontend/components/dashboard/todays-agenda.tsx:71-72,103-115` reads `upcomingDeadlines[0]` and renders
  `taskName` / `matterName` / `dueDate`. Fed from `frontend/app/(app)/org/[slug]/my-work/page.tsx:97,217`
  (`fetchPersonalDashboard(from, to)` → `personalDashboard?.upcomingDeadlines`).
- **CONFIRMED** backend builds it from tasks only: `backend/.../dashboard/DashboardService.java:699-717` —
  `taskRepository.findUpcomingByAssignee(memberId, today).stream().limit(5)` mapped to `UpcomingDeadline(taskId,
  title, matterName, dueDate)`. Court dates, prescription dates and matter due dates never feed it. Endpoint:
  `DashboardController.java:152-156` `getPersonalDashboard`.
- **CONFIRMED** "Today's diary" is deliberately today + overdue band only: `todays-agenda.tsx:13-18` doc comment
  ("GET /api/diary?from=today&to=today for the current user (613D)"); My work `page.tsx:148-151`
  `loadTodaysDiary(today, currentMemberId)`; Home `frontend/lib/dashboard-home-fetch.ts:103-106,112`
  (`feeEarnerId = me` for members, firm-wide for admins/owners). A CMC 45 days out is outside that window by design.
- **CONFIRMED** DiaryService already aggregates all four kinds for a range with a per-fee-earner filter:
  - `backend/.../diary/DiaryController.java:30-45` — `GET /api/diary?from&to&types&feeEarnerId&matterId&includeOverdue`
    returns `List<DiaryEventResponse>` (+ `X-Diary-Types` header).
  - `DiaryKind.java` — `TASK_DUE`, `MATTER_DUE`, `COURT_DATE`, `PRESCRIPTION`; rank orders court dates first within a day.
  - `DiaryService.java:189-236` — fee-earner resolution: **task → assignee else matter lead; court date / prescription /
    matter-due → matter lead** (`leadsByMatter`), then `.filter(e -> q.feeEarnerId() == null || q.feeEarnerId().equals(e.feeEarnerId()))`
    and `.sorted(ORDER)` (date, then kind rank).
  - Item shape `DiaryEventResponse.java:18-34`: `id, kind, date, time, title, subtype, status, matterId, matterName,
    matterReference, clientId, clientName, feeEarnerId, feeEarnerName, overdue, href` (href relative to `/org/{slug}`).
  - Frontend already has the client: `frontend/app/(app)/org/[slug]/diary/diary-actions.ts:24-40` (raw fetch via
    `API_BASE` + `getAuthFetchOptions`, reads the types header) and `DiaryEventRow` in `components/diary/diary-event-row.tsx`
    (used by `TodaysDiaryList`).
- **Caveat (CONFIRMED semantics, HYPOTHESIS for the walk)**: a court date is attributed to the **matter lead**, not to
  the person who created it or to task assignees. Bob sees the RAF CMC only if Bob is the lead of the RAF matter. The
  gap text says "Bob's RAF matter", so this should hold; verify `project_members` lead for that matter before
  claiming the fix would surface it.

### Root cause
Two independent, both pre-existing: (a) the Next-deadline card is a task-centric read model that predates One Diary;
(b) the diary widget's window is a single day. Neither consults court dates beyond today.

### Recommended fix (proposal — product/architect call)
Replace the task-only "Next deadline" with a Diary-backed "Upcoming" read:
- Frontend: in My work `page.tsx`, alongside `loadTodaysDiary(today, memberId)`, call the existing diary action for
  `from = today+1 … to = today+14` (or 30), `feeEarnerId = memberId`, `includeOverdue = false`, and render the first
  N rows with `DiaryEventRow` under a "Next 14 days" / "Upcoming" heading (court dates sort first within a day by
  rank). The "Next deadline" card becomes `upcoming[0]` (which may be a court date), keeping one source for both
  Home and My work. Keep `upcomingDeadlines` from `PersonalDashboard` only if the Tasks widget still needs it;
  otherwise it can be dropped from the response later.
- Backend: no new endpoint needed. Optional: a `limit` param on `GET /api/diary` to avoid fetching a full fortnight.
- Alternative (smaller UI delta): keep the card and make `DashboardService` merge court dates via
  `courtDateRepository.findByStatusInAndScheduledDateBetween` — but that re-implements Diary attribution; the Diary
  route is the single-source option 613D intended.
Product questions: window length (14 vs 30 days), whether admins/owners see firm-wide upcoming (as Home does for
today), and whether court dates on matters where the user is a member-but-not-lead should count.

### Tests
- Frontend: `frontend/__tests__/dashboard/todays-agenda.test.tsx`, `__tests__/dashboard/my-work-dashboard.test.tsx`
  (currently assert the task-based card), diary tests `__tests__/diary/*`.
- Backend: `diary/DiaryServiceTest.java`, `diary/DiaryControllerIntegrationTest.java` (fee-earner filter + range),
  `dashboard/DashboardPersonalIntegrationTest.java` (if `upcomingDeadlines` is changed/removed).
- Playwright: no existing spec asserts "Next Deadline"/"todays-diary"; add a step to the 90-day spec.

---

## 4. G-D12 — Team page keeps Bob and Carol under Pending Invitations; header "5 members (2 pending)"

### Findings
- **CONFIRMED** invitations are a local tenant table, not Keycloak's: `backend/.../invitation/PendingInvitation.java`
  (`status` ∈ `InvitationStatus {PENDING, ACCEPTED, EXPIRED, REVOKED}`, `accept()` at `:66-69` sets
  `ACCEPTED` + `acceptedAt`). Keycloak is only asked to send the email (`InvitationService.java:108-` "Trigger Keycloak
  invitation email (best-effort)").
- **CONFIRMED** the only code that marks an invitation accepted is the JIT member-provisioning path on the invitee's
  first authenticated request: `backend/.../member/MemberFilter.java:137-139` (`findByClerkUserId(...).orElseGet(() ->
  lazyCreateMember(...))`) → `:155-166` looks up `invitationService.findPendingByEmail(jwtEmail)`, takes the
  invitation's role, creates the `Member`, then `:204-213` `invitationService.markAccepted(id)` (wrapped in
  try/catch → `log.warn` on failure). `MemberSyncService.syncMember` (`:70-120`, the `/internal/members/sync` path)
  creates members **without** touching invitations — but nothing in the repo calls that endpoint in Keycloak mode
  (only docs/plans reference it), so Bob and Carol were created by `lazyCreateMember`.
- **CONFIRMED evidence that the invitation lookup worked**: day-00 rows 0.28/0.33/CP A–D.2 show Bob landed as
  **Admin** and Carol as **Member** — the Admin role can only come from the pending invitation (`MemberFilter.java:158-160`),
  so `acceptedInvitationId` was set and `markAccepted` was invoked.
- **CONFIRMED — the real defect is in the list, not the state**:
  - `backend/.../invitation/InvitationController.java:39-44` `GET /api/invitations?status=` — `status` is optional;
    without it `InvitationService.listInvitations(null)` (`:129-137`) returns **all statuses**
    (`findAllByOrderByCreatedAtDesc()`).
  - `frontend/app/(app)/org/[slug]/team/invitation-actions.ts:111-125` `listInvitationsBff()` calls
    `api.get("/api/invitations")` with **no `status` filter**, maps `status` through but nobody filters on it.
  - `frontend/components/team/pending-invitations.tsx:24-28` renders every returned row with a Revoke button;
    `frontend/components/team/invite-member-form.tsx:76-80` sets `pendingCount = invitations.length`;
    `:166` `totalUsed = currentMembers + pendingInvitations` → "5 members (2 pending)" = 3 members + 2 ACCEPTED
    invitations counted as pending. The page-header pill ("3 members", `team/page.tsx:35`) reads
    `billing.limits.currentMembers` = `memberRepository.count()` (`SubscriptionService.java:62`) and is correct.
  - **HYPOTHESIS (likely)**: the DB rows for bob@/carol@ are `ACCEPTED` (no DB evidence captured — walker only
    recorded the UI; `logs/walkB-db-day02-checklist-audit.txt:13-14` shows only the `invitation.created` audit rows).
    If a reproduce shows them still `PENDING`, the fallback explanation is the swallowed `markAccepted` warn at
    `MemberFilter.java:206-211` (check backend log for "Failed to mark invitation").
  - Secondary: the Revoke button is offered on ACCEPTED rows; `revokeInvitation` would 409 ("Only PENDING
    invitations can be revoked", `InvitationService.java:146-151`).
- **CONFIRMED** the unit test mocks only `status: "pending"` rows (`components/team/pending-invitations.test.tsx:51,69,89`),
  so the unfiltered list was never caught.

### Root cause
The Team page's "pending" list and count are built from an **unfiltered** `GET /api/invitations` (all statuses).
Accepted invitations therefore stay visible and are added to the member count. (Reconciliation on registration
most likely already works; it is the read side that is wrong.)

### Recommended fix
1. `listInvitationsBff()` → `api.get("/api/invitations?status=PENDING")` (backend already supports it), and/or
   filter `inv.status === "PENDING"` client-side as belt-and-braces. One-line fix; both the tab and the
   `(n pending)` count derive from it.
2. Change the seat line to not add invitations to "members": e.g. "3 members · 2 pending invitations" — the current
   `totalUsed` wording is a leftover of the plan-tier seat model (memory: no plan-tier subscriptions).
3. Defensive reconcile (optional, backend): in `InvitationService.listInvitations` or `findPendingByEmail`, if
   `memberRepository.existsByEmail(invite.email)` mark it ACCEPTED — covers members created through
   `MemberSyncService` or a swallowed `markAccepted`. Keep as a separate PR if adopted (scope discipline).
Reproduce first: query `pending_invitations` in tenant `tenant_5039f2d497cf` (or re-run the invite flow on the KC
stack) to confirm the stored status before choosing between fix 1 alone and 1+3.

### Tests
- Frontend: `frontend/components/team/pending-invitations.test.tsx`, `components/team/invite-member-form.test.tsx`,
  `__tests__/invite-role-selection.test.tsx` — add an ACCEPTED row to the mocked list and assert it is hidden /
  not counted.
- Backend: `invitation/InvitationServiceTest.java` (no `listInvitations` test today — add status-filter case if the
  backend changes), `orgrole/RoleAssignmentTest.java` (invitation-role path), `MemberFilter` provisioning tests.
- Playwright: `frontend/e2e/tests/keycloak/onboarding.spec.ts` (KC-mode invite/register flow) is the right place
  for an observed check that the tab empties after registration.

---

## 5. G-A1 — Platform-admin Access Requests has no detail view

### Findings
- **CONFIRMED** everything the requester submits is in the row. Submission contract
  `backend/.../accessrequest/dto/AccessRequestDtos.java:14-19` `AccessRequestSubmission(email, fullName,
  organizationName, country, industry)`; the public form `frontend/components/access-request/request-access-form.tsx:312-369`
  has exactly those five inputs (plus the OTP step). There is **no message/notes, IP, referrer or user-agent** field
  on the entity (`AccessRequest.java:22-71`: id, email, fullName, organizationName, country, industry, status,
  otpHash, otpExpiresAt, otpAttempts, otpVerifiedAt, reviewedBy, reviewedAt, keycloakOrgId, provisioningError,
  createdAt, updatedAt).
- **CONFIRMED** the row renders org, email, full name, country, industry, submitted (`createdAt`), status, and
  Approve/Reject for PENDING (`frontend/components/access-request/access-requests-table.tsx:70-95`).
- Fields in the admin response DTO but **not** in the row (`AccessRequestDtos.java:25-37`): `otpVerifiedAt`,
  `reviewedAt`, `reviewedBy`, `updatedAt` — review/process metadata, not "fields Thandi submitted". `keycloakOrgId`
  and `provisioningError` are not even exposed in the DTO.

### Conclusion (facts for the product decision)
Nothing submitted is hidden. A detail view would add only `otpVerifiedAt / reviewedBy / reviewedAt`. The
full-lifecycle scenario's equivalent step already says "inline fields". Amending the 90-day step
(doc line 81) to "verify the row shows all fields Thandi submitted" is a scenario change → needs authorisation
(CLAUDE.md §6); no code change recommended unless the product wants the review metadata surfaced.

### Tests
- `frontend/__tests__/access-request/access-requests-table.test.tsx` (row rendering + tab filtering);
  Playwright `frontend/e2e/tests/keycloak/onboarding.spec.ts`, `demo-full-90day.spec.ts`.

---

## 6. G-C1 — Adverse-party type has no Government / organ-of-state value

### Findings
- **CONFIRMED** there is **no backend enum**: `backend/.../verticals/legal/conflictcheck/AdverseParty.java:29-30`
  `@Column(name = "party_type", nullable = false, length = 20) private String partyType;`. Create/update requests
  validate only `@NotBlank String partyType` (`AdversePartyService.java:61,69`). The registry list filter passes
  `partyType` through as an opaque string (`AdversePartyController.java:40-42`).
- **CONFIRMED** schema: `backend/src/main/resources/db/migration/tenant/V1__baseline.sql:87`
  `party_type character varying(20) NOT NULL` — **no CHECK constraint, no lookup table**. Any value ≤ 20 chars is
  accepted today (`GOVERNMENT` = 10, `STATE_ORGAN` = 11, `PUBLIC_BODY` = 11, `ORGAN_OF_STATE` = 14 all fit). No migration
  needed.
- **CONFIRMED** the value list lives only in the frontend (four sites):
  1. `frontend/lib/types/legal.ts:106-113` `export type AdversePartyType = "NATURAL_PERSON" | "COMPANY" | "TRUST" |
     "CLOSE_CORPORATION" | "PARTNERSHIP" | "OTHER"`.
  2. `frontend/lib/schemas/legal.ts:132-135` `createAdversePartySchema.partyType: z.enum([...same six...])`.
  3. `frontend/components/legal/adverse-party-dialog.tsx:34-41` option list (labels).
  4. `frontend/app/(app)/org/[slug]/legal/adverse-parties/adverse-party-registry-client.tsx:31-50` label `switch`
     and `:155-162` the filter `<select>` options.
- **CONFIRMED** not referenced elsewhere: no `partyType` in the portal (`portal/` grep: none), none in conflict-check
  matching (`conflictcheck/ConflictCheckService` matches on name/id/registration, not type), none in `@b2mash/shared`.
  Backend tests referencing party types: `AdversePartyServiceTest`, `AdversePartyControllerTest`,
  `ConflictCheckServiceTest`, `ConflictCheckControllerTest` (use string literals; unaffected by a new value).

### What adding a type requires
Frontend-only: add the value + label at the four sites above (types union, zod enum, dialog options, registry
switch + filter). No backend enum, no migration, no portal change. If a backend whitelist is wanted for integrity,
that is a new `@Pattern`/enum on `AdversePartyService` request records (+ a CHECK constraint migration) — a
separate decision; today the backend accepts any string.

### Product question
Which label/value: `GOVERNMENT` ("Government / organ of state") covering RAF, SARS, municipalities, provincial
departments — or split `STATE_ORGAN` vs `PUBLIC_BODY`. One value is the minimal change; it also needs the 90-day
scenario wording ("entity type = Government") left as-is rather than amended.

### Tests
- Frontend: `frontend/__tests__/legal/adverse-parties.test.tsx` (dialog + registry), `__tests__/legal/conflict-check.test.tsx`.
- Playwright: `frontend/e2e/tests/legal-lifecycle/day-75-complex.spec.ts`, `demo-full-90day.spec.ts` (RAF step).
- Backend: none required (string column); run `verticals/legal/conflictcheck/*Test` for safety if the service DTOs change.

---

## 7. Blast radius summary

| Gap | Files to change | Tests to run (targeted) | Full gate |
|---|---|---|---|
| G-C2 | `legal/adverse-parties/actions.ts:136-142` (+ optional `link-adverse-party-dialog.tsx:76`) | `__tests__/legal/adverse-parties.test.tsx`, `legal-wire-bodies.test.ts`, `project-legal-tabs.test.tsx`; PW `day-07-14-work-trust`, `day-75-complex` | frontend `lint && build && test` (+ prettier) |
| G-D4 | `lib/api/reports.ts:113-168` (both exporters) — also fixes `trust-accounting/client-ledgers/actions.ts:141-153` | `__tests__/reports-api.test.ts`, `report-runner.test.tsx`, `trust-investments-reports.test.tsx`; PW `finance/reports.spec.ts`; **observed KC-mode export** | frontend gate; no backend change |
| G-D5 | `my-work/page.tsx`, `components/dashboard/todays-agenda.tsx`, maybe `lib/dashboard-home-fetch.ts`; backend only if `limit` param added | `__tests__/dashboard/todays-agenda.test.tsx`, `my-work-dashboard.test.tsx`, `__tests__/diary/*`; backend `diary/*Test`, `DashboardPersonalIntegrationTest` | frontend gate (+ `./mvnw verify` if backend touched) |
| G-D12 | `team/invitation-actions.ts:113`, `components/team/invite-member-form.tsx:166,380-387`; optional backend reconcile in `InvitationService` | `components/team/pending-invitations.test.tsx`, `invite-member-form.test.tsx`, `__tests__/invite-role-selection.test.tsx`; backend `InvitationServiceTest`, `RoleAssignmentTest`; PW `keycloak/onboarding.spec.ts` | frontend gate (+ `./mvnw verify` if backend touched) |
| G-A1 | none (scenario wording) | `access-requests-table.test.tsx` unchanged | — |
| G-C1 | `lib/types/legal.ts`, `lib/schemas/legal.ts`, `components/legal/adverse-party-dialog.tsx`, `adverse-party-registry-client.tsx` | `__tests__/legal/adverse-parties.test.tsx`, `conflict-check.test.tsx`; PW `day-75-complex` | frontend gate |

## 8. Product questions (need authorisation, not agent calls)
1. G-D4: add an export to the bespoke Profitability page (new backend endpoint) or re-point the scenario at the
   `project-profitability` report definition?
2. G-D5: window for "Upcoming" (14 vs 30 days); admins/owners firm-wide or personal; court-date attribution is
   matter-lead only — acceptable?
3. G-D12: wording of the seat line ("3 members · 2 pending") and whether to add the backend reconcile (fix 3).
4. G-A1: amend 90-day step line 81 to inline-row verification (recommended) vs build a detail view that would only add
   OTP-verified / reviewed-by / reviewed-at.
5. G-C1: single `GOVERNMENT` value vs split; backend whitelist or keep free string.
