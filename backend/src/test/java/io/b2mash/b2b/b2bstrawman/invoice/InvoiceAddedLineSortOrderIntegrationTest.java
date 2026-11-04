package io.b2mash.b2b.b2bstrawman.invoice;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomerWithPrerequisiteFields;
import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.invoice.dto.AddLineItemRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.CreateInvoiceRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.InvoiceLineResponse;
import io.b2mash.b2b.b2bstrawman.invoice.dto.InvoiceResponse;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.tariff.TariffItem;
import io.b2mash.b2b.b2bstrawman.verticals.legal.tariff.TariffItemRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.tariff.TariffSchedule;
import io.b2mash.b2b.b2bstrawman.verticals.legal.tariff.TariffScheduleRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
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
 * G88-M10: a tariff or manual line added to a draft fee note without an explicit {@code sortOrder}
 * is appended after the lines already on it (max + 1, the disbursement-append rule), instead of
 * being stored at 0 and tying with the first time line. Every reader orders lines by {@code
 * InvoiceLineRepository.findByInvoiceIdOrderBySortOrder} (no tie-breaker): the API response, the
 * fee-note PDF ({@code InvoiceContextBuilder}), the portal sync and the accounting sync.
 *
 * <p>Time lines come from real {@link TimeEntry} rows through {@link
 * InvoiceCreationService#createDraft}; the tenant is {@code legal-za} so {@code lssa_tariff} is on
 * and the seeded fee-note template renders the PDF.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvoiceAddedLineSortOrderIntegrationTest {

  private static final String ORG_ID = "org_inv_added_line_sort_test";
  private static final LocalDate DATE = LocalDate.of(2026, 10, 23);
  private static final String TARIFF_DESCRIPTION = "Sort order tariff letter";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private InvoiceLineRepository invoiceLineRepository;
  @Autowired private InvoiceCreationService invoiceCreationService;
  @Autowired private TariffScheduleRepository tariffScheduleRepository;
  @Autowired private TariffItemRepository tariffItemRepository;
  @Autowired private GeneratedDocumentService generatedDocumentService;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private UUID taskId;
  private UUID tariffItemId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Added Line Sort Firm", "legal-za");
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_added_line_sort",
                "added_line_sort@test.com",
                "Thandi Sort",
                "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          createActiveCustomerWithPrerequisiteFields(
                              "Added Line Sort Client",
                              "added_line_sort_client@test.com",
                              memberId));
                  customerId = customer.getId();

                  var project = new Project("Added Line Sort Matter", "G88-M10", memberId);
                  project.setCustomerId(customerId);
                  project = projectRepository.save(project);
                  customerProjectRepository.save(
                      new CustomerProject(customerId, project.getId(), memberId));

                  taskId =
                      taskRepository
                          .save(
                              new Task(
                                  project.getId(), "Pleadings", null, null, null, null, memberId))
                          .getId();

                  var schedule =
                      tariffScheduleRepository.save(
                          new TariffSchedule(
                              "Added Line Sort Schedule",
                              "PARTY_AND_PARTY",
                              "HIGH_COURT",
                              LocalDate.of(2024, 4, 1),
                              null,
                              "Test Source"));
                  tariffItemId =
                      tariffItemRepository
                          .save(
                              new TariffItem(
                                  schedule,
                                  "3(a)",
                                  "Letters",
                                  TARIFF_DESCRIPTION,
                                  new BigDecimal("250.00"),
                                  "PER_ITEM",
                                  null,
                                  1))
                          .getId();
                }));
  }

  @Test
  void tariffLineAddedAfterTimeLines_sortsLast_inResponseAndFeeNotePdf() throws IOException {
    var draft = draftWithTimeLines("Drafting the summons", "Consulting the client");

    var response = addLine(draft.id(), tariffRequest());

    var tariffLine = response.lines().getLast();
    assertThat(tariffLine.lineType()).isEqualTo(InvoiceLineType.TARIFF);
    assertThat(tariffLine.sortOrder()).isEqualTo(2);
    assertThat(storedLineTypesInOrder(draft.id()))
        .containsExactly(InvoiceLineType.TIME, InvoiceLineType.TIME, InvoiceLineType.TARIFF);

    String pdfText = renderFeeNotePdfText(draft.id());
    assertThat(pdfText)
        .contains("Drafting the summons", "Consulting the client", TARIFF_DESCRIPTION);
    assertThat(pdfText.indexOf(TARIFF_DESCRIPTION))
        .isGreaterThan(pdfText.indexOf("Drafting the summons"))
        .isGreaterThan(pdfText.indexOf("Consulting the client"));
  }

  @Test
  void manualLineAddedAfterTimeLines_sortsLast() {
    var draft = draftWithTimeLines("Perusing the plea", "Preparing the bundle");

    var response = addLine(draft.id(), manualRequest("Sheriff's fee"));

    var manualLine = response.lines().getLast();
    assertThat(manualLine.lineType()).isEqualTo(InvoiceLineType.MANUAL);
    assertThat(manualLine.sortOrder()).isEqualTo(2);
    assertThat(storedLineTypesInOrder(draft.id()))
        .containsExactly(InvoiceLineType.TIME, InvoiceLineType.TIME, InvoiceLineType.MANUAL);
  }

  @Test
  void successiveAddedLines_keepTheOrderTheyWereAdded() {
    var draft = draftWithTimeLines("Attending the hearing");

    addLine(draft.id(), tariffRequest());
    var response = addLine(draft.id(), manualRequest("Courier"));

    assertThat(response.lines())
        .extracting(InvoiceLineResponse::sortOrder)
        .containsExactly(0, 1, 2);
    assertThat(storedLineTypesInOrder(draft.id()))
        .containsExactly(InvoiceLineType.TIME, InvoiceLineType.TARIFF, InvoiceLineType.MANUAL);
  }

  @Test
  void firstLineOnAnEmptyDraft_getsSortOrderZero() {
    var invoiceId = new AtomicReference<UUID>();
    runInTenant(
        () ->
            invoiceId.set(
                invoiceRepository
                    .save(
                        new Invoice(
                            customerId,
                            "ZAR",
                            "Added Line Sort Client",
                            "added_line_sort_client@test.com",
                            null,
                            "Added Line Sort Firm",
                            memberId))
                    .getId()));

    var response = addLine(invoiceId.get(), tariffRequest());

    assertThat(response.lines()).singleElement().extracting(l -> l.sortOrder()).isEqualTo(0);
  }

  // --- helpers ---

  private AddLineItemRequest tariffRequest() {
    return new AddLineItemRequest(null, null, BigDecimal.ONE, null, null, null, tariffItemId);
  }

  private AddLineItemRequest manualRequest(String description) {
    return new AddLineItemRequest(
        null, description, BigDecimal.ONE, new BigDecimal("100.00"), null, null, null);
  }

  private InvoiceResponse draftWithTimeLines(String... narratives) {
    var timeEntryIds = Arrays.stream(narratives).map(this::seedTimeEntry).toList();
    var holder = new AtomicReference<InvoiceResponse>();
    runInTenant(
        () ->
            holder.set(
                invoiceCreationService.createDraft(
                    new CreateInvoiceRequest(
                        customerId,
                        "ZAR",
                        timeEntryIds,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                    memberId)));
    assertThat(holder.get().lines())
        .extracting(InvoiceLineResponse::lineType)
        .containsOnly(InvoiceLineType.TIME)
        .hasSize(narratives.length);
    return holder.get();
  }

  private InvoiceResponse addLine(UUID invoiceId, AddLineItemRequest request) {
    var holder = new AtomicReference<InvoiceResponse>();
    runInTenant(() -> holder.set(invoiceCreationService.addLineItem(invoiceId, request)));
    return holder.get();
  }

  /** Line types in the order the shared reader query returns them. */
  private List<InvoiceLineType> storedLineTypesInOrder(UUID invoiceId) {
    var holder = new AtomicReference<List<InvoiceLineType>>();
    runInTenant(
        () ->
            holder.set(
                transactionTemplate.execute(
                    tx ->
                        invoiceLineRepository.findByInvoiceIdOrderBySortOrder(invoiceId).stream()
                            .map(InvoiceLine::getLineType)
                            .toList())));
    return holder.get();
  }

  private String renderFeeNotePdfText(UUID invoiceId) throws IOException {
    var pdf = new AtomicReference<byte[]>();
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      generatedDocumentService.resolveDefaultInvoiceTemplate().orElseThrow();
                  pdf.set(
                      generatedDocumentService
                          .generateDocument(
                              template.getId(), invoiceId, false, true, List.of(), memberId)
                          .pdfResult()
                          .pdfBytes());
                }));
    try (var doc = Loader.loadPDF(pdf.get())) {
      return new PDFTextStripper().getText(doc).replaceAll("\\s+", " ");
    }
  }

  private UUID seedTimeEntry(String narrative) {
    var holder = new AtomicReference<UUID>();
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var te = new TimeEntry(taskId, memberId, DATE, 60, true, null, narrative);
                  te.snapshotBillingRate(new BigDecimal("1500.00"), "ZAR");
                  holder.set(timeEntryRepository.save(te).getId());
                }));
    return holder.get();
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
