# Fix Spec: GAP-L-67 — Information Request dialog supports ad-hoc title + items

**Status**: SPEC_READY (cycle 39)
**Severity**: HIGH
**Owner**: Dev (frontend)
**Effort**: M (~1.5 hr)
**Branch**: cut `fix/GAP-L-67` off `bugfix_cycle_2026-04-26-day45`

## Problem

When Bob (admin) tries to create the scenario's REQ-0004 "Supporting medical evidence" with 2 ad-hoc items (per Day 45 §45.1), the `Create Information Request` dialog only exposes a Template selector. Picking "Ad-hoc (no template)" creates a 0-item DRAFT, and the dialog closes silently — leaving Bob on the customer page with no obvious next step. To reach the items the scenario requires, Bob must (a) navigate to the request list, (b) click the new draft, (c) press "+ Add Item" twice on the detail page (a button he didn't know existed), then (d) press "Send Request". That 5-step workaround is undiscoverable, so cycles 1 + 38 both substituted FICA Onboarding Pack (3 items) for the scenario's 2 ad-hoc items.

Backend already supports inline ad-hoc creation: `CreateInformationRequestRequest.items` is wired through `InformationRequestService.createAdHoc()` (line 305-317 in `InformationRequestService.java`). The frontend just doesn't collect the items.

Evidence:
- `qa_cycle/checkpoint-results/cycle38-day45-2-new-request-dialog.yml` — dialog shows Template / Portal Contact / Reminder / Due Date — no Items affordance.
- `qa_cycle/checkpoint-results/cycle38-day45-3-template-options.yml` — template list has FICA / L&D Account / 2 others, no "Medical evidence" pack.
- `qa_cycle/checkpoint-results/day-45.md §Cycle 38 §45.1` — full failing flow trace.

## Root Cause (verified at code level)

**Frontend dialog never collects items**:
- `frontend/components/information-requests/create-request-dialog.tsx:60` — only state vars: `templateId`, `portalContactId`, `reminderDays`, `dueDate`. No `items` state.
- `create-request-dialog.tsx:117-124` — `createRequestAction` payload has no `items` field.
- `create-request-dialog.tsx:174-271` — dialog body has no Items section at all.
- The "Save as Draft" path on `create-request-dialog.tsx:143-144` simply closes the dialog with no navigation; user has no signal that the new draft is empty + un-sendable.
- The "Send Now" path on `create-request-dialog.tsx:131-141` would call `POST /api/information-requests/{id}/send` against a 0-item draft, which throws `InvalidStateException("Cannot send empty request", "Information request must have at least one item before sending")` from `InformationRequestService.java:351-354`. The error is caught silently and the dialog closes, hiding the failure.

**Backend already supports ad-hoc with items inline**:
- `backend/.../dto/InformationRequestDtos.java:19-26` — `CreateInformationRequestRequest` accepts `@Valid List<AdHocItemRequest> items`.
- `backend/.../dto/InformationRequestDtos.java:31-37` — `AdHocItemRequest(name, description, responseType, required, fileTypeHints, sortOrder)`.
- `backend/.../InformationRequestService.java:147-168` — `create(CreateInformationRequestRequest)` routes to `createAdHoc(...)` when `requestTemplateId == null`, threading `request.items()` through.
- `backend/.../InformationRequestService.java:305-317` — `createAdHoc` iterates the items list and persists each one as a `RequestItem`.

**Frontend API client is missing the items field**:
- `frontend/lib/api/information-requests.ts:141-149` — `CreateInformationRequestRequest` interface DOES already declare `items?: CreateInformationRequestItem[]` (good — no API-layer change needed).

**Important clarification — the "request" itself has no title**:
- `backend/.../InformationRequest.java` — entity has `requestNumber`, `customerId`, `projectId`, `portalContactId`, `status`, etc. **No `name`/`title` column.** The displayed identifier is `requestNumber` (e.g., REQ-0004).
- Only `RequestItem.name` exists. So the "Title" the QA agent referenced is shorthand for "the first item's name" / "the overall ask"; it does NOT need a request-level title field. The scenario's "Title: Supporting medical evidence" maps cleanly to a request whose **items** describe the same thing (e.g. item 1 = "Latest specialist medical reports", item 2 = "Independent expert assessment").

