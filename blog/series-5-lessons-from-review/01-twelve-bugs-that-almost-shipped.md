# 12 Bugs That Almost Shipped: What Code Review Actually Catches

*This is Part 1 of "Lessons from 843 Reviews" — real bugs, real fixes, and the patterns behind them.*

---

Over 843 pull requests, code review (both automated via CodeRabbit and manual via AI reviewer agents) caught hundreds of issues before they reached production. Most were minor — naming conventions, missing annotations, style drift. But some were genuine bugs that would've caused data leaks, crashes, or silent data corruption.

Here are 12 of the best catches, organized by the pattern they reveal. Each one is real, from a real PR, with the actual code before and after.

## Category 1: The Null Cascade

The most common production crash pattern in any codebase: a null value propagates through 3-4 function calls until something finally tries to read a property on it.

### Bug 1: `formatCurrency(null)` Crashes the Log Time Dialog

**Found in**: QA Cycle, GAP-030

The Log Time dialog showed billing rate information. When a project didn't have a rate configured, the rate value was `null`. The formatting chain was:

```typescript
// Before (crashes)
<span>{formatCurrency(rate.hourlyRate)}</span>
<span className="text-muted">{formatRateSource(rate.source)}</span>
```

When `rate` was undefined (no rate card configured for this project), the page crashed. But the fix wasn't just adding `?.` — the entire component needed a null state:

```typescript
// After (handles missing rate)
{rate ? (
  <>
    <span>{formatCurrency(rate.hourlyRate)}</span>
    <span className="text-muted">{formatRateSource(rate.source)}</span>
  </>
) : (
  <span className="text-muted-foreground">No rate configured</span>
)}
```

**The lesson**: Null cascades aren't just about adding `?.`. They're about designing for the missing-data state. If a rate can be absent, the UI needs an explicit "no rate" state, not a silent crash.

### Bug 2: Nine Unguarded Property Accesses on Customer Readiness

**Found in**: QA Cycle, GAP-027

The customer detail page showed a "readiness" panel — progress indicators for FICA completion, document uploads, and engagement letter status. The readiness object came from the API:

```typescript
// The API returns null for required fields when no custom fields are set
const readiness = await getCustomerReadiness(customerId);

// Before: 9 places assumed readiness properties were non-null
<div>{readiness.requiredFields.length} fields complete</div>
<div>{readiness.checklistProgress.completedItems} of {readiness.checklistProgress.totalItems}</div>
<div>{readiness.documentStatus.uploaded} documents</div>
```

After a customer lifecycle transition (PROSPECT → ONBOARDING), the readiness endpoint returned a partial object — `requiredFields` was `null` because no fields had been checked yet. Nine property accesses crashed.

```typescript
// After: every property guarded
<div>{readiness?.requiredFields?.length ?? 0} fields complete</div>
<div>{readiness?.checklistProgress?.completedItems ?? 0} of {readiness?.checklistProgress?.totalItems ?? 0}</div>
```

**The lesson**: When an object comes from an API, treat *every* property as potentially null — even if the TypeScript type says otherwise. API responses during state transitions often have partial data.

### Bug 3: `parseUuid()` Didn't Handle Empty Strings

**Found in**: QA Cycle, GAP-031

A utility function parsed UUID strings from URL parameters:

```typescript
// Before
function parseUuid(value: string | null): string | null {
  if (value === null) return null;
  // ... UUID format validation
  return value;
}

// The bug: value can be "" (empty string) from URL params
// "" passes the null check but fails UUID validation silently
```

Empty strings come from URL parameters like `?customerId=` (parameter present but no value). The function checked for `null` but not for empty strings, causing downstream failures in the timesheet report.

```typescript
// After
function parseUuid(value: string | null | undefined): string | null {
  if (!value || value.trim() === '') return null;
  // ... UUID format validation
  return value;
}
```

**The lesson**: URL parameters are strings. They can be `null`, `undefined`, `""`, or whitespace. Guard for all of them.

## Category 2: Multi-Tenant Data Leaks

### Bug 4: SWR Cache Key Not Tenant-Scoped

**Found in**: Code Review, PR for Epic 392B

The AI model selector used SWR for client-side data fetching:

```typescript
// Before: cache key doesn't include org
const { data: models } = useSWR('/api/ai/models', fetcher);
```

Problem: SWR caches globally. When a user switches organizations (navigates from `/org/acme/...` to `/org/other/...`), the cached AI models from Acme's configuration are shown for the Other org. This is a data leak — one org's AI model configuration visible to another.

