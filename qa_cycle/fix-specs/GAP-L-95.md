# Fix Spec: GAP-L-95 — Statement of Account VAT line is R 0,00 despite VAT-registered firm

## Problem

SoA renders `Total fees (excl. VAT): 3400.00`, `VAT: 0`, `Total fees (incl. VAT): 3400.00` — yet the same time entries on INV-0001 attracted R 510 VAT correctly (R 3 400 net + R 510 VAT @ 15% = R 3 910 gross).

Because closing-balance = previous_balance + fees_incl_vat + disbursements − payments_received, and the SoA receives `payments_received = 5160.00` (the full INV-0001 payment from `paymentEvents`) while computing fees as if VAT-free, the closing balance lands at **−R 510,00** — exactly the missing VAT — leaking the shortfall into a phantom over-payment.

Evidence:
- `qa_cycle/checkpoint-results/cycle46-day60-25-soa-generated.yml` lines 432-439.
- `qa_cycle/checkpoint-results/day-60.md §Day 60 Cycle 46 Walk §60.10`.

## Root Cause (verified)

`backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/statement/StatementOfAccountContextBuilder.java:246`:

```java
BigDecimal totalFeesExcl = sumAmounts(feeLines);
BigDecimal vatAmount = BigDecimal.ZERO; // line-level VAT not modelled on TimeEntry yet
BigDecimal totalFeesIncl = totalFeesExcl.add(vatAmount);
```

The author left a literal `// line-level VAT not modelled on TimeEntry yet` TODO and hard-coded VAT to zero. The SoA never references `org_settings.vat_rate` (`OrgSettings.getVatRate()`) nor any per-line VAT computation.

Comparison with the invoicing path: `InvoiceLineDisbursement` and `InvoiceLine` carry per-line `vatAmount` already; the invoice generator multiplies fees by `org_settings.vat_rate` at billing time. The SoA bypasses the invoicing path entirely (it recomputes fees from `time_entries.duration × billingRateSnapshot` for the period), so it must replicate the VAT computation itself.

`org_settings` for Mathebula tenant: `vat_registered=true, vat_rate=15.00`.

## Fix

Compute VAT in `aggregate()` using `OrgSettings.vatRate` whenever `vatRegistered=true`:

```java
// StatementOfAccountContextBuilder.java
// Inject OrgSettingsService (already injected? if not, add to constructor)

private Aggregates aggregate(
    Project project,
    Customer customer,
    UUID projectId,
    LocalDate periodStart,
    LocalDate periodEnd) {
  var feeLines = loadFeeLines(projectId, periodStart, periodEnd);
  BigDecimal totalHours = sumHours(feeLines);
  BigDecimal totalFeesExcl = sumAmounts(feeLines);

  // VAT on fees: derive from org settings. Mirrors InvoiceCalculatorService's per-line treatment
  // (it computes VAT once per line and sums), but the SoA aggregates at the total level because
  // every fee line carries the same rate (no zero-rated or exempt lines on time entries).
  var orgSettings = orgSettingsService.getOrCreateForCurrentTenant();
  BigDecimal vatAmount;
  if (orgSettings.isVatRegistered() && orgSettings.getVatRate() != null) {
    vatAmount =
        totalFeesExcl
            .multiply(orgSettings.getVatRate())
            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
  } else {
    vatAmount = BigDecimal.ZERO;
  }
  BigDecimal totalFeesIncl = totalFeesExcl.add(vatAmount);
  // ... rest unchanged
}
```

Add `OrgSettingsService orgSettingsService` to the constructor:

```java
public StatementOfAccountContextBuilder(
    ProjectRepository projectRepository,
    CustomerRepository customerRepository,
    TimeEntryRepository timeEntryRepository,
    DisbursementService disbursementService,
    ClientLedgerService clientLedgerService,
    TrustAccountRepository trustAccountRepository,
    InvoiceRepository invoiceRepository,
    PaymentEventRepository paymentEventRepository,
    MemberNameResolver memberNameResolver,
    TemplateContextHelper templateContextHelper,
    VerticalModuleGuard moduleGuard,
    ObjectMapper objectMapper,
    OrgSettingsService orgSettingsService) {  // <-- NEW
  // ...
  this.orgSettingsService = orgSettingsService;
}
```

`OrgSettingsService.getOrCreateForCurrentTenant()` already exists (used by `MatterClosureService.performClose` at line 247).

## Scope

Backend only.
Files to modify:
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/statement/StatementOfAccountContextBuilder.java` — constructor + `aggregate()` method (~10 lines).

Files to create: none.
Migration needed: no.

## Verification

1. Restart backend.
2. As Thandi on `cc390c4f-…`: top-bar **Generate Statement of Account** → period 2026-04-01 → 2026-06-30 → Preview & Save.
3. SoA preview iframe assertions:
   - `Total fees (excl. VAT)` = R 3 400,00.
   - `VAT` = R 510,00 (15% of R 3 400).
   - `Total fees (incl. VAT)` = R 3 910,00.
   - `Closing balance owing` = 0 + 3 910 + 1 350 (disbursements incl. their VAT) − 5 160 = R 100,00 owing (or R 0 — verify against the disbursement total).
4. The summary snapshot persisted on the GeneratedDocument (used by the Statements tab list view) must read the same numbers.

## Estimated Effort

**S (1-2 hours)** — single-file change, ~10 lines, with one new test.

## Tests

`StatementOfAccountContextBuilderTest`:
- `aggregate_appliesVatWhenOrgIsRegistered` — seed `org_settings.vat_registered=true, vat_rate=15`, time entries summing R 3 400 → assert vatAmount==510, totalFeesIncl==3910.
- `aggregate_skipsVatWhenOrgIsNotRegistered` — `vat_registered=false` → vatAmount==0, totalFeesIncl==totalFeesExcl.
- `aggregate_handlesNullVatRateAsZero` — `vat_registered=true` but `vat_rate=null` → vatAmount==0 (defensive — should not NPE).

## Regression Risk

`OrgSettingsService.getOrCreateForCurrentTenant()` is already invoked elsewhere on the SoA hot path indirectly — adding it as an explicit dependency is a normal Spring constructor change.
The summary snapshot in `StatementService.summarySnapshotMap` uses the values from the same aggregate; once the aggregate is correct, persisted SoA rows for new generations will also be correct. Pre-existing generated SoA rows (before the fix) will retain stale R 0 VAT — that is forward-only correctness, mirroring BUG-CYCLE26-10's Option B precedent.

## Dispatch Recommendation

**This-cycle (P0 — scenario-blocking).** Goes alongside GAP-L-94. Both are SoA-correctness gaps that Day 61 portal walk depends on. Bundle into one Dev dispatch: a single backend restart, a single PR cycle.
