# Fix Spec: GAP-P-02 — Minimal stub portal UI for information-request pickup + upload

## Problem

Day 4 Checkpoint 4.8 halted on BLOCKER: the portal has **no UI route** for browsing, viewing, or responding to information requests. Backend `GET /portal/requests/{id}` returns a complete FICA payload (3 items, `FILE_UPLOAD` type, PDF/JPG/PNG hints, all PENDING) but there is nothing for the client to click.

Probes from the QA turn:

- `http://localhost:3002/requests` → 404 "Page not found"
- `http://localhost:3002/information-requests` → 404
- `http://localhost:3002/projects/{matterId}` → "The requested resource was not found" (separate issue — GAP-P-03)

Without these two pages, Day 4 Phase B (4.8–4.14) cannot execute, and downstream portal POV days (8/11/15/30/46/61/75) all implicitly depend on this same UI surface for either uploads, proposal acceptance, or status review.

## Root Cause (confirmed via file scan)

`ls portal/app/(authenticated)/` returns `deadlines, home, invoices, layout.tsx, profile, projects, proposals, retainer, settings, trust` — no `requests` or `information-requests` directory. Backend was shipped in Phase 494/498; the matching portal frontend was never authored.

The sidebar nav item for Requests already exists (`portal/lib/nav-items.ts` line 76–82) but is module-gated behind `information_requests`, which is not in any vertical profile's `enabledModules` (see GAP-P-01 for the module gate fix).

## Triage Decision: SPEC_READY — minimal stub

This fix sits at the upper edge of the <2hr budget. It is **SPEC_READY** on the explicit understanding that the deliverable is a functional stub — plain layout, reuses existing Card/Button primitives, no visual polish, no filters, no pagination, no optimistic UI. Polish is deferred as **GAP-P-05** (tracked separately) for a future cycle.

**Why this fits the budget:**

- All backend endpoints already exist and are proven via QA's direct-fetch probes (`GET /portal/requests`, `GET /portal/requests/{id}`, `POST /portal/requests/{id}/items/{itemId}/upload`, `POST /portal/requests/{id}/items/{itemId}/submit`).
- Portal already has working patterns to copy: `projects/page.tsx` (list pattern), `projects/[id]/page.tsx` (detail pattern), `document-list.tsx` (presigned-upload pattern).
- No new hooks, no new types (can inline them), no new layout, no new auth wiring — inherits from `(authenticated)/layout.tsx`.
- No new API client infrastructure — `portalGet` and `portalPost` cover it; the S3 `PUT` uses raw `fetch` (same pattern as existing `document-list.tsx`).
- Two new files, ~260 LOC total.

**If the builder hits 2hr and is not done, it is acceptable to cut scope ruthlessly:** drop the detail-page status polish, drop upload progress indicators, drop per-item descriptions — keep only file input + upload button + submit.

## Fix

### Step 1. List page

Create `portal/app/(authenticated)/requests/page.tsx`:

```tsx
"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { MessageSquare } from "lucide-react";
import { portalGet } from "@/lib/api-client";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";

interface PortalRequest {
  id: string;
  requestNumber: string;
  status: string;
  projectId: string;
  projectName: string;
  totalItems: number;
  submittedItems: number;
  acceptedItems: number;
  rejectedItems: number;
  sentAt: string | null;
  completedAt: string | null;
}

export default function RequestsPage() {
  const [requests, setRequests] = useState<PortalRequest[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    portalGet<PortalRequest[]>("/portal/requests")
      .then((data) => {
        if (!cancelled) setRequests(Array.isArray(data) ? data : []);
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : "Failed to load");
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div>
      <h1 className="font-display mb-6 text-2xl font-semibold text-slate-900">
        Information requests
      </h1>
      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          {error}
        </div>
      )}
      {!error && requests === null && (
        <div className="space-y-3">
          <Skeleton className="h-20 w-full" />
          <Skeleton className="h-20 w-full" />
        </div>
      )}
      {!error && requests !== null && requests.length === 0 && (
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <MessageSquare className="mb-4 size-12 text-slate-300" />
          <p className="text-lg font-medium text-slate-600">No requests yet</p>
          <p className="mt-1 text-sm text-slate-500">
            Your firm will share information requests with you here.
          </p>
        </div>
      )}
      {!error && requests !== null && requests.length > 0 && (
        <ul className="space-y-3">
          {requests.map((r) => (
            <li key={r.id}>
              <Link
                href={`/requests/${r.id}`}
                className="block rounded-lg border border-slate-200 bg-white p-4 transition hover:shadow-md"
              >
                <div className="flex items-center justify-between">
                  <div>
                    <p className="font-mono text-sm text-slate-500">{r.requestNumber}</p>
                    <p className="mt-1 font-medium text-slate-900">{r.projectName}</p>
                  </div>
                  <div className="text-right text-sm text-slate-600">
                    <p>{r.status}</p>
                    <p className="mt-1 text-xs">
                      {r.submittedItems}/{r.totalItems} submitted
                    </p>
                  </div>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
```

