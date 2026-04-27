package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.automation.AutomationRuleRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustNotificationHandlerTest {

  private static final String ORG_ID = "org_trust_notif_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private AutomationRuleRepository automationRuleRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Trust Notification Test Org", "legal-za")
            .schemaName();

    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_trust_notif_owner",
                "trust_notif@test.com",
                "Trust Notif Owner",
                "owner"));
  }

  @Test
  void awaitingApprovalEvent_sendsNotificationsToApprovers() {
    // Publish an awaiting approval event inside a transaction so @TransactionalEventListener fires
    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var event =
                      TrustTransactionApprovalEvent.awaitingApproval(
                          UUID.randomUUID(),
                          UUID.randomUUID(),
                          UUID.randomUUID(),
                          "PAYMENT",
                          new BigDecimal("5000.00"),
                          UUID.randomUUID(),
                          memberId,
                          tenantSchema,
                          ORG_ID);
                  eventPublisher.publishEvent(event);
                }));

    // After the transaction commits, the event handler should have created notifications
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var notifications = notificationRepository.findAll();
                  var trustNotifications =
                      notifications.stream()
                          .filter(n -> "TRUST_PAYMENT_AWAITING_APPROVAL".equals(n.getType()))
                          .toList();
                  assertThat(trustNotifications).isNotEmpty();
                  assertThat(trustNotifications)
                      .allSatisfy(
                          n -> {
                            assertThat(n.getTitle()).contains("payment");
                            assertThat(n.getTitle()).contains("requires approval");
                          });
                }));
  }

  @Test
  void approvedEvent_notifiesRecorder() {
    UUID recorderId = memberId;
    UUID approverId = UUID.randomUUID();

    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var event =
                      TrustTransactionApprovalEvent.approved(
                          UUID.randomUUID(),
                          UUID.randomUUID(),
                          UUID.randomUUID(),
                          "PAYMENT",
                          new BigDecimal("3000.00"),
                          UUID.randomUUID(),
                          recorderId,
                          approverId,
                          tenantSchema,
                          ORG_ID);
                  eventPublisher.publishEvent(event);
                }));

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var notifications = notificationRepository.findAll();
                  var approvedNotifications =
                      notifications.stream()
                          .filter(n -> "TRUST_PAYMENT_APPROVED".equals(n.getType()))
                          .toList();
                  assertThat(approvedNotifications).isNotEmpty();
                  // The notification should be sent to the recorder
                  assertThat(approvedNotifications)
                      .anyMatch(n -> n.getRecipientMemberId().equals(recorderId));
                  assertThat(approvedNotifications)
                      .allSatisfy(
                          n -> {
                            assertThat(n.getTitle()).contains("approved");
                          });
                }));
  }

  @Test
  void rejectedEvent_notifiesRecorderWithReason() {
    UUID recorderId = memberId;
    UUID rejecterId = UUID.randomUUID();

    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var event =
                      TrustTransactionApprovalEvent.rejected(
                          UUID.randomUUID(),
                          UUID.randomUUID(),
                          UUID.randomUUID(),
                          "FEE_TRANSFER",
                          new BigDecimal("1500.00"),
                          UUID.randomUUID(),
                          recorderId,
                          rejecterId,
                          tenantSchema,
                          ORG_ID);
                  eventPublisher.publishEvent(event);
                }));

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var notifications = notificationRepository.findAll();
                  var rejectedNotifications =
                      notifications.stream()
                          .filter(n -> "TRUST_PAYMENT_REJECTED".equals(n.getType()))
                          .toList();
                  assertThat(rejectedNotifications).isNotEmpty();
                  // The notification should be sent to the recorder
                  assertThat(rejectedNotifications)
                      .anyMatch(n -> n.getRecipientMemberId().equals(recorderId));
                  assertThat(rejectedNotifications)
                      .allSatisfy(
                          n -> {
                            assertThat(n.getTitle()).contains("rejected");
                          });
                }));
  }

  @Test
  void trustTemplates_presentInLegalZaPack() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var templates = documentTemplateRepository.findByActiveTrueOrderBySortOrder();
                  var legalZaTemplates =
                      templates.stream().filter(t -> "legal-za".equals(t.getPackId())).toList();

                  // Verify the 3 trust-specific templates are present
                  var templateNames = legalZaTemplates.stream().map(t -> t.getName()).toList();
                  assertThat(templateNames).contains("Client Trust Statement");
                  assertThat(templateNames).contains("Trust Receipt");
                  assertThat(templateNames).contains("Section 35 Cover Letter");
                }));
  }

  @Test
  void reconciliationCompletedEvent_notifiesAdminsAndOwners() {
    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var event =
                      TrustDomainEvent.ReconciliationCompleted.of(
                          UUID.randomUUID(),
                          UUID.randomUUID(),
                          LocalDate.of(2026, 3, 31),
                          memberId,
                          tenantSchema,
                          ORG_ID);
                  eventPublisher.publishEvent(event);
                }));

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var notifications = notificationRepository.findAll();
                  var reconciliationNotifications =
                      notifications.stream()
                          .filter(n -> "TRUST_RECONCILIATION_COMPLETED".equals(n.getType()))
                          .toList();
                  assertThat(reconciliationNotifications).isNotEmpty();
                  assertThat(reconciliationNotifications)
                      .allSatisfy(
                          n -> {
                            assertThat(n.getTitle()).contains("reconciliation completed");
                            assertThat(n.getBody()).contains("2026-03-31");
                          });
                }));
  }

  @Test
  void investmentMaturingEvent_notifiesAdminsAndOwners() {
    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var event =
                      TrustDomainEvent.InvestmentMaturing.of(
                          UUID.randomUUID(),
                          UUID.randomUUID(),
                          UUID.randomUUID(),
                          "First National Bank",
                          new BigDecimal("100000.00"),
                          LocalDate.of(2026, 5, 15),
                          30,
                          tenantSchema,
                          ORG_ID);
                  eventPublisher.publishEvent(event);
                }));

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var notifications = notificationRepository.findAll();
                  var investmentNotifications =
                      notifications.stream()
                          .filter(n -> "TRUST_INVESTMENT_MATURING".equals(n.getType()))
                          .toList();
                  assertThat(investmentNotifications).isNotEmpty();
                  assertThat(investmentNotifications)
                      .allSatisfy(
                          n -> {
                            assertThat(n.getTitle()).contains("maturing in 30 days");
                            assertThat(n.getBody()).contains("First National Bank");
                          });
                }));
  }

  @Test
  void interestPostedEvent_notifiesAdminsAndOwners() {
    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var event =
                      TrustDomainEvent.InterestPosted.of(
                          UUID.randomUUID(),
                          UUID.randomUUID(),
                          LocalDate.of(2026, 1, 1),
                          LocalDate.of(2026, 3, 31),
                          new BigDecimal("5000.00"),
                          new BigDecimal("4250.00"),
                          memberId,
                          tenantSchema,
                          ORG_ID);
                  eventPublisher.publishEvent(event);
                }));

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var notifications = notificationRepository.findAll();
                  var interestNotifications =
                      notifications.stream()
                          .filter(n -> "TRUST_INTEREST_POSTED".equals(n.getType()))
                          .toList();
                  assertThat(interestNotifications).isNotEmpty();
                  assertThat(interestNotifications)
                      .allSatisfy(
                          n -> {
                            assertThat(n.getTitle()).contains("Interest run posted");
                            assertThat(n.getBody()).contains("R5000.00");
                            assertThat(n.getBody()).contains("R4250.00");
                          });
                }));
  }

  @Test
  void automationRules_presentInLegalZaPack() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var rules = automationRuleRepository.findAllByOrderByCreatedAtDesc();

                  var ruleNames = rules.stream().map(r -> r.getName()).toList();
                  assertThat(ruleNames).contains("Investment Maturity Reminder");
                  assertThat(ruleNames).contains("Reconciliation Overdue Reminder");
                  assertThat(ruleNames).contains("Pending Approval Aging");
                }));
  }

  // --- Helpers ---

  private void runInTenantWithCapabilities(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(
            RequestScopes.CAPABILITIES,
            Set.of("APPROVE_TRUST_PAYMENT", "MANAGE_TRUST", "VIEW_TRUST"))
        .run(action);
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
