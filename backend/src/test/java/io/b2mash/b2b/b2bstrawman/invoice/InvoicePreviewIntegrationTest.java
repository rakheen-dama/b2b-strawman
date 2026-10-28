package io.b2mash.b2b.b2bstrawman.invoice;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.PdfRenderingService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
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
 * {@code GET /api/invoices/{id}/preview} — Phase 88 626E (A6, one renderer): the preview is the
 * fee-note Tiptap template the client's emailed PDF uses ({@code
 * GeneratedDocumentService.resolveDefaultInvoiceTemplate}), rendered through the same pipeline. The
 * Thymeleaf {@code invoice-preview.html} path is gone.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvoicePreviewIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_inv_preview_test";
  private static final String ORG_ID_B = "org_inv_preview_test_b";
  private static final String ORG_ID_NO_TPL = "org_inv_preview_no_tpl";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private InvoiceLineRepository invoiceLineRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private PdfRenderingService pdfRenderingService;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID customerId;
  private UUID projectId;
  private UUID invoiceId;
  private UUID noTemplateInvoiceId;

  @BeforeAll
  void setUp() throws Exception {
    // 626E (OD-12): legal-za — the product is legal-only; the fee note is the legal-za pack's.
    provisioningService.provisionTenant(ORG_ID, "Preview Test Org", "legal-za");

    memberIdOwner =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_inv_preview_owner",
                "inv_preview_owner@test.com",
                "Preview Owner",
                "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create test data within tenant scope
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      // Customer
                      var customer =
                          createActiveCustomer("Preview Corp", "preview@test.com", memberIdOwner);
                      customer = customerRepository.save(customer);
                      customerId = customer.getId();

                      // Project
                      var project =
                          new Project("Website Redesign", "Redesign the website", memberIdOwner);
                      project = projectRepository.save(project);
                      projectId = project.getId();

                      // Link project to customer
                      customerProjectRepository.save(
                          new CustomerProject(customerId, projectId, memberIdOwner));

                      // Task
                      var task =
                          new Task(
                              projectId,
                              "Backend API dev",
                              "Build REST API endpoints",
                              "HIGH",
                              "TASK",
                              null,
                              memberIdOwner);
                      task = taskRepository.save(task);

                      // Billable time entry
                      var te =
                          new TimeEntry(
                              task.getId(),
                              memberIdOwner,
                              LocalDate.of(2025, 1, 20),
                              150,
                              true,
                              null,
                              "API development");
                      te.snapshotBillingRate(new BigDecimal("1800.00"), "ZAR");
                      te = timeEntryRepository.save(te);

                      // Create invoice with time entry via API is complex -- create directly
                      var invoice =
                          new Invoice(
                              customerId,
                              "ZAR",
                              "Preview Corp",
                              "preview@test.com",
                              "123 Test Street, Cape Town",
                              "Preview Test Org",
                              memberIdOwner);
                      invoice.updateDraft(
                          LocalDate.of(2025, 2, 28),
                          "January 2025 services",
                          "Net 30",
                          new BigDecimal("675.00"));
                      invoice = invoiceRepository.save(invoice);
                      invoiceId = invoice.getId();

                      // Line item linked to project
                      var line1 =
                          new InvoiceLine(
                              invoice.getId(),
                              projectId,
                              te.getId(),
                              "Backend API dev -- 2025-01-20 -- Preview Owner",
                              new BigDecimal("2.5000"),
                              new BigDecimal("1800.00"),
                              0);
                      invoiceLineRepository.save(line1);

                      // Manual line item without project (Other Items)
                      var line2 =
                          new InvoiceLine(
                              invoice.getId(),
                              null,
                              null,
                              "Project setup fee",
                              new BigDecimal("1.0000"),
                              new BigDecimal("5000.00"),
                              1);
                      invoiceLineRepository.save(line2);

                      // Recalculate totals
                      BigDecimal subtotal = line1.getAmount().add(line2.getAmount());
                      invoice.recalculateTotals(subtotal, false, BigDecimal.ZERO, false);
                      invoiceRepository.save(invoice);
                    }));

    // --- Tenant B (for cross-tenant isolation test) ---
    provisioningService.provisionTenant(ORG_ID_B, "Preview Test Org B", null);
    TestMemberHelper.syncMember(
        mockMvc,
        ORG_ID_B,
        "user_inv_preview_owner_b",
        "inv_preview_owner_b@test.com",
        "Preview Owner B",
        "owner");

    // --- Tenant with no fee-note template (pack row missing) → preview 409 ---
    provisioningService.provisionTenant(ORG_ID_NO_TPL, "Preview No Template Org", "legal-za");
    UUID noTplOwner =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID_NO_TPL,
                "user_inv_preview_no_tpl_owner",
                "inv_preview_no_tpl_owner@test.com",
                "No Template Owner",
                "owner"));
    String noTplSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID_NO_TPL).orElseThrow().getSchemaName();
    ScopedValue.where(RequestScopes.TENANT_ID, noTplSchema)
        .where(RequestScopes.ORG_ID, ORG_ID_NO_TPL)
        .where(RequestScopes.MEMBER_ID, noTplOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      documentTemplateRepository
                          .findPackSeeded("legal-za", "fee-note-za")
                          .forEach(documentTemplateRepository::delete);
                      var customer =
                          customerRepository.save(
                              createActiveCustomer("No Tpl Corp", "no_tpl@test.com", noTplOwner));
                      var invoice =
                          invoiceRepository.save(
                              new Invoice(
                                  customer.getId(),
                                  "ZAR",
                                  "No Tpl Corp",
                                  "no_tpl@test.com",
                                  null,
                                  "Preview No Template Org",
                                  noTplOwner));
                      noTemplateInvoiceId = invoice.getId();
                    }));
  }

  private String previewBody() throws Exception {
    return mockMvc
        .perform(
            get("/api/invoices/" + invoiceId + "/preview")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_preview_owner")))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
  }

  private <T> T inTenant(java.util.function.Supplier<T> action) {
    Object[] holder = new Object[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(() -> holder[0] = action.get());
    @SuppressWarnings("unchecked")
    T value = (T) holder[0];
    return value;
  }

  @Test
  void shouldReturnHtmlWithCorrectContentType() throws Exception {
    mockMvc
        .perform(
            get("/api/invoices/" + invoiceId + "/preview")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_preview_owner")))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(content().string(startsWith("<!DOCTYPE html>")))
        .andExpect(content().string(containsString("<h2>FEE NOTE</h2>")));
  }

  @Test
  void shouldIncludeFirmClientAndDraftBanner() throws Exception {
    mockMvc
        .perform(
            get("/api/invoices/" + invoiceId + "/preview")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_preview_owner")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<h1>Preview Test Org</h1>")))
        .andExpect(content().string(containsString("<strong>To:</strong> Preview Corp")))
        // OD-11 a: a DRAFT shows the banner; the number / issue date / payment reference
        // lines are gated on their values, so no empty "Fee Note Number:" label.
        .andExpect(content().string(containsString("DRAFT &mdash; not yet issued")))
        .andExpect(content().string(not(containsString("Fee Note Number:"))))
        .andExpect(content().string(not(containsString("Issue Date:"))))
        .andExpect(content().string(not(containsString("Payment reference:"))));
  }

  @Test
  void shouldIncludeLineItems() throws Exception {
    mockMvc
        .perform(
            get("/api/invoices/" + invoiceId + "/preview")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_preview_owner")))
        .andExpect(status().isOk())
        .andExpect(
            content().string(containsString("Backend API dev -- 2025-01-20 -- Preview Owner")))
        .andExpect(content().string(containsString("Project setup fee")));
  }

  @Test
  void shouldIncludeTotalsFormattedAsCurrency() throws Exception {
    // 2.5 × 1 800 + 1 × 5 000 = 9 500; manual tax 675 → 10 175 (JDK en-ZA, NBSP grouping).
    mockMvc
        .perform(
            get("/api/invoices/" + invoiceId + "/preview")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_preview_owner")))
        .andExpect(status().isOk())
        .andExpect(
            content().string(containsString("Subtotal (excl. VAT):</strong> R9&nbsp;500,00")))
        .andExpect(content().string(containsString("VAT:</strong> R675,00")))
        .andExpect(content().string(containsString("Total Due:</strong> R10&nbsp;175,00")))
        .andExpect(content().string(not(containsString("9500.00"))));
  }

  @Test
  void previewHtml_equalsTheSentPdfHtml() throws Exception {
    String body = previewBody();

    String sentHtml =
        inTenant(
            () -> {
              var feeNote =
                  documentTemplateRepository.findPackSeeded("legal-za", "fee-note-za").getFirst();
              return pdfRenderingService
                  .generatePdf(feeNote.getId(), invoiceId, memberIdOwner)
                  .htmlPreview();
            });

    assertThat(body).as("preview == the emailed fee-note PDF's HTML (A6)").isEqualTo(sentHtml);
  }

  @Test
  void previewHtml_rendersToPdf_withFormattedAmounts() throws Exception {
    String body = previewBody();

    byte[] pdf = pdfRenderingService.htmlToPdf(body);
    String text;
    try (var doc = Loader.loadPDF(pdf)) {
      text = new PDFTextStripper().getText(doc).replaceAll("[\\s\\u00a0]+", " ");
    }

    assertThat(text)
        .contains("FEE NOTE")
        .contains("DRAFT \u2014 not yet issued")
        .contains("R10 175,00")
        .contains("Project setup fee");
  }

  @Test
  void shouldReturn409WhenNoFeeNoteTemplate() throws Exception {
    mockMvc
        .perform(
            get("/api/invoices/" + noTemplateInvoiceId + "/preview")
                .with(TestJwtFactory.ownerJwt(ORG_ID_NO_TPL, "user_inv_preview_no_tpl_owner")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.title").value("No fee note template"))
        .andExpect(
            jsonPath("$.detail")
                .value(containsString("Settings \u203a Documents \u203a Templates")))
        .andExpect(jsonPath("$.code").value("invoice.preview.no_template"));
  }

  @Test
  void shouldReturn403ForMemberRole() throws Exception {
    mockMvc
        .perform(
            get("/api/invoices/" + invoiceId + "/preview")
                .with(TestJwtFactory.jwtAs(ORG_ID, "user_inv_preview_member", "member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldReturn404ForNonExistentInvoice() throws Exception {
    mockMvc
        .perform(
            get("/api/invoices/" + UUID.randomUUID() + "/preview")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_preview_owner")))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldReturn404WhenAccessingPreviewFromDifferentTenant() throws Exception {
    // Invoice was created in tenant A — accessing from tenant B's admin should return 404
    mockMvc
        .perform(
            get("/api/invoices/" + invoiceId + "/preview")
                .with(TestJwtFactory.ownerJwt(ORG_ID_B, "user_inv_preview_owner_b")))
        .andExpect(status().isNotFound());
  }
}
