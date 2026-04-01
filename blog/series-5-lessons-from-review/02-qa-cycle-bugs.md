# The QA Cycle: 12 Bugs Found by AI Playwright in 90 Minutes

*Part 2 of "Lessons from 843 Reviews" — real bugs, real fixes, and the patterns behind them.*

---

After building the accounting vertical, I needed to know if the product actually *worked*. Not "do the tests pass" — they did. Not "does the API respond correctly" — it did. I needed to know: can an accounting firm owner log in, create a client, complete FICA compliance, create an engagement, log time, and generate an invoice?

I pointed a Claude Code agent with Playwright MCP at the running application and told it: "Be Alice, the firm owner. Do her job for 90 days."

It found 12 bugs in 90 minutes. Three of them were crashes.

## The Setup

The E2E mock-auth stack runs the full application on alternate ports:

| Service | Port | Purpose |
|---------|------|---------|
| Frontend | 3001 | Next.js with mock auth mode |
| Backend | 8081 | Spring Boot with e2e profile |
| Mock IDP | 8090 | JWT generation (no Keycloak needed) |
| Postgres | 5433 | Seeded test data |

The agent authenticates via `/mock-login`, selecting a user (Alice = owner, Bob = admin, Carol = member). It navigates the app using Playwright's accessibility tree — clicking buttons, filling forms, reading text content.

The agent doesn't know the codebase. It doesn't read source code. It acts like a user — clicking what looks clickable, reading what's on screen, and reporting when something goes wrong.

## The Crashes (Severity: Critical)

### BUG-1: Tasks Tab Crashes with TypeError

**Page**: Any project detail, Tasks tab
**Action**: Click "Tasks" tab
**Expected**: Task list renders
**Actual**: Error boundary: "Something went wrong"

```
TypeError: Cannot read properties of undefined (reading 'variant')
```

The Tasks tab rendered a `PriorityBadge` component for each task. When a task had no priority set (valid — priority is optional), the badge tried to read `PRIORITY_CONFIG[task.priority].variant` where `task.priority` was `undefined`.

**Fix**: Added a fallback variant for undefined priority. One line.

**Why tests didn't catch it**: The test data seeder created tasks with explicit priorities. No test created a task with `priority: null` — because the seed script was written before the priority field became optional.

### BUG-2: Resources Page Crashes with 500

**Page**: Resources page (capacity planning grid)
**Action**: Navigate via sidebar
**Actual**: 500 error followed by error boundary

The allocation grid parsed date range strings with `.split('-')`. When the backend returned `null` for unallocated date ranges, `.split()` crashed on null.

**Fix**: Null guard on the `.split()` call, plus a null check on the week selector's initial value.

### BUG-3: Settings > Rates Crashes for Owners

**Page**: Settings, Rates & Currency
**Action**: Navigate as Alice (Owner role)
**Actual**: 500 error followed by error boundary

The rates page fetched three endpoints in parallel (`Promise.all`): billing rates, cost rates, and org settings. The cost rates endpoint returned 500 (a misconfigured query for the e2e test org). Because of `Promise.all`, one failed endpoint crashed the entire page.

**Fix**: `Promise.allSettled` instead of `Promise.all`. Each section renders independently — if cost rates fail, billing rates still show.

**Irony**: When Carol (member role) visited the same page, she correctly saw a "Permission denied" message. The crash only affected the Owner who should have had access.

## The Data Issues (Severity: Major)

### BUG-4: Currency Mismatch on Invoice Page

**Page**: Invoices list
**Visual**: "Total Overdue" shows `$0.00` while "Total Outstanding" shows `R 14 461,25`

Three KPI cards at the top of the invoices page: Outstanding (R), Overdue ($), Paid This Month (R). The overdue card used a different formatter because its value was zero, and the zero-value code path fell through to a hardcoded USD default.

**Fix**: Default currency derived from the first invoice's currency (or org settings), not hardcoded.

**Screenshot evidence**: The QA agent captured `qa_cycle/03-invoices-currency-mismatch.png` showing the mismatch side by side.

### BUG-8: Team Page Roles All Empty

**Page**: Team management
**Visual**: Every team member's "Role" column showed a blank badge

The role normalization function expected lowercase roles (`"owner"`, `"admin"`, `"member"`) but received mixed-case from the API (`"Owner"`, `"Admin"`). The badge mapping failed and returned nothing.

**Fix**: `.toLowerCase()` before matching badge variants.

### BUG-12: All "Generate" Buttons Disabled on Project Templates

**Page**: Project detail, Overview tab, Document Templates section
**Visual**: Every template had a grayed-out "Generate" button

