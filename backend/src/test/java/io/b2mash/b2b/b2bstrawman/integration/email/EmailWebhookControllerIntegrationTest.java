package io.b2mash.b2b.b2bstrawman.integration.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.UUID;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EmailWebhookControllerIntegrationTest {

  private static final String ORG_ID = "org_webhook_test";
  private static final String WEBHOOK_URL = "/api/webhooks/email/sendgrid";

  // ECDSA keypair for webhook signature verification in tests
  private static KeyPair ecKeyPair;
  private static String publicKeyBase64;

  static {
    try {
      // Register BouncyCastle provider — required by SendGrid EventWebhook for ECDSA verification
      if (Security.getProvider("BC") == null) {
        Security.addProvider(new BouncyCastleProvider());
      }
      KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC", "BC");
      keyGen.initialize(new ECGenParameterSpec("P-256"));
      ecKeyPair = keyGen.generateKeyPair();
      // SendGrid expects X.509/DER-encoded public key, base64-encoded
      publicKeyBase64 = Base64.getEncoder().encodeToString(ecKeyPair.getPublic().getEncoded());
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate ECDSA keypair for tests", e);
    }
  }

  @DynamicPropertySource
  static void setWebhookVerificationKey(DynamicPropertyRegistry registry) {
    registry.add("docteams.email.sendgrid.webhook-verification-key", () -> publicKeyBase64);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private EmailDeliveryLogService deliveryLogService;
  @Autowired private EmailDeliveryLogRepository deliveryLogRepository;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private MemberRepository memberRepository;

  private String tenantSchema;

  @BeforeAll
  void provisionTenant() {
    tenantSchema = provisioningService.provisionTenant(ORG_ID, "Webhook Test Org").schemaName();
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    // Create an admin member so notifyAdminsAndOwners can find recipients
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var admin =
                  new Member(
                      "clerk_webhook_admin", "admin@webhook-test.com", "Test Admin", null, "admin");
              memberRepository.save(admin);
            });
  }

  @Test
  void bounce_updates_delivery_log() throws Exception {
    var msgId = "bounce-msg-" + UUID.randomUUID();
    createDeliveryLog("NOTIFICATION", msgId);

    String payload = buildEventPayload("bounce", msgId, "550 No such user", "NOTIFICATION");

    mockMvc
        .perform(
            post(WEBHOOK_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .headers(signatureHeaders(payload)))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var log = deliveryLogRepository.findByProviderMessageId(msgId);
              assertThat(log).isPresent();
              assertThat(log.get().getStatus()).isEqualTo(EmailDeliveryStatus.BOUNCED);
              assertThat(log.get().getErrorMessage()).isEqualTo("550 No such user");
            });
  }

  @Test
  void delivered_updates_delivery_log() throws Exception {
    var msgId = "delivered-msg-" + UUID.randomUUID();
    createDeliveryLog("NOTIFICATION", msgId);

    String payload = buildEventPayload("delivered", msgId, null, "NOTIFICATION");

    mockMvc
        .perform(
            post(WEBHOOK_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .headers(signatureHeaders(payload)))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var log = deliveryLogRepository.findByProviderMessageId(msgId);
              assertThat(log).isPresent();
              assertThat(log.get().getStatus()).isEqualTo(EmailDeliveryStatus.DELIVERED);
            });
  }

  @Test
  void dropped_updates_delivery_log_to_failed() throws Exception {
    var msgId = "dropped-msg-" + UUID.randomUUID();
    createDeliveryLog("NOTIFICATION", msgId);

    String payload = buildEventPayload("dropped", msgId, "Bounced Address", "NOTIFICATION");

    mockMvc
        .perform(
            post(WEBHOOK_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .headers(signatureHeaders(payload)))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var log = deliveryLogRepository.findByProviderMessageId(msgId);
              assertThat(log).isPresent();
              assertThat(log.get().getStatus()).isEqualTo(EmailDeliveryStatus.FAILED);
            });
  }

  @Test
  void missing_tenant_schema_skips_event() throws Exception {
    var msgId = "no-tenant-msg-" + UUID.randomUUID();
    createDeliveryLog("NOTIFICATION", msgId);

    // Event payload without tenantSchema in unique_args
    String payload =
        """
        [{"event":"bounce","sg_message_id":"%s","email":"user@example.com","reason":"bad","unique_args":{"referenceType":"NOTIFICATION","referenceId":"%s"}}]
        """
            .formatted(msgId, UUID.randomUUID());

    mockMvc
        .perform(
            post(WEBHOOK_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .headers(signatureHeaders(payload)))
        .andExpect(status().isOk());

    // Delivery log should NOT be updated (still SENT)
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var log = deliveryLogRepository.findByProviderMessageId(msgId);
              assertThat(log).isPresent();
              assertThat(log.get().getStatus()).isEqualTo(EmailDeliveryStatus.SENT);
            });
  }

  @Test
  void invalid_tenant_schema_format_skips_event() throws Exception {
    var msgId = "bad-tenant-msg-" + UUID.randomUUID();
    createDeliveryLog("NOTIFICATION", msgId);

    // Event payload with invalid tenantSchema format (SQL injection attempt)
    String payload =
        """
        [{"event":"bounce","sg_message_id":"%s","email":"user@example.com","reason":"bad","unique_args":{"tenantSchema":"public; DROP TABLE members","referenceType":"NOTIFICATION","referenceId":"%s"}}]
        """
            .formatted(msgId, UUID.randomUUID());

    mockMvc
        .perform(
            post(WEBHOOK_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .headers(signatureHeaders(payload)))
        .andExpect(status().isOk());

    // Delivery log should NOT be updated (still SENT)
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var log = deliveryLogRepository.findByProviderMessageId(msgId);
              assertThat(log).isPresent();
              assertThat(log.get().getStatus()).isEqualTo(EmailDeliveryStatus.SENT);
            });
  }

  @Test
  void invoice_bounce_creates_admin_notification() throws Exception {
    var msgId = "invoice-bounce-" + UUID.randomUUID();
    createDeliveryLog("INVOICE", msgId);

    String payload = buildEventPayload("bounce", msgId, "mailbox full", "INVOICE");

    mockMvc
        .perform(
            post(WEBHOOK_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .headers(signatureHeaders(payload)))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              // Find notifications of type INVOICE_EMAIL_BOUNCED
              var notifications = notificationRepository.findAll();
              var invoiceBounceNotifications =
                  notifications.stream()
                      .filter(n -> "INVOICE_EMAIL_BOUNCED".equals(n.getType()))
                      .toList();
              assertThat(invoiceBounceNotifications).isNotEmpty();
            });
  }

  @Test
  void bounce_creates_audit_event() throws Exception {
    var msgId = "audit-bounce-" + UUID.randomUUID();
    createDeliveryLog("NOTIFICATION", msgId);

    String payload = buildEventPayload("bounce", msgId, "unknown user", "NOTIFICATION");

    mockMvc
        .perform(
            post(WEBHOOK_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .headers(signatureHeaders(payload)))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var auditEvents = auditEventRepository.findAll();
              var bounceAudits =
                  auditEvents.stream()
                      .filter(e -> "email.delivery.bounced".equals(e.getEventType()))
                      .filter(e -> "email_delivery_log".equals(e.getEntityType()))
                      .toList();
              assertThat(bounceAudits).isNotEmpty();
            });
  }

  @Test
  void sg_message_id_with_filter_suffix_is_matched() throws Exception {
    var msgId = "filter-test-" + UUID.randomUUID();
    createDeliveryLog("NOTIFICATION", msgId);

    // SendGrid may append .filter suffix to sg_message_id
    String payload =
        """
        [{"event":"delivered","sg_message_id":"%s.filter0001","email":"user@example.com","unique_args":{"tenantSchema":"%s","referenceType":"NOTIFICATION","referenceId":"%s"}}]
        """
            .formatted(msgId, tenantSchema, UUID.randomUUID());

    mockMvc
        .perform(
            post(WEBHOOK_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .headers(signatureHeaders(payload)))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var log = deliveryLogRepository.findByProviderMessageId(msgId);
              assertThat(log).isPresent();
              assertThat(log.get().getStatus()).isEqualTo(EmailDeliveryStatus.DELIVERED);
            });
  }

  @Test
  void unsupported_provider_returns_400() throws Exception {
    mockMvc
        .perform(
            post("/api/webhooks/email/unsupported")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[]"))
        .andExpect(status().isBadRequest());
  }

  // --- Signature verification tests ---

  @Test
  void missing_signature_headers_returns_401() throws Exception {
    String payload =
        buildEventPayload("delivered", "msg-" + UUID.randomUUID(), null, "NOTIFICATION");

    // No signature or timestamp headers
    mockMvc
        .perform(post(WEBHOOK_URL).contentType(MediaType.APPLICATION_JSON).content(payload))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void missing_timestamp_header_returns_401() throws Exception {
    String payload =
        buildEventPayload("delivered", "msg-" + UUID.randomUUID(), null, "NOTIFICATION");

    mockMvc
        .perform(
            post(WEBHOOK_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .header("X-Twilio-Email-Event-Webhook-Signature", "invalid-sig"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void invalid_signature_returns_401() throws Exception {
    String payload =
        buildEventPayload("delivered", "msg-" + UUID.randomUUID(), null, "NOTIFICATION");

    mockMvc
        .perform(
            post(WEBHOOK_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .header("X-Twilio-Email-Event-Webhook-Signature", "bm90LWEtdmFsaWQtc2lnbmF0dXJl")
                .header("X-Twilio-Email-Event-Webhook-Timestamp", "1234567890"))
        .andExpect(status().isUnauthorized());
  }

  // --- Helper methods ---

  private void createDeliveryLog(String referenceType, String providerMessageId) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var result = new SendResult(true, providerMessageId, null);
              deliveryLogService.record(
                  referenceType,
                  UUID.randomUUID(),
                  "test-template",
                  "user@example.com",
                  "sendgrid",
                  result);
            });
  }

  private String buildEventPayload(
      String eventType, String sgMessageId, String reason, String referenceType) {
    String reasonField = reason != null ? ",\"reason\":\"" + reason + "\"" : "";
    return """
        [{"event":"%s","sg_message_id":"%s","email":"user@example.com"%s,"unique_args":{"tenantSchema":"%s","referenceType":"%s","referenceId":"%s"}}]
        """
        .formatted(
            eventType, sgMessageId, reasonField, tenantSchema, referenceType, UUID.randomUUID());
  }

  /**
   * Generates valid ECDSA signature headers for the given payload. Mimics SendGrid's webhook
   * signing: the signed data is {@code timestamp_bytes + payload_bytes} (concatenation of raw
   * bytes, not string concatenation). Uses BC provider to match SendGrid's EventWebhook.
   */
  private org.springframework.http.HttpHeaders signatureHeaders(String payload) {
    try {
      String timestamp = String.valueOf(System.currentTimeMillis() / 1000);

      // SendGrid EventWebhook.VerifySignature concatenates timestamp bytes + payload bytes
      byte[] timestampBytes = timestamp.getBytes(StandardCharsets.UTF_8);
      byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
      byte[] signedData = new byte[timestampBytes.length + payloadBytes.length];
      System.arraycopy(timestampBytes, 0, signedData, 0, timestampBytes.length);
      System.arraycopy(payloadBytes, 0, signedData, timestampBytes.length, payloadBytes.length);

      Signature signer = Signature.getInstance("SHA256withECDSA", "BC");
      signer.initSign(ecKeyPair.getPrivate());
      signer.update(signedData);
      byte[] signatureBytes = signer.sign();
      String signatureBase64 = Base64.getEncoder().encodeToString(signatureBytes);

      var headers = new org.springframework.http.HttpHeaders();
      headers.set("X-Twilio-Email-Event-Webhook-Signature", signatureBase64);
      headers.set("X-Twilio-Email-Event-Webhook-Timestamp", timestamp);
      return headers;
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate test webhook signature", e);
    }
  }
}
