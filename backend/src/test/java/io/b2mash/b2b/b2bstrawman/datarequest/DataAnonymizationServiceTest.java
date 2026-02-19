package io.b2mash.b2b.b2bstrawman.datarequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.comment.Comment;
import io.b2mash.b2b.b2bstrawman.comment.CommentRepository;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;

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
  @Autowired private PlanSyncService planSyncService;
  @Autowired private MemberSyncService memberSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  @MockitoBean private S3Client s3Client;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Anon Service Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
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

    org.mockito.Mockito.when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
        .thenReturn(DeleteObjectResponse.builder().build());

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

    org.mockito.Mockito.when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
        .thenReturn(DeleteObjectResponse.builder().build());

    runInTenant(
        () ->
            dataAnonymizationService.executeAnonymization(ids.requestId, CUSTOMER_NAME, memberId));

    verify(s3Client, times(2)).deleteObject(any(DeleteObjectRequest.class));
  }

  @Test
  void documentsDeletedFromDb() {
    var ids = setupFullScenario();

    org.mockito.Mockito.when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
        .thenReturn(DeleteObjectResponse.builder().build());

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

    org.mockito.Mockito.when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
        .thenReturn(DeleteObjectResponse.builder().build());

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

    org.mockito.Mockito.when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
        .thenReturn(DeleteObjectResponse.builder().build());

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

    org.mockito.Mockito.when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
        .thenReturn(DeleteObjectResponse.builder().build());

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

    org.mockito.Mockito.when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
        .thenReturn(DeleteObjectResponse.builder().build());

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

    org.mockito.Mockito.when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
        .thenReturn(DeleteObjectResponse.builder().build());

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

    org.mockito.Mockito.when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
        .thenReturn(DeleteObjectResponse.builder().build());

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
                  new Customer(
                      "Type Test Customer",
                      "type-test@test.com",
                      "+1-555-9999",
                      "TYP-001",
                      "Type test",
                      memberId);
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
                  new Customer(
                      "Status Test Customer",
                      "status-test@test.com",
                      "+1-555-8888",
                      "STA-001",
                      "Status test",
                      memberId);
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

    org.mockito.Mockito.when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
        .thenReturn(DeleteObjectResponse.builder().build());

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

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
    org.mockito.Mockito.reset(s3Client);
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
              new Customer(
                  CUSTOMER_NAME,
                  "anon-customer-" + seq + "@test.com",
                  "+1-555-0300",
                  "ANON-" + seq,
                  "Customer for anonymization test",
                  memberId);
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
                  "EXTERNAL");
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
    var auth =
        new TestingAuthenticationToken(
            "user_anon_svc_test", null, List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
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