**Ancillary observation — Option B is partly already shipped**:
- PR #1156 (commit `9e2be451`, on main) already added `AddItemDialog` (`frontend/components/information-requests/add-item-dialog.tsx`) and a "Send Request" button to `request-detail-client.tsx:184-193,286-295`, gated on `request.status === "DRAFT"`. Both work correctly. The QA agent in cycles 1 + 38 didn't navigate to the draft detail page after creating the empty ad-hoc draft, so they never saw the existing fix.

## Fix (Option A — collect items inline in the create dialog)

Picking Option A over Option B because:
1. Single-flow create-with-items matches the scenario's mental model ("create a request titled X with 2 items, send it") and matches every other "create with body" form in the app (proposals, invoices).
2. Backend route already supports it — zero API churn.
3. The current "click Save as Draft → guess where the draft went → click into it → click + Add Item twice → click Send" is 5 hops with no breadcrumb. Inline items collapses to 1 hop.
4. Smaller blast radius than backfilling navigation logic + empty-state copy + a "send blocked: no items" toast on the customer page.

Option B (existing AddItemDialog on detail page) stays in place as a fallback for editing drafts.

### Step-by-step changes

**File**: `frontend/components/information-requests/create-request-dialog.tsx`

1. Add an `AdHocItem` shape and items state at the top of the component (after the existing `dueDate` state, around line 63):
   ```tsx
   type AdHocItem = {
     id: string; // local-only key for React list rendering
     name: string;
     description: string;
     responseType: "FILE_UPLOAD" | "TEXT_RESPONSE";
     required: boolean;
   };

   const [items, setItems] = useState<AdHocItem[]>([]);
   ```

2. Add `items: []` to `resetForm()` (around current line 89-97).

3. Render an Items section ONLY when `templateId === "ad-hoc"`, placed AFTER the Due Date section and BEFORE the info display (around current line 250-251). The section should:
   - Header row with `<Label>Items</Label>` + an "Add Item" outline button on the right that appends a new blank `AdHocItem` (uuid via `crypto.randomUUID()`) and focuses the new name input.
   - For each item: a row with Name input (required, placeholder "e.g. Latest specialist medical reports"), Description input (optional), ResponseType select (FILE_UPLOAD / TEXT_RESPONSE), Required checkbox, and a trash-icon button to remove.
   - Empty-state text "Add at least one item before sending." when `items.length === 0`.
   - Use the same `space-y-2` / `Label` / `Input` pattern already used in the dialog. Use `Trash2` from `lucide-react` for the remove button.

4. Update `handleSubmit(sendImmediately)` (line 108-150):
   - When `templateId === "ad-hoc"` AND `sendImmediately === true`, validate `items.length > 0` and every item has a non-empty `name.trim()`. If invalid, set an error like "Add at least one item before sending." and return without submitting.
   - When `templateId === "ad-hoc"` AND `sendImmediately === false` (Save as Draft), allow zero items (matches existing behaviour and lets users add items on the detail page).
   - In the API payload at line 117-124, when `templateId === "ad-hoc"` AND `items.length > 0`, include:
     ```ts
     items: items.map((it, idx) => ({
       name: it.name.trim(),
       description: it.description.trim() || undefined,
       responseType: it.responseType,
       required: it.required,
       sortOrder: idx,
     }))
     ```
   - When `templateId !== "ad-hoc"`, do NOT send `items` (let the template seed them) — same as today.

5. Disable the "Send Now" button when `templateId === "ad-hoc" && items.length === 0` (line 292-303 — extend the existing `disabled` expression).

6. Disable the "Add Item" button while `isSubmitting` is true.

### What does NOT change

