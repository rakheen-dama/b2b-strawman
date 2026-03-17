# GAP-P49-005 — Portal Acceptance Page

**Severity**: Blocker (Track T6 — 25 checkpoints entirely blocked)
**Effort**: L (half day)
**Dependencies**: None — all backend APIs exist and are tested

## Problem

The entire document acceptance / e-signing track (Track 6) could not be tested because the client-facing portal has no acceptance page. The firm sends an acceptance request (magic link email), but the link has nowhere to land.

**Backend exists** — `PortalAcceptanceController` at `/api/portal/acceptance/{token}`:
- `GET /{token}` → `PortalPageData` (status, document title, org branding, expiry)
- `GET /{token}/pdf` → PDF bytes (inline Content-Disposition)
- `POST /{token}/accept` → `PortalAcceptResponse` (accepts with typed name)

**Security** — All `/api/portal/acceptance/**` endpoints are `permitAll()` in `SecurityConfig.java`. Token-based auth — no session, no JWT needed.

**Firm-side UI exists** — `SendForAcceptanceDialog`, `AcceptanceDetailPanel`, `AcceptanceStatusBadge`.

## Architecture

### Route

```
frontend/app/accept/[token]/page.tsx
```

**CRITICAL**: The route is at `/accept/[token]`, NOT `/portal/accept/[token]`. The backend `AcceptanceNotificationService` constructs the email link as:
```java
String acceptanceUrl = portalBaseUrl + "/accept/" + request.getRequestToken();
// → http://localhost:3001/accept/{token}  (dev)
// → https://portal.docteams.app/accept/{token}  (production)
```

The route must match this URL. Placing it under `app/portal/` would produce `/portal/accept/{token}`, breaking the email link.

### Route Structure

```
frontend/app/
├── accept/
│   └── [token]/
│       └── page.tsx                    # NEW — acceptance page (top-level, no auth)
├── portal/
│   ├── layout.tsx                      # Root portal layout (metadata only)
│   ├── page.tsx                        # Login page (magic link)
│   └── (authenticated)/
│       ├── layout.tsx                  # PortalAuthGuard + PortalHeader
│       ├── documents/
│       ├── projects/
│       └── requests/
├── (app)/                              # Firm-side app routes
└── ...
```

The acceptance page is intentionally at the app root level — it shares no layout or auth with either the portal (`/portal/`) or the firm app (`/(app)/`). The token IS the auth.

### Data Flow

```
Client browser → GET /api/portal/acceptance/{token}
                 ← PortalPageData { requestId, status, documentTitle, documentFileName,
                                     expiresAt, orgName, orgLogo, brandColor,
                                     acceptedAt, acceptorName }

(Side effect: backend marks request as VIEWED if currently SENT)

PDF loading   → GET /api/portal/acceptance/{token}/pdf
                 ← application/pdf bytes (fetched as blob, rendered via URL.createObjectURL)

Accept action → POST /api/portal/acceptance/{token}/accept
                 Body: { "name": "John Smith" }
                 ← PortalAcceptResponse { status, acceptedAt, acceptorName }
```

### API Client

Do NOT use `portalApi` from `lib/portal-api.ts` (it adds Bearer JWT from localStorage). Instead, create a thin helper or use `fetch` directly — the token is in the URL path, not in headers.

```typescript
// frontend/lib/api/portal-acceptance.ts

const BACKEND_URL = process.env.NEXT_PUBLIC_BACKEND_URL || "http://localhost:8080";

export interface AcceptancePageData {
  requestId: string;
  status: "PENDING" | "SENT" | "VIEWED" | "ACCEPTED" | "EXPIRED" | "REVOKED";
  documentTitle: string | null;
  documentFileName: string | null;
  expiresAt: string | null;
  orgName: string | null;
  orgLogo: string | null;
  brandColor: string | null;
  acceptedAt: string | null;
  acceptorName: string | null;
}

export interface AcceptResponse {
  status: string;
  acceptedAt: string;
  acceptorName: string;
}

export async function getAcceptancePageData(token: string): Promise<AcceptancePageData> {
  const res = await fetch(`${BACKEND_URL}/api/portal/acceptance/${token}`);
  if (!res.ok) throw new Error(res.status === 404 ? "not_found" : "error");
  return res.json();
}

export async function getAcceptancePdf(token: string): Promise<Blob> {
  const res = await fetch(`${BACKEND_URL}/api/portal/acceptance/${token}/pdf`);
  if (!res.ok) throw new Error("Failed to load document");
  return res.blob();
}

export async function acceptDocument(token: string, name: string): Promise<AcceptResponse> {
  const res = await fetch(`${BACKEND_URL}/api/portal/acceptance/${token}/accept`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name }),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    throw new Error(body?.detail || body?.title || "Failed to accept");
  }
  return res.json();
}
```

### Page States

The page has 6 mutually exclusive states:

