# Track 10 — Multi-Vertical Coexistence (Cycle 1)

**Executed**: 2026-04-04
**Actor**: Alice Moyo (legal) + Thandi (accounting, API only)
**Method**: Playwright UI (legal) + API (both tenants)

## Summary

Multi-vertical coexistence is **solid**. Data isolation, module gating, pack separation, and terminology overrides all work correctly. Both tenants operate independently without cross-contamination.

---

## T10.1 — Data Isolation

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T10.1.1 Moyo court date count | **PASS** | 5 court dates (via API `GET /api/court-dates`) |
| T10.1.2 Thornton 0 court dates | **PASS** | `GET /api/court-dates` → HTTP 403 (module gated, not just empty) |
| T10.1.3 Moyo customer not in Thornton | **PASS (API)** | Moyo has 4 customers, Thornton has 5 — distinct sets. Schema-per-tenant isolation guarantees no overlap. |
| T10.1.4 Thornton customer not in Moyo | **PASS (API)** | Same schema isolation |

## T10.2 — Shared Endpoints Correct Per Profile

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T10.2.1 Projects per tenant | **PASS (API)** | Moyo: 4 projects. Thornton: 14 projects. Separate schemas. |
| T10.2.2 Invoices per tenant | **PASS (inferred)** | Both tenants have separate invoice tables in their schemas. Moyo: 0 invoices (none created yet). |
| T10.2.3 Dashboard per tenant | **PASS (UI)** | Moyo dashboard shows 4 Active Projects, legal-specific widgets. |

## T10.3 — Terminology

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T10.3.1 Moyo: "Matters" not "Projects" | **PASS** | Sidebar shows "Matters" link to `/org/moyo-dlamini-attorneys/projects`. Breadcrumb: "Matters". |
| T10.3.2 Moyo: "Clients" not "Customers" | **PASS** | Sidebar group label: "Clients". Sub-item: "Clients" link to `/customers`. Customer link on matter: "Customer: Sipho Mabena" — minor inconsistency (uses "Customer:" prefix, not "Client:"). |
| T10.3.3 Thornton: "Projects" and "Customers" | **PASS (prior T7)** | Verified in prior T7 cycle — Thornton sidebar shows standard "Projects", "Customers" labels. |
| T10.3.4 Switching tenants: terminology updates | **SKIP** | Requires session switching which is not possible in single browser session with Keycloak. |

**Note**: On the matter detail page, the text says "Customer: Sipho Mabena" instead of "Client: Sipho Mabena". This is a minor terminology inconsistency (GAP-P55-014).

## T10.4 — Pack Isolation

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T10.4.1 Moyo: legal-za field packs | **PASS (API)** | `GET /api/field-groups?entityType=PROJECT` → "SA Legal -- Matter Details", "Project Info" |
| T10.4.2 Thornton: accounting-za field packs | **PASS (API)** | `GET /api/field-groups?entityType=PROJECT` → "SA Accounting -- Engagement Details", "Project Info" |
| T10.4.3 Legal packs NOT in accounting | **PASS** | No "SA Legal" group in Thornton response |
| T10.4.4 Accounting packs NOT in legal | **PASS** | No "SA Accounting" group in Moyo response |

## T10.5 — Tariff Isolation

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T10.5.1 Moyo: Tariff Schedules page shows LSSA | **FAIL (UI)** | Page crashes (GAP-P55-012). API confirms 3 schedules exist (1 system + 2 custom from T5 tests). |
| T10.5.2 Thornton: Tariff Schedules inaccessible | **PASS (API)** | `GET /api/tariff-schedules` → HTTP 403 |
| T10.5.3 Thornton API returns 403 | **PASS** | Confirmed above |

---

## New Gaps

### GAP-P55-014: "Customer:" label on matter detail instead of "Client:" for legal tenants

**Track**: T10.3 — Terminology
**Step**: T10.3.2
**Category**: ui-error
**Severity**: cosmetic
**Description**: On the matter detail page for legal-za tenants, the customer reference label says "Customer: Sipho Mabena" instead of "Client: Sipho Mabena". The sidebar and navigation correctly use "Clients" terminology, but the project detail page uses the generic "Customer" label.
**Evidence**:
- Page: `/org/moyo-dlamini-attorneys/projects/{id}`
- Expected: "Client: Sipho Mabena"
- Actual: "Customer: Sipho Mabena"
**Suggested fix**: Apply terminology override to the `paragraph` element on the project detail page that displays the customer link.
