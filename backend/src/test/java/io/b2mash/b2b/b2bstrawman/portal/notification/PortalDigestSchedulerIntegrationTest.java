package io.b2mash.b2b.b2bstrawman.portal.notification;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.util.GreenMail;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.portal.notification.PortalNotificationPreferenceService.PortalNotificationPreferenceUpdate;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.settings.PortalDigestCadence;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for {@link PortalDigestScheduler} (Epic 498B, Phase 68, ADR-258).
 *
 * <ol>
 *   <li>Populated portal contact → digest email sent via GreenMail.
 *   <li>Empty 7-day lookback → NO email (suppressed).
 *   <li>{@code digestEnabled=false} → NO email.
 *   <li>{@code cadence=BIWEEKLY} + {@code digest_last_sent_at} 5 days ago → skipped.
 *   <li>{@code cadence=OFF} → NO email.
 * </ol>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalDigestSchedulerIntegrationTest {

  private static final GreenMail greenMail =
      io.b2mash.b2b.b2bstrawman.testutil.GreenMailTestSupport.getInstance();

  private static final String ORG_ID = "org_portal_digest_sched_test";
  private static final String CONTACT_EMAIL = "digest-contact@test.com";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private MemberSyncService memberSyncService;
  @Autowired private PortalDigestScheduler scheduler;
  @Autowired private PortalContactRepository portalContactRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private PortalNotificationPreferenceService preferenceService;
  @Autowired private PortalReadModelRepository portalReadModelRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private UUID contactId;

  @BeforeAll
  void provisionTenant() {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Portal Digest Sched Test Org", null)
            .schemaName();
    memberId =
        memberSyncService
            .syncMember(
                ORG_ID, "user_digest_owner", "digest-owner@test.com", "Digest Owner", null, "owner")
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
                          createActiveCustomer("Digest Test Customer", CONTACT_EMAIL, memberId);
                      customer = customerRepository.save(customer);
                      customerId = customer.getId();

                      var contact =
                          new PortalContact(
                              ORG_ID,
                              customerId,
                              CONTACT_EMAIL,
                              "Digest Contact",
                              PortalContact.ContactRole.PRIMARY);
                      contact = portalContactRepository.save(contact);
                      contactId = contact.getId();
                    }));
  }

  @BeforeEach
  void resetState() throws Exception {
    greenMail.purgeEmailFromAllMailboxes();

    // Reset tenant-level state: cadence, digest_last_sent_at.
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var actor = new ActorContext(memberId, "owner");
                  orgSettingsService.updatePortalDigestCadence(PortalDigestCadence.WEEKLY, actor);
                  orgSettingsRepository
                      .findForCurrentTenant()
                      .ifPresent(
                          s -> {
                            s.clearDigestLastSent();
                            orgSettingsRepository.save(s);
                          });
                }));

    // Reset per-contact preference to all-on.
    runInTenant(
        () -> {
          preferenceService.getOrCreate(contactId);
          preferenceService.update(
              contactId, new PortalNotificationPreferenceUpdate(true, true, true, true, true));
        });

    // Clear portal read-model invoices seeded by previous tests for isolation.
    portalReadModelRepository.deletePortalInvoicesByOrg(ORG_ID);
  }

  @Test
  void populatedContact_receivesDigestEmail() throws Exception {
    // Seed a portal invoice so the assembler's digest bundle is non-empty.
    seedPortalInvoice(UUID.randomUUID(), "INV-DIGEST-1");

    runInTenant(() -> scheduler.runWeeklyDigest());

    // Wait briefly for the async SMTP delivery to arrive.
    assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();

    MimeMessage[] received = greenMail.getReceivedMessages();
    assertThat(received).isNotEmpty();
    // Filter to the digest recipient (other tests on the JVM may share the inbox briefly).
    boolean found = false;
    for (MimeMessage msg : received) {
      if (CONTACT_EMAIL.equals(msg.getAllRecipients()[0].toString())
          && msg.getSubject() != null
          && msg.getSubject().toLowerCase().contains("weekly update")) {
        found = true;
        break;
      }
    }
    assertThat(found).as("digest email with 'weekly update' subject should be received").isTrue();

    // digest_last_sent_at stamped after a successful run.
    Instant lastSent =
        runInTenantReturning(
            () ->
                orgSettingsRepository
                    .findForCurrentTenant()
                    .map(OrgSettings::getDigestLastSentAt)
                    .orElse(null));
    assertThat(lastSent).isNotNull();
  }

  @Test
  void emptyLookback_suppressesSend() throws Exception {
    // No portal invoices / trust / etc. seeded for this contact — everything is empty.
    runInTenant(() -> scheduler.runWeeklyDigest());

    // Wait a short window to be sure nothing arrives (scheduler is synchronous; email send is
    // fire-and-forget). 2s is enough when zero sends occur.
    boolean anything = greenMail.waitForIncomingEmail(2000, 1);
    // If something arrived it must not be the digest subject.
    MimeMessage[] received = greenMail.getReceivedMessages();
    boolean digestSent = false;
    for (MimeMessage msg : received) {
      if (msg.getSubject() != null && msg.getSubject().toLowerCase().contains("weekly update")) {
        digestSent = true;
        break;
      }
    }
    assertThat(digestSent)
        .as("no digest email should be sent when every section is empty")
        .isFalse();
  }

  @Test
  void digestDisabled_suppressesSend() throws Exception {
    // Seed content so content is not the blocker — it's the preference.
    seedPortalInvoice(UUID.randomUUID(), "INV-DIGEST-OFF");

    runInTenant(
        () ->
            preferenceService.update(
                contactId, new PortalNotificationPreferenceUpdate(false, true, true, true, true)));

    runInTenant(() -> scheduler.runWeeklyDigest());

    greenMail.waitForIncomingEmail(2000, 1);
    MimeMessage[] received = greenMail.getReceivedMessages();
    boolean digestSent = false;
    for (MimeMessage msg : received) {
      if (msg.getSubject() != null && msg.getSubject().toLowerCase().contains("weekly update")) {
        digestSent = true;
        break;
      }
    }
    assertThat(digestSent)
        .as("no digest email when contact opted out via digestEnabled=false")
        .isFalse();
  }

  @Test
  void biweekly_withRecentSend_skips() throws Exception {
    seedPortalInvoice(UUID.randomUUID(), "INV-DIGEST-BW");

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var actor = new ActorContext(memberId, "owner");
                  orgSettingsService.updatePortalDigestCadence(PortalDigestCadence.BIWEEKLY, actor);
                  orgSettingsRepository
                      .findForCurrentTenant()
                      .ifPresent(
                          s -> {
                            // Set last-sent 5 days ago — inside the 12-day BIWEEKLY skip window.
                            s.markDigestSent(Instant.now().minus(Duration.ofDays(5)));
                            orgSettingsRepository.save(s);
                          });
                }));

    runInTenant(() -> scheduler.runWeeklyDigest());

    greenMail.waitForIncomingEmail(2000, 1);
    MimeMessage[] received = greenMail.getReceivedMessages();
    boolean digestSent = false;
    for (MimeMessage msg : received) {
      if (msg.getSubject() != null && msg.getSubject().toLowerCase().contains("weekly update")) {
        digestSent = true;
        break;
      }
    }
    assertThat(digestSent)
        .as("BIWEEKLY cadence within 12-day skip window should not send")
        .isFalse();
  }

  @Test
  void offCadence_neverFires() throws Exception {
    seedPortalInvoice(UUID.randomUUID(), "INV-DIGEST-OFF2");

    runInTenant(
        () -> {
          var actor = new ActorContext(memberId, "owner");
          orgSettingsService.updatePortalDigestCadence(PortalDigestCadence.OFF, actor);
        });

    runInTenant(() -> scheduler.runWeeklyDigest());

    greenMail.waitForIncomingEmail(2000, 1);
    MimeMessage[] received = greenMail.getReceivedMessages();
    boolean digestSent = false;
    for (MimeMessage msg : received) {
      if (msg.getSubject() != null && msg.getSubject().toLowerCase().contains("weekly update")) {
        digestSent = true;
        break;
      }
    }
    assertThat(digestSent).as("cadence=OFF should never produce a digest send").isFalse();
  }

  // ── Helpers ─────────────────────────────────────────────────────────────

  private void seedPortalInvoice(UUID portalInvoiceId, String invoiceNumber) {
    // Directly upsert the portal read-model row — avoids needing a firm-side invoice lifecycle.
    portalReadModelRepository.upsertPortalInvoice(
        portalInvoiceId,
        ORG_ID,
        customerId,
        invoiceNumber,
        "SENT",
        LocalDate.now().minusDays(2),
        LocalDate.now().plusDays(28),
        new BigDecimal("100.00"),
        new BigDecimal("15.00"),
        new BigDecimal("115.00"),
        "USD",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        false,
        false);
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }

  private <T> T runInTenantReturning(java.util.concurrent.Callable<T> callable) throws Exception {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .call(callable::call);
  }
}
