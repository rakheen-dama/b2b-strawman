# CR-002: Settings Items Viewability

**Bug**: BUG-002 — Settings items (templates, compliance packs, checklists) cannot be opened or viewed
**Severity**: Medium
**Status**: Design complete — ready for implementation

---

## Problem Summary

Three Settings sub-pages (Document Templates, Compliance Packs, Checklists) render items as static text. Users cannot inspect item contents — template HTML, pack checklist items, or checklist item details are invisible. Custom checklists cannot be edited after creation despite the backend supporting updates.

## Investigation Findings

### A) Document Templates

| Aspect | Current State | Gap |
|--------|---------------|-----|
| Name in list | `<span>` plain text | Not a `<Link>` |
| PLATFORM template actions | Clone & Customize, Deactivate | No View or Edit |
| ORG_CUSTOM template actions | Edit, Reset to Default, Deactivate | Edit works — but not reachable via name click |
| View-only route | Does not exist | `/settings/templates/[id]` → 404 |
| Edit route | `/settings/templates/[id]/edit/page.tsx` exists | Admin-only, works for ORG_CUSTOM |
| Non-admin access | Entire actions column hidden | Cannot view any template content |
| Backend API | `GET /api/document-templates/{id}` returns full detail including body | Ready |

### B) Compliance Packs

| Aspect | Current State | Gap |
|--------|---------------|-----|
| Display | Static `<div>` with raw `packId`, version, date | Not clickable, no human-readable name |
| Pack contents | Hidden entirely | Checklist items, field defs, retention rules not surfaced |
| Backend detail API | Does not exist | No `/api/compliance-packs/{packId}` endpoint |
| Pack definitions | 3 packs on classpath (JSON files) | Not queryable at runtime |
| Data stored | Only `{packId, version, appliedAt}` in JSONB | `name`, `description`, `jurisdiction` not persisted |

### C) Checklists

| Aspect | Current State | Gap |
|--------|---------------|-----|
| Name in list | `<p>` plain text | Not a `<Link>` |
| Actions available | Clone, Deactivate | No View, no Edit |
| Detail route | Does not exist | No `[id]/` route under `checklists/` |
| Edit route | Does not exist | No `[id]/edit/` route |
| Backend GET by ID | Returns full `ChecklistTemplateResponse` with items | Ready |
| Backend PUT update | `PUT /api/checklist-templates/{id}` exists | Ready but unreachable from UI |
| Frontend API helper | No `getChecklistTemplateById()` | Only list + server actions for clone/deactivate |

---

## Design

### ADR: Inline Expansion vs Detail Pages

**Decision**: Use **detail pages** for templates and checklists; use **inline expansion** for compliance packs.

**Rationale**:
- Templates have rich HTML/CSS content that benefits from a full-page view (the editor form already exists)
- Checklists have ordered item lists with metadata (required, document requirement) — a detail page provides room for the item table + future edit capability
- Compliance packs are read-only, seeded, and have 3–5 items each — inline expansion (accordion/collapsible) is proportionate; a full detail page would feel empty

### A) Document Templates — Make Viewable

**Changes** (4 files modified, 0 new files):

1. **`templates-content.tsx`** — Wrap template name in `<Link>` to edit page for all templates (PLATFORM and ORG_CUSTOM). The edit page will handle read-only mode.

2. **`templates/[id]/edit/page.tsx`** — Remove the admin-only redirect. All authenticated users can view templates. Pass `readOnly` prop to editor when user is non-admin OR template source is PLATFORM.

3. **`TemplateEditorForm.tsx`** — Accept `readOnly?: boolean` prop. When true: disable all inputs, hide Save button, change page title to "View Template". Preview button remains accessible.

4. **`TemplateActionsMenu.tsx`** — Add "View" action for PLATFORM templates (navigates to same `/[id]/edit` route, which will render read-only).

**Behavior matrix**:

| User Role | Template Source | Name Click | Actions Menu | Editor Mode |
|-----------|----------------|------------|--------------|-------------|
| Admin/Owner | ORG_CUSTOM | Opens editor (edit mode) | Edit, Reset, Deactivate | Editable |
| Admin/Owner | PLATFORM | Opens editor (read-only) | View, Clone, Deactivate | Read-only |
| Member | Any | Opens editor (read-only) | (no actions column) | Read-only |

### B) Compliance Packs — Inline Expansion

**Changes** (2 files modified, 1 new backend endpoint):

