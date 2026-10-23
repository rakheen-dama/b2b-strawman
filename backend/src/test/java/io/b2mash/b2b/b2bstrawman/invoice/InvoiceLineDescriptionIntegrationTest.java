package io.b2mash.b2b.b2bstrawman.invoice;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomerWithPrerequisiteFields;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.invoice.dto.CreateInvoiceRequest;
import io.b2mash.b2b.b2bstrawman.invoice.dto.InvoiceResponse;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 88 U15 / G-C10 (architecture §13.3.8): a fee-note line built from a time entry reads {@code
 * "{yyyy-MM-dd} · {fee earner} · {narrative}"} — the entry's own narrative, the task title only as
 * the fallback, capped at 500 chars including a trailing "…" — created through {@link
 * InvoiceCreationService} from real {@link TimeEntry} rows. One line per time entry; {@code
 * invoice_lines.time_entry_id} is the audit link.
 *
 * <p>The ACTIVE customer, matter, client link, task and time entries are saved via repositories
 * (the API creates PROSPECT customers, and time entries need an explicit date, narrative and
 * billing-rate snapshot — beyond what {@code TestEntityHelper} covers).
 *
 * <p>The tenant is provisioned {@code legal-za} so the seeded {@code fee-note-za} template is
 * available: the narrative line is also rendered to a real PDF through the same {@link
 * GeneratedDocumentService} call the invoice-email listener and Generate Document use.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvoiceLineDescriptionIntegrationTest {

  private static final String ORG_ID = "org_inv_line_desc_test";
  private static final String BOB_SUBJECT = "user_line_desc_bob";
  private static final LocalDate DATE = LocalDate.of(2026, 10, 23);
  private static final String PREFIX = "2026-10-23 · Bob Ndlovu · ";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private InvoiceLineRepository invoiceLineRepository;
  @Autowired private InvoiceCreationService invoiceCreationService;
  @Autowired private GeneratedDocumentService generatedDocumentService;

  private String tenantSchema;
  private UUID memberIdBob;
  private UUID customerId;
  private UUID taskId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Line Desc Firm", "legal-za");
    memberIdBob =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, BOB_SUBJECT, "line_desc_bob@test.com", "Bob Ndlovu", "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          createActiveCustomerWithPrerequisiteFields(
                              "Line Desc Client", "line_desc_client@test.com", memberIdBob));
                  customerId = customer.getId();

                  var project = new Project("Line Desc Matter", "Phase 88 U15", memberIdBob);
                  project.setCustomerId(customerId);
                  project = projectRepository.save(project);

                  customerProjectRepository.save(
                      new CustomerProject(customerId, project.getId(), memberIdBob));

                  taskId =
                      taskRepository
                          .save(
                              new Task(
                                  project.getId(),
                                  "Pleadings",
                                  null,
                                  null,
                                  null,
                                  null,
                                  memberIdBob))
                          .getId();
                }));
  }

  @Test
  void narrative_isTheLineDescription_taskTitleNotAppended() {
    UUID teId = seedTimeEntry("Drafting particulars of claim", 120);

    var invoice = createDraft(List.of(teId));

    var line = lineFor(teId);
    assertThat(line.getDescription())
        .isEqualTo("2026-10-23 · Bob Ndlovu · Drafting particulars of claim")
        .doesNotContain("Pleadings")
        .doesNotContain(" -- ");
    assertThat(line.getLineType()).isEqualTo(InvoiceLineType.TIME);
    assertThat(line.getInvoiceId()).isEqualTo(invoice.id());
    assertThat(line.getQuantity()).isEqualByComparingTo("2.0000");
    assertThat(timeEntryInvoiceId(teId)).isEqualTo(invoice.id());
  }

  @Test
  void restCreateDraft_returnsNarrativeLineDescription() throws Exception {
    UUID teId = seedTimeEntry("Consultation with client on settlement offer", 30);

    mockMvc
        .perform(
            post("/api/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "clientId": "%s", "currency": "ZAR", "timeEntryIds": ["%s"] }
                    """
                        .formatted(customerId, teId))
                .with(TestJwtFactory.ownerJwt(ORG_ID, BOB_SUBJECT)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.lines.length()").value(1))
        .andExpect(jsonPath("$.lines[0].timeEntryId").value(teId.toString()))
        .andExpect(
            jsonPath("$.lines[0].description")
                .value("2026-10-23 · Bob Ndlovu · Consultation with client on settlement offer"));
  }

  @Test
  void blankOrNullNarrative_fallsBackToTaskTitle() {
    UUID nullNarrative = seedTimeEntry(null, 60);
    UUID blankNarrative = seedTimeEntry("   ", 60);

    createDraft(List.of(nullNarrative, blankNarrative));

    assertThat(lineFor(nullNarrative).getDescription()).isEqualTo(PREFIX + "Pleadings");
    assertThat(lineFor(blankNarrative).getDescription()).isEqualTo(PREFIX + "Pleadings");
  }

  @Test
  void narrative_isStripped() {
    UUID teId = seedTimeEntry("  Perusal of founding affidavit \n", 45);

    createDraft(List.of(teId));

    assertThat(lineFor(teId).getDescription()).isEqualTo(PREFIX + "Perusal of founding affidavit");
  }

  @Test
  void longNarrative_cappedAt500IncludingEllipsis() {
    String longNarrative = "Drafting heads of argument. ".repeat(30); // 840 chars
    String exactly500 = "y".repeat(500);
    UUID longId = seedTimeEntry(longNarrative, 60);
    UUID boundaryId = seedTimeEntry(exactly500, 60);

    createDraft(List.of(longId, boundaryId));

    String desc = lineFor(longId).getDescription();
    assertThat(desc).startsWith(PREFIX);
    String narrativePart = desc.substring(PREFIX.length());
    assertThat(narrativePart).hasSize(500).endsWith("…");
    assertThat(narrativePart.substring(0, 499)).isEqualTo(longNarrative.substring(0, 499));

    // Boundary: a narrative of exactly 500 chars is not truncated.
    assertThat(lineFor(boundaryId).getDescription()).isEqualTo(PREFIX + exactly500);
  }

  @Test
  void longNarrative_neverSplitsASurrogatePair() {
    // 498 ASCII chars then emoji (a surrogate pair at indices 498-499) and more text: keeping 499
    // chars would cut the pair in half, so only 498 chars are kept before the ellipsis.
    String narrative = "z".repeat(498) + "📝" + " and more narrative text";
    UUID teId = seedTimeEntry(narrative, 60);

    createDraft(List.of(teId));

    String narrativePart = lineFor(teId).getDescription().substring(PREFIX.length());
    assertThat(narrativePart).isEqualTo("z".repeat(498) + "…");
  }

  @Test
  void oneLinePerTimeEntry_eachLinkedByTimeEntryId() {
    UUID te1 = seedTimeEntry("Drafting particulars of claim", 60);
    UUID te2 = seedTimeEntry("Telephone call with opposing attorney", 15);
    UUID te3 = seedTimeEntry(null, 30);

    var invoice = createDraft(List.of(te1, te2, te3));

    assertThat(invoice.lines()).hasSize(3);
    assertThat(invoice.lines())
        .extracting(l -> l.timeEntryId())
        .containsExactlyInAnyOrder(te1, te2, te3);
    assertThat(lineFor(te1).getDescription()).isEqualTo(PREFIX + "Drafting particulars of claim");
    assertThat(lineFor(te2).getDescription())
        .isEqualTo(PREFIX + "Telephone call with opposing attorney");
    assertThat(lineFor(te3).getDescription()).isEqualTo(PREFIX + "Pleadings");
  }

  /**
   * 622A regression: the line text is HTML-escaped by {@code HtmlUtils.htmlEscape}, which turns the
   * "·" separator into {@code &middot;} and "é" into {@code &eacute;}. OpenHTMLToPDF's XML parser
   * only knows the five XML entities, so before the sanitizer fix the fee-note PDF render threw
   * ("The entity "middot" was referenced, but not declared") and Send produced no PDF / no email.
   */
  @Test
  void feeNotePdf_rendersNarrativeLineWithMiddotAndNonAsciiChars() throws IOException {
    UUID teId = seedTimeEntry("Consultation with José", 60);
    var invoice = createDraft(List.of(teId));

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
                              template.getId(), invoice.id(), false, true, List.of(), memberIdBob)
                          .pdfResult()
                          .pdfBytes());
                }));

    assertThat(pdf.get()).isNotEmpty();
    assertThat(new String(pdf.get(), 0, 4)).isEqualTo("%PDF");
    String text;
    try (var doc = Loader.loadPDF(pdf.get())) {
      text = new PDFTextStripper().getText(doc).replaceAll("\\s+", " ");
    }
    assertThat(text).contains(PREFIX + "Consultation with José");
  }

  // --- helpers ---

  private InvoiceResponse createDraft(List<UUID> timeEntryIds) {
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
                    memberIdBob)));
    return holder.get();
  }

  private InvoiceLine lineFor(UUID timeEntryId) {
    var holder = new AtomicReference<InvoiceLine>();
    runInTenant(
        () ->
            holder.set(
                transactionTemplate.execute(
                    tx -> invoiceLineRepository.findByTimeEntryId(timeEntryId).orElseThrow())));
    return holder.get();
  }

  private UUID timeEntryInvoiceId(UUID timeEntryId) {
    var holder = new AtomicReference<UUID>();
    runInTenant(
        () ->
            holder.set(
                transactionTemplate.execute(
                    tx -> timeEntryRepository.findById(timeEntryId).orElseThrow().getInvoiceId())));
    return holder.get();
  }

  private UUID seedTimeEntry(String narrative, int minutes) {
    var holder = new AtomicReference<UUID>();
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var te = new TimeEntry(taskId, memberIdBob, DATE, minutes, true, null, narrative);
                  te.snapshotBillingRate(new BigDecimal("1500.00"), "ZAR");
                  holder.set(timeEntryRepository.save(te).getId());
                }));
    return holder.get();
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdBob)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
