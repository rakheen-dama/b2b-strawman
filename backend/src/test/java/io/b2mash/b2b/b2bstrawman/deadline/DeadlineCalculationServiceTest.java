package io.b2mash.b2b.b2bstrawman.deadline;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createCustomerWithStatus;
import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.deadline.DeadlineCalculationService.DeadlineFilters;
import io.b2mash.b2b.b2bstrawman.deadline.DeadlineCalculationService.DeadlineSummary;
import io.b2mash.b2b.b2bstrawman.deadline.FilingStatusService.CreateFilingStatusRequest;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeadlineCalculationServiceTest {
  private static final String ORG_ID = "org_deadline_calc_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private DeadlineCalculationService deadlineCalculationService;
  @Autowired private FilingStatusService filingStatusService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;

  private String tenantSchema;
  private UUID memberId;

  // Customer IDs populated in @BeforeAll
  private UUID activeCustomerWithFye; // ACTIVE, FYE = 2025-02-28
  private UUID vatRegisteredCustomer; // ACTIVE, FYE + vat_number
  private UUID cipcRegisteredCustomer; // ACTIVE, FYE + cipc_registration_number
  private UUID prospectCustomerWithFye; // PROSPECT, FYE = 2025-02-28 (should be excluded)
  private UUID entityColumnFyeCustomer; // ACTIVE, FYE set via entity column only

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Deadline Calc Test Org", null);
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_dcalc_owner", "dcalc@test.com", "DCalc Owner", "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Customer 1: ACTIVE with FYE on entity column
                  var c1 = createActiveCustomer("Tax Corp", "tax@test.com", memberId);
                  c1.setFinancialYearEnd(LocalDate.of(2025, 2, 28));
                  c1 = customerRepository.saveAndFlush(c1);
                  activeCustomerWithFye = c1.getId();

                  // Customer 2: ACTIVE with FYE entity column + VAT number in JSONB
                  var c2 = createActiveCustomer("VAT Corp", "vat@test.com", memberId);
                  c2.setFinancialYearEnd(LocalDate.of(2025, 6, 30));
                  c2.setCustomFields(Map.of("vat_number", "4123456789"));
                  c2 = customerRepository.saveAndFlush(c2);
                  vatRegisteredCustomer = c2.getId();

                  // Customer 3: ACTIVE with FYE entity column + CIPC registration in JSONB
                  var c3 = createActiveCustomer("CIPC Corp", "cipc@test.com", memberId);
                  c3.setFinancialYearEnd(LocalDate.of(2025, 3, 31));
                  c3.setCustomFields(Map.of("cipc_registration_number", "2020/123456/07"));
                  c3 = customerRepository.saveAndFlush(c3);
                  cipcRegisteredCustomer = c3.getId();

                  // Customer 4: PROSPECT with FYE entity column (should be excluded)
                  var c4 =
                      createCustomerWithStatus(
                          "Prospect Corp", "prospect@test.com", memberId, LifecycleStatus.PROSPECT);
                  c4.setFinancialYearEnd(LocalDate.of(2025, 2, 28));
                  customerRepository.saveAndFlush(c4);
                  prospectCustomerWithFye = c4.getId();

                  // Customer 5: ACTIVE with FYE on entity column only (no JSONB at all)
                  var c5 = createActiveCustomer("Entity Col Corp", "entcol@test.com", memberId);
                  c5.setFinancialYearEnd(LocalDate.of(2025, 4, 30));
                  c5 = customerRepository.saveAndFlush(c5);
                  entityColumnFyeCustomer = c5.getId();
                }));
  }

  @Test
  void calculatesProvisionalTaxDatesCorrectly_fromFye20250228() {
    runInTenant(
        () -> {
          // FYE 2025-02-28:
          // provisional_1 = FYE + 6 months end = 2025-08-31
          // provisional_2 = FYE end of month = 2025-02-28
          // provisional_3 = FYE + 7 months end = 2025-09-30
          // annual_return = FYE + 12 months = 2026-02-28
          var deadlines =
              deadlineCalculationService.calculateDeadlinesForCustomer(
                  activeCustomerWithFye, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 12, 31));

          // Filter to tax category only for this customer
          var taxDeadlines = deadlines.stream().filter(d -> "tax".equals(d.category())).toList();

          assertThat(taxDeadlines).isNotEmpty();

          // Check provisional_1 for year 2025: FYE 2025-02-28 + 6 months = 2025-08-31
          var prov1_2025 =
              taxDeadlines.stream()
                  .filter(
                      d ->
                          "sars_provisional_1".equals(d.deadlineTypeSlug())
                              && d.dueDate().equals(LocalDate.of(2025, 8, 31)))
                  .findFirst();
          assertThat(prov1_2025).isPresent();

          // Check provisional_2 for year 2025: FYE 2025-02-28 end of month = 2025-02-28
          var prov2_2025 =
              taxDeadlines.stream()
                  .filter(
                      d ->
                          "sars_provisional_2".equals(d.deadlineTypeSlug())
                              && d.dueDate().equals(LocalDate.of(2025, 2, 28)))
                  .findFirst();
          assertThat(prov2_2025).isPresent();

          // Check annual_return for year 2025: FYE 2025-02-28 + 12 months = 2026-02-28
          var annual2025 =
              taxDeadlines.stream()
                  .filter(
                      d ->
                          "sars_annual_return".equals(d.deadlineTypeSlug())
                              && d.dueDate().equals(LocalDate.of(2026, 2, 28)))
                  .findFirst();
          assertThat(annual2025).isPresent();
        });
  }

  @Test
  void vatDeadlines_onlyGeneratedForCustomerWithVatNumber() {
    runInTenant(
        () -> {
          // VAT-registered customer should have VAT deadlines
          var vatDeadlines =
              deadlineCalculationService
                  .calculateDeadlinesForCustomer(
                      vatRegisteredCustomer, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
                  .stream()
                  .filter(d -> "sars_vat_return".equals(d.deadlineTypeSlug()))
                  .toList();
          assertThat(vatDeadlines).isNotEmpty();

          // Non-VAT customer should NOT have VAT deadlines
          var noVatDeadlines =
              deadlineCalculationService
                  .calculateDeadlinesForCustomer(
                      activeCustomerWithFye, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
                  .stream()
                  .filter(d -> "sars_vat_return".equals(d.deadlineTypeSlug()))
                  .toList();
          assertThat(noVatDeadlines).isEmpty();
        });
  }

  @Test
  void cipcDeadlines_onlyGeneratedForCustomerWithCipcRegistration() {
    runInTenant(
        () -> {
          // CIPC-registered customer should have CIPC deadlines
          var cipcDeadlines =
              deadlineCalculationService
                  .calculateDeadlinesForCustomer(
                      cipcRegisteredCustomer, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 6, 30))
                  .stream()
                  .filter(d -> "cipc_annual_return".equals(d.deadlineTypeSlug()))
                  .toList();
          assertThat(cipcDeadlines).isNotEmpty();

          // Non-CIPC customer should NOT have CIPC deadlines
          var noCipcDeadlines =
              deadlineCalculationService
                  .calculateDeadlinesForCustomer(
                      activeCustomerWithFye, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 6, 30))
                  .stream()
                  .filter(d -> "cipc_annual_return".equals(d.deadlineTypeSlug()))
                  .toList();
          assertThat(noCipcDeadlines).isEmpty();
        });
  }

  @Test
  void filingStatusOverlay_filedStatusCorrectlyShown() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Mark a specific deadline as filed
                  filingStatusService.upsert(
                      new CreateFilingStatusRequest(
                          activeCustomerWithFye,
                          "sars_provisional_1",
                          "2025",
                          "filed",
                          "Filed on time",
                          null),
                      memberId);

                  var deadlines =
                      deadlineCalculationService.calculateDeadlinesForCustomer(
                          activeCustomerWithFye,
                          LocalDate.of(2025, 1, 1),
                          LocalDate.of(2026, 12, 31));

                  // Find the provisional_1 for 2025 (due 2025-08-31)
                  var prov1 =
                      deadlines.stream()
                          .filter(
                              d ->
                                  "sars_provisional_1".equals(d.deadlineTypeSlug())
                                      && d.dueDate().equals(LocalDate.of(2025, 8, 31)))
                          .findFirst();
                  assertThat(prov1).isPresent();
                  assertThat(prov1.get().status()).isEqualTo("filed");
                  assertThat(prov1.get().filingStatusId()).isNotNull();
                }));
  }

  @Test
  void overdueStatus_computedForPastDueDeadlinesWithNoFilingStatus() {
    runInTenant(
        () -> {
          // Use a date range that includes deadlines in the past (before today)
          var deadlines =
              deadlineCalculationService.calculateDeadlinesForCustomer(
                  activeCustomerWithFye, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));

          // All deadlines in 2024 should be in the past, so non-filed ones should be overdue
          // (except sars_provisional_3 which is voluntary and should remain "pending")
          var overdueDeadlines =
              deadlines.stream().filter(d -> "overdue".equals(d.status())).toList();
          assertThat(overdueDeadlines).isNotEmpty();

          // sars_provisional_3 should be "pending", not "overdue" (voluntary)
          var prov3Deadlines =
              deadlines.stream()
                  .filter(d -> "sars_provisional_3".equals(d.deadlineTypeSlug()))
                  .toList();
          assertThat(prov3Deadlines)
              .allMatch(d -> "pending".equals(d.status()) || "filed".equals(d.status()));
        });
  }

  @Test
  void dateRangeFiltering_excludesOutOfRangeDeadlines() {
    runInTenant(
        () -> {
          // Narrow range: only March 2026
          var deadlines =
              deadlineCalculationService.calculateDeadlinesForCustomer(
                  activeCustomerWithFye, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

          // All returned deadlines must have due dates within the range
          assertThat(deadlines)
              .allMatch(
                  d ->
                      !d.dueDate().isBefore(LocalDate.of(2026, 3, 1))
                          && !d.dueDate().isAfter(LocalDate.of(2026, 3, 31)));
        });
  }

  @Test
  void prospectCustomers_excludedFromCalculation() {
    runInTenant(
        () -> {
          // Calculate for the prospect customer directly -- should return empty
          var deadlines =
              deadlineCalculationService.calculateDeadlinesForCustomer(
                  prospectCustomerWithFye, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 12, 31));

          assertThat(deadlines).isEmpty();

          // Also verify that when calculating for all customers, prospect is not included
          var allDeadlines =
              deadlineCalculationService.calculateDeadlines(
                  LocalDate.of(2025, 1, 1),
                  LocalDate.of(2026, 12, 31),
                  new DeadlineFilters(null, null, null));

          assertThat(allDeadlines).noneMatch(d -> d.customerId().equals(prospectCustomerWithFye));
        });
  }

  @Test
  void calculateSummary_groupsByMonthAndCategory_withCorrectCounts() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Mark one tax deadline as filed so we get a mix of statuses
                  filingStatusService.upsert(
                      new CreateFilingStatusRequest(
                          activeCustomerWithFye,
                          "sars_provisional_2",
                          "2026",
                          "filed",
                          "Filed prov2",
                          null),
                      memberId);

                  // Calculate summary for all customers in a future range (all pending/filed)
                  var summaries =
                      deadlineCalculationService.calculateSummary(
                          LocalDate.of(2026, 1, 1),
                          LocalDate.of(2026, 12, 31),
                          new DeadlineFilters(null, null, activeCustomerWithFye));

                  assertThat(summaries).isNotEmpty();

                  // Each summary should have consistent counts
                  for (DeadlineSummary s : summaries) {
                    assertThat(s.total()).isEqualTo(s.filed() + s.pending() + s.overdue());
                    assertThat(s.month()).matches("\\d{4}-\\d{2}");
                    assertThat(s.category()).isNotBlank();
                  }

                  // Verify the filed deadline shows up in the correct month
                  // sars_provisional_2 for 2026: FYE 2026-02-28 end of month = 2026-02-28
                  var febTax =
                      summaries.stream()
                          .filter(s -> "2026-02".equals(s.month()) && "tax".equals(s.category()))
                          .findFirst();
                  assertThat(febTax).isPresent();
                  assertThat(febTax.get().filed()).isGreaterThanOrEqualTo(1);
                }));
  }

  @Test
  void categoryFilter_returnOnlyMatchingCategory() {
    runInTenant(
        () -> {
          // Filter to "tax" category only
          var taxDeadlines =
              deadlineCalculationService.calculateDeadlines(
                  LocalDate.of(2026, 1, 1),
                  LocalDate.of(2026, 12, 31),
                  new DeadlineFilters("tax", null, activeCustomerWithFye));

          assertThat(taxDeadlines).isNotEmpty();
          assertThat(taxDeadlines).allMatch(d -> "tax".equals(d.category()));

          // Filter to "payroll" category
          var payrollDeadlines =
              deadlineCalculationService.calculateDeadlines(
                  LocalDate.of(2026, 1, 1),
                  LocalDate.of(2026, 12, 31),
                  new DeadlineFilters("payroll", null, activeCustomerWithFye));

          assertThat(payrollDeadlines).isNotEmpty();
          assertThat(payrollDeadlines).allMatch(d -> "payroll".equals(d.category()));

          // "vat" category should return nothing for a non-VAT customer
          var vatDeadlines =
              deadlineCalculationService.calculateDeadlines(
                  LocalDate.of(2026, 1, 1),
                  LocalDate.of(2026, 12, 31),
                  new DeadlineFilters("vat", null, activeCustomerWithFye));

          assertThat(vatDeadlines).isEmpty();
        });
  }

  @Test
  void statusFilter_returnsOnlyMatchingStatus() {
    runInTenant(
        () -> {
          // All deadlines in 2024 for activeCustomerWithFye should be overdue (past)
          // Filter for "overdue" only
          var overdueOnly =
              deadlineCalculationService.calculateDeadlines(
                  LocalDate.of(2024, 1, 1),
                  LocalDate.of(2024, 12, 31),
                  new DeadlineFilters(null, "overdue", activeCustomerWithFye));

          assertThat(overdueOnly).isNotEmpty();
          assertThat(overdueOnly).allMatch(d -> "overdue".equals(d.status()));

          // Filter for "pending" -- sars_provisional_3 (voluntary) should show up as pending
          var pendingOnly =
              deadlineCalculationService.calculateDeadlines(
                  LocalDate.of(2024, 1, 1),
                  LocalDate.of(2024, 12, 31),
                  new DeadlineFilters(null, "pending", activeCustomerWithFye));

          assertThat(pendingOnly).allMatch(d -> "pending".equals(d.status()));

          // Filter for "filed" in a range where nothing is filed -- should be empty
          var filedOnly =
              deadlineCalculationService.calculateDeadlines(
                  LocalDate.of(2028, 1, 1),
                  LocalDate.of(2028, 12, 31),
                  new DeadlineFilters(null, "filed", activeCustomerWithFye));

          assertThat(filedOnly).isEmpty();
        });
  }

  // --- 461.5: Entity getter tests ---

  @Test
  void entityColumnFye_usedForDeadlineCalculation() {
    runInTenant(
        () -> {
          // Customer 5 has FYE set ONLY on the entity column (no JSONB custom fields)
          var deadlines =
              deadlineCalculationService.calculateDeadlinesForCustomer(
                  entityColumnFyeCustomer, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 12, 31));

          assertThat(deadlines).isNotEmpty();
          // Should have tax deadlines calculated from FYE 2025-04-30
          var taxDeadlines = deadlines.stream().filter(d -> "tax".equals(d.category())).toList();
          assertThat(taxDeadlines).isNotEmpty();
        });
  }

  @Test
  void entityColumnFye_nullFinancialYearEnd_noDeadlinesGenerated() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Create customer with null FYE
                  var customer = createActiveCustomer("No FYE Corp", "nofye@test.com", memberId);
                  var saved = customerRepository.saveAndFlush(customer);

                  var deadlines =
                      deadlineCalculationService.calculateDeadlinesForCustomer(
                          saved.getId(), LocalDate.of(2025, 1, 1), LocalDate.of(2026, 12, 31));

                  assertThat(deadlines).isEmpty();
                }));
  }

  @Test
  void entityColumnWorkType_usedForProjectCrossReference() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Create a project with workType on entity column and tax_year in JSONB
                  var project = new Project("Tax Engagement 2026", "Test engagement", memberId);
                  project.setCustomerId(activeCustomerWithFye);
                  project.setWorkType("tax");
                  project.setCustomFields(Map.of("tax_year", "2026"));
                  projectRepository.saveAndFlush(project);

                  var deadlines =
                      deadlineCalculationService.calculateDeadlinesForCustomer(
                          activeCustomerWithFye,
                          LocalDate.of(2026, 1, 1),
                          LocalDate.of(2026, 12, 31));

                  var taxDeadlines =
                      deadlines.stream().filter(d -> "tax".equals(d.category())).toList();
                  assertThat(taxDeadlines).isNotEmpty();
                  // At least one tax deadline should be linked to the project
                  assertThat(taxDeadlines).anyMatch(d -> d.linkedProjectId() != null);
                }));
  }

  @Test
  void taxYearStillReadFromJsonb_notPromoted() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Create project with workType on entity column but NO tax_year in JSONB
                  var project = new Project("Tax Engagement No Year", "No tax year", memberId);
                  project.setCustomerId(activeCustomerWithFye);
                  project.setWorkType("tax");
                  // No tax_year in custom fields
                  projectRepository.saveAndFlush(project);

                  var deadlines =
                      deadlineCalculationService.calculateDeadlinesForCustomer(
                          activeCustomerWithFye,
                          LocalDate.of(2025, 1, 1),
                          LocalDate.of(2026, 12, 31));

                  // Deadlines should exist but the project without tax_year should NOT be
                  // linked
                  var taxDeadlines =
                      deadlines.stream().filter(d -> "tax".equals(d.category())).toList();
                  assertThat(taxDeadlines).isNotEmpty();
                  // None should be linked to this project (no tax_year match)
                  assertThat(taxDeadlines)
                      .filteredOn(d -> d.linkedProjectId() != null)
                      .extracting(d -> d.linkedProjectId())
                      .doesNotContain(project.getId());
                }));
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
