package io.b2mash.b2b.b2bstrawman.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.integration.storage.PresignedUrl;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactService;
import io.b2mash.b2b.b2bstrawman.portal.PortalJwtService;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplateCategory;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reproduction + regression test for OBS-3001.
 *
 * <p>When a fee note (invoice) is sent, the cover-letter PDF must be persisted via the canonical
 * {@code GeneratedDocumentService} pipeline (S3 + a {@code generated_documents} row), not rendered
 * ephemerally for the email attachment only. Without a persisted {@code GeneratedDocument}, the
 * portal fee-note download endpoint ({@code GET /portal/invoices/{id}/download}) finds nothing and
 * returns 404 ("Download failed").
 *
 * <p>Pre-fix this test fails: no GeneratedDocument is persisted on send and the portal download
 * returns 404. Post-fix the GeneratedDocument is persisted and the portal download resolves to a
 * presigned URL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvoiceSentDocumentPersistenceIntegrationTest {

  private static final String ORG_ID = "org_inv_doc_persist_test";
  private static final Map<String, Object> TEMPLATE_CONTENT =
      Map.of(
          "type",
          "doc",
          "content",
          List.of(
              Map.of(
                  "type",
                  "paragraph",
                  "content",
                  List.of(Map.of("type", "text", "text", "Fee note for {{customer.name}}")))));

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private GeneratedDocumentRepository generatedDocumentRepository;
  @Autowired private PortalContactService portalContactService;
  @Autowired private PortalJwtService portalJwtService;
  @Autowired private TransactionTemplate transactionTemplate;

  // Storage is mocked so PDF upload + presigned URL generation succeed deterministically without
  // LocalStack. The persistence assertion (a generated_documents row exists) is the load-bearing
  // check; the presigned-URL stub just lets the portal download endpoint return 200.
  @MockitoBean private StorageService storageService;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private UUID projectId;
  private UUID taskId;
  private String portalToken;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Invoice Doc Persist Org", null);

    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_inv_doc_owner",
                "inv_doc_owner@test.com",
                "Doc Owner",
                "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      Customer customer =
                          TestCustomerFactory.createActiveCustomerWithPrerequisiteFields(
                              "Fee Note Client", "feenote-client@test.com", memberId);
                      customer = customerRepository.save(customer);
                      customerId = customer.getId();

                      Project project =
                          new Project("Fee Note Matter", "Matter for fee note", memberId);
                      project = projectRepository.save(project);
                      projectId = project.getId();

                      customerProjectRepository.save(
                          new CustomerProject(customerId, projectId, memberId));

                      Task task =
                          new Task(
                              projectId,
                              "Billable work",
                              "Work to bill",
                              "MEDIUM",
                              "TASK",
                              null,
                              memberId);
                      task = taskRepository.save(task);
                      taskId = task.getId();

                      // An active INVOICE document template must exist for the send-path listener
                      // to render + persist the fee-note PDF. (Only the accounting-za vertical
                      // auto-seeds one at provision time; this org has no vertical.)
                      var template =
                          new DocumentTemplate(
                              TemplateEntityType.INVOICE,
                              "Fee Note Cover Letter",
                              "fee-note-cover-letter",
                              TemplateCategory.COVER_LETTER,
                              TEMPLATE_CONTENT);
                      documentTemplateRepository.save(template);
                    }));

    // Portal contact for the client so the portal token resolves to this customer.
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () ->
                portalContactService.createContact(
                    ORG_ID,
                    customerId,
                    "feenote-portal@test.com",
                    "Fee Note Contact",
                    PortalContact.ContactRole.PRIMARY));
    portalToken = portalJwtService.issueToken(customerId, ORG_ID);

    when(storageService.generateDownloadUrl(any(String.class), any()))
        .thenReturn(
            new PresignedUrl(
                "https://s3.example.com/fee-note.pdf", Instant.now().plusSeconds(3600)));
  }

  @Test
  void sendingFeeNote_persistsGeneratedDocument_andPortalDownloadResolves() throws Exception {
    var jwt = TestJwtFactory.ownerJwt(ORG_ID, "user_inv_doc_owner");
    UUID timeEntryId = createBillableTimeEntry();

    // Create the fee note.
    var createResult =
        mockMvc
            .perform(
                post("/api/invoices")
                    .with(jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "customerId": "%s",
                          "currency": "ZAR",
                          "timeEntryIds": ["%s"]
                        }
                        """
                            .formatted(customerId, timeEntryId)))
            .andExpect(status().isCreated())
            .andReturn();
    UUID invoiceId =
        UUID.fromString(JsonPath.read(createResult.getResponse().getContentAsString(), "$.id"));

    // Before send: no GeneratedDocument exists for this fee note, so the portal download 404s.
    assertGeneratedInvoiceDocCount(invoiceId, 0);
    mockMvc
        .perform(
            get("/portal/invoices/{id}/download", invoiceId)
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isNotFound());

    // Approve, then send — sending publishes InvoiceSentEvent (AFTER_COMMIT) which the
    // InvoiceEmailEventListener handles synchronously on the request thread.
    mockMvc
        .perform(post("/api/invoices/" + invoiceId + "/approve").with(jwt))
        .andExpect(status().isOk());
    mockMvc
        .perform(post("/api/invoices/" + invoiceId + "/send").with(jwt))
        .andExpect(status().isOk());

    // OBS-3001: after send, the fee-note PDF must be persisted as a GeneratedDocument so the
    // portal download resolves to a presigned URL rather than 404 ("Download failed").
    // The download endpoint is the load-bearing, user-facing assertion: it runs in its own
    // HTTP request / transaction and reads the committed GeneratedDocument row.
    // (Pre-fix: this returns 404 because no GeneratedDocument is ever persisted on send.)
    mockMvc
        .perform(
            get("/portal/invoices/{id}/download", invoiceId)
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.downloadUrl").value("https://s3.example.com/fee-note.pdf"));

    // Belt-and-braces: exactly one GeneratedDocument was persisted for the fee note (re-send
    // idempotency is out of scope — each send renders the current state and the download
    // resolves the most recent).
    assertGeneratedInvoiceDocCount(invoiceId, 1);
  }

  private void assertGeneratedInvoiceDocCount(UUID invoiceId, int expected) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var docs =
                  generatedDocumentRepository
                      .findByPrimaryEntityTypeAndPrimaryEntityIdOrderByGeneratedAtDesc(
                          TemplateEntityType.INVOICE, invoiceId);
              assertThat(docs).hasSize(expected);
            });
  }

  private UUID createBillableTimeEntry() {
    var holder = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var te =
                          new TimeEntry(
                              taskId,
                              memberId,
                              LocalDate.of(2025, 3, 1),
                              60,
                              true,
                              null,
                              "Fee note billable entry");
                      te.snapshotBillingRate(new BigDecimal("1500.00"), "ZAR");
                      te = timeEntryRepository.save(te);
                      holder[0] = te.getId();
                    }));
    return holder[0];
  }
}
