package io.b2mash.b2b.b2bstrawman.portal.notification;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.util.GreenMail;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.event.FieldDateApproachingEvent;
import io.b2mash.b2b.b2bstrawman.event.InvoiceSentEvent;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailDeliveryLog;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailDeliveryLogRepository;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.retainer.event.RetainerPeriodRolloverEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event.TrustTransactionApprovalEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event.TrustTransactionRecordedEvent;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for {@link PortalEmailNotificationChannel} (Epic 498B, Phase 68, ADR-258).
 *
 * <ol>
 *   <li>{@link TrustTransactionApprovalEvent#approved(UUID, UUID, UUID, String, BigDecimal, UUID,
 *       UUID, UUID, String, String)} → {@code portal-trust-activity} email + delivery log entry
 *       with {@code referenceType=PORTAL_TRUST_ACTIVITY}.
 *   <li>{@link RetainerPeriodRolloverEvent} → {@code portal-retainer-period-closed} email +
 *       delivery log entry with {@code referenceType=PORTAL_RETAINER}.
 *   <li>{@link FieldDateApproachingEvent} → {@code portal-deadline-approaching} email + delivery
 *       log entry with {@code referenceType=PORTAL_DEADLINE}.
 *   <li>{@link InvoiceSentEvent} publish → ZERO new delivery-log rows with any of the new {@code
 *       PORTAL_TRUST_ACTIVITY} / {@code PORTAL_DEADLINE} / {@code PORTAL_RETAINER} reference types
 *       (no-double-send assertion per ADR-258).
 * </ol>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalEmailNotificationChannelIntegrationTest {

  private static final GreenMail greenMail =
      io.b2mash.b2b.b2bstrawman.testutil.GreenMailTestSupport.getInstance();

  private static final String ORG_ID = "org_portal_channel_test";
  private static final String CONTACT_EMAIL = "channel-contact@test.com";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private MemberSyncService memberSyncService;
  @Autowired private PortalContactRepository portalContactRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private EmailDeliveryLogRepository deliveryLogRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private UUID contactId;

  @BeforeAll
  void provisionTenant() {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Portal Channel Test Org", null).schemaName();
    memberId =
        memberSyncService
            .syncMember(
                ORG_ID,
                "user_channel_owner",
                "channel-owner@test.com",
                "Channel Owner",
                null,
                "owner")
            .memberId();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      Customer customer =
                          createActiveCustomer("Channel Test Customer", CONTACT_EMAIL, memberId);
                      customer = customerRepository.save(customer);
                      customerId = customer.getId();

                      var contact =
                          new PortalContact(
                              ORG_ID,
                              customerId,
                              CONTACT_EMAIL,
                              "Channel Contact",
                              PortalContact.ContactRole.PRIMARY);
                      contact = portalContactRepository.save(contact);
                      contactId = contact.getId();
                    }));
  }

  @BeforeEach
  void resetInbox() throws Exception {
    greenMail.purgeEmailFromAllMailboxes();
  }

  // ──────────────────────────────────────────────────────────────────────
  // Trust activity
  // ──────────────────────────────────────────────────────────────────────

  @Test
  void trustApprovalEvent_triggersTrustActivityEmail() throws Exception {
    UUID txnId = UUID.randomUUID();
    UUID trustAccountId = UUID.randomUUID();
    UUID projectId = UUID.randomUUID();

    publishInsideTenantTxn(
        () ->
            eventPublisher.publishEvent(
                TrustTransactionApprovalEvent.approved(
                    txnId,
                    trustAccountId,
                    projectId,
                    "DEPOSIT",
                    new BigDecimal("500.00"),
                    customerId,
                    memberId,
                    memberId,
                    tenantSchema,
                    ORG_ID)));

    assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
    MimeMessage[] received = greenMail.getReceivedMessages();
    MimeMessage matchedMessage = null;
    for (MimeMessage msg : received) {
      if (CONTACT_EMAIL.equals(msg.getAllRecipients()[0].toString())
          && msg.getSubject() != null
          && msg.getSubject().toLowerCase().contains("trust account activity")) {
        matchedMessage = msg;
        break;
      }
    }
    assertThat(matchedMessage).as("trust-activity email should be received").isNotNull();

    // BUG-CYCLE26-11: CTA href must deep-link by matter (project) id, NOT trust-account id.
    String body = extractBody(matchedMessage);
    assertThat(body)
        .as("CTA href must deep-link by matter (project) id")
        .contains("/trust/" + projectId);
    assertThat(body)
        .as("CTA href must NOT use trust-account id")
        .doesNotContain("/trust/" + trustAccountId);

    // Delivery log reference type stamped correctly.
    var logs = findLogsByReferenceType("PORTAL_TRUST_ACTIVITY");
    assertThat(logs)
        .as("delivery log should record referenceType=PORTAL_TRUST_ACTIVITY")
        .isNotEmpty();
  }

  // ──────────────────────────────────────────────────────────────────────
  // Trust deposit recorded (GAP-Trust-Nudge-Email-Missing) — DEPOSIT
  // bypasses the awaiting-approval flow and goes straight to RECORDED, so
  // without this listener the client never gets notified by email.
  // ──────────────────────────────────────────────────────────────────────

  @Test
  void trustRecordedEvent_depositTriggersTrustActivityEmail() throws Exception {
    UUID txnId = UUID.randomUUID();
    UUID trustAccountId = UUID.randomUUID();
    UUID projectId = UUID.randomUUID();

    publishInsideTenantTxn(
        () ->
            eventPublisher.publishEvent(
                TrustTransactionRecordedEvent.recorded(
                    txnId,
                    trustAccountId,
                    projectId,
                    "DEPOSIT",
                    new BigDecimal("750.00"),
                    customerId,
                    memberId,
                    tenantSchema,
                    ORG_ID)));

    assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
    MimeMessage[] received = greenMail.getReceivedMessages();
    MimeMessage matchedMessage = null;
    for (MimeMessage msg : received) {
      if (CONTACT_EMAIL.equals(msg.getAllRecipients()[0].toString())
          && msg.getSubject() != null
          && msg.getSubject().toLowerCase().contains("trust account activity")) {
        matchedMessage = msg;
        break;
      }
    }
    assertThat(matchedMessage)
        .as("trust-activity email should be received for DEPOSIT recorded event")
        .isNotNull();

    // BUG-CYCLE26-11: CTA href must deep-link by matter (project) id, NOT trust-account id.
    String body = extractBody(matchedMessage);
    assertThat(body)
        .as("CTA href must deep-link by matter (project) id")
        .contains("/trust/" + projectId);
    assertThat(body)
        .as("CTA href must NOT use trust-account id")
        .doesNotContain("/trust/" + trustAccountId);

    var logs = findLogsByReferenceType("PORTAL_TRUST_ACTIVITY");
    assertThat(logs)
        .as("delivery log should record referenceType=PORTAL_TRUST_ACTIVITY for DEPOSIT")
        .isNotEmpty();
  }

  @Test
  void trustRecordedEvent_nullProjectIdFallsBackToTrustIndex() throws Exception {
    UUID txnId = UUID.randomUUID();
    UUID trustAccountId = UUID.randomUUID();

    publishInsideTenantTxn(
        () ->
            eventPublisher.publishEvent(
                TrustTransactionRecordedEvent.recorded(
                    txnId,
                    trustAccountId,
                    null, // projectId — TRANSFER_OUT/TRANSFER_IN cross-customer txns may have no
                    // matter
                    "DEPOSIT",
                    new BigDecimal("125.00"),
                    customerId,
                    memberId,
                    tenantSchema,
                    ORG_ID)));

    assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
    MimeMessage[] received = greenMail.getReceivedMessages();
    MimeMessage matchedMessage = null;
    for (MimeMessage msg : received) {
      if (CONTACT_EMAIL.equals(msg.getAllRecipients()[0].toString())
          && msg.getSubject() != null
          && msg.getSubject().toLowerCase().contains("trust account activity")) {
        matchedMessage = msg;
        break;
      }
    }
    assertThat(matchedMessage)
        .as("trust-activity email should be received even when projectId is null")
        .isNotNull();

    // BUG-CYCLE26-11 regression: when projectId is null, CTA must point at /trust index,
    // never at /trust/null or /trust/{trustAccountId}.
    String body = extractBody(matchedMessage);
    assertThat(body).as("CTA href must NOT contain /trust/null").doesNotContain("/trust/null");
    assertThat(body)
        .as("CTA href must NOT fall back to trust-account id")
        .doesNotContain("/trust/" + trustAccountId);
    assertThat(body)
        .as("CTA href must point at /trust index when projectId is null")
        .containsPattern("href=\"[^\"]+/trust\"");
  }

  @Test
  void trustRecordedEvent_nonDepositDoesNotTriggerEmail() throws Exception {
    int before = countNewChannelLogs();

    publishInsideTenantTxn(
        () ->
            eventPublisher.publishEvent(
                TrustTransactionRecordedEvent.recorded(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "TRANSFER_OUT",
                    new BigDecimal("250.00"),
                    customerId,
                    memberId,
                    tenantSchema,
                    ORG_ID)));

    // Wait briefly to let any phantom listeners run; expect none.
    greenMail.waitForIncomingEmail(2000, 1);

    int after = countNewChannelLogs();
    assertThat(after)
        .as(
            "Non-DEPOSIT recorded events (TRANSFER_OUT/TRANSFER_IN) must NOT trigger a portal email "
                + "— WITHDRAWAL/FEE_TRANSFER/REFUND have their own notifications coming in E5.1, "
                + "and double-sending is forbidden by ADR-258.")
        .isEqualTo(before);
  }

  // ──────────────────────────────────────────────────────────────────────
  // Retainer period rollover
  // ──────────────────────────────────────────────────────────────────────

  @Test
  void retainerRolloverEvent_triggersRetainerClosedEmail() throws Exception {
    publishInsideTenantTxn(
        () ->
            eventPublisher.publishEvent(
                new RetainerPeriodRolloverEvent(
                    UUID.randomUUID(),
                    customerId,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    LocalDate.now(),
                    LocalDate.now().plusMonths(1),
                    new BigDecimal("2.0"),
                    new BigDecimal("10.0"),
                    LocalDate.now().plusMonths(1),
                    tenantSchema,
                    ORG_ID,
                    Instant.now())));

    assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
    MimeMessage[] received = greenMail.getReceivedMessages();
    boolean matched = false;
    for (MimeMessage msg : received) {
      if (CONTACT_EMAIL.equals(msg.getAllRecipients()[0].toString())
          && msg.getSubject() != null
          && msg.getSubject().toLowerCase().contains("retainer period has closed")) {
        matched = true;
        break;
      }
    }
    assertThat(matched).as("retainer-period-closed email should be received").isTrue();

    var logs = findLogsByReferenceType("PORTAL_RETAINER");
    assertThat(logs).as("delivery log should record referenceType=PORTAL_RETAINER").isNotEmpty();
  }

  // ──────────────────────────────────────────────────────────────────────
  // Field date approaching (custom-field deadline)
  // ──────────────────────────────────────────────────────────────────────

  @Test
  void fieldDateApproachingEvent_triggersDeadlineEmail() throws Exception {
    LocalDate dueDate = LocalDate.now().plusDays(5);
    Map<String, Object> details = new HashMap<>();
    details.put("field_name", "trust_renewal_date");
    details.put("field_label", "Trust Renewal Date");
    details.put("field_value", dueDate.toString());
    details.put("days_until", 5);
    details.put("entity_name", "Channel Test Customer");

    publishInsideTenantTxn(
        () ->
            eventPublisher.publishEvent(
                new FieldDateApproachingEvent(
                    "field_date.approaching",
                    "CUSTOMER",
                    customerId,
                    null,
                    null,
                    "system",
                    tenantSchema,
                    ORG_ID,
                    Instant.now(),
                    details)));

    assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
    MimeMessage[] received = greenMail.getReceivedMessages();
    boolean matched = false;
    for (MimeMessage msg : received) {
      if (CONTACT_EMAIL.equals(msg.getAllRecipients()[0].toString())
          && msg.getSubject() != null
          && msg.getSubject().toLowerCase().startsWith("upcoming deadline")) {
        matched = true;
        break;
      }
    }
    assertThat(matched).as("deadline-approaching email should be received").isTrue();

    var logs = findLogsByReferenceType("PORTAL_DEADLINE");
    assertThat(logs).as("delivery log should record referenceType=PORTAL_DEADLINE").isNotEmpty();
  }

  // ──────────────────────────────────────────────────────────────────────
  // No-double-send: channel must NOT listen to InvoiceSentEvent (ADR-258).
  // ──────────────────────────────────────────────────────────────────────

  @Test
  void invoiceSentEvent_doesNotFireChannelSend() throws Exception {
    // Count existing new-channel delivery log rows. These should not change after the publish.
    int before = countNewChannelLogs();

    Map<String, Object> details = new HashMap<>();
    details.put("entity_name", "Channel Test Customer");

    publishInsideTenantTxn(
        () ->
            eventPublisher.publishEvent(
                new InvoiceSentEvent(
                    "invoice.sent",
                    "INVOICE",
                    UUID.randomUUID(),
                    null,
                    memberId,
                    "Channel Owner",
                    tenantSchema,
                    ORG_ID,
                    Instant.now(),
                    details,
                    memberId,
                    "INV-NO-DOUBLE-1",
                    "Channel Test Customer")));

    // Wait a small window for any phantom listeners to run; we expect none.
    greenMail.waitForIncomingEmail(2000, 1);

    int after = countNewChannelLogs();
    assertThat(after)
        .as(
            "PortalEmailNotificationChannel must not subscribe to InvoiceSentEvent — "
                + "no new PORTAL_TRUST_ACTIVITY/PORTAL_DEADLINE/PORTAL_RETAINER rows expected")
        .isEqualTo(before);
  }

  /**
   * Reflection-based scope assertion (ADR-258). Verifies the channel subscribes ONLY to the three
   * allowed event types. This guards against future regressions where a listener is added for
   * {@code InvoiceSentEvent}, {@code AcceptanceRequestSentEvent}, {@code ProposalSentEvent}, or
   * {@code InformationRequestSentEvent} — all of which already have upstream email senders and
   * would cause double-sends if the channel also fired on them.
   */
  @Test
  void channelSubscribesOnlyToAllowedEventTypes() {
    java.util.Set<Class<?>> allowed =
        java.util.Set.of(
            TrustTransactionApprovalEvent.class,
            TrustTransactionRecordedEvent.class,
            RetainerPeriodRolloverEvent.class,
            FieldDateApproachingEvent.class);

    java.util.List<Class<?>> listenerParams = new java.util.ArrayList<>();
    for (java.lang.reflect.Method m : PortalEmailNotificationChannel.class.getDeclaredMethods()) {
      if (m.isAnnotationPresent(
              org.springframework.transaction.event.TransactionalEventListener.class)
          || m.isAnnotationPresent(org.springframework.context.event.EventListener.class)) {
        assertThat(m.getParameterCount())
            .as(
                "@TransactionalEventListener method %s must take exactly one parameter",
                m.getName())
            .isEqualTo(1);
        listenerParams.add(m.getParameterTypes()[0]);
      }
    }

    assertThat(listenerParams)
        .as(
            "PortalEmailNotificationChannel listener parameter types must be a subset of the three "
                + "allowed events (ADR-258); any other event type would cause double-sends")
        .isNotEmpty()
        .allSatisfy(p -> assertThat(allowed).contains(p));
  }

  // ── helpers ────────────────────────────────────────────────────────────

  private int countNewChannelLogs() throws Exception {
    return runInTenantReturning(
        () ->
            transactionTemplate.execute(
                tx -> {
                  List<EmailDeliveryLog> all = deliveryLogRepository.findAll();
                  int n = 0;
                  for (EmailDeliveryLog log : all) {
                    String rt = log.getReferenceType();
                    if ("PORTAL_TRUST_ACTIVITY".equals(rt)
                        || "PORTAL_DEADLINE".equals(rt)
                        || "PORTAL_RETAINER".equals(rt)) {
                      n++;
                    }
                  }
                  return n;
                }));
  }

  private List<EmailDeliveryLog> findLogsByReferenceType(String referenceType) throws Exception {
    return runInTenantReturning(
        () ->
            transactionTemplate.execute(
                tx ->
                    deliveryLogRepository.findAll().stream()
                        .filter(l -> referenceType.equals(l.getReferenceType()))
                        .toList()));
  }

  /**
   * Publishes the event inside a committed tenant transaction so {@code AFTER_COMMIT} listeners
   * fire. Mirrors {@code TrustLedgerPortalSyncServiceIntegrationTest#publishApprovedEvent}.
   */
  private void publishInsideTenantTxn(Runnable publish) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(() -> transactionTemplate.executeWithoutResult(tx -> publish.run()));
  }

  private <T> T runInTenantReturning(java.util.concurrent.Callable<T> callable) throws Exception {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .call(callable::call);
  }

  /**
   * Extracts the textual body of a multipart MimeMessage so we can grep for CTA hrefs. Walks
   * jakarta.mail multipart structures so HTML parts (where the {@code href} lives) are surfaced.
   */
  private static String extractBody(MimeMessage message) throws Exception {
    Object content = message.getContent();
    if (content instanceof String s) {
      return s;
    }
    StringBuilder out = new StringBuilder();
    if (content instanceof jakarta.mail.Multipart multipart) {
      collectMultipart(multipart, out);
    }
    return out.toString();
  }

  private static void collectMultipart(jakarta.mail.Multipart multipart, StringBuilder out)
      throws Exception {
    for (int i = 0; i < multipart.getCount(); i++) {
      var part = multipart.getBodyPart(i);
      Object inner = part.getContent();
      if (inner instanceof String s) {
        out.append(s);
      } else if (inner instanceof jakarta.mail.Multipart nested) {
        collectMultipart(nested, out);
      }
    }
  }
}
