package io.b2mash.b2b.b2bstrawman.acceptance;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.event.AcceptanceRequestAcceptedEvent;
import io.b2mash.b2b.b2bstrawman.event.AcceptanceRequestExpiredEvent;
import io.b2mash.b2b.b2bstrawman.event.AcceptanceRequestRevokedEvent;
import io.b2mash.b2b.b2bstrawman.event.AcceptanceRequestSentEvent;
import io.b2mash.b2b.b2bstrawman.event.AcceptanceRequestViewedEvent;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocument;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplateCategory;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@RecordApplicationEvents
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AcceptanceServiceIntegrationTest {

  private static final Map<String, Object> CONTENT = Map.of("type", "doc", "content", List.of());

  private static final String ORG_ID = "org_acceptance_svc_test";
  private static final String CLERK_USER_ID = "user_acceptance_svc_test";
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
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ApplicationEvents events;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private UUID templateId;

  @BeforeAll
  void provisionTenantAndSetupData() {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Acceptance Service Test Org").schemaName();
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    // Create member
    var syncResult =
        memberSyncService.syncMember(
            ORG_ID, CLERK_USER_ID, "test@example.com", "Test User", null, "org:admin");
    memberId = syncResult.memberId();

    // Create shared base data (customer + template)
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      Customer customer =
                          createActiveCustomer("Test Customer", "customer@test.com", memberId);
                      customer = customerRepository.save(customer);
                      customerId = customer.getId();

                      DocumentTemplate template =
                          new DocumentTemplate(
                              TemplateEntityType.CUSTOMER,
                              "Test Template",
                              "test-template",
                              TemplateCategory.ENGAGEMENT_LETTER,
                              CONTENT);
                      template = documentTemplateRepository.save(template);
                      templateId = template.getId();
                    }));
  }

  @BeforeEach
  void clearEvents() {
    events.clear();
  }

  // --- createAndSend tests ---

  @Test
  void createAndSend_creates_and_transitions_to_sent() {
    var fixture = createDocAndContact();
    runInTenantWithMember(
        () -> {
          events.clear();
          AcceptanceRequest request =
              acceptanceService.createAndSend(fixture.docId, fixture.contactId, null);

          assertThat(request.getStatus()).isEqualTo(AcceptanceStatus.SENT);
          assertThat(request.getSentAt()).isNotNull();
          assertThat(request.getRequestToken()).isNotNull();
          assertThat(request.getExpiresAt()).isAfter(Instant.now());
          assertThat(request.getCustomerId()).isEqualTo(customerId);
          assertThat(request.getSentByMemberId()).isEqualTo(memberId);

          // Verify event published
          var sentEvents = events.stream(AcceptanceRequestSentEvent.class).toList();
          assertThat(sentEvents).hasSize(1);
          var event = sentEvents.getFirst();
          assertThat(event.eventType()).isEqualTo("acceptance_request.sent");
          assertThat(event.entityType()).isEqualTo("acceptance_request");
          assertThat(event.entityId()).isEqualTo(request.getId());
          assertThat(event.requestId()).isEqualTo(request.getId());
          assertThat(event.generatedDocumentId()).isEqualTo(fixture.docId);
          assertThat(event.portalContactId()).isEqualTo(fixture.contactId);
          assertThat(event.customerId()).isEqualTo(customerId);
          assertThat(event.documentFileName()).isEqualTo("test-document.pdf");
          assertThat(event.expiresAt()).isAfter(Instant.now());
          assertThat(event.contactName()).startsWith("Contact ");
          assertThat(event.actorMemberId()).isEqualTo(memberId);
          assertThat(event.actorName()).isEqualTo("Test User");
          assertThat(event.tenantId()).isEqualTo(tenantSchema);
        });
  }

  @Test
  void createAndSend_auto_revokes_existing_active_request() {
    var fixture = createDocAndContact();
    runInTenantWithMember(
        () -> {
          // Create first request
          AcceptanceRequest first =
              acceptanceService.createAndSend(fixture.docId, fixture.contactId, null);
          UUID firstId = first.getId();

          events.clear();

          // Create second request for same doc-contact pair
          AcceptanceRequest second =
              acceptanceService.createAndSend(fixture.docId, fixture.contactId, null);

          // First should be revoked
          AcceptanceRequest reloaded = acceptanceRequestRepository.findById(firstId).orElseThrow();
          assertThat(reloaded.getStatus()).isEqualTo(AcceptanceStatus.REVOKED);

          // Second should be SENT
          assertThat(second.getStatus()).isEqualTo(AcceptanceStatus.SENT);

          // Auto-revoke should publish a RevokedEvent
          var revokedEvents = events.stream(AcceptanceRequestRevokedEvent.class).toList();
          assertThat(revokedEvents).hasSize(1);
          assertThat(revokedEvents.getFirst().requestId()).isEqualTo(firstId);

          // Only one non-terminal request should exist for this doc-contact pair
          var activeRequests =
              acceptanceRequestRepository.findByGeneratedDocumentIdAndPortalContactIdAndStatusIn(
                  fixture.docId,
                  fixture.contactId,
                  java.util.List.of(
                      AcceptanceStatus.PENDING, AcceptanceStatus.SENT, AcceptanceStatus.VIEWED));
          assertThat(activeRequests).isPresent();
          assertThat(activeRequests.get().getId()).isEqualTo(second.getId());
        });
  }

  @Test
  void createAndSend_validates_document_exists() {
    var fixture = createDocAndContact();
    runInTenantWithMember(
        () -> {
          UUID nonExistentDocId = UUID.randomUUID();
          assertThatThrownBy(
                  () -> acceptanceService.createAndSend(nonExistentDocId, fixture.contactId, null))
              .isInstanceOf(ResourceNotFoundException.class);
        });
  }

  @Test
  void createAndSend_validates_contact_belongs_to_customer() {
    var fixture = createDocAndContact();
    // Create a contact for a DIFFERENT customer (needs a real customer for FK)
    final UUID[] otherContactRef = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      int n = contactCounter.incrementAndGet();
                      Customer otherCustomer =
                          createActiveCustomer(
                              "Other Customer " + n, "other-cust-" + n + "@test.com", memberId);
                      otherCustomer = customerRepository.save(otherCustomer);

                      PortalContact otherContact =
                          new PortalContact(
                              ORG_ID,
                              otherCustomer.getId(),
                              "other-contact-" + n + "@test.com",
                              "Other Contact",
                              PortalContact.ContactRole.GENERAL);
                      otherContact = portalContactRepository.save(otherContact);
                      otherContactRef[0] = otherContact.getId();
                    }));

    runInTenantWithMember(
        () -> {
          assertThatThrownBy(
                  () -> acceptanceService.createAndSend(fixture.docId, otherContactRef[0], null))
              .isInstanceOf(InvalidStateException.class)
              .hasMessageContaining("not associated with the document's customer");
        });
  }

  @Test
  void createAndSend_uses_org_expiry_default() {
    var fixture = createDocAndContact();
    runInTenantWithMember(
        () -> {
          // Set org-level expiry
          OrgSettings settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
          settings.setAcceptanceExpiryDays(14);
          orgSettingsRepository.save(settings);

          AcceptanceRequest request =
              acceptanceService.createAndSend(fixture.docId, fixture.contactId, null);

          // Should expire ~14 days from now (allow 1 day tolerance)
          Instant expectedMin = Instant.now().plus(13, ChronoUnit.DAYS);
          Instant expectedMax = Instant.now().plus(15, ChronoUnit.DAYS);
          assertThat(request.getExpiresAt()).isBetween(expectedMin, expectedMax);
        });
  }

  @Test
  void createAndSend_uses_per_request_expiry_override() {
    var fixture = createDocAndContact();
    runInTenantWithMember(
        () -> {
          AcceptanceRequest request =
              acceptanceService.createAndSend(fixture.docId, fixture.contactId, 7);

          // Should expire ~7 days from now
          Instant expectedMin = Instant.now().plus(6, ChronoUnit.DAYS);
          Instant expectedMax = Instant.now().plus(8, ChronoUnit.DAYS);
          assertThat(request.getExpiresAt()).isBetween(expectedMin, expectedMax);
        });
  }

  // --- markViewed tests ---

  @Test
  void markViewed_transitions_sent_to_viewed() {
    var fixture = createDocAndContact();
    runInTenantWithMember(
        () -> {
          AcceptanceRequest request =
              acceptanceService.createAndSend(fixture.docId, fixture.contactId, null);
          events.clear();

          AcceptanceRequest viewed =
              acceptanceService.markViewed(request.getRequestToken(), "192.168.1.1");

          assertThat(viewed.getStatus()).isEqualTo(AcceptanceStatus.VIEWED);
          assertThat(viewed.getViewedAt()).isNotNull();

          // Verify event
          var viewedEvents = events.stream(AcceptanceRequestViewedEvent.class).toList();
          assertThat(viewedEvents).hasSize(1);
          var event = viewedEvents.getFirst();
          assertThat(event.eventType()).isEqualTo("acceptance_request.viewed");
          assertThat(event.requestId()).isEqualTo(request.getId());
          assertThat(event.ipAddress()).isEqualTo("192.168.1.1");
        });
  }

  @Test
  void markViewed_idempotent_for_viewed() {
    var fixture = createDocAndContact();
    runInTenantWithMember(
        () -> {
          AcceptanceRequest request =
              acceptanceService.createAndSend(fixture.docId, fixture.contactId, null);
          acceptanceService.markViewed(request.getRequestToken(), "192.168.1.1");
          events.clear();

          // Second view should be idempotent
          AcceptanceRequest result =
              acceptanceService.markViewed(request.getRequestToken(), "192.168.1.2");
          assertThat(result.getStatus()).isEqualTo(AcceptanceStatus.VIEWED);

          // No new viewed event should be published
          var viewedEvents = events.stream(AcceptanceRequestViewedEvent.class).toList();
          assertThat(viewedEvents).isEmpty();
        });
  }

  @Test
  void markViewed_rejects_expired() {
    var fixture = createDocAndContact();
    runInTenantWithMember(
        () -> {
          AcceptanceRequest request = createExpiredRequest(fixture);
          events.clear();

          assertThatThrownBy(
                  () -> acceptanceService.markViewed(request.getRequestToken(), "192.168.1.1"))
              .isInstanceOf(InvalidStateException.class)
              .hasMessageContaining("expired");

          // Should publish expired event
          var expiredEvents = events.stream(AcceptanceRequestExpiredEvent.class).toList();
          assertThat(expiredEvents).hasSize(1);
          assertThat(expiredEvents.getFirst().requestId()).isEqualTo(request.getId());
        });
  }

  // --- accept tests ---

  @Test
  void accept_records_metadata_and_transitions() {
    var fixture = createDocAndContact();
    runInTenantWithMember(
        () -> {
          AcceptanceRequest request =
              acceptanceService.createAndSend(fixture.docId, fixture.contactId, null);
          events.clear();

          AcceptanceRequest accepted =
              acceptanceService.accept(
                  request.getRequestToken(),
                  new AcceptanceSubmission("Jane Doe"),
                  "10.0.0.1",
                  "Mozilla/5.0");

          assertThat(accepted.getStatus()).isEqualTo(AcceptanceStatus.ACCEPTED);
          assertThat(accepted.getAcceptedAt()).isNotNull();
          assertThat(accepted.getAcceptorName()).isEqualTo("Jane Doe");
          assertThat(accepted.getAcceptorIpAddress()).isEqualTo("10.0.0.1");
          assertThat(accepted.getAcceptorUserAgent()).isEqualTo("Mozilla/5.0");

          // Verify event
          var acceptedEvents = events.stream(AcceptanceRequestAcceptedEvent.class).toList();
          assertThat(acceptedEvents).hasSize(1);
          var event = acceptedEvents.getFirst();
          assertThat(event.eventType()).isEqualTo("acceptance_request.accepted");
          assertThat(event.requestId()).isEqualTo(request.getId());
          assertThat(event.contactName()).isEqualTo("Jane Doe");
          assertThat(event.documentFileName()).isEqualTo("test-document.pdf");
          assertThat(event.sentByMemberId()).isEqualTo(memberId);
        });
  }

  @Test
  void accept_rejects_expired() {
    var fixture = createDocAndContact();
    runInTenantWithMember(
        () -> {
          AcceptanceRequest request = createExpiredRequest(fixture);
          events.clear();

          assertThatThrownBy(
                  () ->
                      acceptanceService.accept(
                          request.getRequestToken(),
                          new AcceptanceSubmission("Jane Doe"),
                          "10.0.0.1",
                          "Mozilla/5.0"))
              .isInstanceOf(InvalidStateException.class)
              .hasMessageContaining("expired");

          var expiredEvents = events.stream(AcceptanceRequestExpiredEvent.class).toList();
          assertThat(expiredEvents).hasSize(1);
        });
  }

  @Test
  void accept_rejects_revoked() {
    var fixture = createDocAndContact();
    runInTenantWithMember(
        () -> {
          AcceptanceRequest request =
              acceptanceService.createAndSend(fixture.docId, fixture.contactId, null);
          acceptanceService.revoke(request.getId());

          assertThatThrownBy(
                  () ->
                      acceptanceService.accept(
                          request.getRequestToken(),
                          new AcceptanceSubmission("Jane Doe"),
                          "10.0.0.1",
                          "Mozilla/5.0"))
              .isInstanceOf(InvalidStateException.class);
        });
  }

  // --- revoke tests ---

  @Test
  void revoke_transitions_active_request() {
    var fixture = createDocAndContact();
    runInTenantWithMember(
        () -> {
          AcceptanceRequest request =
              acceptanceService.createAndSend(fixture.docId, fixture.contactId, null);
          events.clear();

          AcceptanceRequest revoked = acceptanceService.revoke(request.getId());

          assertThat(revoked.getStatus()).isEqualTo(AcceptanceStatus.REVOKED);
          assertThat(revoked.getRevokedAt()).isNotNull();
          assertThat(revoked.getRevokedByMemberId()).isEqualTo(memberId);

          // Verify event
          var revokedEvents = events.stream(AcceptanceRequestRevokedEvent.class).toList();
          assertThat(revokedEvents).hasSize(1);
          var event = revokedEvents.getFirst();
          assertThat(event.eventType()).isEqualTo("acceptance_request.revoked");
          assertThat(event.requestId()).isEqualTo(request.getId());
          assertThat(event.actorMemberId()).isEqualTo(memberId);
          assertThat(event.actorName()).isEqualTo("Test User");
        });
  }

  @Test
  void revoke_rejects_terminal_status() {
    var fixture = createDocAndContact();
    runInTenantWithMember(
        () -> {
          AcceptanceRequest request =
              acceptanceService.createAndSend(fixture.docId, fixture.contactId, null);
          // Accept first
          acceptanceService.accept(
              request.getRequestToken(),
              new AcceptanceSubmission("Jane Doe"),
              "10.0.0.1",
              "Mozilla/5.0");

          // Revoking an ACCEPTED request should fail
          assertThatThrownBy(() -> acceptanceService.revoke(request.getId()))
              .isInstanceOf(InvalidStateException.class);
        });
  }

  // --- remind tests ---

  @Test
  void remind_resends_email_and_increments_count() {
    var fixture = createDocAndContact();
    runInTenantWithMember(
        () -> {
          AcceptanceRequest request =
              acceptanceService.createAndSend(fixture.docId, fixture.contactId, null);

          AcceptanceRequest reminded = acceptanceService.remind(request.getId());

          assertThat(reminded.getReminderCount()).isEqualTo(1);
          assertThat(reminded.getLastRemindedAt()).isNotNull();

          // Second reminder
          reminded = acceptanceService.remind(request.getId());
          assertThat(reminded.getReminderCount()).isEqualTo(2);
        });
  }

  @Test
  void remind_rejects_expired() {
    var fixture = createDocAndContact();
    runInTenantWithMember(
        () -> {
          AcceptanceRequest request = createExpiredRequest(fixture);
          events.clear();

          assertThatThrownBy(() -> acceptanceService.remind(request.getId()))
              .isInstanceOf(InvalidStateException.class)
              .hasMessageContaining("expired");

          var expiredEvents = events.stream(AcceptanceRequestExpiredEvent.class).toList();
          assertThat(expiredEvents).hasSize(1);
        });
  }

  // --- Helpers ---

  private record TestFixture(UUID docId, UUID contactId) {}

  /** Creates a unique GeneratedDocument + PortalContact pair for test isolation. */
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
                              "test-document.pdf",
                              "s3/test-document-" + n + ".pdf",
                              1024L,
                              memberId);
                      doc = generatedDocumentRepository.save(doc);
                      result[0] = doc.getId();

                      PortalContact contact =
                          new PortalContact(
                              ORG_ID,
                              customerId,
                              "contact-" + n + "@test.com",
                              "Contact " + n,
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

  /** Creates a SENT request with an expiresAt in the past. */
  private AcceptanceRequest createExpiredRequest(TestFixture fixture) {
    AcceptanceRequest request =
        new AcceptanceRequest(
            fixture.docId,
            fixture.contactId,
            customerId,
            "expired-token-" + UUID.randomUUID(),
            Instant.now().minus(1, ChronoUnit.DAYS),
            memberId);
    request.markSent();
    return acceptanceRequestRepository.save(request);
  }
}
