# Phase 49 — Code Review Notes for Architect

From PR #735 code review. These items were deferred as acceptable for now but warrant architectural attention.

## 1. Slug-based clause lookup is O(n) per clause block

**File**: `backend/src/main/java/.../template/TiptapRenderer.java` (lines 157-161)

The slug fallback iterates all clause values with `.stream().filter().findFirst()`. For engagement letters with 4-7 clause blocks, this performs 4-7 linear scans over the clause map. Currently fine, but if clause libraries grow beyond ~20 clauses per template, this becomes quadratic.

**Recommendation**: Build a `Map<String, Clause>` slug-to-clause index at the call site before passing to the renderer. Simple change, worth doing proactively.

## 2. resolveDropdownLabels queries DB on every call

**File**: `backend/src/main/java/.../template/TemplateContextHelper.java` (line 142)

Each call to `resolveDropdownLabels` hits `fieldDefinitionRepository.findByEntityTypeAndActiveTrueOrderBySortOrder()`. A single template render can invoke this 3-4 times (project fields, customer fields, nested customer in project context). Fine for single doc generation but will be a problem for batch operations (e.g., generating all customer statements).

**Recommendation**: Cache field definitions per entity type within a request scope, or pre-fetch all needed entity types at the start of rendering.

## 3. VariableFormatter hardcoded to ZA locale

**File**: `backend/src/main/java/.../template/VariableFormatter.java` (line 43)

Currency formatting uses `Locale.of("en", "ZA")` with an existing TODO about multi-currency. The new loopTable `format: "currency"` attribute routes through this same formatter, meaning all table cells render as ZAR regardless of the invoice's actual currency. Correct for accounting-za vertical, needs attention for multi-currency/multi-locale support.

**Recommendation**: Pass locale/currency from the rendering context (org settings or invoice currency) to `VariableFormatter`. This aligns with the existing `defaultCurrency` field on `OrgSettings`.

## 4. InvoiceContextBuilder customerVatNumber uses raw value

**File**: `backend/src/main/java/.../template/InvoiceContextBuilder.java` (lines 114-118)

The top-level `customerVatNumber` alias reads from `customer.getCustomFields()` (raw storage value) while `customer.customFields` in the context map has been through `resolveDropdownLabels`. Since `vat_number` is a TEXT field (not DROPDOWN), this doesn't cause a bug today, but the inconsistency could bite if field types change.

**Recommendation**: Read the alias from the already-resolved context map instead of from the raw entity.

## 5. Portal acceptance page missing (GAP-P49-005) — Track T6 blocked

**Severity**: Blocker for e-signing workflow

The entire document acceptance / e-signing track (Track 6 — 25 checkpoints) could not be tested because the portal has no acceptance page. The backend is fully implemented, and the firm-side UI exists — only the client-facing portal page is missing.

### What exists (backend)

- `PortalAcceptanceController.java` — REST API at `/api/portal/acceptance/{token}`:
  - `GET /{token}` — view acceptance request details
  - `GET /{token}/pdf` — stream the PDF for in-browser viewing
  - `POST /{token}/accept` — accept with typed name, records IP + timestamp
- `AcceptanceService.java` — token resolution, accept/view/revoke logic, expiry handling
- `AcceptanceCertificateService.java` — generates Certificate of Acceptance PDF with SHA-256 document hash

### What exists (firm-side frontend)

- `frontend/components/acceptance/SendForAcceptanceDialog.tsx` — firm user sends doc for acceptance
- `frontend/components/acceptance/AcceptanceDetailPanel.tsx` — shows acceptance status, metadata, certificate download
- `frontend/components/acceptance/AcceptanceStatusBadge.tsx` — status indicator

### What's missing (portal frontend)

No page exists at `frontend/app/portal/` for acceptance. The portal only has:
- `frontend/app/portal/(authenticated)/documents/` — document viewing
- `frontend/app/portal/(authenticated)/projects/` — project viewing
- `frontend/app/portal/(authenticated)/requests/` — information request responses

### Recommendation

Create a portal acceptance page, likely at `frontend/app/portal/accept/[token]/page.tsx`. This should be **outside** the `(authenticated)` layout since acceptance uses token-based auth (magic link from email), not session auth. The page needs:

1. Call `GET /api/portal/acceptance/{token}` to load acceptance details
2. Embed or link the PDF (`GET /api/portal/acceptance/{token}/pdf`)
3. Acceptance form: full name text input + "I Accept" button
4. Call `POST /api/portal/acceptance/{token}/accept` with typed name
5. Confirmation screen after acceptance
6. Handle expired tokens gracefully (show expiry message)

**Effort**: L (half day) — new page + PDF viewer + form + confirmation + error states.

**Dependencies**: None — all backend APIs exist and are tested. This is purely a frontend build task.
