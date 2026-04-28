package io.b2mash.b2b.b2bstrawman.portal.notification;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for {@link PortalDigestInternalController} (GAP-L-99).
 *
 * <p>Exercises the manual {@code POST /internal/portal/digest/run-weekly} trigger end-to-end
 * through MockMvc + the existing {@code ApiKeyAuthFilter} (header {@code X-API-KEY}). The trigger
 * delegates to {@link PortalDigestScheduler#runWeeklyDigest(PortalDigestScheduler.RunOptions)};
 * cron path is verified separately by {@code PortalDigestSchedulerIntegrationTest}.
 *
 * <p>Cases:
 *
 * <ol>
 *   <li>{@code targetEmail} restricts the send to a single contact (1 of N).
 *   <li>Full sweep without {@code targetEmail} sends to every eligible contact.
 *   <li>{@code dryRun=true} reports counts but performs no SMTP delivery and does not stamp {@code
 *       digestLastSentAt}.
 *   <li>Missing {@code X-API-KEY} → 401 (gated by {@code ApiKeyAuthFilter}).
 *   <li>Unknown {@code orgId} → 404 (mirrors {@code InternalAuditController.resolveSchema}).
 *   <li>Idempotency: a second call with the same target produces a consistent result.
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalDigestInternalControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_portal_digest_internal_test";
  private static final String CONTACT_A_EMAIL = "digest-a@test.com";
  private static final String CONTACT_B_EMAIL = "digest-b@test.com";

  private static final GreenMail greenMail =
      io.b2mash.b2b.b2bstrawman.testutil.GreenMailTestSupport.getInstance();

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private MemberSyncService memberSyncService;
  @Autowired private PortalContactRepository portalContactRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private PortalNotificationPreferenceService preferenceService;
  @Autowired private PortalReadModelRepository portalReadModelRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerAId;
  private UUID customerBId;
  private UUID contactAId;
  private UUID contactBId;

  @BeforeAll
  void provisionTenant() {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Portal Digest Internal Test Org", null)
            .schemaName();
    memberId =
        memberSyncService
            .syncMember(
                ORG_ID,
                "user_digest_internal_owner",
                "digest-internal-owner@test.com",
                "Digest Internal Owner",
                null,
                "owner")
            .memberId();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  Customer customerA =
                      createActiveCustomer("Digest A Customer", CONTACT_A_EMAIL, memberId);
                  customerA = customerRepository.save(customerA);
                  customerAId = customerA.getId();
                  PortalContact contactA =
                      portalContactRepository.save(
                          new PortalContact(
                              ORG_ID,
                              customerAId,
                              CONTACT_A_EMAIL,
                              "Digest Contact A",
                              PortalContact.ContactRole.PRIMARY));
                  contactAId = contactA.getId();

                  Customer customerB =
                      createActiveCustomer("Digest B Customer", CONTACT_B_EMAIL, memberId);
                  customerB = customerRepository.save(customerB);
                  customerBId = customerB.getId();
                  PortalContact contactB =
                      portalContactRepository.save(
                          new PortalContact(
                              ORG_ID,
                              customerBId,
                              CONTACT_B_EMAIL,
                              "Digest Contact B",
                              PortalContact.ContactRole.PRIMARY));
                  contactBId = contactB.getId();
                }));
  }

  @BeforeEach
  void resetState() throws Exception {
    greenMail.purgeEmailFromAllMailboxes();

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

    runInTenant(
        () -> {
          preferenceService.getOrCreate(contactAId);
          preferenceService.update(
              contactAId, new PortalNotificationPreferenceUpdate(true, true, true, true, true));
          preferenceService.getOrCreate(contactBId);
          preferenceService.update(
              contactBId, new PortalNotificationPreferenceUpdate(true, true, true, true, true));
        });

    portalReadModelRepository.deletePortalInvoicesByOrg(ORG_ID);
  }

  @Test
  void runWeekly_withTargetEmail_sendsToSingleContact() throws Exception {
    // Seed content for both contacts.
    seedPortalInvoiceFor(customerAId, "INV-A-1");
    seedPortalInvoiceFor(customerBId, "INV-B-1");

    mockMvc
        .perform(
            post("/internal/portal/digest/run-weekly")
                .header("X-API-KEY", API_KEY)
                .param("orgId", ORG_ID)
                .param("targetEmail", CONTACT_A_EMAIL))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantsProcessed").value(1))
        .andExpect(jsonPath("$.digestsSent").value(1))
        .andExpect(jsonPath("$.dryRun").value(false))
        .andExpect(jsonPath("$.errors").isArray())
        .andExpect(jsonPath("$.errors").isEmpty());

    // Wait for fire-and-forget SMTP delivery.
    assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
    assertThat(countDigestRecipients(greenMail.getReceivedMessages(), CONTACT_A_EMAIL))
        .as("digest delivered to the single targeted contact")
        .isEqualTo(1);
    assertThat(countDigestRecipients(greenMail.getReceivedMessages(), CONTACT_B_EMAIL))
        .as("non-targeted contact must not receive a digest")
        .isZero();
  }

  @Test
  void runWeekly_fullSweep_sendsToAllEligibleContacts() throws Exception {
    seedPortalInvoiceFor(customerAId, "INV-A-2");
    seedPortalInvoiceFor(customerBId, "INV-B-2");

    mockMvc
        .perform(
            post("/internal/portal/digest/run-weekly")
                .header("X-API-KEY", API_KEY)
                .param("orgId", ORG_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantsProcessed").value(1))
        .andExpect(jsonPath("$.digestsSent").value(2))
        .andExpect(jsonPath("$.dryRun").value(false));

    assertThat(greenMail.waitForIncomingEmail(5000, 2)).isTrue();
    assertThat(countDigestRecipients(greenMail.getReceivedMessages(), CONTACT_A_EMAIL))
        .isEqualTo(1);
    assertThat(countDigestRecipients(greenMail.getReceivedMessages(), CONTACT_B_EMAIL))
        .isEqualTo(1);
  }

  @Test
  void runWeekly_dryRun_doesNotSend() throws Exception {
    seedPortalInvoiceFor(customerAId, "INV-A-DRY");
    seedPortalInvoiceFor(customerBId, "INV-B-DRY");

    mockMvc
        .perform(
            post("/internal/portal/digest/run-weekly")
                .header("X-API-KEY", API_KEY)
                .param("orgId", ORG_ID)
                .param("dryRun", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantsProcessed").value(1))
        .andExpect(jsonPath("$.digestsSent").value(2))
        .andExpect(jsonPath("$.dryRun").value(true));

    // Wait briefly to be sure no SMTP delivery occurs.
    greenMail.waitForIncomingEmail(2000, 1);
    assertThat(countDigestRecipients(greenMail.getReceivedMessages(), CONTACT_A_EMAIL))
        .as("dryRun must not deliver to contact A")
        .isZero();
    assertThat(countDigestRecipients(greenMail.getReceivedMessages(), CONTACT_B_EMAIL))
        .as("dryRun must not deliver to contact B")
        .isZero();

    // digestLastSentAt must NOT be stamped on a dry run.
    Instant lastSent =
        runInTenantReturning(
            () ->
                orgSettingsRepository
                    .findForCurrentTenant()
                    .map(OrgSettings::getDigestLastSentAt)
                    .orElse(null));
    assertThat(lastSent).as("dryRun must not stamp digestLastSentAt").isNull();
  }

  @Test
  void runWeekly_unauthorized_returns401_withoutApiKey() throws Exception {
    mockMvc
        .perform(post("/internal/portal/digest/run-weekly").param("orgId", ORG_ID))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void runWeekly_unknownOrgId_returns404() throws Exception {
    mockMvc
        .perform(
            post("/internal/portal/digest/run-weekly")
                .header("X-API-KEY", API_KEY)
                .param("orgId", "org_does_not_exist"))
        .andExpect(status().isNotFound());
  }

  @Test
  void runWeekly_idempotency_secondCallProducesSameShape() throws Exception {
    seedPortalInvoiceFor(customerAId, "INV-A-IDEMP");

    // First call.
    mockMvc
        .perform(
            post("/internal/portal/digest/run-weekly")
                .header("X-API-KEY", API_KEY)
                .param("orgId", ORG_ID)
                .param("targetEmail", CONTACT_A_EMAIL))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.digestsSent").value(1))
        .andExpect(jsonPath("$.errors").isEmpty());

    assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
    int afterFirst = countDigestRecipients(greenMail.getReceivedMessages(), CONTACT_A_EMAIL);
    assertThat(afterFirst).isEqualTo(1);

    // Second call — cadence is WEEKLY (set in @BeforeEach), so the BIWEEKLY skip window does not
    // apply. Both calls should fire and complete cleanly with no errors. We assert the second call
    // returns the same response shape (200 + no errors); the implementation does not artificially
    // suppress repeats.
    mockMvc
        .perform(
            post("/internal/portal/digest/run-weekly")
                .header("X-API-KEY", API_KEY)
                .param("orgId", ORG_ID)
                .param("targetEmail", CONTACT_A_EMAIL))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantsProcessed").value(1))
        .andExpect(jsonPath("$.errors").isEmpty());
  }

  // ── Helpers ─────────────────────────────────────────────────────────────

  /**
   * Counts digest emails ("weekly update" subject) addressed to the given recipient. Filters by
   * recipient because the JVM-singleton GreenMail inbox is shared with other test classes.
   */
  private int countDigestRecipients(MimeMessage[] received, String recipient) throws Exception {
    int count = 0;
    for (MimeMessage msg : received) {
      if (msg.getSubject() == null
          || !msg.getSubject().toLowerCase().contains("weekly update")
          || msg.getAllRecipients() == null
          || msg.getAllRecipients().length == 0) {
        continue;
      }
      if (recipient.equals(msg.getAllRecipients()[0].toString())) {
        count++;
      }
    }
    return count;
  }

  private void seedPortalInvoiceFor(UUID customerId, String invoiceNumber) {
    portalReadModelRepository.upsertPortalInvoice(
        UUID.randomUUID(),
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