- `frontend/lib/api/information-requests.ts` — `CreateInformationRequestRequest.items` already exists.
- `frontend/components/information-requests/request-detail-client.tsx` — `AddItemDialog` and "Send Request" button stay (Option B, already shipped in PR #1156). They serve the "edit existing draft" case.
- Backend — no Java changes. `InformationRequestService.createAdHoc` already loops `items` and persists them.
- Database / migrations — no.

## Scope

- **Frontend only.** Single file: `frontend/components/information-requests/create-request-dialog.tsx`.
- Files to modify: `frontend/components/information-requests/create-request-dialog.tsx`.
- Files to create: none.
- Backend changes: none.
- Migration needed: no.

## Verification (browser-driven, Bob's session, full Day 45 §45.1 flow)

1. `bash compose/scripts/svc.sh status` — confirm all services healthy.
2. Bob (admin) logs into Keycloak frontend `:3000`, navigates to Sipho's RAF matter → Information Requests tab → click "+ New Request".
3. Dialog opens. Pick `Template: Ad-hoc (no template)`. Items section appears.
4. Click "Add Item" twice. Fill row 1 name = "Latest specialist medical reports", responseType = "File upload", required = checked. Fill row 2 name = "Independent expert assessment", responseType = "File upload", required = checked.
5. Pick Sipho portal contact, leave Reminder = 5, set Due Date = 2026-06-17 (Day 0 + 52).
6. Click "Send Now".
7. Dialog closes. New row REQ-NNNN appears in the request list with status SENT and 0/2 items.
8. Read-only DB SELECT confirms:
   - `information_requests` row with `request_template_id IS NULL`, `status='SENT'`, `due_date='2026-06-17'`, `sent_at` populated.
   - `request_items` 2 rows with `name` matching the scenario, `sort_order` 0 and 1, `status='PENDING'`.
9. Mailpit GET `/api/v1/messages?limit=5` confirms a magic-link email "Information request REQ-NNNN from Mathebula & Partners" delivered to `sipho.portal@example.com` with portal :3002 URL + token + orgId.
10. Negative path: open the dialog again, pick Ad-hoc, do NOT add any items, click "Send Now". Expect: `Send Now` button is disabled OR shows inline error "Add at least one item before sending." No backend call. No row in DB.
11. Save-as-Draft path with 0 items: open dialog, pick Ad-hoc, click "Save as Draft". Expect: row created with `status='DRAFT'`, 0 items. User can navigate to the detail page (existing flow) and use the AddItemDialog to add items.

## Estimated Effort

**M, ~1.5 hr** for one Dev agent including:
- Code change (~30 min).
- Vitest unit test on the dialog (~30 min) — assert (a) Items section hidden when template selected, (b) Items section shown when ad-hoc, (c) Send Now disabled with 0 items, (d) Send Now invokes `createRequestAction` with the items array shape.
- Browser-driven verification (~30 min).

## Tests

**New** (recommended):
- `frontend/__tests__/create-request-dialog.test.tsx` (new file) — Vitest + happy-dom. Cases:
  - `shows items section only when ad-hoc template selected`
  - `Add Item button appends a blank row`
  - `Trash button removes the row`
  - `Send Now disabled when ad-hoc + 0 items`
  - `Send Now disabled when any item name is blank`
  - `submitting calls createRequestAction with items array (sortOrder 0,1, trimmed names, required + responseType propagated)`
  - `template path does not include items in payload`
  - Use `afterEach(cleanup)` per the frontend Radix-leak rule. Use a unique trigger label per test to avoid "multiple elements found".

**Update**:
- None — `add-item-dialog.test.tsx` covers Option B and stays unchanged.
- No backend test churn — `InformationRequestService.createAdHoc` already has coverage from the `createAdHoc_*` cases in `InformationRequestServiceTest`.

## Regression Risk

- **Template path unchanged**: when `templateId !== "ad-hoc"` the payload omits `items`, identical to today. Verify no template-create regression by re-running cycle 38's REQ-0004 substitution flow and confirming FICA Onboarding Pack still creates a 3-item SENT request.
- **AddItemDialog on detail page unchanged**: Option B stays — DRAFTs created via Save-as-Draft (any path) can still use it.
- **No backend change**: zero risk to portal-side rendering, magic-link emails, reminder scheduler, or audit emission. Audit log already records `ad_hoc=true` for ad-hoc creates (`InformationRequestService.java:319-332`).
- **`crypto.randomUUID()` availability**: client-side; Next.js 16 ships with secure context. Acceptable on all supported browsers.
- **Form-state size**: rendered list is ~1-10 items; no virtualization required.

## Recommended Dev dispatch

Single Dev agent, single PR `fix/GAP-L-67` cut from `bugfix_cycle_2026-04-26-day45`. Frontend-only — HMR picks up changes, no svc.sh restart. Squash-merge into `bugfix_cycle_2026-04-26-day45`.