```typescript
// After: cache key includes org slug
const { data: models } = useSWR(`/api/ai/models?org=${orgSlug}`, fetcher);
```

**The lesson**: In multi-tenant SPAs, every client-side cache key must include the tenant identifier. SWR, React Query, Apollo Cache — they all cache globally by default. If you switch tenants without invalidating or scoping the cache, you leak data.

### Bug 5: Carol Sees Alice's Identity

**Found in**: QA Cycle, BUG-7

After switching users via the mock login (Alice → Carol), the sidebar still showed Alice's avatar, name, and email. The identity wasn't refreshing on user switch.

The root cause: the auth context was cached in a React context that didn't invalidate when the JWT changed. The session cookie updated, but the React state was stale.

```typescript
// Before: identity loaded once, never refreshed
const [identity, setIdentity] = useState<User | null>(null);
useEffect(() => {
  fetchIdentity().then(setIdentity);
}, []); // Empty deps = runs once

// After: identity refreshes when auth token changes
const { data: identity } = useSWR('/api/me', fetcher, {
  revalidateOnFocus: true,
  revalidateOnReconnect: true,
});
```

**The lesson**: Auth state must be reactive, not static. `useState` + `useEffect([], [])` loads once. If the user changes (login switch, token refresh, org switch), the stale state persists. Use SWR or a similar library that revalidates.

## Category 3: Async Race Conditions

### Bug 6: SSE Emitter Handlers Registered After Submit

**Found in**: Code Review, Epic 390B

The chat endpoint used Server-Sent Events (SSE) with a thread pool:

```java
// Before: race condition
var emitter = new SseEmitter(timeout);
executor.submit(() -> {
    // This runs on a different thread
    // By the time it starts, handlers might not be registered yet
    streamResponse(emitter, messages);
});
emitter.onCompletion(() -> cleanup());  // ← registered AFTER submit
emitter.onTimeout(() -> cleanup());     // ← might miss events
return emitter;
```

The executor could start streaming before `onCompletion`/`onTimeout` were registered. If the stream completed very quickly (e.g., a short response or an error), the completion handler never fired, leaving resources uncleaned.

```java
// After: handlers registered before submit
var emitter = new SseEmitter(timeout);
emitter.onCompletion(() -> cleanup());  // ← before submit
emitter.onTimeout(() -> cleanup());     // ← before submit
emitter.onError(e -> cleanup());        // ← before submit
executor.submit(() -> streamResponse(emitter, messages));
return emitter;
```

**The lesson**: Always register event handlers before starting async work. It's a race condition between "when does the async work complete" and "when are the handlers ready." Register first, start second.

### Bug 7: Stale Closure Over Streaming Messages

**Found in**: Code Review, Epic 391A

The chat UI accumulated messages in state and displayed them. The SSE event handler updated the message list:

```typescript
// Before: stale closure
const [messages, setMessages] = useState<Message[]>([]);

const handleMessage = useCallback((event: MessageEvent) => {
  const msg = JSON.parse(event.data);
  setMessages([...messages, msg]);  // ← captures `messages` at callback creation time
}, [messages]);
```

When multiple SSE events arrived in rapid succession, `messages` in the closure was stale — it captured the array from the previous render, not the current one. Messages were dropped.

```typescript
// After: functional update avoids stale closure
const handleMessage = useCallback((event: MessageEvent) => {
  const msg = JSON.parse(event.data);
  setMessages(prev => [...prev, msg]);  // ← always reads latest state
}, []); // No dependency on messages
```

**The lesson**: Never reference state variables inside event handlers. Use functional updates (`setMessages(prev => ...)`) or refs. This is React 101, but it bites even experienced developers when streaming data is involved.

## Category 4: Seed Script Bugs

### Bug 8: Keycloak Seed Role Mismatch

**Found in**: Code Review, Epic 402A

The Keycloak bootstrap script seeded test users with `role: "admin"`:

```bash
# Keycloak seed script
kcadm.sh create users -r app -s username=bob -s email=bob@test.com
kcadm.sh add-roles -r app --uname bob --rolename admin
```

But `MemberFilter` lazy-creates members with `role: "member"` by default (unless a pending invitation specifies otherwise). The seed script's "admin" role only existed in Keycloak, not in the app database.

