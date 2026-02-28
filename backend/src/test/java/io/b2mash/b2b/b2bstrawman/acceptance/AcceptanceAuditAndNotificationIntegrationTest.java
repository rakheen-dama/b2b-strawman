package io.b2mash.b2b.b2bstrawman.acceptance;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventFilter;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationRepository;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocument;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplateCategory;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AcceptanceAuditAndNotificationIntegrationTest {

  private static final Map<String, Object> CONTENT = Map.of("type", "doc", "content", List.of());

  private static final String ORG_ID = "org_accept_audit_test";
  private static final String CLERK_USER_ID = "user_accept_audit_test";
  private final AtomicInteger contactCounter = new AtomicInteger(0);

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private MemberSyncService memberSyncService;
  @Autowired private AcceptanceService acceptanceService;
  @Autowired private AcceptanceRequestRepository acceptanceRequestRepository;
  @Autowired private GeneratedDocumentRepository generatedDocumentRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private PortalContactRepository portalContactRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private AuditService auditService;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private UUID templateId;

  @BeforeAll
  void provisionTenantAndSetupData() {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Accept Audit Test Org").schemaName();
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    var syncResult =
        memberSyncService.syncMember(
            ORG_ID, CLERK_USER_ID, "audit-test@example.com", "Audit Test User", null, "org:admin");
    memberId = syncResult.memberId();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      Customer customer =
                          createActiveCustomer(
                              "Audit Test Customer", "audit-cust@test.com", memberId);
                      customer = customerRepository.save(customer);
                      customerId = customer.getId();

                      DocumentTemplate template =
                          new DocumentTemplate(
                              TemplateEntityType.CUSTOMER,
                              "Audit Test Template",
                              "audit-test-template",
                              TemplateCategory.ENGAGEMENT_LETTER,
                              CONTENT);
                      template = documentTemplateRepository.save(template);
                      templateId = template.getId();
                    }));
  }

  private record TestFixture(UUID docId, UUID contactId) {}

  private TestFixture createDocAndContact() {
    final UUID[] result = new UUID[2];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      int n = contactCounter.incrementAndGet();

                      GeneratedDocument doc =
                          new GeneratedDocument(
                              templateId,
                              TemplateEntityType.CUSTOMER,
                              customerId,
                              "audit-test-document.pdf",
                              "s3/audit-test-document-" + n + ".pdf",
                              1024L,
                              memberId);
                      doc = generatedDocumentRepository.save(doc);
                      result[0] = doc.getId();

                      PortalContact contact =
                          new PortalContact(
                              ORG_ID,
                              customerId,
                              "audit-contact-" + n + "@test.com",
                              "Audit Contact " + n,
                              PortalContact.ContactRole.PRIMARY);
                      contact = portalContactRepository.save(contact);
                      result[1] = contact.getId();
                    }));
    return new TestFixture(result[0], result[1]);
  }

  private void runInTenantWithMember(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(action);
  }

  @Test
  void createAndSend_records_created_and_sent_audit_events() {
    var fixture = createDocAndContact();
    final UUID[] requestIdHolder = new UUID[1];

    runInTenantWithMember(
        () -> {
          var request = acceptanceService.createAndSend(fixture.docId(), fixture.contactId(), null);
          requestIdHolder[0] = request.getId();
        });

    UUID requestId = requestIdHolder[0];

    runInTenantWithMember(
        () -> {
          // Verify acceptance.created audit event
          var createdPage =
              auditService.findEvents(
                  new AuditEventFilter(
                      "acceptance_request", requestId, null, "acceptance.created", null, null),
                  PageRequest.of(0, 10));
          assertThat(createdPage.getTotalElements()).isGreaterThanOrEqualTo(1);
          var createdEvent = createdPage.getContent().getFirst();
          assertThat(createdEvent.getEventType()).isEqualTo("acceptance.created");
          assertThat(createdEvent.getDetails()).containsKey("document_file_name");
          assertThat(createdEvent.getDetails()).containsKey("contact_name");

          // Verify acceptance.sent audit event
          var sentPage =
              auditService.findEvents(
                  new AuditEventFilter(
                      "acceptance_request", requestId, null, "acceptance.sent", null, null),
                  PageRequest.of(0, 10));
          assertThat(sentPage.getTotalElements()).isGreaterThanOrEqualTo(1);
          var sentEvent = sentPage.getContent().getFirst();
          assertThat(sentEvent.getEventType()).isEqualTo("acceptance.sent");
          assertThat(sentEvent.getDetails()).containsKey("contact_email");
        });
  }

  @Test
  void accept_records_accepted_audit_event() {
    var fixture = createDocAndContact();
    final String[] tokenHolder = new String[1];
    final UUID[] requestIdHolder = new UUID[1];

    runInTenantWithMember(
        () -> {
          var request = acceptanceService.createAndSend(fixture.docId(), fixture.contactId(), null);
          tokenHolder[0] = request.getRequestToken();
          requestIdHolder[0] = request.getId();
        });

    // Accept in tenant scope (portal contact, no member ID needed for accept)
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              acceptanceService.accept(
                  tokenHolder[0],
                  new AcceptanceSubmission("John Doe"),
                  "192.168.1.1",
                  "TestBrowser/1.0");
            });

    UUID requestId = requestIdHolder[0];

    runInTenantWithMember(
        () -> {
          var page =
              auditService.findEvents(
                  new AuditEventFilter(
                      "acceptance_request", requestId, null, "acceptance.accepted", null, null),
                  PageRequest.of(0, 10));
          assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(1);
          var event = page.getContent().getFirst();
          assertThat(event.getEventType()).isEqualTo("acceptance.accepted");
          assertThat(event.getDetails()).containsEntry("acceptor_name", "John Doe");
        });
  }

  @Test
  void revoke_records_revoked_audit_event() {
    var fixture = createDocAndContact();
    final UUID[] requestIdHolder = new UUID[1];

    runInTenantWithMember(
        () -> {
          var request = acceptanceService.createAndSend(fixture.docId(), fixture.contactId(), null);
          requestIdHolder[0] = request.getId();
        });

    UUID requestId = requestIdHolder[0];

    runInTenantWithMember(() -> acceptanceService.revoke(requestId));

    runInTenantWithMember(
        () -> {
          var page =
              auditService.findEvents(
                  new AuditEventFilter(
                      "acceptance_request", requestId, null, "acceptance.revoked", null, null),
                  PageRequest.of(0, 10));
          assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(1);
          var event = page.getContent().getFirst();
          assertThat(event.getEventType()).isEqualTo("acceptance.revoked");
          assertThat(event.getDetails()).containsKey("revoked_by");
        });
  }

  @Test
  void remind_records_reminded_audit_event() {
    var fixture = createDocAndContact();
    final UUID[] requestIdHolder = new UUID[1];

    runInTenantWithMember(
        () -> {
          var request = acceptanceService.createAndSend(fixture.docId(), fixture.contactId(), null);
          requestIdHolder[0] = request.getId();
        });

    UUID requestId = requestIdHolder[0];

    runInTenantWithMember(() -> acceptanceService.remind(requestId));

    runInTenantWithMember(
        () -> {
          var page =
              auditService.findEvents(
                  new AuditEventFilter(
                      "acceptance_request", requestId, null, "acceptance.reminded", null, null),
                  PageRequest.of(0, 10));
          assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(1);
          var event = page.getContent().getFirst();
          assertThat(event.getEventType()).isEqualTo("acceptance.reminded");
          assertThat(event.getDetails()).containsKey("reminder_count");
        });
  }

  @Test
  void accept_creates_notification_for_sender() {
    var fixture = createDocAndContact();
    final String[] tokenHolder = new String[1];
    final UUID[] requestIdHolder = new UUID[1];

    runInTenantWithMember(
        () -> {
          var request = acceptanceService.createAndSend(fixture.docId(), fixture.contactId(), null);
          tokenHolder[0] = request.getRequestToken();
          requestIdHolder[0] = request.getId();
        });

    // Accept (triggers event -> notification via event handler)
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              acceptanceService.accept(
                  tokenHolder[0],
                  new AcceptanceSubmission("Jane Doe"),
                  "10.0.0.1",
                  "TestBrowser/2.0");
            });

    UUID requestId = requestIdHolder[0];

    // The notification is created in an AFTER_COMMIT handler, so we need to wait briefly
    // and verify in tenant scope
    runInTenantWithMember(
        () -> {
          boolean exists =
              notificationRepository.existsByTypeAndReferenceEntityId(
                  "ACCEPTANCE_COMPLETED", requestId);
          assertThat(exists)
              .as(
                  "Notification with type ACCEPTANCE_COMPLETED should exist for request %s",
                  requestId)
              .isTrue();
        });
  }
}
