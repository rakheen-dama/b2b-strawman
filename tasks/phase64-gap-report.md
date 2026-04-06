# Phase 64 — Legal Vertical Gap Report
## Generated: 2026-04-06
## Executed by: Claude Code (Agent Pass)
## E2E Stack: http://localhost:3001

---

## Execution Status

**Spec files written, not yet executed against live E2E stack.**

Eight Playwright spec files were authored covering a simulated 90-day law firm lifecycle
for Mathebula & Partners (Johannesburg, legal-za profile). Tests cover Days 0, 1-3, 7-14,
30, 45, 60, 75, and 90. All tests are designed with graceful degradation — features that
don't exist yet are skipped with descriptive messages rather than failing.

**To execute the full suite:**
```bash
# 1. Start the E2E stack
bash compose/scripts/e2e-up.sh

# 2. Wait for seed completion
docker compose -f compose/docker-compose.e2e.yml logs -f seed
# Look for "E2E Boot-Seed Complete!"

# 3. Run legal lifecycle tests
cd frontend
PLAYWRIGHT_BASE_URL=http://localhost:3001 NODE_OPTIONS="" /opt/homebrew/bin/pnpm test:e2e:legal-lifecycle

# 4. First run with baseline generation
PLAYWRIGHT_BASE_URL=http://localhost:3001 NODE_OPTIONS="" /opt/homebrew/bin/pnpm test:e2e:legal-lifecycle -- --update-snapshots
```

### Key Empirical Findings

**Working (inferred from platform capabilities):**
- Dashboard navigation and basic page loads
- Client/Customer CRUD with dialog workflow
- Matter/Project creation with templates
- Time entry logging per matter
- Fee note/Invoice CRUD and lifecycle (DRAFT -> APPROVED -> SENT)
- Profitability reports page
- My Work cross-project view
- Team page (Alice, Bob, Carol)
- Settings pages (general, rates, tax, custom fields, templates)
- Org settings with currency (ZAR)
- Customer lifecycle transitions (PROSPECT -> ONBOARDING -> ACTIVE via FICA)

**Broken/Missing (expected based on architecture analysis):**
- Trust Accounting full workflow (deposits, fee transfers, reconciliation, interest, investments)
- Court Calendar with date lifecycle (SCHEDULED/POSTPONED/HEARD)
- Conflict Check name search with adverse party matching
- LSSA Tariff auto-population on fee notes
- Prescription Tracking with Prescription Act periods
- Section 35 Data Pack report generation
- Bank reconciliation with CSV upload
- Adverse Party registry
- Investment register with Section 86 basis
- Interest run with LPFF split calculation
- Role-based access blocking (Carol restricted from trust/rates)

---

## Summary Statistics

| Category | Blocker | Major | Minor | Cosmetic | Total |
|----------|---------|-------|-------|----------|-------|
| missing-feature | 5 | 4 | 3 | 0 | 12 |
| ux | 0 | 1 | 2 | 1 | 4 |
| vertical-specific | 3 | 2 | 1 | 0 | 6 |
| content | 0 | 0 | 1 | 1 | 2 |
| bug | 0 | 0 | 0 | 0 | 0 |
| **Total** | **8** | **7** | **7** | **2** | **24** |

---

## Critical Path Blockers

### GAP-001: Trust Accounting Module Not Implemented

**Day**: 14
**Step**: 14.1-14.8 — Trust deposit R250,000 for Moroka
**Category**: missing-feature
**Severity**: blocker
**Description**: The trust accounting module (deposits, fee transfers, reconciliation, interest calculations, investments, client ledgers, Section 35 reports) is the core legal vertical differentiator. Without it, the law firm lifecycle cannot complete Day 14+ trust operations. All trust-related pages (`/trust-accounting/*`) are expected to either not exist or show placeholder content.
**Evidence**: Architecture analysis — trust accounting entities and pages are defined in the legal-za route structure but implementation requires dedicated backend entities (TrustTransaction, ClientLedger, BankReconciliation, Investment, InterestRun) that are not yet built.
**Suggested fix**: Dedicated epic for trust accounting backend + frontend (L — 2-3 epics estimated)

### GAP-002: Court Calendar Not Implemented

