# Fix Spec: GAP-PE-005 — Portal frontend has no proposals page

## Problem
The backend has a complete portal proposals API at `/portal/api/proposals` (list, detail, accept, decline) — all functional and tested via API calls. However, the portal frontend (port 3002) has NO proposals route, no proposals nav item, and no proposals page. Portal contacts cannot view, accept, or decline proposals through the UI.

Evidence from QA Cycle 2 (T4.4.1): "No portal frontend page for proposals. The portal backend has endpoints... but the portal frontend has NO proposals page — only projects, invoices, profile."

The portal nav (`portal/components/portal-header.tsx` line 12-15) only has Projects and Invoices:
```typescript
const NAV_LINKS = [
  { href: "/projects", label: "Projects" },
  { href: "/invoices", label: "Invoices" },
];
```

## Root Cause
This is a **feature gap**, not a bug. The portal proposals backend was built (Phase 32) but the portal frontend page was never implemented. The `PendingAcceptancesList` component on the projects page handles *document* acceptance requests, not proposal acceptance.

## Feasibility Assessment
A minimal proposals page requires:
1. **Proposals list page** — list SENT proposals for the current customer with title, fee, status, date
2. **Proposal detail page** — show proposal content (rendered HTML), fee details, accept/decline buttons
3. **Accept/decline actions** — call `/portal/api/proposals/{id}/accept` and `/portal/api/proposals/{id}/decline`
4. **Nav link** — add "Proposals" to the header nav

The backend API already returns all needed data (`PortalProposalSummary` and `PortalProposalDetail` records). The portal has existing patterns for list/detail pages (projects, invoices). The API client (`portalGet`, `portalPost`) is already set up.

**Estimated time: 1.5-2 hours** for a minimal but functional proposals UI. This is within the 2-hour threshold.

## Fix

### 1. Add proposals list page
Create `portal/app/(authenticated)/proposals/page.tsx`:
- Fetch `GET /portal/api/proposals` via `portalGet`
- Display list of proposals with: title, proposal number, fee amount, status badge (SENT/ACCEPTED/DECLINED/EXPIRED), sent date
- Filter to show only SENT proposals prominently (with a section for past proposals)
- Link each SENT proposal to detail page

### 2. Add proposal detail page
Create `portal/app/(authenticated)/proposals/[id]/page.tsx`:
- Fetch `GET /portal/api/proposals/{id}` via `portalGet`
- Display: firm name, proposal title, content HTML (rendered from backend), fee details (model, amount, currency), expiry date
- For SENT proposals: show Accept and Decline buttons
- Accept: `POST /portal/api/proposals/{id}/accept` → show success message, redirect to projects
- Decline: show a reason text input, `POST /portal/api/proposals/{id}/decline` with `{ reason }` → show confirmation

### 3. Add nav link
In `portal/components/portal-header.tsx`, add to `NAV_LINKS`:
```typescript
const NAV_LINKS = [
  { href: "/projects", label: "Projects" },
  { href: "/proposals", label: "Proposals" },
  { href: "/invoices", label: "Invoices" },
];
```

### 4. Add types
In `portal/lib/types.ts` (or wherever portal types live), add:
```typescript
export interface PortalProposal {
  id: string;
  proposalNumber: string;
  title: string;
  status: string;
  feeModel: string;
  fixedFeeAmount?: number;
  fixedFeeCurrency?: string;
  sentAt: string;
  expiresAt?: string;
  orgName: string;
}

export interface PortalProposalDetail extends PortalProposal {
  contentHtml: string;
  retainerAmount?: number;
  retainerCurrency?: string;
  retainerHoursIncluded?: number;
  hourlyRateNote?: string;
}
```

## Scope
Portal frontend only
Files to create:
- `portal/app/(authenticated)/proposals/page.tsx` — proposals list page
- `portal/app/(authenticated)/proposals/[id]/page.tsx` — proposal detail page with accept/decline
Files to modify:
- `portal/components/portal-header.tsx` — add Proposals nav link
- `portal/lib/types.ts` — add proposal types (if not already present)
Migration needed: no

## Verification
1. Login as Naledi portal contact
2. See "Proposals" in nav header
3. Click Proposals — see list of proposals (SENT ones prominently)
4. Click into a SENT proposal — see full detail with content, fee info
5. Accept a proposal — success message, project auto-created
6. Decline a proposal — enter reason, see confirmation
7. Re-run T4.4.1 through T4.5.4

## Estimated Effort
M (30 min - 2 hr) — new pages but follows existing portal patterns closely
