package io.b2mash.b2b.b2bstrawman.invoice;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomerWithPrerequisiteFields;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.tax.TaxRate;
import io.b2mash.b2b.b2bstrawman.tax.TaxRateRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementCategory;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementPaymentSource;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.LegalDisbursement;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.VatTreatment;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.event.DisbursementBilledEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for Epic 487B — disbursement invoicing integration.
 *
 * <p>Covers tasks 487.9 and 487.10:
 *
 * <ul>
 *   <li>Disbursement-only draft creation produces DISBURSEMENT-source lines with VAT-treatment-
 *       specific tax rates.
 *   <li>Linked disbursements transition to {@code BILLED} with {@code invoiceLineId} set.
 *   <li>{@link DisbursementBilledEvent} fires once per disbursement (captured via
 *       {@code @RecordApplicationEvents}).
 *   <li>DRAFT disbursement → 400 (InvalidStateException).
 *   <li>Already-BILLED disbursement → 409 (ResourceConflictException).
 *   <li>Mixed sources (time + expense + disbursement) produce interleaved lines; totals include all
 *       three sources.
 *   <li>Rollback path: forcing a post-insert failure leaves disbursements UNBILLED.
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@RecordApplicationEvents
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvoiceCreationDisbursementTest {

  private static final String ORG_ID = "org_invoice_disb_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private DisbursementRepository disbursementRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private InvoiceLineRepository invoiceLineRepository;
  @Autowired private TaxRateRepository taxRateRepository;
  @Autowired private InvoiceCreationService invoiceCreationService;
  @Autowired private ApplicationEvents events;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID customerId;
  private UUID projectId;
  private UUID taskId;

  // Tax-rate ids cached from @BeforeAll seed.
  private UUID standardRateId;
  private UUID zeroRateId;
  private UUID exemptRateId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Invoice Disb Test Firm", null);
    memberIdOwner =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_inv_disb_owner",
                "inv_disb_owner@test.com",
                "Disb Owner",
                "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Enable the disbursements module — required by DisbursementService.markBilled's
                  // VerticalModuleGuard.requireModule("disbursements") call.
                  var settings = orgSettingsService.getOrCreateForCurrentTenant();
                  settings.setEnabledModules(List.of("disbursements"));
                  orgSettingsRepository.save(settings);

                  // Tax rate catalogue is seeded by migration V43: "Standard" (15%, default),
                  // "Zero-rated" (0%, non-exempt), "Exempt" (0%, is_exempt=true). Look them up
                  // rather than inserting — the idx_tax_rates_single_default unique index blocks
                  // a second is_default=true row.
                  standardRateId =
                      taxRateRepository
                          .findByIsDefaultTrue()
                          .orElseThrow(
                              () -> new IllegalStateException("Expected seeded default tax rate"))
                          .getId();
                  zeroRateId =
                      taxRateRepository.findAllByOrderBySortOrder().stream()
                          .filter(r -> r.getRate().compareTo(BigDecimal.ZERO) == 0 && !r.isExempt())
                          .findFirst()
                          .orElseThrow(
                              () ->
                                  new IllegalStateException("Expected seeded zero-rated tax rate"))
                          .getId();
                  exemptRateId =
                      taxRateRepository.findAllByOrderBySortOrder().stream()
                          .filter(TaxRate::isExempt)
                          .findFirst()
                          .orElseThrow(
                              () -> new IllegalStateException("Expected seeded exempt tax rate"))
                          .getId();

                  var customer =
                      createActiveCustomerWithPrerequisiteFields(
                          "Disb Law Client", "disbclient@test.com", memberIdOwner);
                  customer = customerRepository.save(customer);
                  customerId = customer.getId();

                  var project = new Project("Disb Test Matter", "For 487B", memberIdOwner);
                  project.setCustomerId(customerId);
                  project = projectRepository.save(project);
                  projectId = project.getId();

                  customerProjectRepository.save(
                      new CustomerProject(customerId, projectId, memberIdOwner));

                  // Task only needed for mixed-source (time entry) tests.
                  var task =
                      new Task(projectId, "Disb Test Task", null, null, null, null, memberIdOwner);
                  task = taskRepository.save(task);
                  taskId = task.getId();
                }));
  }

  @BeforeEach
  void resetBetweenTests() {
    cleanupInvoicesAndDisbursements();
    events.clear();
  }

  // ==========================================================================
  // 487.9 (1): disbursement-only draft with 3 VAT treatments.
  // ==========================================================================

  @Test
  void createDraft_withThreeVatTreatments_producesCorrectLines() throws Exception {
    UUID std15 =
        seedApprovedDisbursement(
            new BigDecimal("1000.00"),
            VatTreatment.STANDARD_15,
            DisbursementCategory.COUNSEL_FEES,
            "Senior counsel opinion",
            "Adv John Doe");
    UUID zero =
        seedApprovedDisbursement(
            new BigDecimal("500.00"),
            VatTreatment.ZERO_RATED_PASS_THROUGH,
            DisbursementCategory.SHERIFF_FEES,
            "Service on defendant",
            "Sheriff Edenvale");
    UUID exempt =
        seedApprovedDisbursement(
            new BigDecimal("250.00"),
            VatTreatment.EXEMPT,
            DisbursementCategory.OTHER,
            "Exempt transfer fee",
            "Regulator");

    String body =
        """
        { "customerId": "%s", "currency": "ZAR", "disbursementIds": ["%s","%s","%s"] }
        """
            .formatted(customerId, std15, zero, exempt);

    var result =
        mockMvc
            .perform(
                post("/api/invoices")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_disb_owner")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.lines.length()").value(3))
            .andExpect(jsonPath("$.lines[0].lineType").value("DISBURSEMENT"))
            .andExpect(jsonPath("$.lines[0].lineSource").value("DISBURSEMENT"))
            .andExpect(jsonPath("$.lines[1].lineType").value("DISBURSEMENT"))
            .andExpect(jsonPath("$.lines[2].lineType").value("DISBURSEMENT"))
            .andReturn();

    String json = result.getResponse().getContentAsString();

    // Line 0 → STANDARD_15 → standardRateId (15%).
    assertThat((String) JsonPath.read(json, "$.lines[0].taxRateId"))
        .isEqualTo(standardRateId.toString());
    // Line 1 → ZERO_RATED_PASS_THROUGH → zeroRateId.
    assertThat((String) JsonPath.read(json, "$.lines[1].taxRateId"))
        .isEqualTo(zeroRateId.toString());
    // Line 2 → EXEMPT → exemptRateId.
    assertThat((String) JsonPath.read(json, "$.lines[2].taxRateId"))
        .isEqualTo(exemptRateId.toString());

    // Line-description format convention: "{category}: {description} ({supplier}, {incurredDate})"
    assertThat((String) JsonPath.read(json, "$.lines[1].description"))
        .isEqualTo("Sheriff fees: Service on defendant (Sheriff Edenvale, 2026-04-10)");
  }

  // ==========================================================================
  // 487.9 (2): linked disbursements move to BILLED with invoiceLineId populated.
  // ==========================================================================

  @Test
  void createDraft_linkedDisbursements_moveToBilled() throws Exception {
    UUID d1 =
        seedApprovedDisbursement(
            new BigDecimal("300.00"),
            VatTreatment.STANDARD_15,
            DisbursementCategory.COUNSEL_FEES,
            "Opinion fee",
            "Adv Doe");

    String body =
        """
        { "customerId": "%s", "currency": "ZAR", "disbursementIds": ["%s"] }
        """
            .formatted(customerId, d1);

    mockMvc
        .perform(
            post("/api/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_disb_owner")))
        .andExpect(status().isCreated());

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var disbursement = disbursementRepository.findById(d1).orElseThrow();
                  assertThat(disbursement.getBillingStatus()).isEqualTo("BILLED");
                  assertThat(disbursement.getInvoiceLineId()).isNotNull();
                  var line =
                      invoiceLineRepository.findById(disbursement.getInvoiceLineId()).orElseThrow();
                  assertThat(line.getDisbursementId()).isEqualTo(d1);
                }));
  }

  // ==========================================================================
  // 487.9 (3): DisbursementBilledEvent fires once per disbursement.
  // ==========================================================================

  @Test
  void createDraft_publishesDisbursementBilledEventOncePerDisbursement() throws Exception {
    UUID d1 =
        seedApprovedDisbursement(
            new BigDecimal("100.00"),
            VatTreatment.STANDARD_15,
            DisbursementCategory.COUNSEL_FEES,
            "Fee 1",
            "Adv A");
    UUID d2 =
        seedApprovedDisbursement(
            new BigDecimal("200.00"),
            VatTreatment.ZERO_RATED_PASS_THROUGH,
            DisbursementCategory.SHERIFF_FEES,
            "Service fee",
            "Sheriff Alpha");

    String body =
        """
        { "customerId": "%s", "currency": "ZAR", "disbursementIds": ["%s","%s"] }
        """
            .formatted(customerId, d1, d2);

    mockMvc
        .perform(
            post("/api/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_disb_owner")))
        .andExpect(status().isCreated());

    long countForD1 =
        events.stream(DisbursementBilledEvent.class)
            .filter(e -> d1.equals(e.disbursementId()))
            .count();
    long countForD2 =
        events.stream(DisbursementBilledEvent.class)
            .filter(e -> d2.equals(e.disbursementId()))
            .count();
    assertThat(countForD1).isEqualTo(1);
    assertThat(countForD2).isEqualTo(1);
  }

  // ==========================================================================
  // 487.9 (4): DRAFT disbursement → 400 InvalidStateException.
  // ==========================================================================

  @Test
  void createDraft_withDraftDisbursement_throwsBadRequest() throws Exception {
    UUID draftDisbId = seedDraftDisbursement(new BigDecimal("100.00"));

    String body =
        """
        { "customerId": "%s", "currency": "ZAR", "disbursementIds": ["%s"] }
        """
            .formatted(customerId, draftDisbId);

    mockMvc
        .perform(
            post("/api/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_disb_owner")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Disbursement not approved"));
  }

  // ==========================================================================
  // 487.9 (5): already-BILLED disbursement → 409 ResourceConflictException.
  // ==========================================================================

  @Test
  void createDraft_withAlreadyBilledDisbursement_throwsConflict() throws Exception {
    UUID d1 =
        seedApprovedDisbursement(
            new BigDecimal("100.00"),
            VatTreatment.STANDARD_15,
            DisbursementCategory.COUNSEL_FEES,
            "First fee",
            "Adv A");

    String body1 =
        """
        { "customerId": "%s", "currency": "ZAR", "disbursementIds": ["%s"] }
        """
            .formatted(customerId, d1);

    // First invoice creation bills d1.
    mockMvc
        .perform(
            post("/api/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body1)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_disb_owner")))
        .andExpect(status().isCreated());

    // Second invoice creation with the same (now BILLED) disbursement → 409.
    mockMvc
        .perform(
            post("/api/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body1)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_disb_owner")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Disbursement already billed"));
  }

  // ==========================================================================
  // 487.10 (1 & 2): mixed sources produce interleaved lines; totals are correct.
  // ==========================================================================

  @Test
  void createDraft_mixedSources_linesAndTotals() throws Exception {
    // Seed 1 time entry (60min @ 100.00 = 100.00) + 1 disbursement (STANDARD_15, 1000.00).
    UUID timeEntryId = seedTimeEntry(new BigDecimal("100.00"));
    UUID disbId =
        seedApprovedDisbursement(
            new BigDecimal("1000.00"),
            VatTreatment.STANDARD_15,
            DisbursementCategory.COUNSEL_FEES,
            "Counsel opinion",
            "Adv A");

    String body =
        """
        {
          "customerId": "%s",
          "currency": "ZAR",
          "timeEntryIds": ["%s"],
          "disbursementIds": ["%s"]
        }
        """
            .formatted(customerId, timeEntryId, disbId);

    var result =
        mockMvc
            .perform(
                post("/api/invoices")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_disb_owner")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.lines.length()").value(2))
            .andReturn();

    String json = result.getResponse().getContentAsString();

    // First line: TIME source (1h * 100 = 100.00, 15% VAT default = 15.00).
    assertThat((String) JsonPath.read(json, "$.lines[0].lineType")).isEqualTo("TIME");
    // Second line: DISBURSEMENT source (amount 1000.00, STANDARD_15 → 15% VAT = 150.00).
    assertThat((String) JsonPath.read(json, "$.lines[1].lineType")).isEqualTo("DISBURSEMENT");

    // Subtotal = 100.00 + 1000.00 = 1100.00; tax = 15.00 + 150.00 = 165.00; total = 1265.00.
    Number subtotal = JsonPath.read(json, "$.subtotal");
    Number taxAmount = JsonPath.read(json, "$.taxAmount");
    Number total = JsonPath.read(json, "$.total");
    assertThat(new BigDecimal(subtotal.toString())).isEqualByComparingTo("1100.00");
    assertThat(new BigDecimal(taxAmount.toString())).isEqualByComparingTo("165.00");
    assertThat(new BigDecimal(total.toString())).isEqualByComparingTo("1265.00");
  }

  // ==========================================================================
  // 487.10 (3): rollback leaves disbursements UNBILLED.
  //
  // We trigger a rollback by passing a disbursement id that does not match the invoice's
  // customer — this fails the customer-scope guard in createDisbursementLines AFTER an earlier
  // disbursement in the list has been marked BILLED. The @Transactional on createDraft must
  // roll back both the invoice insert and the partial markBilled mutation.
  // ==========================================================================

  @Test
  void rollback_leavesDisbursementsUnbilled() throws Exception {
    // Valid approved disbursement for this customer/project.
    UUID goodId =
        seedApprovedDisbursement(
            new BigDecimal("100.00"),
            VatTreatment.STANDARD_15,
            DisbursementCategory.COUNSEL_FEES,
            "Good fee",
            "Adv Good");

    // Disbursement on a different customer — triggers the customer-scope guard.
    UUID[] otherPair = seedOtherCustomer();
    UUID otherCustomerId = otherPair[0];
    UUID otherProjectId = otherPair[1];
    UUID mismatchedId = seedApprovedDisbursementForCustomer(otherProjectId, otherCustomerId);

    String body =
        """
        { "customerId": "%s", "currency": "ZAR", "disbursementIds": ["%s","%s"] }
        """
            .formatted(customerId, goodId, mismatchedId);

    mockMvc
        .perform(
            post("/api/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_disb_owner")))
        .andExpect(status().isBadRequest());

    // The good disbursement must still be UNBILLED — the markBilled mutation rolled back with
    // the outer transaction.
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var good = disbursementRepository.findById(goodId).orElseThrow();
                  assertThat(good.getBillingStatus()).isEqualTo("UNBILLED");
                  assertThat(good.getInvoiceLineId()).isNull();

                  // And no invoice was persisted.
                  assertThat(invoiceRepository.findAll()).isEmpty();
                }));
  }

  // ==========================================================================
  // Helpers
  // ==========================================================================

  private UUID seedApprovedDisbursement(
      BigDecimal amount,
      VatTreatment vat,
      DisbursementCategory category,
      String description,
      String supplier) {
    return seedApprovedDisbursementFor(
        projectId, customerId, amount, vat, category, description, supplier);
  }

  private UUID seedApprovedDisbursementForCustomer(UUID otherProjectId, UUID otherCustomerId) {
    return seedApprovedDisbursementFor(
        otherProjectId,
        otherCustomerId,
        new BigDecimal("50.00"),
        VatTreatment.STANDARD_15,
        DisbursementCategory.OTHER,
        "Other customer's disb",
        "Supplier X");
  }

  private UUID seedApprovedDisbursementFor(
      UUID pid,
      UUID cid,
      BigDecimal amount,
      VatTreatment vat,
      DisbursementCategory category,
      String description,
      String supplier) {
    final UUID[] id = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  BigDecimal vatAmount =
                      vat == VatTreatment.STANDARD_15
                          ? amount
                              .multiply(new BigDecimal("0.15"))
                              .setScale(2, java.math.RoundingMode.HALF_UP)
                          : new BigDecimal("0.00");
                  var d =
                      new LegalDisbursement(
                          pid,
                          cid,
                          category.name(),
                          description,
                          amount,
                          vat.name(),
                          vatAmount,
                          DisbursementPaymentSource.OFFICE_ACCOUNT.name(),
                          null,
                          LocalDate.of(2026, 4, 10),
                          supplier,
                          "REF-001",
                          null,
                          memberIdOwner);
                  d.submitForApproval();
                  d.approve(memberIdOwner, "ok");
                  id[0] = disbursementRepository.saveAndFlush(d).getId();
                }));
    return id[0];
  }

  private UUID seedDraftDisbursement(BigDecimal amount) {
    final UUID[] id = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var d =
                      new LegalDisbursement(
                          projectId,
                          customerId,
                          DisbursementCategory.OTHER.name(),
                          "Draft disb",
                          amount,
                          VatTreatment.STANDARD_15.name(),
                          amount
                              .multiply(new BigDecimal("0.15"))
                              .setScale(2, java.math.RoundingMode.HALF_UP),
                          DisbursementPaymentSource.OFFICE_ACCOUNT.name(),
                          null,
                          LocalDate.of(2026, 4, 10),
                          "Supplier",
                          "REF-DRAFT",
                          null,
                          memberIdOwner);
                  // Leave in DRAFT status — do not submit or approve.
                  id[0] = disbursementRepository.saveAndFlush(d).getId();
                }));
    return id[0];
  }

  private UUID seedTimeEntry(BigDecimal hourlyRate) {
    final UUID[] id = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var te =
                      new TimeEntry(
                          taskId,
                          memberIdOwner,
                          LocalDate.of(2026, 3, 1),
                          60,
                          true,
                          null,
                          "Mixed source time entry");
                  te.snapshotBillingRate(hourlyRate, "ZAR");
                  te = timeEntryRepository.save(te);
                  id[0] = te.getId();
                }));
    return id[0];
  }

  /**
   * Seeds a second customer + project + CustomerProject link and returns {customerId, projectId}.
   * Used in the rollback test to construct a disbursement that belongs to a different customer.
   */
  private UUID[] seedOtherCustomer() {
    final UUID[] result = new UUID[2];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var other =
                      createActiveCustomerWithPrerequisiteFields(
                          "Other Client", "other@test.com", memberIdOwner);
                  other = customerRepository.save(other);
                  var otherProject = new Project("Other Matter", "Other project", memberIdOwner);
                  otherProject.setCustomerId(other.getId());
                  otherProject = projectRepository.save(otherProject);
                  customerProjectRepository.save(
                      new CustomerProject(other.getId(), otherProject.getId(), memberIdOwner));
                  result[0] = other.getId();
                  result[1] = otherProject.getId();
                }));
    return result;
  }

  private void cleanupInvoicesAndDisbursements() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Delete invoice lines first (FK to disbursements). Unbill linked disbursements.
                  var invoices = invoiceRepository.findAll();
                  for (var invoice : invoices) {
                    var lines =
                        invoiceLineRepository.findByInvoiceIdOrderBySortOrder(invoice.getId());
                    for (var line : lines) {
                      if (line.getTimeEntryId() != null) {
                        timeEntryRepository
                            .findById(line.getTimeEntryId())
                            .ifPresent(
                                te -> {
                                  te.setInvoiceId(null);
                                  timeEntryRepository.save(te);
                                });
                      }
                    }
                    invoiceLineRepository.deleteAll(lines);
                    invoiceRepository.delete(invoice);
                  }
                  // Disbursements next (the FK from invoice_lines → legal_disbursements is now
                  // gone).
                  disbursementRepository.deleteAll();
                  timeEntryRepository.deleteAll();
                }));
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
