package io.b2mash.b2b.b2bstrawman.collections;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.Notification;
import io.b2mash.b2b.b2bstrawman.notification.NotificationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Escalation tests (Phase 83, 589A.4). A sufficiently overdue invoice is FLAGGED exactly once
 * (idempotent re-scan), notifies admins/owners only (never plain members), and writes a {@code
 * collections.escalation.flagged} audit row. No gate, no AI, no email at the ESCALATION stage.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CollectionsEscalationTest {

  private static final String ORG_ID = "org_collections_escalation_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CollectionsScanService scanService;
  @Autowired private CollectionActivityRepository activityRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private AuditEventRepository auditEventRepository;
  @MockitoSpyBean private AuditService auditService;

  private String tenantSchema;
  private UUID ownerMemberId;
  private UUID adminMemberId;
  private UUID plainMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Collections Escalation Test Org", null);
    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_esc_owner", "esc_owner@test.com", "Esc Owner", "owner"));
    adminMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_esc_admin", "esc_admin@test.com", "Esc Admin", "admin"));
    plainMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_esc_member", "esc_member@test.com", "Esc Member", "member"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    runInTenant(
        () -> {
          var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
          settings.getCollections().updateCollectionsSettings(true, 7, 21, 45, 60);
          orgSettingsRepository.save(settings);
        });
  }

  @BeforeEach
  void resetSpies() {
    // Each test configures its own audit-service stubbing; reset so a spy stub from one test
    // (the rollback case below) never bleeds into the idempotent-escalation test. A reset spy
    // calls the real AuditService by default.
    Mockito.reset(auditService);
  }

  private void runInTenant(Runnable body) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(body);
  }

  private UUID seedSentInvoice(String name, int daysOverdue) {
    UUID[] holder = new UUID[1];
    runInTenant(
        () -> {
          Customer customer =
              TestCustomerFactory.createActiveCustomer(name, name + "@test.com", ownerMemberId);
          var savedCustomer = customerRepository.save(customer);
          var invoice =
              new Invoice(
                  savedCustomer.getId(),
                  "ZAR",
                  name,
                  name + "@test.com",
                  null,
                  "Test Org",
                  ownerMemberId);
          invoice.updateDraft(LocalDate.now().minusDays(daysOverdue), null, null, BigDecimal.ZERO);
          invoice.recalculateTotals(BigDecimal.valueOf(1500), false, BigDecimal.ZERO, false);
          invoice.approve("INV-" + UUID.randomUUID().toString().substring(0, 6), ownerMemberId);
          invoice.markSent();
          holder[0] = invoiceRepository.save(invoice).getId();
        });
    return holder[0];
  }

  @Test
  void escalationFlaggedOnce_notifiesAdminsAndOwnersOnly_withAuditRow() {
    // 70d overdue crosses the 60d escalate threshold.
    UUID invoiceId = seedSentInvoice("Escalate Co", 70);

    runInTenant(scanService::scanForTenant);
    // Re-scan same day — escalation row must remain exactly one (idempotent).
    runInTenant(scanService::scanForTenant);

    runInTenant(
        () -> {
          var escalation =
              activityRepository.findByInvoiceIdAndStage(invoiceId, CollectionStage.ESCALATION);
          assertThat(escalation).isPresent();
          assertThat(escalation.get().getStatus()).isEqualTo(CollectionActivityStatus.FLAGGED);

          // Notification landed for owner + admin, never for the plain member.
          assertThat(hasEscalationNotification(ownerMemberId, invoiceId)).isTrue();
          assertThat(hasEscalationNotification(adminMemberId, invoiceId)).isTrue();
          assertThat(hasEscalationNotification(plainMemberId, invoiceId)).isFalse();

          // Audit row present for this activity.
          var audits =
              auditEventRepository
                  .findByFilter(
                      "collection_activity",
                      escalation.get().getId(),
                      null,
                      "collections.escalation.flagged",
                      null,
                      null,
                      PageRequest.of(0, 10))
                  .getContent();
          assertThat(audits).isNotEmpty();
          assertThat(audits.get(0).getDetails())
              .containsEntry("invoice_id", invoiceId.toString())
              .containsKey("invoice_number")
              .containsEntry("days_overdue", "70");
        });
  }

  @Test
  void notificationNotSent_whenCandidateTransactionRollsBackAfterEscalationRecorded() {
    // 80d overdue crosses the 60d escalate threshold. Force a failure AFTER the ESCALATION row +
    // pending-notification are recorded but BEFORE the candidate tx commits, by throwing from the
    // escalation audit write. The candidate REQUIRES_NEW tx rolls back, so the ESCALATION row is
    // never durable — and the notification (now fired only post-commit) must therefore NOT go out.
    // Pre-fix, the notification was sent inline before the audit write and would have leaked,
    // producing a duplicate on the next scan and breaking escalated-once.
    UUID invoiceId = seedSentInvoice("Rollback Co", 80);

    Mockito.doThrow(new RuntimeException("audit boom — forces candidate tx rollback"))
        .when(auditService)
        .log(
            Mockito.argThat(
                record ->
                    record != null && "collections.escalation.flagged".equals(record.eventType())));

    // Scan must not blow up — one bad candidate is swallowed by the per-candidate try/catch.
    runInTenant(scanService::scanForTenant);

    runInTenant(
        () -> {
          // Escalation row rolled back with the candidate tx — not durable.
          assertThat(
                  activityRepository.findByInvoiceIdAndStage(invoiceId, CollectionStage.ESCALATION))
              .isEmpty();
          // And crucially, no escalation notification was sent (fires only after commit now).
          assertThat(hasEscalationNotification(ownerMemberId, invoiceId)).isFalse();
          assertThat(hasEscalationNotification(adminMemberId, invoiceId)).isFalse();
        });
  }

  private boolean hasEscalationNotification(UUID recipientId, UUID invoiceId) {
    return notificationRepository
        .findByRecipientMemberId(recipientId, PageRequest.of(0, 100))
        .getContent()
        .stream()
        .anyMatch(
            (Notification n) ->
                "COLLECTION_ESCALATED".equals(n.getType())
                    && invoiceId.equals(n.getReferenceEntityId()));
  }
}
