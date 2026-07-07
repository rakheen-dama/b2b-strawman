# Deferred-Gap Fixes Verification — 2026-06-15 (QA, Keycloak dev stack)

Branch: `bugfix_cycle_2026-06-14`. Stack: frontend :3000, backend :8080, gateway :8443 (svc.sh status: backend/gateway/frontend UP+healthy). Driver: Playwright MCP, logged in as Nomsa (OWNER) via Keycloak OIDC (existing session). **Zero new live-Claude calls** — pure read/render + console/network verification.

Scope: verify three merged fixes (AIVERIFY-003, AIVERIFY-009, AIVERIFY-004), then STOP. No code changed; test + document only.

---

## AIVERIFY-003 — assistant pending-suggestions widget 404 → server-action — **VERIFIED**

**Page**: `/org/verifain-attorneys/customers/809563f8-1feb-4043-bbd7-c9aeaf356900` (test client Sipho Dlamini).

**Method**: fresh navigation (dashboard → customer page), 3s settle, then network + console capture scoped to the current navigation. Repeated once to eliminate session-state ambiguity.

**Evidence**:
- **No `404` on `/api/assistant/invocations`** on the page load. Network (incl. static) filtered for `api/assistant|invocations|404` returns ONLY the page GET (200) and the server-action POSTs (200) — there is **no request to `/api/assistant/invocations` at all** anymore (the old client-fetch is gone).
- The widget loads its data via the **server-action path**: two `POST` to the page route returning `[200] OK`. Request headers confirm it is a Next.js server action — `next-action: 009fbcbe4c1b669fb54da0f757fe209fd36c208698`, `accept: text/x-component`.
- **Console scoped to this navigation: 0 errors, 0 warnings** (both the initial visit and the fresh reload).
- Note: a stale `all:true` console dump still contains older `/api/assistant/invocations` 404s, but their `referer`/origin URLs are prior pages (`projects/new`, `settings/audit-log`) from earlier in the session — NOT this page load. The "since last navigation" console for the customer page is clean.
- Widget area renders cleanly (FICA Verification / AI panel, "Verify with AI" button, generate-template links) — clean empty state for pending suggestions, which is a valid PASS per spec.

**Screenshot**: `qa_cycle/checkpoint-results/aiverify-003-customer-widget.png`

→ **VERIFIED** — no 404, data via server action (200), widget renders, console clean.

---

## AIVERIFY-009 — ExecutionGateCard hydration/locale mismatch — **VERIFIED**

**Page**: `/org/verifain-attorneys/ai/reviews` — 18 PENDING execution gates render (from earlier V3–V8 stages).

**Method**: navigate, 3s settle, console capture (warning level — includes errors per tool semantics) scoped to the navigation; plus a full-session `all:true` console grep for hydration strings.

**Evidence**:
- **NO React hydration warning** on load. Console (warning+error) scoped to the page: **0 errors, 0 warnings**. Full-session `all:true` console grep for `hydrat|did not match|Text content did not match|server-rendered` → **0 matches**. The old bug's text-mismatch warning is absent.
- **Timestamp renders sensibly** in pinned en-ZA / Africa/Johannesburg format: e.g. `15 Jun 2026, 14:33`, `15 Jun 2026, 12:29`, `15 Jun 2026, 12:12`, `15 Jun 2026, 01:14`.
- **Countdown / time-remaining renders** (post-mount): e.g. `2d 17h remaining`, `2d 15h remaining`, `2d 4h remaining` on the respective PENDING gates.

**Screenshot**: `qa_cycle/checkpoint-results/aiverify-009-gate-cards.png`

→ **VERIFIED** — no hydration warning; deterministic ZA timestamp + post-mount countdown both render.

---

## AIVERIFY-004 — compliance single-PUBLISHED (happy-path regression check) — **VERIFIED**

**DB precondition (SELECT-only)**:
- `SELECT status, count(*) FROM tenant_c6107524c9b4.compliance_audit_reports GROUP BY status;` → `PUBLISHED | 1` (exactly one).
- Index present: `uq_compliance_audit_reports_single_published` (alongside pkey, status, created_at, execution indexes).

**Page**: `/org/verifain-attorneys/compliance` → **AI Audit** tab → "Latest Audit".

**Evidence**:
- The single PUBLISHED report renders fully: heading "AI Compliance Audit" / "Latest Audit", full summary (grade C assessment), "Published 14 Jun 2026 by bbfdd8ac-…", "Category Scores" section, and a "Findings (9)" table (GOV-001 HIGH, PRESC-001 MEDIUM, …) with severity/category/status columns.
- **No 500, no error.** Console scoped to the page: **0 errors, 0 warnings**. Report detail loads cleanly.
- Did NOT publish a fresh report (would require a new compliance-audit Claude call + gate approval) — per spec, the merged unit test `ComplianceAuditReportConcurrencyTest` is the load-bearing proof of the two-reviewer race; this pass confirms only the happy-path read/render is not regressed by the flush+index change.

**Screenshot**: `qa_cycle/checkpoint-results/aiverify-004-published-report.png`

→ **VERIFIED** (happy-path, no regression) — index enforced + 1 PUBLISHED report renders cleanly.

---

## Summary

| Gap | Result | Core evidence |
|-----|--------|---------------|
| AIVERIFY-003 | **VERIFIED** | No `/api/assistant/invocations` 404; server-action POST 200 (`next-action` header); console clean |
| AIVERIFY-009 | **VERIFIED** | 0 hydration warnings (scoped + full-session grep); `15 Jun 2026, 14:33` ZA timestamp + `2d 17h remaining` countdown |
| AIVERIFY-004 | **VERIFIED** | DB: 1 PUBLISHED + `uq_…_single_published` index; AI-Audit report renders (summary + 9 findings), no 500, console clean |

No new defects observed during this pass.