1. **Backend: `CompliancePackController.java`** (NEW) — Add `GET /api/compliance-packs/{packId}` endpoint that reads the pack JSON from classpath and returns a `CompliancePackDetailResponse`:
   ```
   {
     packId, name, description, version, jurisdiction, customerType,
     checklistTemplate: { name, items: [...] },
     fieldDefinitions: [...],
     retentionOverrides: [...]
   }
   ```
   This endpoint is read-only and tenant-scoped (uses the tenant's schema context but reads classpath files).

2. **Frontend: `compliance/page.tsx`** — Replace static `<div>` with an expandable/collapsible row. On expand, fetch `GET /api/compliance-packs/{packId}` and display:
   - Pack name (human-readable) and description
   - Checklist items table (name, description, required badge, document requirement)
   - Field definitions list (label, type, required)
   - Retention overrides table (record type, trigger, days, action)

   Display the human-readable `name` as the row title (instead of raw `packId`). Show `packId` as a subtle subtitle/badge.

3. **Frontend: `lib/api.ts` or `lib/compliance-api.ts`** — Add `getCompliancePackDetail(packId)` helper.

### C) Checklists — Detail/View Page + Edit Support

**Changes** (3 files modified, 2 new files):

1. **`checklists/[id]/page.tsx`** (NEW) — Detail page following the settings `[id]` pattern:
   - Fetches `GET /api/checklist-templates/{id}`
   - Displays: name, description, source badge (PLATFORM/ORG_CUSTOM), pack badge, customer type, auto-instantiate flag, active status
   - Items table: ordered list showing name, description, required badge, document requirement badge, depends-on indicator
   - For ORG_CUSTOM templates when user is admin: show Edit button (inline edit or link to edit route)
   - For PLATFORM templates: read-only, no edit capability

2. **`checklists/page.tsx`** — Wrap template name `<p>` in `<Link>` to `/settings/checklists/{id}`.

3. **`ChecklistTemplateActions.tsx`** — Add "View" action for all templates (navigates to detail page). Add "Edit" action for ORG_CUSTOM templates (navigates to detail page in edit mode, or separate edit route).

4. **`checklists/[id]/edit/page.tsx`** (NEW, optional) — Edit page for ORG_CUSTOM templates. Reuses the creation form pattern from `checklists/new/page.tsx` but pre-populates fields. Calls `PUT /api/checklist-templates/{id}`. Admin-only.

5. **`lib/checklist-api.ts`** — Add `getChecklistTemplateById(id)` and `updateChecklistTemplate(id, data)` helpers.

---

## Implementation Order

The three sub-items are independent and can be implemented in parallel. Suggested order for serial execution:

1. **A) Document Templates** (~30 min) — Smallest change, no new files or endpoints, just wiring existing pieces together
2. **C) Checklists** (~1.5 hr) — New detail page + edit page, but backend is ready
3. **B) Compliance Packs** (~1.5 hr) — Requires new backend endpoint + frontend expansion UI

**Total estimate**: ~3.5 hours for a single developer, or ~1.5 hours with parallel execution.

---

## Files Affected (Summary)

### Backend (1 new file)
- `CompliancePackController.java` — NEW: `GET /api/compliance-packs/{packId}`

### Frontend — Templates (4 modified)
- `settings/templates/templates-content.tsx` — name → Link
- `settings/templates/[id]/edit/page.tsx` — remove admin-only redirect, add readOnly support
- `components/templates/TemplateEditorForm.tsx` — accept readOnly prop
- `components/templates/TemplateActionsMenu.tsx` — add View action for PLATFORM

### Frontend — Compliance (2 modified, 1 new)
- `settings/compliance/page.tsx` — expandable pack rows
- `lib/compliance-api.ts` or `lib/api.ts` — add pack detail fetcher
- `lib/types.ts` — add `CompliancePackDetail` type

### Frontend — Checklists (3 modified, 2 new)
- `settings/checklists/page.tsx` — name → Link
- `components/compliance/ChecklistTemplateActions.tsx` — add View/Edit actions
- `lib/checklist-api.ts` — add getById + update helpers
- `settings/checklists/[id]/page.tsx` — NEW: detail/view page
- `settings/checklists/[id]/edit/page.tsx` — NEW: edit page for ORG_CUSTOM

### Tests
- Backend: integration test for `GET /api/compliance-packs/{packId}` (classpath resolution, 404 for unknown pack)
- Frontend: test for checklist detail page rendering (items display, read-only vs editable modes)
- Frontend: test for template editor read-only mode
- Frontend: test for compliance pack expansion

---

## Risk Assessment

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| Compliance pack classpath resolution fails in prod | Low | Pack files are already loaded by `CompliancePackSeeder` at startup — same resolution mechanism |
| Template readOnly mode still allows accidental saves | Low | Server-side: PLATFORM templates reject PUT via service validation. Client-side: Save button hidden + inputs disabled |
| Checklist edit breaks existing instances | Low | Editing a template doesn't retroactively change instantiated checklists — instances are copies |
| Non-admin users confused by read-only editor | Low | Clear visual indicator (banner or disabled state) communicates non-editable |

---

## Out of Scope

- **BUG-003** (Template preview entity picker) — tracked separately, can be combined with Template changes
- **Checklist item reordering** — future enhancement, not part of this fix
- **Compliance pack editing** — packs are seeded/immutable by design; only viewing is added
- **Bulk operations** — no multi-select needed for this fix
