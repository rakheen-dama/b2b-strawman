package io.b2mash.b2b.b2bstrawman.billingrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.BillingRunResponse;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos.CreateBillingRunRequest;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLine;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineType;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementCategory;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementPaymentSource;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.LegalDisbursement;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.VatTreatment;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 86 Epic 598B replacement-path test: with the Phase 17 retainer leg removed (no {@code
 * RetainerBillingService}, no {@code includeRetainers} flag, no {@code /retainer-preview} or {@code
 * /retainer-generate} endpoints), a legal-za firm's ordinary time-and-disbursement billing run
 * still runs end to end — create, preview, generate, approve — and produces an invoice carrying
 * both {@link InvoiceLineType#TIME} and {@link InvoiceLineType#DISBURSEMENT} lines.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BillingRunWithoutRetainersIntegrationTest {

  private static final String ORG_ID = "org_br_without_retainers";
  private static final String OWNER = "user_brwr_owner";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private DisbursementRepository disbursementRepository;
  @Autowired private BillingRunItemRepository billingRunItemRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private InvoiceLineRepository invoiceLineRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private UUID disbursementId;
  private String billingRunId;

  @BeforeAll
  void setUp() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Billing Run Without Retainers Org", "legal-za")
            .schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "brwr_owner@test.com", "BRWR Owner", "owner"));

    inTenant(
        () ->
            transactionTemplate.execute(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomerWithPrerequisiteFields(
                              "Thandi Estate Matter", "thandi-estate@test.com", memberId));
                  customerId = customer.getId();

                  var project = new Project("EST-2026-001", "Thandi deceased estate", memberId);
                  project.setCustomerId(customerId);
                  project = projectRepository.save(project);
                  customerProjectRepository.save(
                      new CustomerProject(customerId, project.getId(), memberId));

                  var task =
                      taskRepository.save(
                          new Task(
                              project.getId(),
                              "Draft liquidation account",
                              null,
                              "MEDIUM",
                              "TASK",
                              null,
                              memberId));

                  var timeEntry =
                      new TimeEntry(
                          task.getId(),
                          memberId,
                          LocalDate.of(2026, 5, 12),
                          90,
                          true,
                          null,
                          "Liquidation account drafting");
                  timeEntry.snapshotBillingRate(new BigDecimal("1800.00"), "ZAR");
                  timeEntryRepository.save(timeEntry);

                  var disbursement =
                      new LegalDisbursement(
                          project.getId(),
                          customerId,
                          DisbursementCategory.SHERIFF_FEES.name(),
                          "Sheriff service on executor",
                          new BigDecimal("850.00"),
                          VatTreatment.ZERO_RATED_PASS_THROUGH.name(),
                          BigDecimal.ZERO,
                          DisbursementPaymentSource.OFFICE_ACCOUNT.name(),
                          null,
                          LocalDate.of(2026, 5, 14),
                          "Sheriff Pretoria",
                          "REF-PTA-598",
                          null,
                          memberId);
                  disbursement.submitForApproval();
                  disbursement.approve(memberId, "ok");
                  disbursementId = disbursementRepository.saveAndFlush(disbursement).getId();
                  return null;
                }));
  }

  @Test
  @Order(1)
  void timeAndDisbursementBillingRunSurvivesWithoutRetainers() throws Exception {
    // Create — the request carries no includeRetainers, and the response exposes none.
    var createResult =
        mockMvc
            .perform(
                post("/api/billing-runs")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "May 2026 fee notes",
                          "periodFrom": "2026-05-01",
                          "periodTo": "2026-05-31",
                          "currency": "ZAR",
                          "includeExpenses": true
                        }
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PREVIEW"))
            .andExpect(jsonPath("$.includeExpenses").value(true))
            .andExpect(jsonPath("$.includeRetainers").doesNotExist())
            .andReturn();
    billingRunId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Preview — id-scoped lookup of this test's customer (never items[0] on a shared tenant).
    var previewResult =
        mockMvc
            .perform(
                post("/api/billing-runs/" + billingRunId + "/preview")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isOk())
            .andReturn();
    List<String> customerItemIds =
        JsonPath.read(
            previewResult.getResponse().getContentAsString(),
            "$.items[?(@.clientId == '" + customerId + "')].id");
    assertThat(customerItemIds).as("preview must include the seeded customer").hasSize(1);

    // Generate.
    mockMvc
        .perform(
            post("/api/billing-runs/" + billingRunId + "/generate")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.totalFailed").value(0));

    // Approve.
    mockMvc
        .perform(
            post("/api/billing-runs/" + billingRunId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.failureCount").value(0));

    inTenant(
        () ->
            transactionTemplate.execute(
                tx -> {
                  var item =
                      billingRunItemRepository
                          .findByBillingRunId(UUID.fromString(billingRunId))
                          .stream()
                          .filter(i -> customerId.equals(i.getCustomerId()))
                          .findFirst()
                          .orElseThrow();
                  assertThat(item.getInvoiceId()).isNotNull();

                  var invoice = invoiceRepository.findById(item.getInvoiceId()).orElseThrow();
                  assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.APPROVED);

                  var lines =
                      invoiceLineRepository.findByInvoiceIdOrderBySortOrder(invoice.getId());
                  assertThat(lines)
                      .extracting(InvoiceLine::getLineType)
                      .contains(InvoiceLineType.TIME, InvoiceLineType.DISBURSEMENT)
                      .allMatch(
                          t -> t == InvoiceLineType.TIME || t == InvoiceLineType.DISBURSEMENT);
                  assertThat(lines)
                      .filteredOn(l -> l.getLineType() == InvoiceLineType.DISBURSEMENT)
                      .extracting(InvoiceLine::getDisbursementId)
                      .containsExactly(disbursementId);
                  return null;
                }));
  }

  @Test
  @Order(2)
  void retainerBillingEndpointsAreGone() throws Exception {
    // Were BillingRunController @GetMapping("/{id}/retainer-preview") and
    // @PostMapping("/{id}/retainer-generate"). Unmapped → 404, not 403.
    String runId = billingRunId != null ? billingRunId : UUID.randomUUID().toString();
    mockMvc
        .perform(
            get("/api/billing-runs/" + runId + "/retainer-preview")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post("/api/billing-runs/" + runId + "/retainer-generate")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"retainerAgreementIds\":[\"" + UUID.randomUUID() + "\"]}"))
        .andExpect(status().isNotFound());
  }

  @Test
  @Order(3)
  void retainerPackageIsAbsentFromTheClasspath() {
    var classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.b2mash.b2b.b2bstrawman");
    // Non-vacuous: the importer must actually see production classes (the billingrun package).
    assertThat(classes.stream().map(c -> c.getPackageName()))
        .contains("io.b2mash.b2b.b2bstrawman.billingrun")
        .noneMatch(
            p ->
                p.equals("io.b2mash.b2b.b2bstrawman.retainer")
                    || p.startsWith("io.b2mash.b2b.b2bstrawman.retainer."));
    assertThatThrownBy(
            () -> Class.forName("io.b2mash.b2b.b2bstrawman.billingrun.RetainerBillingService"))
        .isInstanceOf(ClassNotFoundException.class);
    assertThatThrownBy(() -> Class.forName("io.b2mash.b2b.b2bstrawman.retainer.RetainerAgreement"))
        .isInstanceOf(ClassNotFoundException.class);

    // DTO contract: neither the create request nor the response carries includeRetainers.
    assertThat(componentNames(CreateBillingRunRequest.class))
        .contains("includeExpenses")
        .doesNotContain("includeRetainers");
    assertThat(componentNames(BillingRunResponse.class))
        .contains("includeExpenses")
        .doesNotContain("includeRetainers");
  }

  private static List<String> componentNames(Class<? extends Record> recordType) {
    return Arrays.stream(recordType.getRecordComponents()).map(RecordComponent::getName).toList();
  }

  private <T> T inTenant(Supplier<T> action) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .call(action::get);
  }
}