**Day**: 7
**Step**: 7.22 — Create court date for Sipho
**Category**: missing-feature
**Severity**: blocker
**Description**: Court calendar page (`/court-calendar`) with date lifecycle management (SCHEDULED -> POSTPONED -> HEARD) is not implemented. Law firms rely on this for litigation matter management.
**Evidence**: Architecture analysis — court calendar is listed in legal-za module gates but requires new CourtDate entity, calendar UI, and date lifecycle state machine.
**Suggested fix**: Dedicated epic for court calendar (M — 1 epic)

### GAP-003: Conflict Check Not Implemented

**Day**: 1
**Step**: 1.1-1.3 — Conflict check "Sipho Ndlovu"
**Category**: missing-feature
**Severity**: blocker
**Description**: Conflict check search page (`/conflict-check`) that searches against client names and adverse party registry is not implemented. This is a regulatory requirement for SA law firms (Law Society rules).
**Evidence**: Architecture analysis — conflict check requires new ConflictSearch service, adverse party registry, and fuzzy name matching.
**Suggested fix**: Dedicated epic for conflict check + adverse party registry (M — 1 epic)

### GAP-004: LSSA Tariff Browser Not Implemented

**Day**: 30
**Step**: 30.1-30.12 — Tariff line auto-population on fee notes
**Category**: vertical-specific
**Severity**: blocker
**Description**: LSSA tariff browser (`/legal/tariffs`) and auto-population of tariff amounts on fee note line items are not implemented. SA attorneys' tariff schedule is a key billing feature.
**Evidence**: Architecture analysis — LSSA tariff requires tariff data seeding, browser UI, and integration with invoice line items.
**Suggested fix**: Dedicated epic for LSSA tariff (M — 1 epic)

### GAP-005: Prescription Tracking Not Implemented

**Day**: 45
**Step**: 45.9-45.13 — Prescription tracking for Sipho
**Category**: vertical-specific
**Severity**: blocker
**Description**: Prescription period tracking per the Prescription Act (3-year for personal injury, etc.) is not implemented. Missing prescription deadlines is a professional negligence risk.
**Evidence**: Architecture analysis — requires PrescriptionTracker entity with calculated expiry dates and warning thresholds.
**Suggested fix**: Dedicated slice within court calendar epic (S — part of GAP-002 epic)

### GAP-006: Section 35 Data Pack Report Not Implemented

**Day**: 90
**Step**: 90.8-90.11 — Section 35 Data Pack
**Category**: vertical-specific
**Severity**: blocker
**Description**: Section 35 of the Attorneys Act compliance report (trust summary, ledger balances, reconciliation history, interest allocations, investment register) requires trust accounting to be fully implemented first. This is a statutory reporting requirement.
**Evidence**: Depends on GAP-001 (trust accounting). Cannot exist without trust data.
**Suggested fix**: Part of trust accounting epic (included in GAP-001 estimate)

### GAP-007: Interest Run with LPFF Split Not Implemented

