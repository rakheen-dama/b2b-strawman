# Tech Debt Assessment — Verification & Improvement Plan

**Date:** 2026-05-11
**Assessors:** Architecture review team (5 parallel verification agents)
**Scope:** All findings in `kazi-architecture/80-tech-debt-and-gaps.md` (Sections A–N, 70+ items)
**Method:** Each claim cross-referenced against current codebase (`backend/`, `frontend/`, `portal/`, `gateway/`, `compose/`)

---

## 1. Verification Summary

| Category | Count |
|----------|-------|
| **Confirmed as stated** | ~55 |
| **Partially confirmed** (nuance changes risk assessment) | 5 |
| **Not actual issues** (false positives — remove from register) | 5 |

The architecture team's analysis was thorough and overwhelmingly accurate. The register is a reliable risk inventory. The main corrections are noted below.

---

## 2. Findings That Are NOT Actual Issues

These should be **removed** from `80-tech-debt-and-gaps.md`.

### A-04: "No webhook tenant-identification design" — FALSE

`PaymentWebhookController` already implements tenant identification:
- `extractTenantSchema(sanitizedProvider, payload)` parses provider-specific payloads
- `extractStripeSchema()` and `extractPayFastSchema()` resolve tenant from webhook data
- `RequestScopes.runForTenant()` binds tenant context before processing

Either implemented after the architecture scan or missed during discovery.

### B-08: "ADR-139/156 retired without Superseded marker" — FALSE

Both ADRs currently carry `Status: Proposed`. They were never retired or superseded. The finding is factually incorrect as written.

### H-09 / E-03: "RetainerPeriod.PeriodStatus.INVOICED unwired" — FALSE

`PeriodStatus` enum contains only `OPEN` and `CLOSED`. The `INVOICED` value was already removed. This finding is stale.

### H-07: "ViewFilterHelper tableName SQL surface" — NOT AN ISSUE

`tableName` is passed from caller-controlled enum / `SavedView`, never from user input. Safe by design. Only becomes a risk if the call-site contract changes — no action needed today.

### J-01: "AI assistant calls /api/assistant/chat directly" — UNVERIFIABLE

No AI assistant hook or component found in the current codebase. Feature likely not yet implemented or was removed.

---

## 3. Partially Confirmed Findings

These are real but the risk profile differs from what the register states.

### A-03: "No audit retention purge" — INFRASTRUCTURE EXISTS, DORMANT

`AuditRetentionProperties` defines `purgeEnabled`, `domainEventsDays` (default 1095), `securityEventsDays` (default 365). `RetentionService.findExpiredAuditEvents()` is implemented. Config `purge-enabled: false` means the purge never runs. Not "absent" — dormant. Activation is a config change plus a scheduled job wiring.

### A-08: "DSAR PAIA scope ZA-only" — PARTIALLY TRUE

`ComplianceTemplatePackSeeder` is hard-coded to `"ZA"`. However, `JurisdictionDefaults` already handles ZA, EU, BR with per-jurisdiction retention periods, regulator names, and mandatory document types. The broader compliance framework is multi-jurisdiction aware; only the PAIA template generation is ZA-locked.

### B-03: "Reporting uses Thymeleaf, ADR-263 says Tiptap" — TWO VALID PATHS

Thymeleaf is used for system-provided report templates (`ReportRenderingService`). Tiptap is used for user-authored document rendering (`TiptapRenderer`, which replaced Thymeleaf for documents). Both ADRs are correct for their respective scopes. This is intentional domain separation, not drift.

### H-08: "Audit triggers may not cover TRUNCATE" — TRUE BUT LOW PROBABILITY

