package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLine;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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

/**
 * Integration tests for Epic 487A — disbursement unbilled read model.
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>{@code GET /api/legal/disbursements/unbilled?projectId=} returns only APPROVED + UNBILLED
 *       rows, ordered by incurredDate ASC, with correct totals.
 *   <li>{@link io.b2mash.b2b.b2bstrawman.invoice.UnbilledTimeService#getUnbilledTime(UUID,
 *       LocalDate, LocalDate)} populates a non-empty {@code disbursements} list when the {@code
 *       disbursements} module is enabled for the current tenant, and an empty list otherwise.
 * </ul>
 *
 * <p>Uses two provisioned tenants — one with the {@code disbursements} module enabled (legal) and
 * one without (non-legal) — so both branches of the module-gate can be asserted at the HTTP
 * boundary.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DisbursementUnbilledEndpointTest {

  private static final String LEGAL_ORG_ID = "org_disb_unbilled_legal";
  private static final String NONLEGAL_ORG_ID = "org_disb_unbilled_nonlegal";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private DisbursementRepository disbursementRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private InvoiceLineRepository invoiceLineRepository;

  private String legalTenantSchema;
  private String nonLegalTenantSchema;

  private UUID legalOwnerMemberId;
  private UUID nonLegalOwnerMemberId;

  // Legal-tenant fixture data — seeded once per test class.
  private UUID customerId;
  private UUID projectId;
  private UUID otherProjectId;

  // Id assertion targets — set inside @BeforeAll.
  private UUID approvedUnbilledId; // The only row that should appear in test 1.

  // Ordering test — seeded rows on a separate project.
  private UUID orderingProjectId;
  private UUID orderingEarliestId; // 2026-01-15
  private UUID orderingMiddleId; // 2026-02-20
  private UUID orderingLatestId; // 2026-03-10

  // Totals test — seeded rows on a separate project.
  private UUID totalsProjectId;

  // Non-legal-tenant fixture data.
  private UUID nonLegalCustomerId;

  @BeforeAll
  void setup() throws Exception {
    // --- Legal tenant ---
    provisioningService.provisionTenant(LEGAL_ORG_ID, "Unbilled-Disbursements Test Firm", null);
    legalOwnerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                LEGAL_ORG_ID,
                "user_disb_unbilled_owner",
                "disb_unbilled_owner@test.com",
                "Disb Unbilled Owner",
                "owner"));
    legalTenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(LEGAL_ORG_ID).orElseThrow().getSchemaName();

    runInLegalTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsService.getOrCreateForCurrentTenant();
                  settings.setEnabledModules(List.of("disbursements"));
                  orgSettingsRepository.save(settings);

                  var customer =
                      createActiveCustomer(
                          "Unbilled Disb Client", "unbilled_disb@test.com", legalOwnerMemberId);
                  customer = customerRepository.saveAndFlush(customer);
                  customerId = customer.getId();

                  var project =
                      new Project(
                          "Unbilled Test Matter (filter)",
                          "Primary matter for filter-correctness test",
                          legalOwnerMemberId);
                  project.setCustomerId(customerId);
                  project = projectRepository.saveAndFlush(project);
                  projectId = project.getId();

                  var other =
                      new Project(
                          "Other Matter (different project)",
                          "Matter used to prove project filter",
                          legalOwnerMemberId);
                  other.setCustomerId(customerId);
                  other = projectRepository.saveAndFlush(other);
                  otherProjectId = other.getId();

                  var ordering =
                      new Project(
                          "Ordering Matter", "Matter for ordering test", legalOwnerMemberId);
                  ordering.setCustomerId(customerId);
                  ordering = projectRepository.saveAndFlush(ordering);
                  orderingProjectId = ordering.getId();

                  var totals =
                      new Project("Totals Matter", "Matter for totals test", legalOwnerMemberId);
                  totals.setCustomerId(customerId);
                  totals = projectRepository.saveAndFlush(totals);
                  totalsProjectId = totals.getId();

                  // --- Filter-correctness fixture on `projectId` ---
                  // 4 disbursements: DRAFT, PENDING_APPROVAL, APPROVED+UNBILLED, APPROVED+BILLED.
                  // Only the third should appear in the /unbilled response.
                  saveDraft(projectId, "DRAFT-on-projectId", "100.00");
                  saveSubmitted(projectId, "PENDING-on-projectId", "200.00");
                  approvedUnbilledId =
                      saveApproved(projectId, "APPROVED-UNBILLED-on-projectId", "300.00");
                  saveBilled(projectId, "APPROVED-BILLED-on-projectId", "400.00");

                  // On otherProjectId, seed one approved+unbilled row — proves the projectId
                  // filter actually scopes the result.
                  saveApproved(otherProjectId, "APPROVED-UNBILLED-on-OTHER", "999.00");

                  // --- Ordering fixture on `orderingProjectId` ---
                  orderingMiddleId =
                      saveApprovedOn(
                          orderingProjectId,
                          "Ordering middle",
                          "100.00",
                          LocalDate.of(2026, 2, 20));
                  orderingLatestId =
                      saveApprovedOn(
                          orderingProjectId,
                          "Ordering latest",
                          "100.00",
                          LocalDate.of(2026, 3, 10));
                  orderingEarliestId =
                      saveApprovedOn(
                          orderingProjectId,
                          "Ordering earliest",
                          "100.00",
                          LocalDate.of(2026, 1, 15));

                  // --- Totals fixture on `totalsProjectId` ---
                  // STANDARD_15: amount=1000, vat=150; ZERO_RATED_PASS_THROUGH: amount=500, vat=0.
                  // Expect totalAmount=1500.00, totalVat=150.00.
                  saveApprovedStandard15(totalsProjectId, "Counsel fee 1000", "1000.00");
                  saveApprovedZeroRated(totalsProjectId, "Sheriff fee 500", "500.00");
                }));

    // --- Non-legal tenant ---
    provisioningService.provisionTenant(NONLEGAL_ORG_ID, "Unbilled-Disb Non-Legal Firm", null);
    nonLegalOwnerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                NONLEGAL_ORG_ID,
                "user_disb_unbilled_nonlegal_owner",
                "disb_unbilled_nonlegal_owner@test.com",
                "NonLegal Owner",
                "owner"));
    nonLegalTenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(NONLEGAL_ORG_ID).orElseThrow().getSchemaName();

    runInNonLegalTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsService.getOrCreateForCurrentTenant();
                  settings.setEnabledModules(List.of());
                  orgSettingsRepository.save(settings);

                  var customer =
                      createActiveCustomer(
                          "NonLegal Client", "nonlegal@test.com", nonLegalOwnerMemberId);
                  customer = customerRepository.saveAndFlush(customer);
                  nonLegalCustomerId = customer.getId();
                }));
  }

  // ==========================================================================
  // 1. Filter correctness — only APPROVED+UNBILLED rows returned.
  // ==========================================================================

  @Test
  void legalTenant_GET_unbilled_returnsOnlyApprovedAndUnbilled() throws Exception {
    mockMvc
        .perform(
            get("/api/legal/disbursements/unbilled")
                .param("projectId", projectId.toString())
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_disb_unbilled_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projectId").value(projectId.toString()))
        .andExpect(jsonPath("$.currency").value("ZAR"))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(approvedUnbilledId.toString()))
        .andExpect(jsonPath("$.items[0].description").value("APPROVED-UNBILLED-on-projectId"));
  }

  // ==========================================================================
  // 2. Ordering — by incurredDate ASC (then createdAt ASC).
  // ==========================================================================

  @Test
  void legalTenant_GET_unbilled_ordersByIncurredDateAsc() throws Exception {
    mockMvc
        .perform(
            get("/api/legal/disbursements/unbilled")
                .param("projectId", orderingProjectId.toString())
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_disb_unbilled_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(3))
        .andExpect(jsonPath("$.items[0].id").value(orderingEarliestId.toString()))
        .andExpect(jsonPath("$.items[0].incurredDate").value("2026-01-15"))
        .andExpect(jsonPath("$.items[1].id").value(orderingMiddleId.toString()))
        .andExpect(jsonPath("$.items[1].incurredDate").value("2026-02-20"))
        .andExpect(jsonPath("$.items[2].id").value(orderingLatestId.toString()))
        .andExpect(jsonPath("$.items[2].incurredDate").value("2026-03-10"));
  }

  // ==========================================================================
  // 3. Totals — pre-computed totalAmount + totalVat (scale 2, HALF_UP).
  // ==========================================================================

  @Test
  void legalTenant_GET_unbilled_computesTotalsCorrectly() throws Exception {
    mockMvc
        .perform(
            get("/api/legal/disbursements/unbilled")
                .param("projectId", totalsProjectId.toString())
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_disb_unbilled_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.totalAmount").value(1500.00))
        .andExpect(jsonPath("$.totalVat").value(150.00));
  }

  // ==========================================================================
  // 4. Module enabled — UnbilledTimeService includes a populated `disbursements` list.
  // ==========================================================================

  @Test
  void legalTenant_GET_customerUnbilledTime_includesPopulatedDisbursementsList() throws Exception {
    mockMvc
        .perform(
            get("/api/customers/" + customerId + "/unbilled-time")
                .with(TestJwtFactory.ownerJwt(LEGAL_ORG_ID, "user_disb_unbilled_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customerId").value(customerId.toString()))
        .andExpect(jsonPath("$.disbursements").isArray())
        // Legal tenant: approved+unbilled rows across all projects for this customer
        // (no projectId narrowing). At least the 1 on projectId + 1 on otherProjectId + 3 on
        // orderingProjectId + 2 on totalsProjectId = 7 rows.
        .andExpect(jsonPath("$.disbursements.length()").value(7))
        .andExpect(jsonPath("$.disbursements[0].id").isNotEmpty())
        .andExpect(jsonPath("$.disbursements[0].category").isNotEmpty());
  }

  // ==========================================================================
  // 5. Module disabled — UnbilledTimeService returns an empty `disbursements` list.
  // ==========================================================================

  @Test
  void nonLegalTenant_GET_customerUnbilledTime_returnsEmptyDisbursementsList() throws Exception {
    mockMvc
        .perform(
            get("/api/customers/" + nonLegalCustomerId + "/unbilled-time")
                .with(
                    TestJwtFactory.ownerJwt(NONLEGAL_ORG_ID, "user_disb_unbilled_nonlegal_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customerId").value(nonLegalCustomerId.toString()))
        .andExpect(jsonPath("$.disbursements").isArray())
        .andExpect(jsonPath("$.disbursements.length()").value(0));
  }

  // ==========================================================================
  // Helpers — direct entity seeding via the repository to bypass the HTTP submit/approve
  // workflow (faster + deterministic for setup).
  // ==========================================================================

  /** Seeds a DRAFT disbursement. */
  private UUID saveDraft(UUID pid, String description, String amount) {
    var d =
        newOffice(
            pid, description, amount, DisbursementCategory.COUNSEL_FEES, LocalDate.of(2026, 4, 1));
    return disbursementRepository.saveAndFlush(d).getId();
  }

  /** Seeds a PENDING_APPROVAL disbursement. */
  private UUID saveSubmitted(UUID pid, String description, String amount) {
    var d =
        newOffice(
            pid, description, amount, DisbursementCategory.COUNSEL_FEES, LocalDate.of(2026, 4, 1));
    d.submitForApproval();
    return disbursementRepository.saveAndFlush(d).getId();
  }

  /** Seeds an APPROVED + UNBILLED disbursement (SHERIFF_FEES / ZERO_RATED_PASS_THROUGH). */
  private UUID saveApproved(UUID pid, String description, String amount) {
    return saveApprovedOn(pid, description, amount, LocalDate.of(2026, 4, 1));
  }

  private UUID saveApprovedOn(UUID pid, String description, String amount, LocalDate incurredDate) {
    var d = newOffice(pid, description, amount, DisbursementCategory.SHERIFF_FEES, incurredDate);
    d.submitForApproval();
    d.approve(legalOwnerMemberId, "ok");
    return disbursementRepository.saveAndFlush(d).getId();
  }

  /**
   * Seeds an APPROVED + BILLED disbursement. Creates a real {@link Invoice} + {@link InvoiceLine}
   * so the {@code legal_disbursements.invoice_line_id} FK resolves (the constraint lives in
   * migration {@code V100}).
   */
  private UUID saveBilled(UUID pid, String description, String amount) {
    var d =
        newOffice(
            pid, description, amount, DisbursementCategory.COUNSEL_FEES, LocalDate.of(2026, 4, 1));
    d.submitForApproval();
    d.approve(legalOwnerMemberId, "ok");
    var invoice =
        new Invoice(
            customerId,
            "ZAR",
            "Unbilled Disb Client",
            "unbilled_disb@test.com",
            null,
            "Disb unbilled billed-row seed",
            legalOwnerMemberId);
    var savedInvoice = invoiceRepository.saveAndFlush(invoice);
    var line =
        new InvoiceLine(
            savedInvoice.getId(),
            pid,
            null,
            "Disbursement line",
            new BigDecimal("1"),
            new BigDecimal(amount),
            0);
    var savedLine = invoiceLineRepository.saveAndFlush(line);
    d.markBilled(savedLine.getId());
    return disbursementRepository.saveAndFlush(d).getId();
  }

  /** Seeds an APPROVED + UNBILLED STANDARD_15 disbursement (used for totals test). */
  private UUID saveApprovedStandard15(UUID pid, String description, String amount) {
    var amt = new BigDecimal(amount);
    var vat = amt.multiply(new BigDecimal("0.15")).setScale(2, java.math.RoundingMode.HALF_UP);
    var d =
        new LegalDisbursement(
            pid,
            customerId,
            DisbursementCategory.COUNSEL_FEES.name(),
            description,
            amt,
            VatTreatment.STANDARD_15.name(),
            vat,
            DisbursementPaymentSource.OFFICE_ACCOUNT.name(),
            null,
            LocalDate.of(2026, 4, 1),
            "Supplier Co",
            "REF-001",
            null,
            legalOwnerMemberId);
    d.submitForApproval();
    d.approve(legalOwnerMemberId, "ok");
    return disbursementRepository.saveAndFlush(d).getId();
  }

  /** Seeds an APPROVED + UNBILLED ZERO_RATED_PASS_THROUGH disbursement (used for totals test). */
  private UUID saveApprovedZeroRated(UUID pid, String description, String amount) {
    var d =
        new LegalDisbursement(
            pid,
            customerId,
            DisbursementCategory.SHERIFF_FEES.name(),
            description,
            new BigDecimal(amount),
            VatTreatment.ZERO_RATED_PASS_THROUGH.name(),
            new BigDecimal("0.00"),
            DisbursementPaymentSource.OFFICE_ACCOUNT.name(),
            null,
            LocalDate.of(2026, 4, 1),
            "Sheriff Edenvale",
            "SH-001",
            null,
            legalOwnerMemberId);
    d.submitForApproval();
    d.approve(legalOwnerMemberId, "ok");
    return disbursementRepository.saveAndFlush(d).getId();
  }

  private LegalDisbursement newOffice(
      UUID pid,
      String description,
      String amount,
      DisbursementCategory category,
      LocalDate incurredDate) {
    var amt = new BigDecimal(amount);
    // For SHERIFF_FEES the default VAT is ZERO_RATED_PASS_THROUGH; COUNSEL_FEES defaults to
    // STANDARD_15. We compute the VAT consistently with DisbursementService.computeVatAmount.
    VatTreatment vat =
        category == DisbursementCategory.SHERIFF_FEES
                || category == DisbursementCategory.DEEDS_OFFICE_FEES
                || category == DisbursementCategory.COURT_FEES
            ? VatTreatment.ZERO_RATED_PASS_THROUGH
            : VatTreatment.STANDARD_15;
    BigDecimal vatAmount =
        vat == VatTreatment.STANDARD_15
            ? amt.multiply(new BigDecimal("0.15")).setScale(2, java.math.RoundingMode.HALF_UP)
            : new BigDecimal("0.00");
    return new LegalDisbursement(
        pid,
        customerId,
        category.name(),
        description,
        amt,
        vat.name(),
        vatAmount,
        DisbursementPaymentSource.OFFICE_ACCOUNT.name(),
        null,
        incurredDate,
        "Supplier Co",
        "REF-001",
        null,
        legalOwnerMemberId);
  }

  private void runInLegalTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, legalTenantSchema)
        .where(RequestScopes.ORG_ID, LEGAL_ORG_ID)
        .where(RequestScopes.MEMBER_ID, legalOwnerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }

  private void runInNonLegalTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, nonLegalTenantSchema)
        .where(RequestScopes.ORG_ID, NONLEGAL_ORG_ID)
        .where(RequestScopes.MEMBER_ID, nonLegalOwnerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