### Step 2. Detail page

Create `portal/app/(authenticated)/requests/[id]/page.tsx`:

```tsx
"use client";

import { useCallback, useEffect, useState } from "react";
import { use } from "react";
import { portalGet, portalPost } from "@/lib/api-client";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

interface RequestItem {
  id: string;
  name: string;
  description: string | null;
  responseType: string;
  required: boolean;
  fileTypeHints: string | null;
  sortOrder: number;
  status: string;
  rejectionReason: string | null;
  documentId: string | null;
  textResponse: string | null;
}

interface RequestDetail {
  id: string;
  requestNumber: string;
  status: string;
  projectId: string;
  projectName: string;
  totalItems: number;
  submittedItems: number;
  acceptedItems: number;
  rejectedItems: number;
  sentAt: string | null;
  completedAt: string | null;
  items: RequestItem[];
}

interface UploadInitResponse {
  documentId: string;
  uploadUrl: string;
  expiresAt: string;
}

export default function RequestDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);
  const [detail, setDetail] = useState<RequestDetail | null>(null);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      const data = await portalGet<RequestDetail>(`/portal/requests/${id}`);
      setDetail(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load");
    }
  }, [id]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  if (error) {
    return <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">{error}</div>;
  }
  if (!detail) {
    return <p className="text-sm text-slate-500">Loading…</p>;
  }

  return (
    <div>
      <div className="mb-6">
        <p className="font-mono text-sm text-slate-500">{detail.requestNumber}</p>
        <h1 className="font-display mt-1 text-2xl font-semibold text-slate-900">
          {detail.projectName}
        </h1>
        <p className="mt-2 text-sm text-slate-600">
          {detail.submittedItems}/{detail.totalItems} submitted • status {detail.status}
        </p>
      </div>
      <ul className="space-y-4">
        {detail.items.map((item) => (
          <li key={item.id}>
            <ItemCard requestId={id} item={item} onSubmitted={refresh} />
          </li>
        ))}
      </ul>
    </div>
  );
}

function ItemCard({
  requestId,
  item,
  onSubmitted,
}: {
  requestId: string;
  item: RequestItem;
  onSubmitted: () => void;
}) {
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const done = item.status === "SUBMITTED" || item.status === "ACCEPTED";

  async function handleUpload() {
    if (!file) return;
    setBusy(true);
    setErr(null);
    try {
      const init = await portalPost<UploadInitResponse>(
        `/portal/requests/${requestId}/items/${item.id}/upload`,
        { fileName: file.name, contentType: file.type || "application/octet-stream", size: file.size },
      );
      const put = await fetch(init.uploadUrl, {
        method: "PUT",
        headers: { "Content-Type": file.type || "application/octet-stream" },
        body: file,
      });
      if (!put.ok) throw new Error(`Upload failed (${put.status})`);
      await portalPost<void>(
        `/portal/requests/${requestId}/items/${item.id}/submit`,
        { documentId: init.documentId, textResponse: null },
      );
      onSubmitted();
    } catch (e) {
      setErr(e instanceof Error ? e.message : "Upload failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base font-semibold text-slate-900">
          {item.name}
          {item.required && <span className="ml-2 text-xs text-red-600">required</span>}
        </CardTitle>
        {item.description && (
          <p className="mt-1 text-sm text-slate-600">{item.description}</p>
        )}
        {item.fileTypeHints && (
          <p className="mt-1 text-xs text-slate-500">Accepts: {item.fileTypeHints}</p>
        )}
      </CardHeader>
      <CardContent>
        {done ? (
          <p className="text-sm text-teal-700">Submitted — status: {item.status}</p>
        ) : item.responseType === "FILE_UPLOAD" ? (
          <div className="flex flex-col gap-2">
            <input
              type="file"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              className="text-sm"
              disabled={busy}
            />
            <Button
              type="button"
              onClick={handleUpload}
              disabled={!file || busy}
              className="self-start"
            >
              {busy ? "Uploading…" : "Upload and submit"}
            </Button>
            {err && <p className="text-sm text-red-700">{err}</p>}
          </div>
        ) : (
          <p className="text-sm text-slate-500">Unsupported response type: {item.responseType}</p>
        )}
      </CardContent>
    </Card>
  );
}
```

