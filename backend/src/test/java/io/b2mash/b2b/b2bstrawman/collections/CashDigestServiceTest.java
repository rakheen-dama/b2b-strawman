package io.b2mash.b2b.b2bstrawman.collections;

import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.util.GreenMail;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfile;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfileRepository;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailDeliveryLog;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailDeliveryLogRepository;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailDeliveryStatus;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.Notification;
import io.b2mash.b2b.b2bstrawman.notification.NotificationPreference;
import io.b2mash.b2b.b2bstrawman.notification.NotificationPreferenceRepository;
import io.b2mash.b2b.b2bstrawman.notification.NotificationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.testutil.GreenMailTestSupport;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 593B.3 — end-to-end digest delivery (§8.4 digest row). AI-enabled (stub) → narrative + risks in
 * the GreenMail-observed email + bell for owners/admins ONLY; AI-disabled → numbers-only email (no
 * narrative block), still delivered; a muted owner gets the email but no bell; a plain member gets
 * neither; one delivery-log row per recipient; exactly one {@code collections.digest.sent} audit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CashDigestServiceTest {

  private static final GreenMail greenMail = GreenMailTestSupport.getInstance();

  private static final String AI_ORG_ID = "org_cash_digest_ai_test";
  private static final String NOAI_ORG_ID = "org_cash_digest_noai_test";

  private static final String NARRATIVE_SENTINEL = "STUB-DIGEST-NARRATIVE";
  // Distinctive prose from the stub AI risk's `why`. The stub risk is grounded to the seeded debtor
  // (DETERMINISTIC_DEBTOR) so the narrate() grounding filter keeps it; this sentinel — which only
  // renders inside the aiRisks block — proves the AI risk survived and rendered.
  private static final String AI_RISK_SENTINEL = "STUB-DIGEST-RISK-WHY";
  private static final String DETERMINISTIC_DEBTOR = "Real Debtor Co";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private AiFirmProfileRepository firmProfileRepository;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private NotificationPreferenceRepository preferenceRepository;
  @Autowired private EmailDeliveryLogRepository deliveryLogRepository;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private CashDigestService cashDigestService;

  // AI-enabled tenant members.
  private String aiSchema;
  private UUID aiOwnerId;
  private UUID aiAdminId;
  private UUID aiMemberId;
  private UUID aiMutedOwnerId;
  private String aiOwnerEmail = "cd_ai_owner@test.com";
  private String aiAdminEmail = "cd_ai_admin@test.com";
  private String aiMemberEmail = "cd_ai_member@test.com";
  private String aiMutedOwnerEmail = "cd_ai_muted@test.com";

  // AI-disabled tenant.
  private String noAiSchema;
  private UUID noAiOwnerId;
  private String noAiOwnerEmail = "cd_noai_owner@test.com";

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(AI_ORG_ID, "Cash Digest AI Test Org", null);
    aiOwnerId = sync(AI_ORG_ID, "user_cd_ai_owner", aiOwnerEmail, "AI Owner", "owner");
    aiAdminId = sync(AI_ORG_ID, "user_cd_ai_admin", aiAdminEmail, "AI Admin", "admin");
    aiMemberId = sync(AI_ORG_ID, "user_cd_ai_member", aiMemberEmail, "AI Member", "member");
    aiMutedOwnerId = sync(AI_ORG_ID, "user_cd_ai_muted", aiMutedOwnerEmail, "AI Muted", "owner");
    aiSchema = orgSchemaMappingRepository.findByClerkOrgId(AI_ORG_ID).orElseThrow().getSchemaName();
    runIn(
        aiSchema,
        AI_ORG_ID,
        aiOwnerId,
        () -> {
          if (firmProfileRepository.findAll().isEmpty()) {
            firmProfileRepository.save(new AiFirmProfile(aiOwnerId));
          }
          // Mute CASH_DIGEST in-app for the muted owner — email must still go out.
          preferenceRepository.save(
              new NotificationPreference(aiMutedOwnerId, "CASH_DIGEST", false, true));
          seedOverdueInvoice();
        });

    provisioningService.provisionTenant(NOAI_ORG_ID, "Cash Digest No-AI Test Org", null);
    noAiOwnerId = sync(NOAI_ORG_ID, "user_cd_noai_owner", noAiOwnerEmail, "NoAI Owner", "owner");
    noAiSchema =
        orgSchemaMappingRepository.findByClerkOrgId(NOAI_ORG_ID).orElseThrow().getSchemaName();
    // Deliberately NO firm-profile row → pre-flight (b) fails → numbers-only fallback.
    runIn(noAiSchema, NOAI_ORG_ID, noAiOwnerId, this::seedOverdueInvoice);
  }

  @BeforeEach
  void purgeMailbox() throws Exception {
    greenMail.purgeEmailFromAllMailboxes();
  }

  private UUID sync(String orgId, String subject, String email, String name, String role)
      throws Exception {
    return UUID.fromString(TestMemberHelper.syncMember(mockMvc, orgId, subject, email, name, role));
  }

  private static void runIn(String schema, String orgId, UUID member, Runnable body) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
        .where(RequestScopes.MEMBER_ID, member)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(body);
  }

  /** One SENT invoice overdue by 40 days so the debtor book has a deterministic top-risk row. */
  private void seedOverdueInvoice() {
    Customer customer =
        customerRepository.save(
            TestCustomerFactory.createActiveCustomer(
                DETERMINISTIC_DEBTOR, "realdebtor@test.com", RequestScopes.MEMBER_ID.get()));
    var invoice =
        new Invoice(
            customer.getId(),
            "ZAR",
            DETERMINISTIC_DEBTOR,
            "realdebtor@test.com",
            null,
            "Test Org",
            RequestScopes.MEMBER_ID.get());
    invoice.updateDraft(LocalDate.now().minusDays(40), null, null, BigDecimal.ZERO);
    invoice.recalculateTotals(BigDecimal.valueOf(2500), false, BigDecimal.ZERO, false);
    invoice.approve(
        "INV-" + UUID.randomUUID().toString().substring(0, 8), RequestScopes.MEMBER_ID.get());
    invoice.markSent();
    invoiceRepository.save(invoice);
  }

  @Test
  void aiEnabled_narrativeEmailAndBellForOwnersAdmins_memberGetsNeither_mutedGetsEmailOnly()
      throws Exception {
    runIn(aiSchema, AI_ORG_ID, aiOwnerId, () -> cashDigestService.processTenant());

    // Three owner/admin recipients received email; the plain member did not.
    assertThat(greenMail.waitForIncomingEmail(5_000L, 3)).isTrue();
    MimeMessage[] received = greenMail.getReceivedMessages();
    var recipients = recipientAddresses(received);
    // Exactly one email per owner/admin (three), none to the plain member — no duplicate sends.
    assertThat(recipients).containsExactlyInAnyOrder(aiOwnerEmail, aiAdminEmail, aiMutedOwnerEmail);

    // Every AI-enabled email carries the narration sentinel + the AI-only risk + the deterministic
    // debtor from the always-rendered table.
    for (MimeMessage message : received) {
      String body = extractBody(message);
      assertThat(body).contains(NARRATIVE_SENTINEL);
      assertThat(body).contains(AI_RISK_SENTINEL);
      assertThat(body).contains(DETERMINISTIC_DEBTOR);
    }

    runIn(
        aiSchema,
        AI_ORG_ID,
        aiOwnerId,
        () -> {
          UUID orgSettingsId = orgSettingsRepository.findForCurrentTenant().orElseThrow().getId();
          // Bell for owner + admin; NOT for the muted owner (preference off) or the plain member.
          assertThat(hasCashDigestBell(aiOwnerId)).isTrue();
          assertThat(hasCashDigestBell(aiAdminId)).isTrue();
          assertThat(hasCashDigestBell(aiMutedOwnerId)).isFalse();
          assertThat(hasCashDigestBell(aiMemberId)).isFalse();

          // Exactly one CASH_DIGEST delivery-log row per recipient email — no duplicates, none for
          // the plain member.
          assertThat(deliveredEmails(orgSettingsId))
              .containsExactlyInAnyOrder(aiOwnerEmail, aiAdminEmail, aiMutedOwnerEmail);

          // Exactly one audit row for this run.
          var audits =
              auditEventRepository
                  .findByFilter(
                      "cash_digest",
                      orgSettingsId,
                      null,
                      "collections.digest.sent",
                      null,
                      null,
                      PageRequest.of(0, 10))
                  .getContent();
          assertThat(audits).hasSize(1);
          assertThat(audits.get(0).getDetails())
              .containsEntry("ai_narrated", "true")
              .containsEntry("recipients", "3");
        });
  }

  @Test
  void aiDisabled_numbersOnlyEmail_stillDelivered_withAudit() throws Exception {
    runIn(noAiSchema, NOAI_ORG_ID, noAiOwnerId, () -> cashDigestService.processTenant());

    assertThat(greenMail.waitForIncomingEmail(5_000L, 1)).isTrue();
    MimeMessage[] received = greenMail.getReceivedMessages();
    // Exactly one email to the sole owner — no duplicate send.
    assertThat(recipientAddresses(received)).containsExactly(noAiOwnerEmail);

    String body = extractBody(received[0]);
    // Numbers-only fallback: the deterministic debtor renders, but no narration block.
    assertThat(body).contains(DETERMINISTIC_DEBTOR);
    assertThat(body).doesNotContain(NARRATIVE_SENTINEL);
    assertThat(body).doesNotContain(AI_RISK_SENTINEL);

    runIn(
        noAiSchema,
        NOAI_ORG_ID,
        noAiOwnerId,
        () -> {
          UUID orgSettingsId = orgSettingsRepository.findForCurrentTenant().orElseThrow().getId();
          assertThat(hasCashDigestBell(noAiOwnerId)).isTrue();
          // Exactly one delivery-log row for the sole owner.
          assertThat(deliveredEmails(orgSettingsId)).containsExactly(noAiOwnerEmail);
          var audits =
              auditEventRepository
                  .findByFilter(
                      "cash_digest",
                      orgSettingsId,
                      null,
                      "collections.digest.sent",
                      null,
                      null,
                      PageRequest.of(0, 10))
                  .getContent();
          assertThat(audits).hasSize(1);
          assertThat(audits.get(0).getDetails()).containsEntry("ai_narrated", "false");
        });
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private boolean hasCashDigestBell(UUID memberId) {
    return notificationRepository
        .findByRecipientMemberId(memberId, PageRequest.of(0, 100))
        .getContent()
        .stream()
        .anyMatch((Notification n) -> "CASH_DIGEST".equals(n.getType()));
  }

  private List<String> deliveredEmails(UUID orgSettingsId) {
    return deliveryLogRepository.findAll().stream()
        .filter(l -> "CASH_DIGEST".equals(l.getReferenceType()))
        .filter(l -> orgSettingsId.equals(l.getReferenceId()))
        .filter(l -> l.getStatus() == EmailDeliveryStatus.SENT)
        .map(EmailDeliveryLog::getRecipientEmail)
        .toList();
  }

  private static List<String> recipientAddresses(MimeMessage[] messages) throws Exception {
    var out = new java.util.ArrayList<String>();
    for (MimeMessage m : messages) {
      out.add(m.getAllRecipients()[0].toString());
    }
    return out;
  }

  private String extractBody(jakarta.mail.Part part) throws Exception {
    Object content = part.getContent();
    if (content instanceof String s) {
      return s;
    }
    if (content instanceof jakarta.mail.Multipart mp) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < mp.getCount(); i++) {
        sb.append(extractBody(mp.getBodyPart(i)));
      }
      return sb.toString();
    }
    return content == null ? "" : content.toString();
  }
}