`V12__create_audit_events.sql` creates a `BEFORE UPDATE` trigger. `V74__prevent_audit_delete.sql` creates a `BEFORE DELETE` trigger. Neither covers TRUNCATE (PostgreSQL triggers don't fire on TRUNCATE by default). Risk is low because TRUNCATE requires superuser privileges and would be an obvious administrative mistake.

### J-02: "Portal JWT in localStorage (XSS exposure)" — INTENTIONAL TRADEOFF

Confirmed in code: `localStorage.setItem(JWT_KEY, jwt)` in `portal/lib/auth.ts`. This is a deliberate decision per ADR-077, not a bug. Worth revisiting as portal scope grows, but not a defect.

---

## 4. Execution Plan

### Approach

- **Sequential execution** — one PR at a time, each reviewed and build-verified before moving on.
- **Small related items may be grouped** — hard limit of 3 items per PR.
- **Every PR must pass the full build** (`./mvnw verify` for backend, `pnpm lint && pnpm build && pnpm test` for frontend/portal).
- **Every PR gets a review pass** before merge.
- **Speed is not a priority.** Correctness and de-risking are.

### PR Sequence

#### ~~PR 1 — Security: backend authorization gaps (B-09, B-10)~~ ALREADY DONE

Both items were already implemented and tested before this assessment:

- **B-09:** `ReportingController` has class-level `@RequiresCapability("FINANCIAL_VISIBILITY")` + `ReportingControllerCapabilityIntegrationTest` (6 endpoint assertions).
- **B-10:** `RetainerAgreementService` calls `verticalModuleGuard.requireModule("retainer_agreements")` at 8 entry points + `RetainerModuleGuardIntegrationTest` (11 endpoint assertions).
- **Note:** `getRetainer()` (GET `/api/retainers/{id}`) intentionally omits `@RequiresCapability` — members can view a specific retainer by ID. Confirmed as intentional design 2026-05-11.

#### PR 2 — Security: portal JWT suspend revocation (J-03)

Single item — more complex, needs careful testing.

- Add `suspendedAt` timestamp check in portal auth middleware: if `jwt.iat < contact.suspendedAt`, reject.
- Backend: ensure `PortalContact.suspend()` sets `suspendedAt`.
- Test: suspend a contact, verify in-flight JWT is rejected; verify non-suspended contacts unaffected.
- **Build gate:** `./mvnw verify` + portal `pnpm lint && pnpm build && pnpm test`

#### PR 3 — Active defects: vertical profile JSON fixes (B-04, B-05)

Two one-line JSON fixes, same domain (vertical profile wiring).

- Fix `accounting-za.json`: `"deadlines"` → `"regulatory_deadlines"`.
- Add `"automation": ["automation-legal-za"]` to `legal-za.json` packs section.
- Boot-test or unit test confirming modules/packs resolve correctly.
- **Build gate:** `./mvnw verify`

#### PR 4 — Enforcement: filter-chain + audit ArchUnit tests (K-02, K-03)

Two related enforcement tests — both are ArchUnit rules protecting load-bearing conventions.

- ArchUnit test pinning security filter chain order (Bearer → Tenant → Member → SubscriptionGuard → PlatformAdmin).
- ArchUnit test enforcing "audit emission must be in-transaction" (no `@TransactionalEventListener` in audit package).
- **Build gate:** `./mvnw verify`

#### PR 5 — Enforcement: vertical-profile JSON validity + dead event (K-05, G-01, F-02)

Three small enforcement items.

- Unit test parsing all `vertical-profiles/*.json` files and asserting valid deserialization.
- Change `VerticalProfileRegistry` to fail-fast on parse error instead of silent `continue`.
- Either wire `MemberOverAllocatedEvent` to a notification listener or remove dead emission — decide based on product intent.
- **Build gate:** `./mvnw verify`

#### PR 6 — Cleanup: subscription plan-tier state removal (B-01 / H-05 / R-03)

Single item — medium risk, needs careful verification.

- Grep all references to `PENDING_CANCELLATION`, `GRACE_PERIOD`, `LOCKED`.
- Remove from `SubscriptionStatus` enum, `VALID_TRANSITIONS`, `ADMIN_ALLOWED_TARGETS`, `isWriteEnabled()`.
- Flyway migration to verify no DB rows exist in these states (fail if any do).
- **Build gate:** `./mvnw verify`

#### PR 7 — Cleanup: Member.clerkUserId rename (B-02)

Single item — straightforward rename with Flyway migration.

- Rename field to `idpSub` in Java.
- Flyway: `ALTER TABLE member RENAME COLUMN clerk_user_id TO idp_sub`.
- Update all DTOs, mappers, tests.
- **Build gate:** `./mvnw verify`

#### PR 8 — Cleanup: audit TRUNCATE + retention purge activation (A-07, A-03)

Two related audit-table items.

- Flyway migration: `REVOKE TRUNCATE ON audit_events FROM PUBLIC, app_user`.
- Wire `RetentionService.findExpiredAuditEvents()` into a `@Scheduled` purge job.
- Enable `purge-enabled: true` in prod config (keep `false` in local/test).
- **Build gate:** `./mvnw verify`

#### PR 9 — Cleanup: ProjectMember package move + retention cascade (D-08, E-01)

Two small items in the project/customer domain.

- Move `ProjectMember.java` from `member/` to `projects/` package.
- In `CustomerLifecycleService.offboard()`, cascade `retentionClockStartedAt` to customer's projects.
- Test: customer offboard → verify project retention clocks are set.
- **Build gate:** `./mvnw verify`

#### PR 10 — Cleanup: naming and doc fixes (C-01, B-07, B-06)

Three low-risk naming/documentation items.

- Rename "Customer Contact" → "Portal Contact" in `action-form.tsx`.
- Rename `consulting-generic.json` → `base.json` (or document the naming choice).
- Update phase 66 doc to match shipped `consulting-za.json`.
- **Build gate:** `./mvnw verify` + frontend `pnpm lint && pnpm build && pnpm test`

#### After PR 10 — ADR drafting (Wave 5+)

Remaining items require design decisions before implementation. Each ADR is a separate conversation:

1. **ADR: Observability stack** (R-02 / A-02 / I-01–I-08) — Micrometer+Prometheus vs OpenTelemetry, SLO definitions.
2. **ADR: BillingStatus/PaymentStatus consolidation** (C-02 / C-03) — single enum vs type-aliased per-domain.
3. **ADR: Frontend/portal shared package** (D-02 / J-05) — workspace extraction vs sync-check.
4. **ADR: OpenAPI codegen** (J-04) — springdoc + generator vs manual sync.
5. **ADR: Event payload versioning** (F-01 / F-05) — version field vs schema registry.
6. **ADR: Event listener DLQ** (F-03) — in-process retry vs persistent DLQ vs Spring Modulith.
7. **ADR: Profile-switch orphans / pack uninstall** (R-01 / A-01 / G-02) — reconciler removal vs admin flow vs accept fragility.
8. **ADR: OrgSettings decomposition** (D-07) — phased extraction behind facade.
9. **ADR: Pack content versioning** (G-03 / G-05) — migration framework for installed packs.

---

## 5. Items to Remove from the Register

Update `kazi-architecture/80-tech-debt-and-gaps.md` — delete these rows:

| ID | Reason for Removal |
|---|---|
| A-04 | Webhook tenant identification IS implemented in `PaymentWebhookController` |
| B-08 | ADR-139/156 are `Status: Proposed`, not retired — finding is incorrect |
| H-09 / E-03 | `PeriodStatus.INVOICED` already removed (enum is OPEN/CLOSED only) |
| J-01 | AI assistant feature not in current codebase |
| H-07 | Not an issue — `tableName` is caller-controlled from enum, not user input |

Update these rows to reflect nuance:

| ID | Correction |
|---|---|
| A-03 | Infrastructure exists (config + query). Reclassify as "purge dormant" not "purge absent." |
| A-08 | Jurisdiction framework supports multi-jurisdiction. Only PAIA template is ZA-only. |
| B-03 | Not drift — Thymeleaf for reports, Tiptap for documents. Both ADRs valid for their scope. |
| H-08 | Add "low probability" qualifier — TRUNCATE requires superuser. |
| J-02 | Add "intentional per ADR-077" — known tradeoff, not a bug. |

---

## 6. Items Not Addressed in This Plan

The following items from the register are **confirmed but intentionally deferred** because they are low-severity cosmetic issues or require product decisions beyond the engineering team's scope:

| ID | Item | Reason for Deferral |
|---|---|---|
| C-04 | `CLOSED` is non-terminal | By design for legal vertical. Document, don't change. |
| C-05 | "Tariff Schedule" overloaded | Two valid meanings in different contexts. Terminology map handles it. |
| C-06 | `consulting-generic` vs `consulting-za` | Addressed by B-07 rename in PR 10. |
| C-07 | "Workflow" is not a code term | Product terminology. Add to contributing guide when one exists. |
| E-02 | 7-state `LifecycleStatus` redundancy | Requires product decision on OFFBOARDING intermediate state. |
| E-06 | ChecklistTemplate snapshot vs live | Snapshot-on-instantiation is a valid pattern. Document as a contract. |
| F-04 | Sealed interface adds-only | Slight friction, not architectural. Accept. |
| F-05 | Cross-module event coupling | Addressed by F-01 (event versioning) in ADR phase. |
| D-06 | Dual project-detail paths | Intentional per ADR-031/078. Data shape drift addressed by J-04 (OpenAPI). |
| G-04 | Slug-conflict resolution undefined | Low probability. Add registration-time check when packs proliferate. |
| G-06 | Vertical-pack admin authoring | Product decision. Classpath-resource model works for now. |
| A-05 | No gateway rate limiting | Needs product decision on rate-limit policy. |
| A-06 | No integration-key rotation | Infrastructure exists. Enforcement cadence is a policy decision. |
| D-01 | PackInstaller migration (10 seeders) | Medium effort, no urgency. Schedule in housekeeping sprint. |
| D-05 | AutomationEventListener universal subscription | Profile actual cost first. May not be a problem at current scale. |
| L-* | Per-module fragility notes | Each tracked in its module page. No separate action needed. |

---

## 7. Risk Matrix

```
                    Low Effort          Medium Effort       High Effort
                    ─────────────────── ─────────────────── ───────────────────
  High Priority     B-09, B-10 (PR 1)   J-03 (PR 2)        R-02/A-02 (ADR)
  (security/defect) B-04, B-05 (PR 3)                       R-01/A-01 (ADR)
                    K-02, K-03 (PR 4)

  Medium Priority   B-02 (PR 7)         B-01/H-05 (PR 6)   D-02/J-05 (ADR)
  (debt/hygiene)    D-08 (PR 9)         E-01 (PR 9)        J-04 (ADR)
                    K-05, F-02 (PR 5)   C-02/C-03 (ADR)    F-01 (ADR)
                    A-07 (PR 8)         A-03 (PR 8)

  Low Priority      B-06, B-07 (PR 10)  —                   D-07 (ADR)
  (cosmetic/future) C-01 (PR 10)                            G-03 (ADR)
```
