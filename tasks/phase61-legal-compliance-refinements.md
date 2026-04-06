# Phase 61 — Legal Compliance Refinements: Section 86 Investment Distinction & KYC Verification Integration

Phase 61 closes two compliance gaps identified in the Phase 60 trust accounting system and the Phase 14 checklist engine. Track 1 adds an investment basis distinction (Section 86(3) firm-initiated vs Section 86(4) client-instructed) with conditional LPFF share in interest calculations. Track 2 adds optional KYC verification integration via the existing BYOAK infrastructure from Phase 21.

**Architecture doc**: `architecture/phase61-legal-compliance-refinements.md`

**ADRs**:
- [ADR-235](adr/ADR-235-statutory-vs-configurable-lpff-share.md) -- Statutory vs Configurable LPFF Share for Section 86(4) investments (hardcoded 5% constant)
- [ADR-236](adr/ADR-236-kyc-provider-adapter-strategy.md) -- KYC Provider Adapter Strategy (BYOAK with KycVerificationPort, Check ID SA returns NEEDS_REVIEW)

**Dependencies on prior phases**:
- **Phase 60** (Trust Accounting): `TrustInvestment` entity, `TrustInvestmentService`, `TrustInvestmentRepository`, `InterestCalculationService`, `InterestAllocation` entity, `LpffRate`, `TrustAccount` -- all must exist before Phase 61 runs
- **Phase 14** (Customer Compliance & Lifecycle): `ChecklistInstanceItem` entity, `ChecklistInstanceService`, FICA checklist packs
- **Phase 21** (BYOAK Integrations): `IntegrationDomain` enum, `@IntegrationAdapter` annotation, `IntegrationRegistry`, `SecretStore`, `OrgIntegration` entity, `IntegrationGuardService`
- **Phase 6** (Audit): `AuditService`, `AuditEventBuilder`

