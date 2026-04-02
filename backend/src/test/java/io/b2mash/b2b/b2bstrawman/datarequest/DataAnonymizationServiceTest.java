package io.b2mash.b2b.b2bstrawman.datarequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.comment.Comment;
import io.b2mash.b2b.b2bstrawman.comment.CommentRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.integration.storage.PresignedUrl;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataAnonymizationServiceTest {

  private static final String ORG_ID = "org_anon_svc_test";
  private static final String CUSTOMER_NAME = "Anon Test Customer";

  private int scenarioCounter = 0;

  @Autowired private DataAnonymizationService dataAnonymizationService;
  @Autowired private DataSubjectRequestService dataSubjectRequestService;
  @Autowired private DataSubjectRequestRepository requestRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private CommentRepository commentRepository;
  @Autowired private PortalContactRepository portalContactRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private MemberSyncService memberSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  @MockitoBean private StorageService storageService;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Anon Service Test Org", null);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    var syncResult =
        memberSyncService.syncMember(
            ORG_ID, "user_anon_svc_test", "anon_svc@test.com", "Anon SVC Tester", null, "owner");
    memberId = syncResult.memberId();
  }

  @Test
  void customerPiiAnonymized() {
    var ids = setupFullScenario();

    runInTenant(
        () ->
            dataAnonymizationService.executeAnonymization(ids.requestId, CUSTOMER_NAME, memberId));

    runInTenant(
        () -> {
          var customer = customerRepository.findById(ids.customerId).orElseThrow();
          assertThat(customer.getName()).startsWith("Anonymized Customer ");
          assertThat(customer.getEmail()).contains("@anonymized.invalid");
          assertThat(customer.getPhone()).isNull();
          assertThat(customer.getIdNumber()).isNull();
        });
  }

  @Test
  void documentsDeletedFromS3() {
    var ids = setupFullScenario();

    runInTenant(
        () ->
            dataAnonymizationService.executeAnonymization(ids.requestId, CUSTOMER_NAME, memberId));

    verify(storageService, times(2)).delete(any(String.class));
  }

  @Test
  void documentsDeletedFromDb() {
    var ids = setupFullScenario();

    runInTenant(
        () ->
            dataAnonymizationService.executeAnonymization(ids.requestId, CUSTOMER_NAME, memberId));

    runInTenant(
        () -> {
          var docs = documentRepository.findByCustomerId(ids.customerId);
          assertThat(docs).isEmpty();
        });
  }

  @Test
  void portalVisibleCommentsRedacted() {
    var ids = setupFullScenario();

    runInTenant(
        () ->
            dataAnonymizationService.executeAnonymization(ids.requestId, CUSTOMER_NAME, memberId));

    runInTenant(
        () -> {
          // The shared comment was on a document that got deleted, so check via ID
          var comment = commentRepository.findById(ids.sharedCommentId).orElseThrow();
          assertThat(comment.getBody()).isEqualTo("[Removed]");
        });
  }

  @Test
  void internalCommentsPreserved() {
    var ids = setupFullScenario();

    runInTenant(
        () ->
            dataAnonymizationService.executeAnonymization(ids.requestId, CUSTOMER_NAME, memberId));

    runInTenant(
        () -> {
          var comment = commentRepository.findById(ids.internalCommentId).orElseThrow();
          assertThat(comment.getBody()).isEqualTo("Internal note — should be preserved");
        });
  }

  @Test
  void portalContactsAnonymized() {
    var ids = setupFullScenario();

    runInTenant(
        () ->
            dataAnonymizationService.executeAnonymization(ids.requestId, CUSTOMER_NAME, memberId));

    runInTenant(
        () -> {
          var contacts = portalContactRepository.findByCustomerId(ids.customerId);
          assertThat(contacts).hasSize(1);
          assertThat(contacts.getFirst().getDisplayName()).isEqualTo("Removed Contact");
          assertThat(contacts.getFirst().getEmail()).contains("@anonymized.invalid");
        });
  }

  @Test
  void invoicesPreserved() {
    var ids = setupFullScenario();

    runInTenant(
        () ->
            dataAnonymizationService.executeAnonymization(ids.requestId, CUSTOMER_NAME, memberId));

    runInTenant(
        () -> {
          var invoices = invoiceRepository.findByCustomerId(ids.customerId);
          assertThat(invoices).hasSize(1);
        });
  }

  @Test
  void timeEntriesPreserved() {
    var ids = setupFullScenario();

    runInTenant(
        () ->
            dataAnonymizationService.executeAnonymization(ids.requestId, CUSTOMER_NAME, memberId));

    runInTenant(
        () -> {
          var entries =
              timeEntryRepository.findByMemberIdAndDateBetween(
                  memberId, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
          assertThat(entries).isNotEmpty();
        });
  }

  @Test
  void customerTransitionedToOffboarded() {
    var ids = setupFullScenario();

    runInTenant(
        () ->
            dataAnonymizationService.executeAnonymization(ids.requestId, CUSTOMER_NAME, memberId));

    runInTenant(
        () -> {
          var customer = customerRepository.findById(ids.customerId).orElseThrow();
          assertThat(customer.getLifecycleStatus()).isEqualTo(LifecycleStatus.OFFBOARDED);
        });
  }

  @Test
  void nameMismatchRejected() {
    var ids = setupFullScenario();

    assertThatThrownBy(
            () ->
                runInTenant(
                    () ->
                        dataAnonymizationService.executeAnonymization(
                            ids.requestId, "Wrong Name", memberId)))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void wrongRequestTypeRejected() {
    // Create an ACCESS request (not DELETION) and put it IN_PROGRESS
    var customerId =
        runInTenant(
            () -> {
              var customer =
                  TestCustomerFactory.createActiveCustomer(
                      "Type Test Customer", "type-test@test.com", memberId);
              return customerRepository.save(customer).getId();
            });

    var request =
        runInTenant(
            () ->
                dataSubjectRequestService.createRequest(
                    customerId, "ACCESS", "Access request", memberId));

    runInTenant(() -> dataSubjectRequestService.startProcessing(request.getId(), memberId));

    assertThatThrownBy(
            () ->
                runInTenant(
                    () ->
                        dataAnonymizationService.executeAnonymization(
                            request.getId(), "Type Test Customer", memberId)))
        .isInstanceOf(ResourceConflictException.class);
  }

  @Test
  void wrongStatusRejected() {
    // Create a DELETION request but leave it in RECEIVED (not IN_PROGRESS)
    var customerId =
        runInTenant(
            () -> {
              var customer =
                  TestCustomerFactory.createActiveCustomer(
                      "Status Test Customer", "status-test@test.com", memberId);
              return customerRepository.save(customer).getId();
            });

    var request =
        runInTenant(
            () ->
                dataSubjectRequestService.createRequest(
                    customerId, "DELETION", "Delete request", memberId));

    assertThatThrownBy(
            () ->
                runInTenant(
                    () ->
                        dataAnonymizationService.executeAnonymization(
                            request.getId(), "Status Test Customer", memberId)))
        .isInstanceOf(ResourceConflictException.class);
  }

  @Test
  void auditEventRecordsCounts() {
    var ids = setupFullScenario();

    runInTenant(
        () ->
            dataAnonymizationService.executeAnonymization(ids.requestId, CUSTOMER_NAME, memberId));

    runInTenant(
        () -> {
          var page =
              auditEventRepository.findByFilter(
                  "data_subject_request",
                  ids.requestId,
                  null,
                  "data.deletion.executed",
                  null,
                  null,
                  Pageable.ofSize(10));
          assertThat(page.getContent()).hasSize(1);
          var details = page.getContent().getFirst().getDetails();
          assertThat(details).containsKey("documentsDeleted");
          assertThat(details).containsKey("commentsRedacted");
          assertThat(details).containsKey("portalContactsAnonymized");
        });
  }

  // --- Tests for standalone anonymizeCustomer() (Epic 375A) ---

  @Test
  void anonymizeCustomer_setsAnonymizedStatusAndAnonymizesPii() {
    mockStorageForExport();
    UUID customerId =
        runInTenant(
            () -> {
              var customer =
                  TestCustomerFactory.createActiveCustomer(
                      "Anon Status Customer", "anon-status@test.com", memberId);
              customer.setNotes("Some sensitive notes");
              customer.setCustomFields(Map.of("tax_id", "123-456"));
              return customerRepository.save(customer).getId();
            });

    runInTenant(
        () ->
            dataAnonymizationService.anonymizeCustomer(
                customerId, "Anon Status Customer", "Test reason", memberId));

    runInTenant(
        () -> {
          var customer = customerRepository.findById(customerId).orElseThrow();
          // Lifecycle status
          assertThat(customer.getLifecycleStatus()).isEqualTo(LifecycleStatus.ANONYMIZED);
          // PII anonymized
          assertThat(customer.getName()).startsWith("Anonymized Customer ");
          assertThat(customer.getEmail()).contains("@anonymized.invalid");
          assertThat(customer.getPhone()).isNull();
          assertThat(customer.getIdNumber()).isNull();
          // Notes and custom fields cleared
          assertThat(customer.getNotes()).isNull();
          assertThat(customer.getCustomFields()).isNull();
        });
  }

  @Test
  void anonymizeCustomer_clearsNotesAndCustomFields() {
    mockStorageForExport();
    UUID customerId =
        runInTenant(
            () -> {
              var customer =
                  TestCustomerFactory.createActiveCustomer(
                      "Notes Fields Customer", "notes-fields@test.com", memberId);
              customer.setNotes("Sensitive notes here");
              customer.setCustomFields(Map.of("industry", "Tech", "size", "Large"));
              return customerRepository.save(customer).getId();
            });

    runInTenant(
        () ->
            dataAnonymizationService.anonymizeCustomer(
                customerId, "Notes Fields Customer", null, memberId));

    runInTenant(
        () -> {
          var customer = customerRepository.findById(customerId).orElseThrow();
          assertThat(customer.getNotes()).isNull();
          assertThat(customer.getCustomFields()).isNull();
        });
  }

  @Test
  void anonymizeCustomer_auditEventContainsPreAnonymizationExportKey() {
    mockStorageForExport();
    UUID customerId = createTestCustomer("Audit Export Customer", "audit-export@test.com");

    runInTenant(
        () ->
            dataAnonymizationService.anonymizeCustomer(
                customerId, "Audit Export Customer", "GDPR request", memberId));

    runInTenant(
        () -> {
          var page =
              auditEventRepository.findByFilter(
                  "customer",
                  customerId,
                  null,
                  "data.subject.anonymized",
                  null,
                  null,
                  Pageable.ofSize(10));
          assertThat(page.getContent()).isNotEmpty();
          var auditEvent = page.getContent().getFirst();
          assertThat(auditEvent.getEventType()).isEqualTo("data.subject.anonymized");
          assertThat(auditEvent.getDetails()).containsKey("preAnonymizationExportKey");
          assertThat((String) auditEvent.getDetails().get("preAnonymizationExportKey"))
              .contains("exports/compliance-");
        });
  }

  @Test
  void previewAnonymization_returnsCorrectEntityCounts() {
    UUID customerId =
        runInTenant(
            () -> {
              var customer =
                  TestCustomerFactory.createActiveCustomer(
                      "Preview Customer", "preview@test.com", memberId);
              customer.setCustomFields(Map.of("field1", "val1", "field2", "val2"));
              return customerRepository.save(customer).getId();
            });

    var preview = runInTenant(() -> dataAnonymizationService.previewAnonymization(customerId));

    assertThat(preview.customerId()).isEqualTo(customerId);
    assertThat(preview.customerName()).isEqualTo("Preview Customer");
    assertThat(preview.customFieldValues()).isEqualTo(2);
    assertThat(preview.portalContacts()).isZero();
    assertThat(preview.projects()).isZero();
    assertThat(preview.documents()).isZero();
    assertThat(preview.timeEntries()).isZero();
    assertThat(preview.invoices()).isZero();
    assertThat(preview.comments()).isZero();
    assertThat(preview.financialRecordsRetained()).isZero();
    assertThat(preview.financialRetentionExpiresAt()).isNull();
  }

  @Test
  void anonymizeCustomer_rejectedIfConfirmationNameDoesNotMatch() {
    mockStorageForExport();
    UUID customerId = createTestCustomer("Mismatch Anon Customer", "mismatch-anon@test.com");

    assertThatThrownBy(
            () ->
                runInTenant(
                    () ->
                        dataAnonymizationService.anonymizeCustomer(
                            customerId, "Wrong Name", null, memberId)))
        .isInstanceOf(InvalidStateException.class)
        .hasMessageContaining("Confirmation name does not match customer name");
  }

  @Test
  void anonymizeCustomer_rejectedIfAlreadyAnonymized() {
    mockStorageForExport();
    UUID customerId = createTestCustomer("Already Anon Customer", "already-anon@test.com");

    // First anonymization should succeed
    runInTenant(
        () ->
            dataAnonymizationService.anonymizeCustomer(
                customerId, "Already Anon Customer", null, memberId));

    // Second anonymization should fail
    String hash = customerId.toString().substring(0, 6);
    String anonymizedName = "Anonymized Customer " + hash;

    assertThatThrownBy(
            () ->
                runInTenant(
                    () ->
                        dataAnonymizationService.anonymizeCustomer(
                            customerId, anonymizedName, null, memberId)))
        .isInstanceOf(ResourceConflictException.class)
        .hasMessageContaining("already anonymized");
  }

  @Test
  void anonymizeCustomer_preservesInvoiceFinancialFieldsAndUpdatesCustomerReference() {
    mockStorageForExport();
    UUID customerId = createTestCustomer("Invoice Preserve Customer", "invoice-preserve@test.com");

    UUID invoiceId =
        runInTenant(
            () -> {
              var invoice =
                  new Invoice(
                      customerId,
                      "ZAR",
                      "Invoice Preserve Customer",
                      "invoice-preserve@test.com",
                      "123 Test St, Test City",
                      "Test Org",
                      memberId);
              return invoiceRepository.save(invoice).getId();
            });

    runInTenant(
        () ->
            dataAnonymizationService.anonymizeCustomer(
                customerId, "Invoice Preserve Customer", null, memberId));

    runInTenant(
        () -> {
          var invoice = invoiceRepository.findById(invoiceId).orElseThrow();

          // Financial fields preserved
          assertThat(invoice.getCurrency()).isEqualTo("ZAR");
          assertThat(invoice.getSubtotal()).isNotNull();
          assertThat(invoice.getTaxAmount()).isNotNull();
          assertThat(invoice.getTotal()).isNotNull();
          assertThat(invoice.getStatus()).isNotNull();

          // Customer reference updated to REF-{shortId}
          String hash = customerId.toString().substring(0, 6);
          assertThat(invoice.getCustomerName()).isEqualTo("REF-" + hash);
          assertThat(invoice.getCustomerEmail()).isNull();
          assertThat(invoice.getCustomerAddress()).isNull();
        });
  }

  // --- Helpers for standalone anonymization tests ---

  private void mockStorageForExport() {
    when(storageService.upload(any(String.class), any(byte[].class), any(String.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(storageService.generateDownloadUrl(any(), any()))
        .thenReturn(
            new PresignedUrl("https://example.com/download", Instant.now().plusSeconds(3600)));
  }

  private UUID createTestCustomer(String name, String email) {
    return runInTenant(
        () -> {
          var customer = TestCustomerFactory.createActiveCustomer(name, email, memberId);
          return customerRepository.save(customer).getId();
        });
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
    org.mockito.Mockito.reset(storageService);
  }

  /**
   * Sets up the full scenario: customer, documents, comments (shared + internal), portal contact,
   * invoice, time entry, and a DELETION request in IN_PROGRESS status.
   */
  private ScenarioIds setupFullScenario() {
    int seq = ++scenarioCounter;
    return runInTenant(
        () -> {
          // 1. Create customer (unique email per scenario to avoid unique constraint violations)
          var customer =
              TestCustomerFactory.createActiveCustomer(
                  CUSTOMER_NAME, "anon-customer-" + seq + "@test.com", memberId);
          customer = customerRepository.save(customer);
          UUID customerId = customer.getId();

          // 2. Create a project (needed for comments and documents)
          var project = new Project("Anon Test Project", "Test project", memberId);
          project = projectRepository.save(project);
          UUID projectId = project.getId();

          // 3. Create customer-scoped documents with S3 keys
          var doc1 =
              new Document(
                  Document.Scope.CUSTOMER,
                  projectId,
                  customerId,
                  "contract.pdf",
                  "application/pdf",
                  1024,
                  memberId,
                  Document.Visibility.SHARED);
          doc1.assignS3Key("org/test/customer/" + customerId + "/doc1.pdf");
          doc1.confirmUpload();
          doc1 = documentRepository.save(doc1);

          var doc2 =
              new Document(
                  Document.Scope.CUSTOMER,
                  projectId,
                  customerId,
                  "invoice-copy.pdf",
                  "application/pdf",
                  2048,
                  memberId,
                  Document.Visibility.INTERNAL);
          doc2.assignS3Key("org/test/customer/" + customerId + "/doc2.pdf");
          doc2.confirmUpload();
          doc2 = documentRepository.save(doc2);

          // 4. Create a SHARED comment on doc1 (portal-visible) and an INTERNAL comment
          var sharedComment =
              new Comment(
                  "DOCUMENT",
                  doc1.getId(),
                  projectId,
                  memberId,
                  "Shared comment — should be redacted",
                  "SHARED");
          sharedComment = commentRepository.save(sharedComment);

          var internalComment =
              new Comment(
                  "DOCUMENT",
                  doc1.getId(),
                  projectId,
                  memberId,
                  "Internal note — should be preserved",
                  "INTERNAL");
          internalComment = commentRepository.save(internalComment);

          // 5. Create a portal contact
          var contact =
              new PortalContact(
                  ORG_ID,
                  customerId,
                  "portal-contact-" + seq + "@test.com",
                  "Test Contact",
                  PortalContact.ContactRole.PRIMARY);
          contact = portalContactRepository.save(contact);

          // 6. Create an invoice (to verify preservation)
          var invoice =
              new Invoice(
                  customerId,
                  "USD",
                  CUSTOMER_NAME,
                  "anon-customer-" + seq + "@test.com",
                  "123 Test St",
                  "Test Org",
                  memberId);
          invoice = invoiceRepository.save(invoice);

          // 7. Create a task and time entry (to verify preservation)
          var task =
              new Task(projectId, "Test Task", "Task desc", "MEDIUM", "TASK", null, memberId);
          task = taskRepository.save(task);
          var timeEntry =
              new TimeEntry(task.getId(), memberId, LocalDate.now(), 60, true, null, "Test work");
          timeEntry = timeEntryRepository.save(timeEntry);

          // 8. Create a DELETION request and move to IN_PROGRESS
          var request =
              new DataSubjectRequest(
                  customerId,
                  "DELETION",
                  "Delete all data",
                  memberId,
                  LocalDate.now().plusDays(30));
          request = requestRepository.save(request);
          request.startProcessing(memberId);
          request = requestRepository.save(request);

          return new ScenarioIds(
              customerId,
              projectId,
              request.getId(),
              sharedComment.getId(),
              internalComment.getId());
        });
  }

  private record ScenarioIds(
      UUID customerId,
      UUID projectId,
      UUID requestId,
      UUID sharedCommentId,
      UUID internalCommentId) {}

  private <T> T runInTenant(Callable<T> callable) {
    var auth = new TestingAuthenticationToken("user_anon_svc_test", null, Collections.emptyList());
    SecurityContextHolder.getContext().setAuthentication(auth);

    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .call(
            () ->
                transactionTemplate.execute(
                    tx -> {
                      try {
                        return callable.call();
                      } catch (RuntimeException e) {
                        throw e;
                      } catch (Exception e) {
                        throw new RuntimeException(e);
                      }
                    }));
  }

  private void runInTenant(Runnable runnable) {
    runInTenant(
        () -> {
          runnable.run();
          return null;
        });
  }
}