The "Generate" button checked three prerequisites: the project must have a customer linked, the customer must have OrgSettings configured, and the template must match the entity type. The check was over-zealous — for PROJECT-type templates, it required a customer and OrgSettings even when neither was needed for the template's context.

**Fix**: Only check prerequisites relevant to the template's entity type. PROJECT templates don't need customer-level validation.

## The UX Issues (Severity: Minor)

### BUG-5: Tax Summary Shows "(%) " Instead of Label

**Page**: Invoice detail, Tax summary row
**Visual**: `(%) ` instead of `VAT (15%)`

The tax label was formatted as `${invoice.taxLabel} (${invoice.taxRate}%)`. When `taxLabel` was null (no tax label configured), it rendered as `null (15%)`, which was further trimmed to `(%) ` by a display filter.

**Fix**: `invoice.taxLabel ?? "Tax"` — simple fallback.

### BUG-6: Header Shows "No org" on First Load

**Page**: All pages, on initial navigation after login
**Duration**: ~500ms flash before the real org name appears

The org name came from an API call that resolved after the header rendered. For 500ms, the header showed "No org" as the loading state.

**Fix**: Show the org slug (available immediately from the URL) while the full name loads. `e2e-test-org` isn't pretty but it's better than "No org."

### BUG-7: Carol Sees Alice's Identity After User Switch

**Page**: All pages after switching users via mock login
**Visual**: Sidebar still shows Alice's avatar and name after logging in as Carol

The auth context was cached in a React `useState` that didn't invalidate when the JWT changed. The session cookie updated, but the React state was stale.

**Fix**: Replaced static `useState` + `useEffect([], [])` with SWR that revalidates on focus and reconnect.

### BUG-10: React Hydration Error on Every Page

**Page**: All pages
**Console**: `Warning: Text content did not match...`

A date formatting mismatch between server and client render. The server rendered dates in UTC; the client re-rendered in the user's timezone. React flagged the difference.

**Fix**: Consistent date formatting using `toLocaleDateString('en-CA')` on both sides.

### BUG-11: Error Message Says "projects" Instead of "tasks"

**Page**: Project detail, Tasks tab (after crash was fixed)
**Visual**: Error fallback says "Unable to load projects. Please try again."

The tasks tab reused an error component from the projects page without updating the message.

**Fix**: Generic fallback: "Unable to load this page. Please try again."

### BUG-9: Two "Unknown" Members in Team Page

**Page**: Team management
**Visual**: 5 members shown instead of expected 3

Two additional "Unknown" members — remnants from a previous test run where the seed script created duplicate users with different IDs.

**Fix**: Cleaned the seed script to be idempotent (upsert, not insert).

## The Fix Cadence

All 12 bugs were fixed in 2 PRs:
- **PR #726**: 8 bug fixes (crashes, data issues, UX issues)
- **PR #727**: 4 bug fixes (hydration, identity, header, seed cleanup)

Total fix time: about 3 hours. Total QA time: about 90 minutes.

The verification cycle (agent re-tests each fix) added another 30 minutes, documented in `qa_cycle/bug-verification-2026-03-17.md`. Every fix was verified with the same Playwright flow that found the bug.

## Why Manual Testing Wouldn't Have Found These

Some of these bugs are obvious when you use the app (BUG-4's currency mismatch is visible). But several are subtle:

- **BUG-1** only triggers when a task has no priority — most manual testers create tasks with all fields filled
- **BUG-3** only crashes for Owner role, not Member — a tester using the default member account would see a clean permission denied page
- **BUG-7** only manifests when switching users — most testers don't switch users mid-session
- **BUG-12** only triggers for PROJECT templates when no customer is linked — most testers create a project with a customer first

The AI QA agent found these because it systematically walked through every page, every tab, and every user role. It didn't skip steps because it was "pretty sure that works." It clicked everything. That's the value.

## What I'd Do Differently

**Run the QA cycle earlier.** I ran it after building 20+ phases. If I'd run it after Phase 8 or 10, I'd have caught the null-guard and currency patterns earlier and established conventions that prevented them in later phases.

**Seed data must mirror production ambiguity.** The seed script created "perfect" data — every field filled, every relationship present. Real data has nulls, empty strings, missing relationships, and partial states. The seed should deliberately create messy data.

**`Promise.allSettled` should be the default for parallel fetches.** Any page that loads multiple endpoints should use `allSettled` to prevent one failure from crashing everything. I've now added this to the frontend CLAUDE.md as a convention.

---

*Next in this series: [5 Architectural Debts Found in Code Review (And When to Fix Them)](03-architectural-debt.md)*

*Previous: [12 Bugs That Almost Shipped](01-twelve-bugs-that-almost-shipped.md)*