### Step 3. Nav entry

The sidebar already has the "Requests" item in `portal/lib/nav-items.ts` line 76–82. Nothing to change here IF GAP-P-01 Step 2 (adding `"information_requests"` to vertical profiles' `enabledModules`) lands. The gate will then evaluate true and the item will appear.

## Scope

- Files to create:
  - `portal/app/(authenticated)/requests/page.tsx` (~90 LOC)
  - `portal/app/(authenticated)/requests/[id]/page.tsx` (~180 LOC)
- Files to modify: none (nav item already exists; module-gate fix is in GAP-P-01 spec)
- Migration needed: no
- Env / config: no
- Backend changes: **none** — all endpoints exist and are verified working.

## Verification

1. Portal HMR picks up the new routes; no restart needed.
2. Re-run Day 4 Checkpoints 4.8–4.12 as Sipho:
   - 4.8 — click sidebar "Requests" → `/requests` renders list with REQ-0001 card showing `Dlamini v Road Accident Fund / SENT / 0/3 submitted`. Click it → `/requests/{id}` renders header plus three item cards (ID copy / Proof of residence / Bank statement), each with a file input and Upload button.
   - 4.9 — three item cards visible, each with per-item upload slot. PASS.
   - 4.10 — select three test PDFs, click "Upload and submit" on each. Each triggers `POST /portal/requests/{id}/items/{itemId}/upload` (201 + presigned URL), `PUT {presignedUrl}` to LocalStack, `POST /portal/requests/{id}/items/{itemId}/submit`. After each submission the card flips to "Submitted — status: SUBMITTED".
   - 4.11 — optional note not covered by this stub (skip, log as minor drift).
   - 4.12 — after third upload, the request header reflects `3/3 submitted` and status `IN_PROGRESS` (or `COMPLETED` if backend auto-closes on last submit). PASS.
3. Back on `/home`, the Pending info requests card count should drop from 1 to 0 (if backend flips status away from SENT) or stay at 1 with a sub-label — depends on backend state machine; either is acceptable for this stub.
4. `/requests/not-a-real-id` → error panel "The requested resource was not found." (reuses existing 404 handling in `portalGet`).
5. No console errors, no 500s in `.svc/logs/portal.log`.

## Estimated Effort

**M (1.5–2 hr)** — two new page files, reusing existing primitives and patterns. Builder must stay strict on scope — no styling polish, no filters, no pagination, no optimistic state. If hitting 2hr without the submit flow working, cut the detail-page refresh-on-success and rely on manual refresh.

## Explicitly out of scope for this spec (tracked separately)

- **GAP-P-05** (new, to be logged on merge) — portal requests UI polish: empty/loading skeletons, status badges with colour coding, upload progress indicators, inline "optional note" textarea for non-file items, per-item rejection-reason display, file-type client-side validation, pagination/filtering on the list page.
- **GAP-P-02-ext** — text-response items (scenario Day 8 has `TEXT_RESPONSE` items). This stub handles only `FILE_UPLOAD`. Extend once Day 8 scenario requires it.

## Status Triage

**SPEC_READY — with stub scope locked.** The decision to admit this at the upper budget edge is justified because: (a) every other approach blocks the cycle entirely, (b) the rest of the 90-day scenario depends on portal responsiveness, (c) the backend is unambiguously ready, (d) patterns exist in-repo to copy from. If Dev comes back and says "this is actually a half-day of work," escalate to WONT_FIX this cycle and accept portal-POV days 4/8/11/15/30/46/61/75 as NOT_EXECUTED until a dedicated phase slice ships.
