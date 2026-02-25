package io.b2mash.b2b.b2bstrawman.portal;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailDeliveryLogRepository;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailDeliveryStatus;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import jakarta.mail.internet.MimeMessage;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalEmailServiceIntegrationTest {

  private static final GreenMail greenMail;

  static {
    greenMail = new GreenMail(ServerSetupTest.SMTP);
    greenMail.start();
  }

  @DynamicPropertySource
  static void configureGreenMail(DynamicPropertyRegistry registry) {
    registry.add("spring.mail.host", () -> greenMail.getSmtp().getBindTo());
    registry.add("spring.mail.port", () -> String.valueOf(greenMail.getSmtp().getPort()));
    registry.add("spring.mail.username", () -> "");
    registry.add("spring.mail.password", () -> "");
    registry.add("spring.mail.properties.mail.smtp.auth", () -> "false");
    registry.add("spring.mail.properties.mail.smtp.starttls.enable", () -> "false");
    registry.add("docteams.email.sender-address", () -> "test@docteams.app");
  }

  private static final String ORG_ID = "org_portal_email_test";
  private static final String CONTACT_EMAIL = "portal-contact@test.com";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private MemberSyncService memberSyncService;
  @Autowired private PortalEmailService portalEmailService;
  @Autowired private MagicLinkService magicLinkService;
  @Autowired private PortalContactRepository portalContactRepository;
  @Autowired private MagicLinkTokenRepository magicLinkTokenRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private EmailDeliveryLogRepository deliveryLogRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;
  private PortalContact savedContact;

  @BeforeAll
  void provisionTenant() {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Portal Email Test Org").schemaName();
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    memberId =
        memberSyncService
            .syncMember(
                ORG_ID, "user_portal_email_alice", "alice@test.com", "Alice Test", null, "owner")
            .memberId();

    // Create a customer (FK required) and portal contact in tenant scope
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var customer =
                          createActiveCustomer("Portal Test Customer", CONTACT_EMAIL, memberId);
                      customer = customerRepository.save(customer);

                      var contact =
                          new PortalContact(
                              ORG_ID,
                              customer.getId(),
                              CONTACT_EMAIL,
                              "Test Contact",
                              PortalContact.ContactRole.PRIMARY);
                      savedContact = portalContactRepository.save(contact);
                    }));
  }

  @AfterAll
  static void stopGreenMail() {
    greenMail.stop();
  }

  @BeforeEach
  void resetGreenMail() throws Exception {
    greenMail.purgeEmailFromAllMailboxes();
  }

  @Test
  void sends_email_to_contact() throws Exception {
    UUID tokenId = UUID.randomUUID();
    String rawToken = "test-token";

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(() -> portalEmailService.sendMagicLinkEmail(savedContact, rawToken, tokenId));

    MimeMessage[] received = greenMail.getReceivedMessages();
    assertThat(received).hasSize(1);
    assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo(CONTACT_EMAIL);
    assertThat(received[0].getSubject()).containsIgnoringCase("portal access link");
  }

  @Test
  void records_delivery_log() {
    UUID tokenId = UUID.randomUUID();
    String rawToken = "test-token";

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(() -> portalEmailService.sendMagicLinkEmail(savedContact, rawToken, tokenId));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var logs = deliveryLogRepository.findAll();
              var relevant =
                  logs.stream().filter(l -> tokenId.equals(l.getReferenceId())).findFirst();
              assertThat(relevant).isPresent();
              assertThat(relevant.get().getStatus()).isEqualTo(EmailDeliveryStatus.SENT);
              assertThat(relevant.get().getReferenceType()).isEqualTo("MAGIC_LINK");
              assertThat(relevant.get().getTemplateName()).isEqualTo("portal-magic-link");
            });
  }

  @Test
  void failure_does_not_throw() {
    greenMail.stop();
    try {
      UUID tokenId = UUID.randomUUID();
      String rawToken = "test-token";
      assertThatCode(
              () ->
                  ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
                      .where(RequestScopes.ORG_ID, ORG_ID)
                      .run(
                          () ->
                              portalEmailService.sendMagicLinkEmail(
                                  savedContact, rawToken, tokenId)))
          .doesNotThrowAnyException();
    } finally {
      greenMail.start();
    }
  }

  @Test
  void generateToken_triggers_email() throws Exception {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(() -> magicLinkService.generateToken(savedContact.getId(), "127.0.0.1"));

    MimeMessage[] received = greenMail.getReceivedMessages();
    assertThat(received).hasSize(1);
    assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo(CONTACT_EMAIL);
  }

  @Test
  void token_returned_on_email_failure() {
    greenMail.stop();
    try {
      String[] rawToken = new String[1];
      ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
          .where(RequestScopes.ORG_ID, ORG_ID)
          .run(
              () -> {
                rawToken[0] = magicLinkService.generateToken(savedContact.getId(), "127.0.0.1");
              });
      assertThat(rawToken[0]).isNotNull().isNotBlank();
    } finally {
      greenMail.start();
    }
  }

  @Test
  void no_unsubscribe_link_in_magic_link_email() throws Exception {
    UUID tokenId = UUID.randomUUID();
    String rawToken = "test-token";

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(() -> portalEmailService.sendMagicLinkEmail(savedContact, rawToken, tokenId));

    MimeMessage[] received = greenMail.getReceivedMessages();
    assertThat(received).hasSize(1);

    String content = getEmailContent(received[0]);
    assertThat(content.toLowerCase()).doesNotContain("unsubscribe");
  }

  @Test
  void delivery_log_referenceId_matches_token_id() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(() -> magicLinkService.generateToken(savedContact.getId(), "127.0.0.1"));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              // Find the most recent magic link token for this contact
              var tokens = magicLinkTokenRepository.findAll();
              var latestToken =
                  tokens.stream()
                      .filter(t -> savedContact.getId().equals(t.getPortalContactId()))
                      .max(java.util.Comparator.comparing(MagicLinkToken::getCreatedAt))
                      .orElseThrow();

              // Find the delivery log entry
              var logs = deliveryLogRepository.findAll();
              var relevant =
                  logs.stream()
                      .filter(l -> "MAGIC_LINK".equals(l.getReferenceType()))
                      .filter(l -> latestToken.getId().equals(l.getReferenceId()))
                      .findFirst();
              assertThat(relevant)
                  .as("Delivery log referenceId should match MagicLinkToken.id")
                  .isPresent();
            });
  }

  /** Helper to extract text content from a MimeMessage (handles multipart). */
  private String getEmailContent(MimeMessage message) throws Exception {
    Object content = message.getContent();
    if (content instanceof String str) {
      return str;
    }
    if (content instanceof jakarta.mail.internet.MimeMultipart multipart) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < multipart.getCount(); i++) {
        var part = multipart.getBodyPart(i);
        if (part.getContent() instanceof String partContent) {
          sb.append(partContent);
        }
      }
      return sb.toString();
    }
    return content.toString();
  }
}
