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
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementCategory;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementPaymentSource;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.LegalDisbursement;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.VatTreatment;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for Epic 488B — appending disbursement lines to an existing DRAFT invoice via
 * {@code POST /api/invoices/{id}/disbursement-lines}.
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>Appending to an empty DRAFT invoice (sort order starts at 0).
 *   <li>Appending to a DRAFT invoice that already has disbursement lines (sort order continues from
 *       max).
 *   <li>Rejecting append on a non-DRAFT invoice (approved → 400 InvalidStateException).
 *   <li>Rejecting already-BILLED disbursements (409 ResourceConflictException).
 *   <li>Validation: empty disbursementIds list → 400.
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvoiceAppendDisbursementLinesIntegrationTest {

  private static final String ORG_ID = "org_invoice_append_disb_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private DisbursementRepository disbursementRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private InvoiceLineRepository invoiceLineRepository;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID customerId;
  private UUID projectId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Invoice Append Disb Test Firm", null);
    memberIdOwner =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_inv_append_owner",
                "inv_append_owner@test.com",
                "Append Owner",
                "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsService.getOrCreateForCurrentTenant();
                  settings.setEnabledModules(List.of("disbursements"));
                  orgSettingsRepository.save(settings);

                  var customer =
                      createActiveCustomerWithPrerequisiteFields(
                          "Append Law Client", "appendclient@test.com", memberIdOwner);
                  customer = customerRepository.save(customer);
                  customerId = customer.getId();

                  var project = new Project("Append Test Matter", "For 488B", memberIdOwner);
                  project.setCustomerId(customerId);
                  project = projectRepository.save(project);
                  projectId = project.getId();

                  customerProjectRepository.save(
                      new CustomerProject(customerId, projectId, memberIdOwner));
                }));
  }

  @BeforeEach
  void resetBetweenTests() {
    cleanupInvoicesAndDisbursements();
  }

  // ==========================================================================
  // Append to an empty DRAFT invoice — sort orders start at 0.
  // ==========================================================================

  @Test
  void append_toEmptyDraft_addsDisbursementLines() throws Exception {
    String draftInvoiceId = createEmptyDraftInvoice();

    UUID d1 =
        seedApprovedDisbursement(
            new BigDecimal("400.00"),
            VatTreatment.STANDARD_15,
            DisbursementCategory.COUNSEL_FEES,
            "Counsel opinion",
            "Adv Alpha");
    UUID d2 =
        seedApprovedDisbursement(
            new BigDecimal("600.00"),
            VatTreatment.STANDARD_15,
            DisbursementCategory.SHERIFF_FEES,
            "Sheriff service",
            "Sheriff Beta");

    String body =
        """
        { "disbursementIds": ["%s","%s"] }
        """
            .formatted(d1, d2);

    var result =
        mockMvc
            .perform(
                post("/api/invoices/" + draftInvoiceId + "/disbursement-lines")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_append_owner")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lines.length()").value(2))
            .andExpect(jsonPath("$.lines[0].lineType").value("DISBURSEMENT"))
            .andExpect(jsonPath("$.lines[1].lineType").value("DISBURSEMENT"))
            .andReturn();

    String json = result.getResponse().getContentAsString();
    Number subtotal = JsonPath.read(json, "$.subtotal");
    assertThat(new BigDecimal(subtotal.toString())).isEqualByComparingTo("1000.00");

    // Disbursements must now be BILLED.
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  assertThat(disbursementRepository.findById(d1).orElseThrow().getBillingStatus())
                      .isEqualTo("BILLED");
                  assertThat(disbursementRepository.findById(d2).orElseThrow().getBillingStatus())
                      .isEqualTo("BILLED");
                }));
  }

  // ==========================================================================
  // Append to a DRAFT invoice that already has lines — sort order continues.
  // ==========================================================================

  @Test
  void append_toDraftWithExistingLines_preservesExistingAndContinuesSortOrder() throws Exception {
    UUID dExisting =
        seedApprovedDisbursement(
            new BigDecimal("100.00"),
            VatTreatment.STANDARD_15,
            DisbursementCategory.COUNSEL_FEES,
            "Existing line",
            "Adv Existing");

    String createBody =
        """
        { "customerId": "%s", "currency": "ZAR", "disbursementIds": ["%s"] }
        """
            .formatted(customerId, dExisting);

    var createResult =
        mockMvc
            .perform(
                post("/api/invoices")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody)
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_append_owner")))
            .andExpect(status().isCreated())
            .andReturn();

    String createdInvoiceId =
        JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    UUID dNew =
        seedApprovedDisbursement(
            new BigDecimal("250.00"),
            VatTreatment.STANDARD_15,
            DisbursementCategory.SEARCH_FEES,
            "Append line",
            "Adv Append");

    String appendBody =
        """
        { "disbursementIds": ["%s"] }
        """
            .formatted(dNew);

    var result =
        mockMvc
            .perform(
                post("/api/invoices/" + createdInvoiceId + "/disbursement-lines")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(appendBody)
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_append_owner")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lines.length()").value(2))
            .andReturn();

    String json = result.getResponse().getContentAsString();
    Integer sortOrder0 = JsonPath.read(json, "$.lines[0].sortOrder");
    Integer sortOrder1 = JsonPath.read(json, "$.lines[1].sortOrder");
    assertThat(sortOrder0).isEqualTo(0);
    assertThat(sortOrder1).isEqualTo(1);

    Number subtotal = JsonPath.read(json, "$.subtotal");
    assertThat(new BigDecimal(subtotal.toString())).isEqualByComparingTo("350.00");
  }

  // ==========================================================================
  // Non-DRAFT invoice → 400 InvalidStateException.
  // ==========================================================================

  @Test
  void append_toNonDraftInvoice_returnsBadRequest() throws Exception {
    // An invoice can only be approved if it has at least one line item, so
    // create a draft with a disbursement first, then approve it so it leaves
    // DRAFT.
    UUID dInitial =
        seedApprovedDisbursement(
            new BigDecimal("50.00"),
            VatTreatment.STANDARD_15,
            DisbursementCategory.COUNSEL_FEES,
            "Initial fee",
            "Adv Initial");
    String createBody =
        """
        { "customerId": "%s", "currency": "ZAR", "disbursementIds": ["%s"] }
        """
            .formatted(customerId, dInitial);
    var createResult =
        mockMvc
            .perform(
                post("/api/invoices")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody)
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_append_owner")))
            .andExpect(status().isCreated())
            .andReturn();
    String draftInvoiceId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Approve the invoice so it's no longer in DRAFT.
    mockMvc
        .perform(
            post("/api/invoices/" + draftInvoiceId + "/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_append_owner")))
        .andExpect(status().isOk());

    UUID d1 =
        seedApprovedDisbursement(
            new BigDecimal("100.00"),
            VatTreatment.STANDARD_15,
            DisbursementCategory.COUNSEL_FEES,
            "Too late",
            "Adv Late");

    String body =
        """
        { "disbursementIds": ["%s"] }
        """
            .formatted(d1);

    mockMvc
        .perform(
            post("/api/invoices/" + draftInvoiceId + "/disbursement-lines")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_append_owner")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invoice not editable"))
        .andExpect(jsonPath("$.status").value(400));
  }

  // ==========================================================================
  // Already-BILLED disbursement → 409 ResourceConflictException.
  // ==========================================================================

  @Test
  void append_withAlreadyBilledDisbursement_returnsConflict() throws Exception {
    UUID d1 =
        seedApprovedDisbursement(
            new BigDecimal("100.00"),
            VatTreatment.STANDARD_15,
            DisbursementCategory.COUNSEL_FEES,
            "First fee",
            "Adv A");

    // First invoice bills d1.
    String createBody =
        """
        { "customerId": "%s", "currency": "ZAR", "disbursementIds": ["%s"] }
        """
            .formatted(customerId, d1);
    mockMvc
        .perform(
            post("/api/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_append_owner")))
        .andExpect(status().isCreated());

    // Now try to append the same (BILLED) disbursement to a fresh draft.
    String draftInvoiceId = createEmptyDraftInvoice();
    String body =
        """
        { "disbursementIds": ["%s"] }
        """
            .formatted(d1);

    mockMvc
        .perform(
            post("/api/invoices/" + draftInvoiceId + "/disbursement-lines")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_append_owner")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Disbursement already billed"))
        .andExpect(jsonPath("$.status").value(409));
  }

  // ==========================================================================
  // Empty list → 400 validation error.
  // ==========================================================================

  @Test
  void append_withEmptyList_returnsBadRequest() throws Exception {
    String draftInvoiceId = createEmptyDraftInvoice();
    String body =
        """
        { "disbursementIds": [] }
        """;

    mockMvc
        .perform(
            post("/api/invoices/" + draftInvoiceId + "/disbursement-lines")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_append_owner")))
        .andExpect(status().isBadRequest());
  }

  // ==========================================================================
  // Helpers
  // ==========================================================================

  private String createEmptyDraftInvoice() throws Exception {
    String body =
        """
        { "customerId": "%s", "currency": "ZAR" }
        """
            .formatted(customerId);
    var result =
        mockMvc
            .perform(
                post("/api/invoices")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_append_owner")))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private UUID seedApprovedDisbursement(
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
                          projectId,
                          customerId,
                          category.name(),
                          description,
                          amount,
                          vat.name(),
                          vatAmount,
                          DisbursementPaymentSource.OFFICE_ACCOUNT.name(),
                          null,
                          LocalDate.of(2026, 4, 10),
                          supplier,
                          "REF-APPEND",
                          null,
                          memberIdOwner);
                  d.submitForApproval();
                  d.approve(memberIdOwner, "ok");
                  id[0] = disbursementRepository.saveAndFlush(d).getId();
                }));
    return id[0];
  }

  private void cleanupInvoicesAndDisbursements() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var invoices = invoiceRepository.findAll();
                  for (var invoice : invoices) {
                    var lines =
                        invoiceLineRepository.findByInvoiceIdOrderBySortOrder(invoice.getId());
                    invoiceLineRepository.deleteAll(lines);
                    invoiceRepository.delete(invoice);
                  }
                  disbursementRepository.deleteAll();
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
