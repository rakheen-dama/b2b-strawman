# ADR-357: Terminology Guard Extended to JSX/Prop Literals, Pack JSON and Backend Message Strings — Guard Tests on Both Sides, One Allow-List, the 589-Site Census as the Baseline That Must Reach Zero

**Status**: Accepted

**Context**:

Phase 87 deleted the runtime terminology layer ([ADR-338](ADR-338-terminology-layer-removal-single-vocabulary.md)) and Epic 611D added `frontend/__tests__/no-terminology-remnants.test.ts` (portal twin in 611E): it bans the deleted mechanism's identifiers and asserts that no **JSX text node** under `app/(app)/org/**` says `Project(s)` / `Customer(s)`, with `no-terminology-remnants.allowlist.json` for legitimate uses. The capstone's Day 90 sweep still found legacy words in breadcrumbs, settings tabs, dialogs, audit labels, reports, fee-note pages and pack text (G-A2…G-D20, E.8) because the guard's scope was JSX text only. R8's full census (`tasks/phase88-research/R8-terminology-census-shell-layout.md` §0–1) counts **≈589 user-visible sites**: 261 frontend/portal (148 JSX literals, 57 label maps, 33 error/toast/zod messages, 23 JSX props across 129 files), 24 pack JSON / email HTML, 26 backend labels and registries (`VerticalModuleRegistry`, `AuditEventTypeRegistry:110 "Invoice"`, `StandardReportPackSeeder`, `VariableMetadataRegistry`, `PrerequisiteContext`, `EmailTemplate`, `NotificationService` titles), and 278 backend exception/validation messages (4 `ResourceNotFoundException` strings across 132 call sites + 146 distinct others); ~259 test lines in ~70 files pin the old strings (R8 §8). Legitimate exceptions exist: `/api/invoices` and `/invoices` routes, `INV-%04d` numbering, the statutory "tax invoice" wording (VAT Act s20, `fee-note-za.json:171`), the ADR-342-pinned audit/entity literals, MCP tool descriptions written for Claude, marketing copy and archived code. Founder decision D11/D12 settles the tab words and audit labels.

**Options Considered**:

1. **Guard tests: extend the Epic 611 vitest to string literals in label/title/description/placeholder/aria-label props, `{ label: "…" }` maps and toast/error/zod messages (adding `Invoices?`), and add a backend JUnit source scan over pack JSON, email HTML, registries and exception/validation message literals; one allow-list per side (CHOSEN).**
   - Pros:
     - Runs in the existing `pnpm test` and `./mvnw verify` gates with no new tooling.
     - The merge-gate hook already enforces both.
     - A single mechanism on each side, extending the one that exists rather than adding a second.
     - The allow-list is reviewable data (file + text + reason, every entry must stay live), which is how 611D already works.
   - Cons:
     - A heuristic scan: it looks for the words in literal positions, so prose that uses "project" in a legitimate sense needs an allow-list entry.
     - A backend message built by concatenation can evade it (the census shows none).
2. **ESLint rule (frontend) + Checkstyle/ArchUnit rule (backend).**
   - Pros:
     - IDE feedback at edit time.
     - Per-file disable comments.
   - Cons:
     - Per-line `eslint-disable` comments are precisely the loophole an allow-list with reasons avoids.
     - A backend lint cannot read pack JSON or email HTML.
     - Two new rule implementations with their own test harnesses, for a check that a source-scan test does in 60 lines (`EntityTypeLiteralStabilityTest` is the precedent).
3. **Review-only (reviewer checklist item, no automation).**
   - Pros:
     - No code.
   - Cons:
     - 611 proved the words return within one phase without a guard.
     - CLAUDE.md §8 says status reports are not truth — a reviewer's eye is not a gate.
     - The sweep found 589 sites after a phase that "finished" terminology.