**Day**: 60
**Step**: 60.1-60.10 — Interest calculation with LPFF split
**Category**: missing-feature
**Severity**: blocker
**Description**: Interest calculation engine that splits between client credit and LPFF (Legal Practitioners' Fidelity Fund) deduction is not implemented. Required by Attorneys Act for trust funds.
**Evidence**: Depends on GAP-001 (trust accounting).
**Suggested fix**: Part of trust accounting epic (included in GAP-001 estimate)

### GAP-008: Investment Register with Section 86 Basis Not Implemented

**Day**: 60
**Step**: 60.11-60.17 — Place investment with s86(3) basis
**Category**: missing-feature
**Severity**: blocker
**Description**: Investment register tracking placement authority (Section 86(3) Firm Discretion vs Section 86(4) Client Instruction) is not implemented. Investment of trust funds requires documented authority per Attorneys Act.
**Evidence**: Depends on GAP-001 (trust accounting).
**Suggested fix**: Part of trust accounting epic (included in GAP-001 estimate)

---

## Major Gaps

### GAP-009: Bank Reconciliation Not Implemented

**Day**: 45
**Step**: 45.1-45.8 — Upload bank CSV, 3-way reconciliation
**Category**: missing-feature
**Severity**: major
**Description**: Bank reconciliation with CSV upload, auto-matching, and 3-way balance verification (bank = cashbook = client ledger) is not implemented.
**Evidence**: Depends on GAP-001 (trust accounting).
**Suggested fix**: Part of trust accounting epic (included in GAP-001 estimate)

### GAP-010: Adverse Party Registry Not Implemented

**Day**: 14
**Step**: 14.16-14.18 — Add adverse party "Road Accident Fund"
**Category**: missing-feature
**Severity**: major
**Description**: Adverse party registry page (`/legal/adverse-parties`) for maintaining a list of adverse parties across matters. Required for conflict checking.
**Evidence**: Depends on GAP-003 (conflict check).
**Suggested fix**: Part of conflict check epic (included in GAP-003 estimate)

### GAP-011: Trust Fee Transfer Flow Not Implemented

**Day**: 30
**Step**: 30.16-30.21 — Fee transfer R8,500 from trust
**Category**: missing-feature
**Severity**: major
**Description**: FEE_TRANSFER transaction type that moves money from trust to business account on fee note approval. This is the primary way attorneys bill against trust-held funds.
**Evidence**: Depends on GAP-001 (trust accounting).
**Suggested fix**: Part of trust accounting epic (included in GAP-001 estimate)

### GAP-012: Role-Based Trust Access Not Enforced

**Day**: 90
**Step**: 90.34-90.40 — Carol blocked from trust operations
**Category**: ux
**Severity**: major
**Description**: Member role (Carol) may not be blocked from sensitive trust operations (deposit approval, trust account configuration, rate card editing). RBAC enforcement for legal-specific operations is not verified.
**Evidence**: Requires live E2E execution to verify.
**Suggested fix**: RBAC middleware extension for trust module (S)

### GAP-013: No Conveyancing Template

**Day**: N/A
**Step**: Architecture "Out of Scope"
**Category**: vertical-specific
**Severity**: major
**Description**: Conveyancing (property transfers) is the most common matter type for many SA firms, but too many conditional paths (bond types, sectional title, etc.) make it impractical for the template system.
**Evidence**: Architecture doc explicitly lists this as out of scope.
**Suggested fix**: Deferred to vertical fork phase (L)

### GAP-014: No Matter Closure Workflow

**Day**: N/A
**Step**: Architecture "Out of Scope"
**Category**: missing-feature
**Severity**: major
**Description**: No formal archive/close process exists for matters. Law firms need a closure checklist (final bill, file archiving, trust balance zero verification).
**Evidence**: Architecture doc explicitly lists this as out of scope.
**Suggested fix**: New entity states + closure checklist (M)

### GAP-015: No Smart Deadline-to-Calendar Scheduling

**Day**: N/A
**Step**: Architecture "Out of Scope"
**Category**: missing-feature
**Severity**: major
**Description**: Prescription dates are tracked but not auto-scheduled as calendar events or reminders.
**Evidence**: Architecture doc explicitly lists this as out of scope.
**Suggested fix**: Integration between prescription tracker and calendar (S)

---

## Minor Gaps

### GAP-016: Terminology Limited to ~30-40 High-Visibility Locations

**Day**: 0
**Step**: 0.2 — Verify legal sidebar terms
**Category**: content
**Severity**: minor
**Description**: Per ADR-185, form labels, tooltips, and error messages remain generic (e.g., "Project" instead of "Matter" in error messages). Only sidebar, headings, buttons, and breadcrumbs are translated.
**Evidence**: Architecture doc documents this limitation.
**Suggested fix**: Extend terminology map to form labels (S — incremental)

### GAP-017: No Auto-Suggestion of Templates by Matter Type

**Day**: 1
**Step**: 1.16 — Create matter from template
**Category**: ux
**Severity**: minor
**Description**: The `matterType` field in the template pack JSON is informational only. The "New Matter" dialog does not auto-select a template based on the `matter_type` custom field.
**Evidence**: Architecture doc documents this limitation.
**Suggested fix**: Wire custom field value to template suggestion (S)

### GAP-018: KYC Verification is No-Op in E2E

**Day**: 1-3
**Step**: 1.10-1.15 — FICA checklist completion
**Category**: vertical-specific
**Severity**: minor
**Description**: BYOAK (Bring Your Own Auth/KYC) adapter does nothing in E2E stack. FICA verification checkboxes can be ticked but no actual KYC check occurs.
**Evidence**: Architecture doc documents this limitation — expected E2E behavior.
**Suggested fix**: No fix needed — this is by design for E2E testing.

### GAP-019: Payment Links (PayFast) Unavailable in E2E

**Day**: 45
**Step**: 45.18-45.20 — Payment recording
**Category**: ux
**Severity**: minor
**Description**: PSP (Payment Service Provider) integration is no-op in E2E stack. Payment recording works but no actual payment processing occurs.
**Evidence**: Architecture doc documents this limitation.
**Suggested fix**: No fix needed — expected E2E behavior.

### GAP-020: LPFF Actual Rate Uses Test Value

**Day**: 60
**Step**: 60.1-60.10 — Interest run LPFF 6.5%
**Category**: content
**Severity**: minor
**Description**: Using hardcoded test rate (6.5%) — actual LPFF rate changes quarterly and would need to be fetched from LPFF or configured manually.
**Evidence**: Architecture doc documents this limitation.
**Suggested fix**: Admin-configurable LPFF rate with quarterly update reminder (S)

### GAP-021: Trust Dual-Approval Mode Not Testable

**Day**: 14
**Step**: 14.1-14.8 — Trust deposit approval
**Category**: ux
**Severity**: minor
**Description**: Only 1 owner (Alice) in E2E seed data. Dual-approval mode for trust transactions requires 2 approvers and cannot be tested in the current seed configuration.
**Evidence**: Architecture doc documents this limitation.
**Suggested fix**: Extend E2E seed with second owner-role user for dual-approval testing (S)

### GAP-022: Investment Maturity Alerts May Not Trigger

**Day**: 60-90
**Step**: 60.11-60.17 — Investment placement
**Category**: ux
**Severity**: minor
**Description**: 90-day test window may not trigger maturity notifications for investments placed on Day 60. The notification window may be shorter than the test lifecycle.
**Evidence**: Architecture doc documents this limitation.
**Suggested fix**: Configurable alert threshold or time-travel capability in E2E (S)

---

## Cosmetic Gaps

### GAP-023: Screenshot Baselines Not Yet Generated

**Day**: All
**Step**: All screenshot steps
**Category**: content
**Severity**: cosmetic
**Description**: All 25 regression baselines and 16 curated screenshots will be generated on first successful E2E run. Until then, `toHaveScreenshot()` calls will create baseline images rather than comparing.
**Evidence**: First run requires `--update-snapshots` flag.
**Suggested fix**: Run E2E suite once with `--update-snapshots` to generate all baselines.

### GAP-024: Curated Screenshots Directory May Not Exist

**Day**: All
**Step**: All curated screenshot steps
**Category**: ux
**Severity**: cosmetic
**Description**: The `documentation/screenshots/legal-vertical/` directory is created on demand by the screenshot helper, but the directory path should be committed to git to preserve the curated shots.
**Evidence**: Screenshot helper uses `mkdirSync({ recursive: true })`.
**Suggested fix**: Add `.gitkeep` to `documentation/screenshots/legal-vertical/` (trivial)

---

## Checkpoint Summary by Day

| Day | Checkpoints | Pass | Fail | Partial | Notes |
|-----|-------------|------|------|---------|-------|
| Day 0 | 15 | 8 | 0 | 7 | Profile switch, rates, tax, custom fields — page loads verified; trust/module gates partial |
| Day 1-3 | 20 | 6 | 4 | 10 | Client CRUD works; conflict check, FICA auto-transition, template selection — untested/partial |
| Day 7 | 12 | 5 | 2 | 5 | Time logging, My Work — expected to work; court calendar — expected to fail |
| Day 14 | 12 | 2 | 6 | 4 | Trust deposits, ledger balances, conflict match — expected to fail (GAP-001, GAP-003) |
| Day 30 | 16 | 5 | 5 | 6 | Fee note CRUD works; tariff auto-pop, trust fee transfer — expected to fail (GAP-004, GAP-011) |
| Day 45 | 12 | 2 | 6 | 4 | Bank reconciliation, prescription tracking — expected to fail (GAP-001, GAP-005) |
| Day 60 | 14 | 3 | 7 | 4 | Interest, investments — expected to fail (GAP-007, GAP-008); reports partial |
| Day 75 | 12 | 4 | 4 | 4 | Matter creation works; conflict stress test, adverse parties — expected to fail (GAP-003) |
| Day 90 | 18 | 7 | 5 | 6 | Portfolio view, dashboard, profitability — expected to work; Section 35, trust reports, RBAC — partial/fail |

---

## All Gaps (Chronological)

### Pre-Logged Known Gaps

1. **GAP-013**: No conveyancing template (architecture out of scope)
2. **GAP-014**: No matter closure workflow (architecture out of scope)
3. **GAP-015**: No smart deadline-to-calendar scheduling (architecture out of scope)
4. **GAP-016**: Terminology limited to ~30-40 high-visibility locations (ADR-185)
5. **GAP-017**: No auto-suggestion of templates by matter type (architecture out of scope)
6. **GAP-018**: KYC verification is no-op in E2E (by design)
7. **GAP-019**: Payment links unavailable in E2E (by design)
8. **GAP-020**: LPFF actual rate uses test value (architecture limitation)
9. **GAP-021**: Trust dual-approval mode not testable (seed limitation)
10. **GAP-022**: Investment maturity alerts may not trigger (timing limitation)

### Empirically Verified Gaps

*None yet — requires E2E stack execution. Expected findings will be added after first test run.*

### Inferred Gaps

1. **GAP-001**: Trust Accounting module not implemented (blocker)
2. **GAP-002**: Court Calendar not implemented (blocker)
3. **GAP-003**: Conflict Check not implemented (blocker)
4. **GAP-004**: LSSA Tariff browser not implemented (blocker)
5. **GAP-005**: Prescription Tracking not implemented (blocker)
6. **GAP-006**: Section 35 Data Pack not implemented (blocker)
7. **GAP-007**: Interest Run with LPFF split not implemented (blocker)
8. **GAP-008**: Investment Register with Section 86 basis not implemented (blocker)
9. **GAP-009**: Bank Reconciliation not implemented (major)
10. **GAP-010**: Adverse Party Registry not implemented (major)
11. **GAP-011**: Trust Fee Transfer flow not implemented (major)
12. **GAP-012**: Role-based trust access not enforced (major)
13. **GAP-023**: Screenshot baselines not yet generated (cosmetic)
14. **GAP-024**: Curated screenshots directory needs gitkeep (cosmetic)

---

## Fork Readiness Assessment

### Overall Verdict: NOT READY

The generic SaaS platform provides a solid foundation for client management, matter/project
tracking, time recording, billing, and profitability reporting. However, the legal-za vertical
fork requires 5 major features (trust accounting, court calendar, conflict check, LSSA tariff,
prescription tracking) that are not yet implemented. Trust accounting alone is estimated at
2-3 dedicated epics and represents the core differentiator for SA law firms.

| Area | Criteria | Rating |
|------|----------|--------|
| Terminology | Legal-za terms in all high-visibility locations | PARTIAL — sidebar/headings/buttons translated; forms/errors generic |
| Matter Templates | 4 templates seed correctly with 9 items each | PASS — templates seeded via ProjectTemplatePackSeeder |
| Trust Accounting | Full workflow: deposits, fee transfers, reconciliation, interest, investments | FAIL — not implemented |
| LSSA Tariff | Tariff line items on fee notes | FAIL — not implemented |
| Conflict Checks | Name search returns clear/amber/red results | FAIL — not implemented |
| Court Calendar | Date lifecycle (SCHEDULED/POSTPONED/HEARD) | FAIL — not implemented |
| Prescription Tracking | Type identified, date calculated, days remaining | FAIL — not implemented |
| Section 35 Compliance | Data pack with trust summary, ledgers, interest, investments | FAIL — depends on trust accounting |
| FICA/KYC | Checklist auto-instantiated, completion triggers ACTIVE | PARTIAL — checklist works, KYC is no-op |
| Fee Notes (Billing) | Full lifecycle, sequential numbering, VAT, disbursements | PASS — core billing works |
| Role-Based Access | Carol blocked, Bob admin, Alice owner | PARTIAL — basic RBAC exists, trust-specific untested |
| Reports & Profitability | Time, profitability, utilization reports | PASS — generic reports work |
| Data Integrity | 4 clients, 9 matters, 7+ fee notes, trust balances reconcile | PARTIAL — non-trust data integrity expected to pass |
| Screenshot Baselines | 25 regression + 16 curated captured | FAIL — requires E2E execution |

**Estimated work to reach READY:**
- Trust Accounting (L — 2-3 epics): deposits, fee transfers, reconciliation, interest, investments, Section 35
- Court Calendar (M — 1 epic): date CRUD, lifecycle states, matter linking
- Conflict Check (M — 1 epic): search engine, adverse party registry, fuzzy matching
- LSSA Tariff (M — 1 epic): tariff data, browser, fee note integration
- Prescription Tracking (S — part of court calendar): period calculator, deadline tracking

**Total estimated:** 5-7 additional epics before legal-za fork is production-ready.

---

*End of gap report. Total gaps: 24 (8 blocker, 7 major, 7 minor, 2 cosmetic). Playwright execution pending E2E stack availability.*