When Bob logged in, `MemberFilter` created him as "member" (no pending invitation found), ignoring the Keycloak role. Bob appeared in the UI as "member" despite being "admin" in Keycloak.

```bash
# Fixed: role assignment happens through the app's invitation system, not Keycloak roles
# Seed script creates invitation → member filter accepts it → correct role assigned
```

**The lesson**: If your auth provider assigns roles and your app also assigns roles, they can disagree. Decide which is authoritative and enforce it. In DocTeams, the app database is authoritative — Keycloak provides identity, the app provides authorization.

## Category 5: Architectural Debt

### Bug 9: Slug-Based Clause Lookup is O(n) per Block

**Found in**: Code Review, PR #735 (deferred)

```java
// TiptapRenderer.java — for each clause block in the template:
clauses.values().stream()
    .filter(c -> c.getSlug().equals(blockSlug))
    .findFirst()
    .orElse(null);
```

An engagement letter has 4-7 clause blocks. Each block scans the full clause list. With 20 clauses, that's 80-140 comparisons. Not a bug today — but it's O(n×m) where n=blocks and m=clauses, and it will be a problem when clause libraries grow.

The review noted it and recommended pre-building a `Map<String, Clause>` index. The fix is trivial but the *detection* is what matters — this kind of quadratic behavior is invisible in functional testing but shows up as latency in production.

### Bug 10: DB Queries Per Template Render

**Found in**: Code Review, PR #735 (deferred)

```java
// resolveDropdownLabels is called once per entity type in the template context
// A project template triggers: resolveDropdownLabels(PROJECT), resolveDropdownLabels(CUSTOMER)
// An invoice template triggers: resolveDropdownLabels(INVOICE), resolveDropdownLabels(CUSTOMER),
//                                 resolveDropdownLabels(PROJECT)
```

Each call hits the database. A single template render generates 3-4 DB queries for field definitions that don't change within a request. For single document generation, this is fine. For batch operations (generating all customer statements), it's a performance issue.

The recommended fix: request-scoped caching of field definitions. Load once per entity type per request.

### Bug 11: Hardcoded ZA Locale in Currency Formatting

**Found in**: Code Review, PR #735

```java
// VariableFormatter.java
private static final Locale DEFAULT_LOCALE = Locale.of("en", "ZA");

public String formatCurrency(Object value) {
    return NumberFormat.getCurrencyInstance(DEFAULT_LOCALE).format(value);
}
```

Every currency value — in templates, invoices, reports — renders as ZAR with South African formatting. This is correct for the `accounting-za` vertical. But for a tenant using USD or EUR, the formatter still uses ZA locale.

The fix: pass locale from the rendering context (org settings currency) instead of hardcoding.

### Bug 12: Provisioning Hardcodes `null` for Vertical Profile

**Found in**: QA Cycle, GAP-008

```java
// ProvisioningController.java (before fix)
provisioningService.provisionTenant(orgId, orgName, null);  // ← always null
```

The provisioning endpoint accepted the org name but hardcoded `null` for the vertical profile. This meant every new tenant got generic packs only — no accounting-za compliance checklists, no FICA fields, no engagement letter templates. The vertical profile was being set *after* provisioning via a separate settings update, but by then the pack seeders had already run (and skipped the vertical packs because the profile was null).

```java
// After: vertical profile passed through DTO
provisioningService.provisionTenant(request.orgId(), request.orgName(), request.verticalProfile());
```

**The lesson**: Feature flags and configuration that affect *initialization* must be set *during* initialization, not after. Seeding runs once. If the profile isn't set when seeders run, the packs are never applied (until a manual re-seed).

## The Pattern Behind the Patterns

Looking across all 12 bugs, three meta-patterns emerge:

1. **Null handling is a design problem, not a syntax problem.** Adding `?.` fixes the crash. Designing for the absent-data state fixes the product. (Bugs 1, 2, 3)

2. **Multi-tenant state must be explicitly scoped — everywhere.** Database queries are scoped by `search_path`. But caches, React state, and session storage all need manual scoping. (Bugs 4, 5, 8)

3. **The happy path passes tests. The edge path ships bugs.** Empty strings, null rates, quick SSE completions, stale closures, batch rendering — these are all edge cases that functional tests don't exercise. Review catches them because it asks "what if?" instead of "does it work?" (Bugs 3, 6, 7, 9, 10)

---

*Next in this series: [The QA Cycle: 12 Bugs Found by AI Playwright in 90 Minutes](02-qa-cycle-bugs.md)*