**Decision**: Option 1 — `no-terminology-remnants.test.ts` (and its portal twin) scan, in addition to JSX text: string-literal initialisers of `label`, `title`, `description`, `placeholder`, `aria-label`, `emptyMessage` props and object keys, `toast.*`/`setError`/zod message arguments and `error:` returns in server actions, for `\b(Projects?|Customers?|Invoices?)\b` under `app/`, `components/`, `lib/`, `hooks/`; a new backend `TerminologyRemnantScanTest` scans `src/main/resources/{field-packs,template-packs,clause-packs,automation-templates,compliance-packs,request-packs,templates/email}` and the message-bearing Java literals (`ResourceNotFoundException`/`ResourceConflictException`/`InvalidStateException`/`ValidationException`/`ForbiddenException` arguments, registry labels, notification titles) for the same words; each side has one allow-list file with `{ file, text, reason }`; the allow-list reasons are limited to: API/route paths (`/api/invoices`, `/invoices`), `INV-` numbering, statutory "tax invoice" wording, ADR-342-pinned literals (with the `// ADR-342` comment), MCP tool descriptions, marketing copy, archived code; U21 drives the census from 589 to 0 and the guard holds it there.

**Amended 2026-10-29 (628D)**: the backend allow-list reason set adds `statutory term` — a statute's own defined term that must not be paraphrased, e.g. "customer due diligence" (FIC Act 38 of 2001, Chapter 3 Part 1) in the conveyancing clause pack. It follows rationale 4 (named, finite, justified): each entry must cite the Act in its reason (`statutory term — FIC Act 38 of 2001 'customer due diligence'`). `statutory tax invoice` stays the separate VAT Act reason. Enforced by `TerminologyRemnantScanTest.REASONS`.

**Rationale**:

1. **A census, not a sample, needs a counter that cannot forget.** The requirement is "repo-wide grep showing zero outside the allow-list"; a test that *is* that grep turns the acceptance check into a permanent gate.
2. **Extend, do not duplicate.** The 611D test, its allow-list format and `readSourceTree` helper exist and are known to the team; the backend precedent (`EntityTypeLiteralStabilityTest`, a source scan with a comment convention) shows a JUnit scan is the project's way to pin literals.
3. **ADR-342 is the boundary.** Stored `entity_type` and event-key literals (`"project"`, `"customer"`, `"invoice"`) are never user-visible and never change; the guard allows them only where the ADR-342 comment is present, so the two guards compose instead of conflicting. Legal words for those literals live in the UI label map and the registry's display labels (D12, [ADR-349](ADR-349-activity-feed-correlation-key.md)).
4. **Statutory and wire exceptions are named, finite and justified**; a reason outside the enumerated set fails review, which keeps the allow-list from becoming option 3 by another name.
5. **Legal-only product:** there is no second vertical to need "Project" or "Customer" in user copy, so the baseline of zero is correct, not aspirational ([ADR-330](ADR-330-archive-tag-not-fork.md)).

**Consequences**:

- Positive:
  - E.8 PASS: the Day 90 sweep finds zero Project/Customer/Invoice in sidebar, breadcrumbs, settings, dialogs, audit labels, reports, fee-note pages and pack text, and the suites keep it so.
  - The four `ResourceNotFoundException` strings become constants (`"Client"`, `"Matter"`, `"Fee note"`, `"Disbursement"`) so 132 call sites change by one sed and cannot drift individually.
- Negative:
  - ~70 vitest/Playwright files and the backend pins listed in R8 §8 (`ReportingControllerTest:138`, `VariableMetadataEndpointTest:66-136`, `EmailNotificationChannelIntegrationTest:240`, `InvoiceTerminologyLegalZaTest`, `VisualRegressionTest:104,143` …) are updated in the same PRs.
  - This is the heaviest U21 cost and the reason it lands last among code units.
  - Pack-text changes reach existing tenants only through [ADR-356](ADR-356-pack-content-reconciliation.md).
  - The backend scan asserts the JSON, the reconcile asserts the tenant.
  - The scan rejects legitimate English ("project" as a verb) until allow-listed.
  - The reason set is deliberately narrow, so such prose is usually rewritten instead.
  - Xero copy keeps "Xero invoice" where the Xero object is meant (allow-listed with reason "third-party object name"), per R8's note 1.
- Related:
  - [ADR-338](ADR-338-terminology-layer-removal-single-vocabulary.md), [ADR-342](ADR-342-audit-and-entity-tag-strings-stable-under-renamed-wire.md), [ADR-341](ADR-341-settings-taxonomy-five-families-controls-stay-pages-go.md) (tab ids kept for redirects while labels change, D11), [ADR-356](ADR-356-pack-content-reconciliation.md), `tasks/phase88-gap-register.md` D11/D12, U21.