**Migration note**: V86 tenant migration covers both tracks (investment_basis column + checklist verification columns + interest_allocations audit columns). V85 is the latest existing migration.

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 452 | Foundation: V86 Migration + InvestmentBasis Enum + TrustAccountingConstants | Backend | -- (Phase 60 complete) | S | 452A | **Done** (PR #957) |
| 453 | Interest Calculation Basis Distinction + Audit Trail | Backend | 452 | M | 453A, 453B | **Done** (PR #958) |
| 454 | Investment Register Report + Section 35 Data Pack Updates | Backend | 453 | S | 454A | |
| 455 | Frontend: Investment Form + Register + Interest Table Updates | Frontend | 453 | M | 455A | |
| 456 | KYC Adapter Infrastructure: Port, Adapters, Service | Backend | 452 | M | 456A, 456B | |
| 457 | KYC Controller + ChecklistInstanceItem Extension | Backend | 456 | S | 457A | |
| 458 | Frontend: KYC Verification Dialog + Checklist Integration | Frontend | 457 | M | 458A, 458B | |

---

## Dependency Graph

```
PHASE 60 COMPLETE (epics 441-451)
Phase 14 (ChecklistInstanceItem exists)
Phase 21 (BYOAK integration infrastructure exists)
        |
        |
[E452A V86 migration (investment_basis + checklist verification
 columns + interest_allocations audit columns) + InvestmentBasis
 enum + TrustAccountingConstants class]
        |
        +───────────────────────────────────────+
        |                                       |
  TRACK 1: Investment Basis            TRACK 2: KYC Verification
  (sequential)                         (sequential, independent)
  ─────────────────────                ──────────────────────────
        |                                       |
[E453A TrustInvestment entity                [E456A KycVerificationPort
 extension (investmentBasis field) +          interface + request/result
 TrustInvestmentService extension +           records + status enum +
 InterestCalculationService                   VerifyNowKycAdapter +
 conditional LPFF share logic +               CheckIdKycAdapter +
 InterestAllocation extension                 NoOpKycAdapter +
 (statutoryRateApplied) + tests]              IntegrationDomain extension
        |                                     + unit tests]
[E453B Investment endpoint                       |
 modifications (investmentBasis                [E456B KycVerificationService
 field in request/response +                   (resolve adapter, call
 filter parameter) + additional                verify, update checklist
 integration tests]                            item, audit) +
        |                                     ChecklistInstanceItem
[E454A Investment register report              entity extension (5
 update (basis column + LPFF rate              verification columns) +
 per investment) + Section 35                  integration tests]
 data pack (86(3)/86(4) separation)                |
 + tests]                                     [E457A KycVerification
        |                                      Controller (3 endpoints:
[E455A Frontend: Place Investment              verify, poll result,
 dialog basis radio + register                 integration status) +
 table basis column/filter +                   controller integration
 interest allocation statutory                 tests]
 rate display + 86(6) advisory                     |
 note + tests]                                [E458A Frontend: KYC
                                               "Verify Now" button
                                               on checklist + KYC
                                               verification dialog
                                               + POPIA consent flow
                                               + result display +
                                               types + schemas +
                                               actions + tests]
                                                   |
                                              [E458B Frontend: KYC
                                               integration settings
                                               card + configure dialog
                                               + test connection +
                                               remove integration +
                                               tests]
```

**Parallel opportunities**:
- After E452A: Track 1 (E453) and Track 2 (E456) are completely independent and can run in parallel.
- After E453B: E454 (reports) and E455 (frontend) can start. E455 only depends on API changes from E453, not on E454.
- After E456B: E457 (controller) follows sequentially.
- After E457A: E458A and E458B are independent frontend slices and can run in parallel.
- Track 1 and Track 2 share only the V86 migration (E452A). After that, they have zero dependencies on each other.

---

## Implementation Order

### Stage 0: Foundation (Migration + Enums + Constants)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 0a | 452 | 452A | V86 tenant migration (`investment_basis` column on `trust_investments`, 5 verification columns on `checklist_instance_items`, `lpff_rate_id` + `statutory_rate_applied` on `interest_allocations`). `InvestmentBasis` enum. `TrustAccountingConstants` class with `STATUTORY_LPFF_SHARE_PERCENT`. Unit tests (~3). Backend only. | **Done** (PR #957) |

### Stage 1: Investment Basis Logic + KYC Port/Adapters (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 1a (parallel) | 453 | 453A | Extend `TrustInvestment` entity with `investmentBasis` field. Extend `TrustInvestmentService` to accept/return `investmentBasis`. Extend `InterestCalculationService` with conditional LPFF share logic (statutory 5% for CLIENT_INSTRUCTION, general rate for FIRM_DISCRETION). Extend `InterestAllocation` with `lpffRateId` + `statutoryRateApplied`. Integration tests (~12). Backend only. | **Done** (PR #958) |
| 1b (parallel) | 456 | 456A | `KycVerificationPort` interface + `KycVerificationRequest` record + `KycVerificationResult` record + `KycVerificationStatus` enum + `VerifyNowKycAdapter` + `CheckIdKycAdapter` + `NoOpKycAdapter`. Add `KYC_VERIFICATION` to `IntegrationDomain` enum. Unit tests (~8). Backend only. | |

### Stage 2: Investment Endpoints + KYC Service (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 2a (parallel) | 453 | 453B | Modify investment endpoints to accept/return `investmentBasis` field. Add `?investmentBasis=` filter parameter. Additional controller integration tests (~6). Backend only. | **Done** (PR #958) |
| 2b (parallel) | 456 | 456B | `KycVerificationService` orchestrator (resolve adapter, call verify, update checklist item, record POPIA consent in metadata, audit). Extend `ChecklistInstanceItem` entity with 5 verification columns. Integration tests (~10). Backend only. | |

### Stage 3: Reports + KYC Controller (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 3a (parallel) | 454 | 454A | Extend investment register report data provider (basis column + LPFF rate per investment). Update Section 35 data pack to separate 86(3) and 86(4) investments. Integration tests (~8). Backend only. | |
| 3b (parallel) | 457 | 457A | `KycVerificationController` with 3 endpoints: `POST /api/kyc/verify`, `GET /api/kyc/result/{ref}`, `GET /api/integrations/kyc/status`. Controller integration tests (~6). Backend only. | |

### Stage 4: Frontend Investment + Frontend KYC (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 4a (parallel) | 455 | 455A | Place Investment dialog: investment basis radio group + help text + 86(6) advisory note. Investment register table: basis column + filter + statutory rate display. Interest allocation table: "5% (statutory)" display. Frontend tests (~8). Frontend only. | |
| 4b (parallel) | 458 | 458A | Conditional "Verify Now" button on FICA checklist items. KYC verification dialog with POPIA consent flow. Result display per status (VERIFIED/NOT_VERIFIED/NEEDS_REVIEW/ERROR). Types, Zod schemas, server actions. Frontend tests (~6). Frontend only. | |

### Stage 5: Frontend KYC Settings

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 5a | 458 | 458B | KYC integration settings card in Settings -> Integrations. Configure dialog (provider selector, API key, test connection). Remove integration action. Frontend tests (~4). Frontend only. | |

### Timeline

```
Stage 0:  [452A]                                             <- foundation (single slice)
Stage 1:  [453A]  //  [456A]                                 <- investment logic + KYC adapters (parallel)
Stage 2:  [453B]  //  [456B]                                 <- investment endpoints + KYC service (parallel)
Stage 3:  [454A]  //  [457A]                                 <- reports + KYC controller (parallel)
Stage 4:  [455A]  //  [458A]                                 <- frontend investment + frontend KYC (parallel)
Stage 5:  [458B]                                             <- KYC settings card (sequential after 458A)
```

---

## Epic 452: Foundation -- V86 Migration + InvestmentBasis Enum + TrustAccountingConstants

**Goal**: Lay the database foundation for both Phase 61 tracks. Create the V86 tenant migration that adds the `investment_basis` column to `trust_investments`, five verification columns to `checklist_instance_items`, and two audit trail columns to `interest_allocations`. Create the `InvestmentBasis` enum and the `TrustAccountingConstants` class with the statutory LPFF share constant.

**References**: Architecture doc Sections 61.2.1 (TrustInvestment extension), 61.2.3 (ChecklistInstanceItem extension), 61.2.4 (InterestAllocation extension), 61.2.2 (InvestmentBasis enum), 61.2.9 (statutory constant), 61.7 (V86 migration SQL); ADR-235 (why constant, not configurable).

**Dependencies**: Phase 60 complete (all Phase 60 tables must exist -- V85 migration already applied).

**Scope**: Backend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **452A** | 452.1--452.5 | V86 tenant migration (ALTER TABLE for investment_basis + 5 checklist verification columns + 2 interest_allocations audit columns + CHECK constraints). `InvestmentBasis` enum. `TrustAccountingConstants` class with `STATUTORY_LPFF_SHARE_PERCENT = 0.05`. Unit tests (~3). Backend only. | **Done** (PR #957) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 452.1 | Create V86 tenant migration | 452A | -- | New file: `backend/src/main/resources/db/migration/tenant/V86__phase61_investment_basis_and_kyc_verification.sql`. Full SQL from architecture doc Section 61.7: ALTER TABLE `trust_investments` ADD `investment_basis` VARCHAR(20) NOT NULL DEFAULT 'FIRM_DISCRETION' + CHECK constraint. ALTER TABLE `checklist_instance_items` ADD 5 nullable columns (`verification_provider` VARCHAR(30), `verification_reference` VARCHAR(200), `verification_status` VARCHAR(20), `verified_at` TIMESTAMPTZ, `verification_metadata` JSONB) + CHECK constraint on verification_status. ALTER TABLE `interest_allocations` ADD `lpff_rate_id` UUID REFERENCES `lpff_rates(id)` + `statutory_rate_applied` BOOLEAN NOT NULL DEFAULT false. Pattern: `V85__create_trust_accounting_tables.sql` for syntax conventions. |
| 452.2 | Create `InvestmentBasis` enum | 452A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/InvestmentBasis.java`. Two values: `FIRM_DISCRETION` (Section 86(3)), `CLIENT_INSTRUCTION` (Section 86(4)). Simple enum, no constructor params needed. Pattern: `TrustAccountStatus.java` or `TrustAccountType.java` in the same package. |
| 452.3 | Create `TrustAccountingConstants` class | 452A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustAccountingConstants.java`. Public final class with private constructor. `public static final BigDecimal STATUTORY_LPFF_SHARE_PERCENT = new BigDecimal("0.05");` with Javadoc comment citing Section 86(5) of the Legal Practice Act. See ADR-235. |
| 452.4 | Write migration smoke test | 452A | 452.1 | Extend existing migration test or create new: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/V86MigrationTest.java`. 2 tests: (1) V86 applies without error on a schema with V85 already applied, (2) CHECK constraint on `investment_basis` rejects invalid values (direct SQL INSERT with 'INVALID' value should fail). Integration test with Testcontainers. Pattern: Phase 60 migration tests (epic 438 task 438.5). |
| 452.5 | Write unit test for constant and enum | 452A | 452.2, 452.3 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustAccountingConstantsTest.java`. 1 test: verify `STATUTORY_LPFF_SHARE_PERCENT` equals `new BigDecimal("0.05")` with exact scale. Pure unit test, no Spring context. |

### Key Files

**Slice 452A -- Create:**
- `backend/src/main/resources/db/migration/tenant/V86__phase61_investment_basis_and_kyc_verification.sql`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/InvestmentBasis.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustAccountingConstants.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/V86MigrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustAccountingConstantsTest.java`

**Slice 452A -- Read for context:**
- `backend/src/main/resources/db/migration/tenant/V85__create_trust_accounting_tables.sql` -- Migration syntax and pattern reference
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustAccountStatus.java` -- Enum pattern reference
- Architecture doc Section 61.7 -- Full V86 SQL

### Architecture Decisions

- **Single V86 migration for both tracks**: Both the investment basis column (Track 1) and the checklist verification columns (Track 2) go into one migration. This avoids ordering issues and ensures all dependent slices can start from a single foundation point. The migration is purely additive (ALTER TABLE ADD COLUMN) and backward-compatible.
- **Default value for investment_basis**: `'FIRM_DISCRETION'` is the safe default. Any investments created during Phase 60 before this migration are treated as firm-initiated, which uses the general LPFF arrangement rate -- the behavior Phase 60 already implements.
- **Constant over configuration (ADR-235)**: The 5% statutory rate is a `BigDecimal` constant, not stored in the `LpffRate` table or `application.yml`. The rate is prescribed by Section 86(5) of the Legal Practice Act and has not changed since 2014. Making it editable creates a surface for silent non-compliance.
- **Foundation before logic**: The migration and enums are in their own epic to ensure they are merged before any dependent slice starts. All other epics (453-458) depend on E452A.

---

## Epic 453: Interest Calculation Basis Distinction + Audit Trail

**Goal**: Implement the core compliance logic that distinguishes Section 86(3) firm-initiated investments from Section 86(4) client-instructed investments in interest calculations. Extend the `TrustInvestment` entity with the `investmentBasis` field, modify the `InterestCalculationService` to apply the statutory 5% LPFF share for client-instructed investments, and extend `InterestAllocation` with audit trail fields that record which rate source was used.

**References**: Architecture doc Sections 61.2.1, 61.2.4, 61.3.1, 61.4.1; ADR-235 (statutory constant rationale).

**Dependencies**: Epic 452 (V86 migration, InvestmentBasis enum, TrustAccountingConstants).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **453A** | 453.1--453.7 | Extend `TrustInvestment` entity with `investmentBasis` field. Extend `TrustInvestmentService` to accept/return `investmentBasis` in create flow. Extend `InterestCalculationService` with conditional LPFF share logic (statutory 5% for CLIENT_INSTRUCTION, general rate for FIRM_DISCRETION). Extend `InterestAllocation` entity with `lpffRateId` (nullable UUID) + `statutoryRateApplied` (boolean). Integration tests (~12). Backend only. | **Done** (PR #958) |
| **453B** | 453.8--453.12 | Modify investment controller endpoints to accept `investmentBasis` in create request, include it in responses, and support `?investmentBasis=` filter parameter on list endpoint. Controller integration tests (~6). Backend only. | **Done** (PR #958) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 453.1 | Extend `TrustInvestment` entity with `investmentBasis` field | 453A | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/` -- find TrustInvestment entity (created by Phase 60 epic 446). Add `@Enumerated(EnumType.STRING) @Column(name = "investment_basis", nullable = false) private InvestmentBasis investmentBasis;` with default `InvestmentBasis.FIRM_DISCRETION`. Add to constructor. Pattern: how `TrustAccount` has string-typed enum fields. |
| 453.2 | Extend `TrustInvestmentService` to handle `investmentBasis` | 453A | 453.1 | Modify: `TrustInvestmentService` (created by Phase 60 epic 446). Accept `InvestmentBasis` in the create/place investment flow. Include `investmentBasis` in the response DTO. Validate that `investmentBasis` is not null on create. |
| 453.3 | Extend `InterestAllocation` entity with audit trail fields | 453A | -- | Modify: `InterestAllocation` entity (created by Phase 60 epic 445). Add `@Column(name = "lpff_rate_id") private UUID lpffRateId;` (nullable). Add `@Column(name = "statutory_rate_applied", nullable = false) private boolean statutoryRateApplied = false;`. Add to constructor/builder as appropriate. |
| 453.4 | Implement conditional LPFF share logic in `InterestCalculationService` | 453A | 453.1, 453.3 | Modify: `InterestCalculationService` (created by Phase 60 epic 445). In the interest calculation loop for investments: check `investment.getInvestmentBasis()`. If `CLIENT_INSTRUCTION`: use `TrustAccountingConstants.STATUTORY_LPFF_SHARE_PERCENT`, set `statutoryRateApplied = true`, set `lpffRateId = null`. If `FIRM_DISCRETION`: use the existing `LpffRate` table lookup, set `statutoryRateApplied = false`, set `lpffRateId = effectiveRate.getId()`. See architecture doc Section 61.3.1 pseudocode. |
| 453.5 | Write integration tests for statutory rate enforcement | 453A | 453.4 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/InvestmentBasisInterestTest.java`. 6 tests: (1) CLIENT_INSTRUCTION investment uses exactly 5% LPFF share, (2) FIRM_DISCRETION investment uses LpffRate table rate, (3) mixed-basis interest run correctly splits allocations, (4) CLIENT_INSTRUCTION allocation has `statutoryRateApplied = true` and `lpffRateId = null`, (5) FIRM_DISCRETION allocation has `statutoryRateApplied = false` and `lpffRateId` populated, (6) backward compatibility -- investments with default FIRM_DISCRETION use general rate. |
| 453.6 | Write integration tests for TrustInvestment basis field | 453A | 453.2 | Extend or create: investment service tests. 4 tests: (1) create investment with CLIENT_INSTRUCTION persists correctly, (2) create investment with FIRM_DISCRETION persists correctly, (3) create investment without basis defaults to FIRM_DISCRETION, (4) get investment returns investmentBasis in response. |
| 453.7 | Write interest allocation audit trail tests | 453A | 453.4 | 2 tests: (1) after interest run with mixed investments, query all allocations and verify each has correct rate source, (2) verify an auditor can filter allocations by `statutory_rate_applied = true` and all use exactly 5%. |
| 453.8 | Modify investment create endpoint for `investmentBasis` | 453B | 453A | Modify: `TrustInvestmentController` (created by Phase 60 epic 446). The `POST /api/trust-accounts/{accountId}/investments` request DTO adds `investmentBasis` (required, enum: `FIRM_DISCRETION` or `CLIENT_INSTRUCTION`). Thin controller -- delegates to service. |
| 453.9 | Modify investment response DTOs to include `investmentBasis` | 453B | 453A | Modify: investment response records in controller or DTO package. Add `investmentBasis` field to `TrustInvestmentResponse` (or equivalent). Ensure `GET /api/trust-investments/{id}` and list endpoint responses include the field. |
| 453.10 | Add filter parameter to list investments endpoint | 453B | 453A | Modify: `TrustInvestmentController` list endpoint. Add optional `@RequestParam InvestmentBasis investmentBasis` filter. Modify `TrustInvestmentRepository` or service to support filtering by `investmentBasis`. `GET /api/trust-accounts/{accountId}/investments?investmentBasis=CLIENT_INSTRUCTION`. |
| 453.11 | Write controller integration tests -- basis CRUD | 453B | 453.8, 453.9 | New file or extend: `TrustInvestmentControllerTest`. 4 tests: (1) POST with CLIENT_INSTRUCTION returns 201 with basis in response, (2) POST with FIRM_DISCRETION returns 201, (3) GET by id returns investmentBasis, (4) GET list filtered by CLIENT_INSTRUCTION returns only matching investments. |
| 453.12 | Write controller integration test -- basis validation | 453B | 453.8 | 2 tests: (1) POST without investmentBasis returns 400 (required field), (2) POST with invalid basis value returns 400. |

### Key Files

**Slice 453A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/` -- `TrustInvestment` entity (add investmentBasis field) -- exact file path depends on Phase 60 epic 446 structure
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/` -- `TrustInvestmentService` (accept/return investmentBasis)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/` -- `InterestCalculationService` (conditional LPFF share logic) -- created by Phase 60 epic 445
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/` -- `InterestAllocation` entity (add lpffRateId + statutoryRateApplied)

**Slice 453A -- Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/InvestmentBasisInterestTest.java`

**Slice 453B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/` -- `TrustInvestmentController` (add investmentBasis to request/response, add filter)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/` -- `TrustInvestmentRepository` (add findByInvestmentBasis query)

**Slice 453B -- Create:**
- Controller integration tests for investment basis endpoints

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/lpff/LpffRate.java` -- Rate entity structure
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/lpff/LpffRateRepository.java` -- Effective rate query pattern
- Architecture doc Section 61.3.1 -- Interest calculation pseudocode with basis distinction

### Architecture Decisions

- **Conditional logic in service, not database**: The 86(3)/86(4) distinction is implemented as a conditional branch in `InterestCalculationService`, not as separate tables or rate types. This follows ADR-235: the statutory rate is a code constant, not a database record.
- **InterestAllocation dual audit fields**: Each allocation records both the `lpffRateId` (nullable FK to LpffRate for arrangement rates) and a `statutoryRateApplied` boolean. This is intentionally redundant -- an auditor can verify 86(4) compliance by querying either field. The FK is null for statutory allocations to explicitly denote "this rate did not come from the LpffRate table."
- **investmentBasis is required on create**: Unlike the V86 migration default (FIRM_DISCRETION for backward compatibility with existing rows), the API requires explicit selection. This forces the user to make a conscious choice about the investment type.
- **Two slices for backend**: Service logic (453A) is separated from controller modifications (453B) to keep each slice under the 10-file limit. The service slice is the larger and more critical one.

---

## Epic 454: Investment Register Report + Section 35 Data Pack Updates

**Goal**: Extend the investment register report to display the investment basis column and the applicable LPFF rate per investment. Update the Section 35 data pack generation to separately list 86(3) and 86(4) investments with their respective rates, as required for trust audit compliance.

**References**: Architecture doc Sections 61.6.2 (investment register), Slice 61B description; Phase 60 epic 447 (trust reports).

**Dependencies**: Epic 453 (investment basis field must exist on entity and be populated in responses).

**Scope**: Backend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **454A** | 454.1--454.5 | Extend investment register report data provider to include `investmentBasis` and applicable LPFF rate per investment. Update Section 35 data pack to separate 86(3) and 86(4) investments with respective rates. Integration tests (~8). Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 454.1 | Extend investment register report data provider | 454A | -- | Modify: trust report data provider for INVESTMENT_REGISTER (created by Phase 60 epic 447). Add `investmentBasis` field to report row data. Add `applicableLpffRate` field: for FIRM_DISCRETION show the general arrangement rate percentage, for CLIENT_INSTRUCTION show "5% (statutory)". Pattern: existing report data providers in the report package. |
| 454.2 | Update Section 35 data pack generator | 454A | 454.1 | Modify: SECTION_35_DATA_PACK report data provider (created by Phase 60 epic 447). Split investments into two sections: "Section 86(3) Investments (Firm Discretion)" with general LPFF rate, and "Section 86(4) Investments (Client Instruction)" with statutory 5% rate. Add per-section subtotals (principal, interest earned, LPFF share). |
| 454.3 | Add investment basis to interest allocation report | 454A | -- | Modify: INTEREST_ALLOCATION report data provider (created by Phase 60 epic 447). For each allocation row, show whether it used the general arrangement rate or the statutory rate. Display "5% (statutory)" for allocations where `statutory_rate_applied = true`. |
| 454.4 | Write integration tests for investment register report | 454A | 454.1 | 4 tests: (1) report with only FIRM_DISCRETION investments shows general rate, (2) report with only CLIENT_INSTRUCTION investments shows "5% (statutory)", (3) report with mixed basis types shows correct rates per row, (4) report filter by investment basis returns subset. |
| 454.5 | Write integration tests for Section 35 data pack | 454A | 454.2 | 4 tests: (1) data pack separates 86(3) and 86(4) investments, (2) 86(3) section shows general rate, (3) 86(4) section shows statutory rate, (4) data pack with no 86(4) investments omits that section. |

### Key Files

**Slice 454A -- Modify:**
- Trust report data providers for INVESTMENT_REGISTER, SECTION_35_DATA_PACK, INTEREST_ALLOCATION (created by Phase 60 epic 447, location TBD based on Phase 60 implementation)

**Slice 454A -- Create:**
- Integration test file for report basis extensions

**Read for context:**
- Phase 60 epic 447 report data providers -- exact file names depend on Phase 60 implementation
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/report/` -- Report framework and data provider pattern
- Architecture doc Section 61.6.2 -- Investment register display requirements

### Architecture Decisions

- **Single slice for reports**: Reports and data packs are read-only extensions that add columns and sections. The total file count is small (3 data providers modified + 1 test file created), fitting within one slice.
- **Section 35 separation by investment basis**: The Legal Practice Act expects an auditor to see 86(3) and 86(4) investments reported separately. The data pack generator groups by `investmentBasis` and formats each section with the applicable rate. If a trust account has no 86(4) investments, that section is omitted entirely.
- **"5% (statutory)" display text**: Report output uses the display text "5% (statutory)" rather than just "5%" to make the rate source explicit. This is the same treatment the frontend will use (epic 455).

---

## Epic 455: Frontend -- Investment Form + Register + Interest Table Updates

**Goal**: Update the frontend investment UI to support the investment basis distinction. Add an investment basis radio group to the Place Investment dialog, add a basis column and filter to the investment register table, show "5% (statutory)" in interest allocation tables for 86(4) investments, and add the Section 86(6) advisory note about LPFF-approved banks.

**References**: Architecture doc Sections 61.6.1 (Place Investment dialog), 61.6.2 (investment register), 61.6.3 (interest allocation table), 61.6.4 (86(6) advisory note).

**Dependencies**: Epic 453 (API returns investmentBasis field in responses, accepts it in create request, supports filter parameter).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **455A** | 455.1--455.7 | Place Investment dialog: investment basis radio group with help text. 86(6) advisory note on dialog and trust account form. Investment register table: basis column (badge) + filter dropdown. Interest allocation table: "5% (statutory)" for 86(4) allocations + footnote. TypeScript types + Zod schema updates. Frontend tests (~8). Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 455.1 | Update TypeScript types for investment basis | 455A | -- | Modify: trust accounting types file (created by Phase 60 epic 450). Add `investmentBasis: 'FIRM_DISCRETION' \| 'CLIENT_INSTRUCTION'` to `TrustInvestment` type. Add `investmentBasis` to create investment request type. Add to Zod schema in `lib/schemas/` -- investment basis is required on create, validated as enum. |
| 455.2 | Add investment basis radio group to Place Investment dialog | 455A | 455.1 | Modify: Place Investment dialog component (created by Phase 60 epic 450). Add radio button group: label "Investment initiated by", options "Firm (surplus trust funds)" mapping to `FIRM_DISCRETION` and "Client instruction" mapping to `CLIENT_INSTRUCTION`. Default: `FIRM_DISCRETION`. Dynamic help text that changes with selection (FIRM_DISCRETION: "Interest follows your firm's LPFF arrangement rate." / CLIENT_INSTRUCTION: "Interest paid to client, with 5% to the LPFF (Section 86(5))."). Use Shadcn RadioGroup component. |
| 455.3 | Add Section 86(6) advisory note | 455A | -- | Modify: Place Investment dialog + trust account creation/edit form (created by Phase 60). Add an informational callout/alert below bank details: "The bank must have an arrangement with the Legal Practitioners Fidelity Fund (Section 86(6)). Contact the LPFF to verify." Use Shadcn Alert component with info variant. Purely informational -- no validation logic. |
| 455.4 | Add investment basis column to register table | 455A | 455.1 | Modify: investment register table component (created by Phase 60 epic 450). Add "Investment Basis" column showing "Firm" (muted badge) or "Client Instruction" (primary badge). Add "LPFF Rate" column: for FIRM_DISCRETION show the general rate (e.g., "8.5%"), for CLIENT_INSTRUCTION show "5% (statutory)" styled distinctively. Use Shadcn Badge component. |
| 455.5 | Add investment basis filter to register table | 455A | 455.4 | Modify: investment register page (created by Phase 60 epic 450). Add dropdown filter for investment basis: options "All", "Firm Discretion", "Client Instruction". Passes `?investmentBasis=` query parameter to the API. Follow existing filter patterns on the page. |
| 455.6 | Update interest allocation table for statutory rate display | 455A | -- | Modify: interest allocation detail component (created by Phase 60 epic 450). For allocations where `statutory_rate_applied = true` (or equivalent response field), display "5% (statutory)" in the LPFF rate column instead of the general rate percentage. Add a footnote: "Section 86(5): client-instructed investments carry a statutory 5% LPFF share." |
| 455.7 | Write frontend tests | 455A | 455.2, 455.4, 455.6 | Create or extend tests for investment components. 8 tests: (1) Place Investment dialog renders radio group with correct options, (2) selecting CLIENT_INSTRUCTION shows statutory help text, (3) 86(6) advisory note renders, (4) register table shows basis column, (5) register table shows "5% (statutory)" for CLIENT_INSTRUCTION investments, (6) filter dropdown renders with options, (7) interest allocation shows "5% (statutory)" for 86(4) allocations, (8) interest allocation shows general rate for 86(3) allocations. |

### Key Files

**Slice 455A -- Modify:**
- `frontend/app/(app)/org/[slug]/trust-accounting/` -- investment-related page and components (created by Phase 60 epic 450)
- `frontend/lib/schemas/` -- trust investment Zod schema
- `frontend/components/` -- trust accounting components directory (investment dialog, register table, interest allocation table)

**Slice 455A -- Read for context:**
- `frontend/components/ui/radio-group.tsx` -- Shadcn RadioGroup component
- `frontend/components/ui/badge.tsx` -- Badge variants for basis display
- `frontend/components/ui/alert.tsx` -- Alert component for advisory note
- Architecture doc Sections 61.6.1--61.6.4 -- UI specifications

### Architecture Decisions

- **Single frontend slice**: All investment UI changes are closely related and share the same component tree. The Place Investment dialog, register table, and interest table are all on the same or adjacent pages. Splitting further would create artificial cross-slice dependencies.
- **investmentBasis as required field in dialog**: The radio group defaults to FIRM_DISCRETION but requires explicit selection. This mirrors the backend validation (epic 453B) and forces conscious choice.
- **Advisory note is informational only**: The Section 86(6) bank arrangement note has no validation logic. The system cannot verify a bank's LPFF arrangement status. The note serves as a compliance reminder for the firm admin.

---

## Epic 456: KYC Adapter Infrastructure -- Port, Adapters, Service

**Goal**: Build the KYC verification integration infrastructure following the BYOAK pattern from Phase 21. Create the `KycVerificationPort` interface, three adapters (VerifyNow, Check ID SA, NoOp), supporting records and enums, extend `IntegrationDomain`, and build the `KycVerificationService` orchestrator that resolves the adapter, calls verify, updates the checklist item, and records audit events.

**References**: Architecture doc Sections 61.2.5--61.2.8 (KYC domain model), 61.3.2 (KYC verification flow); ADR-236 (BYOAK strategy, Check ID SA returns NEEDS_REVIEW).

**Dependencies**: Epic 452 (V86 migration for checklist verification columns).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **456A** | 456.1--456.7 | `KycVerificationPort` interface + `KycVerificationRequest` record + `KycVerificationResult` record + `KycVerificationStatus` enum + `VerifyNowKycAdapter` + `CheckIdKycAdapter` + `NoOpKycAdapter`. Add `KYC_VERIFICATION("noop")` to `IntegrationDomain` enum. Unit tests (~8). Backend only. | |
| **456B** | 456.8--456.14 | `KycVerificationService` orchestrator (resolve adapter via `IntegrationRegistry`, call verify, update `ChecklistInstanceItem` verification columns, record POPIA consent in metadata JSONB, emit audit events). Extend `ChecklistInstanceItem` entity with 5 verification columns. Integration tests (~10). Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 456.1 | Create `KycVerificationStatus` enum | 456A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/kyc/KycVerificationStatus.java`. Values: `VERIFIED`, `NOT_VERIFIED`, `NEEDS_REVIEW`, `ERROR`. Pattern: `IntegrationDomain.java` enum style. |
| 456.2 | Create `KycVerificationRequest` record | 456A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/kyc/KycVerificationRequest.java`. Fields: `String idNumber`, `String fullName`, `String dateOfBirth` (optional), `String idDocumentType` ("SA_ID", "SMART_ID", "PASSPORT"). Java record. Pattern: existing request records in `integration/payment/` or `integration/email/`. |
| 456.3 | Create `KycVerificationResult` record | 456A | 456.1 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/kyc/KycVerificationResult.java`. Fields: `KycVerificationStatus status`, `String providerName`, `String providerReference`, `String reasonCode`, `String reasonDescription`, `Instant verifiedAt`, `Map<String, String> metadata`. Java record. |
| 456.4 | Create `KycVerificationPort` interface | 456A | 456.2, 456.3 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/kyc/KycVerificationPort.java`. Single method: `KycVerificationResult verify(KycVerificationRequest request)`. Pattern: existing port interfaces in `integration/payment/` (e.g., PaymentGateway or equivalent). |
| 456.5 | Create `VerifyNowKycAdapter` | 456A | 456.4 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/kyc/VerifyNowKycAdapter.java`. `@Component` + `@IntegrationAdapter(domain = IntegrationDomain.KYC_VERIFICATION, slug = "verifynow")`. Implements `KycVerificationPort`. Calls VerifyNow REST API (`POST /verifications`, poll `GET /verifications/{id}`). Uses `SecretStore` for API key retrieval. Maps VerifyNow response to `KycVerificationResult`. Returns `VERIFIED`, `NOT_VERIFIED`, or `NEEDS_REVIEW`. Error handling: catch exceptions, return `KycVerificationResult(ERROR, ...)`. Pattern: existing adapters in `integration/payment/` or `integration/email/`. |
| 456.6 | Create `CheckIdKycAdapter` + `NoOpKycAdapter` | 456A | 456.4 | New files: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/kyc/CheckIdKycAdapter.java` and `NoOpKycAdapter.java`. CheckIdKycAdapter: `@IntegrationAdapter(domain = KYC_VERIFICATION, slug = "checkid")`. Calls Check ID SA REST API for format validation. **Always returns NEEDS_REVIEW** (per ADR-236 -- format validation is not identity verification). Maps birth date, citizenship to metadata. NoOpKycAdapter: `@IntegrationAdapter(domain = KYC_VERIFICATION, slug = "noop")`. Returns `ERROR` with `reasonDescription = "No KYC provider configured"`. Defensive fallback only. |
| 456.7 | Add `KYC_VERIFICATION` to IntegrationDomain + write unit tests | 456A | 456.5, 456.6 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationDomain.java`. Add `KYC_VERIFICATION("noop")` enum value. Write unit tests: (1) IntegrationDomain.KYC_VERIFICATION exists with default slug "noop", (2) VerifyNowKycAdapter maps success response to VERIFIED, (3) VerifyNowKycAdapter maps failure to NOT_VERIFIED, (4) CheckIdKycAdapter always returns NEEDS_REVIEW, (5) NoOpKycAdapter returns ERROR, (6-8) adapter annotation verification tests. 8 tests total, mix of unit + mocked integration. |
| 456.8 | Extend `ChecklistInstanceItem` entity with verification columns | 456B | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistInstanceItem.java`. Add 5 nullable fields: `@Column(name = "verification_provider") private String verificationProvider;`, `@Column(name = "verification_reference") private String verificationReference;`, `@Column(name = "verification_status") private String verificationStatus;`, `@Column(name = "verified_at") private Instant verifiedAt;`, `@JdbcTypeCode(SqlTypes.JSON) @Column(name = "verification_metadata", columnDefinition = "jsonb") private Map<String, Object> verificationMetadata;`. All nullable. Add setters. Pattern: existing JSONB fields in codebase. |
| 456.9 | Create `KycVerificationService` -- orchestrator | 456B | 456A, 456.8 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/kyc/KycVerificationService.java`. Spring `@Service`. Constructor-injected: `IntegrationRegistry`, `ChecklistInstanceItemRepository`, `AuditEventService`. Method `verifyIdentity(UUID customerId, UUID checklistInstanceItemId, KycVerificationRequest request, boolean consentAcknowledged, UUID actorMemberId)`: (1) Validate `consentAcknowledged == true` or throw `InvalidStateException`, (2) Resolve adapter via `IntegrationRegistry.resolve(KYC_VERIFICATION, KycVerificationPort.class)`, (3) Call `adapter.verify(request)`, (4) Update `ChecklistInstanceItem` verification columns based on result status, (5) If VERIFIED: also set checklist item status to COMPLETED + completedAt + completedBy, (6) Record POPIA consent in `verification_metadata` JSONB: `{"consent_acknowledged_at": "<ISO>", "consent_acknowledged_by": "<memberId>"}`, (7) Emit audit event (KYC_VERIFICATION_INITIATED, KYC_VERIFICATION_COMPLETED, or KYC_VERIFICATION_FAILED), (8) Return result. |
| 456.10 | Create `KycIntegrationStatusResponse` record | 456B | -- | New file or nested record: `KycIntegrationStatusResponse(boolean configured, String provider)`. Used by the status endpoint. Service method: `getKycIntegrationStatus()` -- calls `IntegrationGuardService.isConfigured("KYC_VERIFICATION")` and gets active provider slug. |
| 456.11 | Write integration tests -- service happy path | 456B | 456.9 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/kyc/KycVerificationServiceTest.java`. 5 tests: (1) VERIFIED result updates checklist item to COMPLETED with verification columns populated, (2) NOT_VERIFIED result updates verification columns but item stays PENDING, (3) NEEDS_REVIEW result updates verification columns but item stays PENDING, (4) ERROR result does not update checklist item, (5) POPIA consent metadata recorded with correct actor and timestamp. |
| 456.12 | Write integration tests -- consent and error handling | 456B | 456.9 | 3 tests: (1) `consentAcknowledged = false` throws InvalidStateException, (2) adapter throws exception -- service returns ERROR result and item unchanged, (3) checklist item not found -- throws ResourceNotFoundException. |
| 456.13 | Write integration test -- integration status | 456B | 456.10 | 2 tests: (1) KYC configured -- returns `{ configured: true, provider: "verifynow" }`, (2) KYC not configured -- returns `{ configured: false, provider: null }`. |
| 456.14 | Write integration test -- audit events | 456B | 456.9 | 2 additional tests confirming audit events emitted: (1) successful verification emits KYC_VERIFICATION_COMPLETED, (2) failed provider call emits KYC_VERIFICATION_FAILED. |

### Key Files

**Slice 456A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/kyc/KycVerificationPort.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/kyc/KycVerificationRequest.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/kyc/KycVerificationResult.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/kyc/KycVerificationStatus.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/kyc/VerifyNowKycAdapter.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/kyc/CheckIdKycAdapter.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/kyc/NoOpKycAdapter.java`

**Slice 456A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationDomain.java` -- Add KYC_VERIFICATION enum value

**Slice 456B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/kyc/KycVerificationService.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/kyc/KycVerificationServiceTest.java`

**Slice 456B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistInstanceItem.java` -- Add 5 verification columns

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationAdapter.java` -- Adapter annotation pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationRegistry.java` -- Registry resolution pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationGuardService.java` -- isConfigured pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/secret/` -- SecretStore for API key encryption
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/payment/` -- Existing BYOAK adapter pattern reference
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistInstanceItem.java` -- Entity to extend

### Architecture Decisions

- **Two slices: adapters then service**: 456A creates the port, records, and all three adapters (7 new files + 1 modified). 456B creates the orchestrator service, extends the entity, and writes integration tests (3 new files + 1 modified). Each stays under 10 files.
- **Check ID SA always returns NEEDS_REVIEW (ADR-236)**: Format validation confirms a structurally valid ID number but does not verify the person's identity against Home Affairs. Returning VERIFIED would create a false sense of compliance. The adapter records the pre-check result but the firm must still verify manually.
- **NoOp adapter as defensive fallback**: The `NoOpKycAdapter` returns ERROR and should never be reached from the UI (the "Verify Now" button is hidden when no integration is configured). It exists for the `IntegrationRegistry` resolution pattern to always return a non-null adapter.
- **POPIA consent in JSONB**: The consent acknowledgement is recorded in `verification_metadata` JSONB, not as a separate column. The system records that the firm user confirmed consent was obtained -- not the consent itself. The actual written consent from the client is the firm's responsibility.
- **Checklist item auto-completion on VERIFIED only**: When the provider returns VERIFIED, the checklist item is automatically completed (status = COMPLETED). For NOT_VERIFIED and NEEDS_REVIEW, the item remains PENDING. This ensures that only definitive verification auto-completes the compliance requirement.

---

## Epic 457: KYC Controller + Integration Status Endpoint

**Goal**: Create the `KycVerificationController` with three endpoints: trigger verification, poll result, and check integration status. These are the HTTP surface that the frontend will call. The controller follows thin-controller discipline -- pure delegation to `KycVerificationService`.

**References**: Architecture doc Section 61.4.2 (KYC endpoints).

**Dependencies**: Epic 456 (KycVerificationService, KycVerificationPort, all supporting types).

**Scope**: Backend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **457A** | 457.1--457.5 | `KycVerificationController` with 3 endpoints: `POST /api/kyc/verify`, `GET /api/kyc/result/{ref}`, `GET /api/integrations/kyc/status`. Request/response DTOs. `@RequiresCapability` on all endpoints. Controller integration tests (~6). Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 457.1 | Create `KycVerificationController` | 457A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/kyc/KycVerificationController.java`. `@RestController` + `@RequestMapping("/api")`. Three endpoints: (1) `POST /api/kyc/verify` with `@RequiresCapability("MANAGE_LEGAL")` -- accepts `KycVerifyRequest` body, delegates to `KycVerificationService.verifyIdentity()`, returns `KycVerifyResponse`, (2) `GET /api/kyc/result/{reference}` with `@RequiresCapability("MANAGE_LEGAL")` -- delegates to `KycVerificationService.getResult()`, (3) `GET /api/integrations/kyc/status` with `@RequiresCapability("VIEW_TRUST")` -- delegates to `KycVerificationService.getKycIntegrationStatus()`. **Thin controller discipline**: every method is a one-liner. Pattern: `IntegrationController.java`. |
| 457.2 | Define request/response DTO records | 457A | 457.1 | New records (nested in controller or separate file): `KycVerifyRequest(UUID customerId, UUID checklistInstanceItemId, String idNumber, String fullName, String idDocumentType, boolean consentAcknowledged)`, `KycVerifyResponse(String status, String providerName, String providerReference, String reasonCode, String reasonDescription, Instant verifiedAt, boolean checklistItemUpdated)`. Pattern: existing controller DTO records. |
| 457.3 | Write controller integration tests -- happy path | 457A | 457.1 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/kyc/KycVerificationControllerTest.java`. 3 tests: (1) POST `/api/kyc/verify` with valid request returns 200 with VERIFIED status (mocked adapter), (2) GET `/api/kyc/result/{ref}` returns result, (3) GET `/api/integrations/kyc/status` returns configured status. |
| 457.4 | Write controller integration tests -- validation and errors | 457A | 457.1 | 2 tests: (1) POST `/api/kyc/verify` with `consentAcknowledged = false` returns 400, (2) POST `/api/kyc/verify` without required fields returns 400. |
| 457.5 | Write controller authorization test | 457A | 457.1 | 1 test: POST `/api/kyc/verify` with member lacking MANAGE_LEGAL returns 403. |

### Key Files

**Slice 457A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/kyc/KycVerificationController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/integration/kyc/KycVerificationControllerTest.java`

**Slice 457A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationController.java` -- Controller pattern for integration endpoints
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/kyc/KycVerificationService.java` -- Service to delegate to
- Backend CLAUDE.md -- Thin controller discipline rules

### Architecture Decisions

- **Separate controller epic**: The controller is split from the service (epic 456) to keep each slice under the file limit and to allow the service to be fully tested before the HTTP layer is added. This follows the Phase 60 pattern where controllers were in separate epics from services.
- **MANAGE_LEGAL for verify, VIEW_TRUST for status**: KYC verification modifies checklist items (MANAGE_LEGAL). The status check is read-only and used by the frontend to conditionally show the "Verify Now" button -- a wider audience (VIEW_TRUST) can check if KYC is configured.
- **consentAcknowledged validated at both layers**: The DTO requires `consentAcknowledged`, the controller passes it through, and the service validates it must be `true`. Belt-and-suspenders approach for a POPIA compliance requirement.

---

## Epic 458: Frontend -- KYC Verification Dialog + Checklist Integration + Settings Card

**Goal**: Build the frontend KYC verification experience. Add a conditional "Verify Now" button to FICA checklist items, create the KYC verification dialog with POPIA consent flow, and add a KYC integration settings card in the integrations page.

**References**: Architecture doc Sections 61.6.5 (Verify Now button), 61.6.6 (verification dialog), 61.6.7 (settings card).

**Dependencies**: Epic 457 (KYC endpoints available).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **458A** | 458.1--458.7 | Conditional "Verify Now" button on FICA checklist items. KYC verification dialog with POPIA consent checkbox, provider display, pre-filled fields, result display per status (VERIFIED/NOT_VERIFIED/NEEDS_REVIEW/ERROR). Previous verification result badge. TypeScript types, Zod schemas, server actions. Frontend tests (~6). Frontend only. | |
| **458B** | 458.8--458.12 | KYC integration settings card in Settings -> Integrations. Configure dialog (provider selector, API key input, test connection button). Remove integration action. Frontend tests (~4). Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 458.1 | Create KYC TypeScript types and Zod schemas | 458A | -- | New or extend types file: `KycVerifyRequest`, `KycVerifyResponse`, `KycIntegrationStatus` TypeScript interfaces. Zod schema for verify request validation (idNumber required, fullName required, idDocumentType enum, consentAcknowledged must be true). Place in `lib/schemas/kyc.ts` or similar. Pattern: existing schemas in `lib/schemas/`. |
| 458.2 | Create KYC server actions | 458A | 458.1 | New file: `frontend/app/(app)/org/[slug]/customers/[id]/kyc-actions.ts` (or similar location near checklist). Server actions: `verifyKycAction(formData)` -- calls `POST /api/kyc/verify` via `lib/api.ts`, `getKycStatusAction()` -- calls `GET /api/integrations/kyc/status`, `getKycResultAction(reference)` -- calls `GET /api/kyc/result/{ref}`. Pattern: existing server actions (e.g., `checklist-actions.ts`). |
| 458.3 | Add "Verify Now" button to checklist item row | 458A | 458.2 | Modify: `frontend/components/compliance/ChecklistInstanceItemRow.tsx`. Conditionally render "Verify Now" button when: (a) KYC integration is configured (check via `getKycStatusAction()` or prop passed from parent), (b) checklist item is of type "identity_verification" (or equivalent identifier), (c) item status is PENDING or not yet COMPLETED. Show alongside existing "Mark Complete" option. Show previous verification result as a badge if verification columns populated (Verified/Not Verified/Needs Review with provider name and timestamp). |
| 458.4 | Create KYC verification dialog component | 458A | 458.1, 458.2 | New file: `frontend/components/compliance/KycVerificationDialog.tsx`. `"use client"` component. Pre-fills ID number (from customer's `id_passport_number` custom field) and full name. ID document type dropdown (SA ID Card, Smart ID Card, Passport). Provider display text (e.g., "VerifyNow -- verified against Home Affairs"). POPIA consent checkbox with notice text: "By proceeding, you confirm that [Client Name] has given explicit written consent for identity verification against government databases, as required by POPIA and FICA." "Verify" button disabled until consent checked. On submit: calls `verifyKycAction()`. Result display replaces form: VERIFIED (green banner + reference + timestamp), NOT_VERIFIED (red banner + reason + retry option), NEEDS_REVIEW (amber banner + explanation), ERROR (grey banner + retry suggestion). Uses Shadcn Dialog, Checkbox, Select, Alert. Pattern: existing dialog components in `components/compliance/`. |
| 458.5 | Implement result display and checklist item refresh | 458A | 458.4 | Extend dialog: after verification result, trigger re-fetch of checklist instance to reflect updated item status (COMPLETED for VERIFIED, unchanged for others). Use SWR `mutate()` or `router.refresh()`. Display verification result badge on the checklist item row after dialog closes. |
| 458.6 | Write frontend tests -- button visibility and dialog | 458A | 458.3, 458.4 | 4 tests: (1) "Verify Now" button visible when KYC configured and item is PENDING identity verification, (2) "Verify Now" button hidden when KYC not configured, (3) "Verify Now" button hidden when item already COMPLETED, (4) dialog shows POPIA consent notice and "Verify" disabled until consent checked. |
| 458.7 | Write frontend tests -- result display | 458A | 458.4 | 2 tests: (1) VERIFIED result shows green banner and checklist item updated, (2) NOT_VERIFIED result shows red banner with reason. |
| 458.8 | Create KYC integration settings card | 458B | -- | Modify: `frontend/app/(app)/org/[slug]/settings/integrations/page.tsx`. Add a new integration card for "KYC Verification". Status badge: "Configured" (green) or "Not Configured" (muted). When configured: show provider name. "Configure" button opens dialog. "Remove" button clears integration. Pattern: existing integration cards on the page (follow the exact card structure used for payment/email integrations). |
| 458.9 | Create KYC configuration dialog | 458B | 458.8 | New file: `frontend/components/settings/KycConfigurationDialog.tsx`. `"use client"` component. Provider selector dropdown (VerifyNow / Check ID SA). API key input (password type). "Test Connection" button -- calls backend integration test endpoint. Save button -- stores integration via existing integration actions. Cancel button. Pattern: existing integration configuration dialogs in settings. |
| 458.10 | Create KYC settings server actions | 458B | -- | New or extend: `frontend/app/(app)/org/[slug]/settings/integrations/actions.ts`. Actions: `configureKycIntegration(provider, apiKey)`, `removeKycIntegration()`, `testKycConnection(provider, apiKey)`. Follow existing integration action patterns. |
| 458.11 | Write frontend tests -- settings card | 458B | 458.8, 458.9 | 3 tests: (1) KYC card renders with "Not Configured" when no integration, (2) KYC card shows provider name when configured, (3) configuration dialog renders with provider selector and API key field. |
| 458.12 | Write frontend test -- remove integration | 458B | 458.8 | 1 test: clicking "Remove" calls remove action and card updates to "Not Configured". |

### Key Files

**Slice 458A -- Create:**
- `frontend/lib/schemas/kyc.ts` -- Zod schemas for KYC verification
- `frontend/app/(app)/org/[slug]/customers/[id]/kyc-actions.ts` -- KYC server actions
- `frontend/components/compliance/KycVerificationDialog.tsx` -- Verification dialog

**Slice 458A -- Modify:**
- `frontend/components/compliance/ChecklistInstanceItemRow.tsx` -- Add "Verify Now" button and verification result badge

**Slice 458B -- Create:**
- `frontend/components/settings/KycConfigurationDialog.tsx` -- Integration configuration dialog

**Slice 458B -- Modify:**
- `frontend/app/(app)/org/[slug]/settings/integrations/page.tsx` -- Add KYC integration card
- `frontend/app/(app)/org/[slug]/settings/integrations/actions.ts` -- Add KYC integration actions

**Read for context:**
- `frontend/components/compliance/ChecklistInstanceItemRow.tsx` -- Existing checklist item row pattern
- `frontend/components/compliance/ChecklistInstancePanel.tsx` -- Checklist panel container
- `frontend/app/(app)/org/[slug]/customers/[id]/checklist-actions.ts` -- Existing checklist server actions
- `frontend/app/(app)/org/[slug]/settings/integrations/page.tsx` -- Existing integration cards pattern
- `frontend/app/(app)/org/[slug]/settings/integrations/actions.ts` -- Existing integration actions pattern
- Frontend CLAUDE.md -- RSC serialization boundary rules, Shadcn conventions, SWR patterns

### Architecture Decisions

- **Two frontend slices: checklist UI then settings**: 458A handles the verification flow (checklist button, dialog, result display -- user-facing compliance workflow). 458B handles the admin setup flow (settings card, configuration dialog). These are on different pages and target different user roles (practitioner vs admin).
- **SWR for KYC status check**: The "Verify Now" button visibility depends on whether KYC is configured for the tenant. This is fetched via SWR with a stable cache key, so the status check is not repeated on every checklist item render. The cache is invalidated when the settings page changes the integration configuration.
- **POPIA consent as a blocking UI gate**: The "Verify" button is disabled until the consent checkbox is checked. This is a UI-level enforcement that mirrors the backend validation (consentAcknowledged must be true). Both layers enforce the requirement independently.
- **KYC dialog is client-only**: The verification dialog requires hooks (form state, loading state, SWR mutation), event handlers (consent checkbox, submit), and dynamic UI (result display). It must be a `"use client"` component. The checklist item row can remain a server component, passing serializable props to the dialog trigger.
- **Verification result badge on checklist item**: After a verification attempt (regardless of outcome), the checklist item row shows the last result as a small badge (e.g., "Verified via VerifyNow at 14:30"). This persists after the dialog closes because the verification columns are stored on the entity.

---

### Critical Files for Implementation
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationDomain.java`
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistInstanceItem.java`
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/resources/db/migration/tenant/V85__create_trust_accounting_tables.sql`
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/components/compliance/ChecklistInstanceItemRow.tsx`
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/app/(app)/org/[slug]/settings/integrations/page.tsx`