| State | Condition | Rendering |
|-------|-----------|-----------|
| **Loading** | Initial fetch in progress | Centered spinner |
| **Not Found** | 404 from backend | "This link is invalid" message |
| **Expired** | `status === "EXPIRED"` | "This request expired on {date}" message |
| **Revoked** | `status === "REVOKED"` | "This request is no longer available" message |
| **Ready** | `status === "SENT" \|\| "VIEWED"` | PDF viewer + acceptance form |
| **Accepted** | `status === "ACCEPTED"` | Green confirmation with timestamp |

### PDF Viewing

No existing PDF viewer component in the codebase. Approach:

1. Fetch PDF as blob via `getAcceptancePdf(token)`
2. Create object URL: `URL.createObjectURL(blob)`
3. Render via `<iframe>` with `src={objectUrl}` and `type="application/pdf"`
4. Revoke object URL on unmount to prevent memory leaks

```tsx
// Simplified PDF viewer pattern
const [pdfUrl, setPdfUrl] = useState<string | null>(null);

useEffect(() => {
  getAcceptancePdf(token).then(blob => {
    setPdfUrl(URL.createObjectURL(blob));
  });
  return () => { if (pdfUrl) URL.revokeObjectURL(pdfUrl); };
}, [token]);

// In render:
{pdfUrl && (
  <iframe
    src={pdfUrl}
    className="h-[600px] w-full rounded-lg border"
    title="Document preview"
  />
)}
```

Why blob + objectURL instead of direct iframe to backend URL:
- Avoids exposing `NEXT_PUBLIC_BACKEND_URL` in the DOM
- Works behind reverse proxies that restrict cross-origin iframes
- Allows a proper loading state while PDF downloads

### Acceptance Form

```tsx
<form onSubmit={handleAccept}>
  <Label htmlFor="acceptor-name">Full legal name</Label>
  <Input
    id="acceptor-name"
    value={name}
    onChange={(e) => setName(e.target.value)}
    placeholder="Type your full name to accept"
    required
    minLength={2}
    maxLength={255}
  />
  <Button type="submit" disabled={isAccepting || name.trim().length < 2}>
    {isAccepting ? "Accepting..." : "I Accept This Document"}
  </Button>
</form>
```

Validation matches backend `AcceptanceSubmission`: `@NotBlank @Size(min = 2, max = 255)`.

### Branding

The `PortalPageData` includes `orgName`, `orgLogo`, and `brandColor`. Use these to:
- Show org name in the page header
- Display org logo if available
- Apply `brandColor` as accent color on the Accept button

### UX Copy

- **Ready state**: "Please review the document below. When you're ready, type your full name and click 'I Accept' to confirm."
- **Expired**: "This acceptance request expired on {formatted date}. Please contact {orgName} to request a new link."
- **Revoked**: "This acceptance request has been withdrawn. Please contact {orgName} for more information."
- **Accepted**: "You accepted this document on {formatted date}. A confirmation has been sent to your email."
- **Not found**: "This link is not valid. It may have already been used or the URL may be incorrect."

### Component Structure

Single page component — no need for sub-components given the simplicity:

```
AcceptancePortalPage (page.tsx)
├── Loading state (Loader2 spinner)
├── Error state (AlertCircle + message)
├── Terminal state (EXPIRED / REVOKED)
├── Accepted state (CheckCircle2 + timestamp)
└── Ready state
    ├── Org branding header (logo + name)
    ├── Document info (title, expiry notice)
    ├── PDF iframe
    └── Acceptance form (name input + button)
```

### Styling

Follow existing portal patterns:
- `min-h-screen bg-slate-50 dark:bg-slate-950` for page background
- `max-w-3xl mx-auto px-4 py-8` for content width (slightly narrower than authenticated portal's `max-w-5xl` since this is a focused, single-purpose page)
- Use existing Shadcn components: `Button`, `Input`, `Label`
- Lucide icons: `CheckCircle2`, `AlertCircle`, `Loader2`, `Clock`, `FileText`

### Testing

**Frontend tests** (`__tests__/portal-acceptance-page.test.tsx`):
1. Renders loading state
2. Renders "not found" for 404 token
3. Renders expired state with formatted date
4. Renders revoked state
5. Renders acceptance form for VIEWED status
6. Submits acceptance with typed name
7. Shows confirmation after successful acceptance
8. Disables submit when name is too short

**E2E test** (Playwright):
1. Navigate to `http://localhost:3001/accept/{token}`
2. Verify PDF iframe loads
3. Type name in input
4. Click Accept
5. Verify confirmation state appears

### Files to Create

| File | Purpose |
|------|---------|
| `frontend/lib/api/portal-acceptance.ts` | API client (3 functions) |
| `frontend/app/accept/[token]/page.tsx` | Page component |
| `frontend/__tests__/portal-acceptance-page.test.tsx` | Unit tests |

### Files to Modify

None. This is a purely additive change.
