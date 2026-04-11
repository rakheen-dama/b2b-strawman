# Fix Spec: GAP-PE-006 — No SHARED documents visible in portal

## Problem
All documents in the tenant schema have `INTERNAL` visibility. Generated documents (2 PDFs in `generated_documents` table) are in a separate table from portal-visible `documents`. There is no mechanism to auto-share generated documents to the portal or to change document visibility to SHARED through the firm-side UI. The portal correctly hides INTERNAL docs (showing "No documents shared yet"), but there is no practical way for documents to become portal-visible.

Evidence from QA Cycle 2 (T3.3.2): "All existing documents have INTERNAL visibility (not SHARED), so portal shows 'No documents shared yet' for every project."

## Root Cause Analysis
This is a **feature gap** at the intersection of two systems:

1. **Document visibility**: Documents have a `visibility` field (INTERNAL/SHARED) but the firm-side UI provides no button or dropdown to change a document's visibility to SHARED. The only way is direct DB manipulation.

2. **Generated documents**: The `generated_documents` table (from Phase 12) stores PDF generation records. These are separate from the `documents` table that the portal reads. Generated documents are never automatically added to the `documents` table with SHARED visibility.

Fixing this properly requires either:
- (A) Adding a "Share to portal" button on the firm-side document list (updates visibility to SHARED, fires `DocumentVisibilityChangedEvent` which `PortalEventHandler` already handles)
- (B) Auto-sharing generated documents when they are created for a customer-linked project
- (C) Both

## Feasibility Assessment
**Option A alone is sufficient for this bugfix cycle.** The `PortalEventHandler.onDocumentVisibilityChanged()` already handles the SHARED visibility transition correctly. We just need a UI affordance to trigger it.

However, this requires changes to both the firm-side frontend AND backend:
- Backend: endpoint to change document visibility (may already exist)
- Frontend: "Share" button on document list/detail

**Estimated time: ~2 hours** if the backend endpoint exists. Possibly more if it doesn't.

## Fix — WONT_FIX Rationale
After analysis, this gap requires:
1. A firm-side UI button to share individual documents (frontend changes)
2. Potentially a new backend endpoint for visibility toggle
3. Policy decisions about what happens when generated documents are created (auto-share or manual)
4. The firm-side frontend is a separate app (port 3000), not the portal being tested

This is a **product design decision** that goes beyond a bugfix. The portal correctly handles SHARED documents when they exist — the issue is that there is no workflow to create SHARED documents. This should be addressed as a feature enhancement in a future phase, not a bugfix.

**However**, if a minimal fix is desired: add a "Share with client" toggle to the existing document visibility in the firm-side document detail API. The `DocumentVisibilityChangedEvent` handler in `PortalEventHandler` will handle the rest.

## Scope
N/A (WONT_FIX — requires product design decision + firm-side UI changes)

## Estimated Effort
L (> 2 hr) — involves firm-side frontend + backend changes + product policy decision
