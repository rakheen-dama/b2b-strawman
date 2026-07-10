package io.b2mash.b2b.b2bstrawman.collections;

import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.util.GreenMail;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGateRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGateService;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfile;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfileRepository;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailDeliveryLogRepository;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailDeliveryStatus;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailRateLimiter;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.testutil.GreenMailTestSupport;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 590B.4 — the phase's core safety proof (§8.4 row 4 + row-7 re-assertion with REAL drafted gates).
 * The ONLY send path is {@code GateActionExecutor.executeSendCollectionReminder}: scan (stub
 * composer) → PENDING gate → {@code AiExecutionGateService.approve} → GreenMail-observed email with
 * the drafted subject + portalUrl CTA → delivery log ({@code COLLECTION_REMINDER}) → activity
 * {@code SENT} + {@code collections.reminder.sent} audit. Provider failure → {@code SEND_FAILED}
 * then re-proposed by the next scan; rate limit → {@code SKIPPED(rate_limited)} with gateId
 * retained; reject/expiry transitions re-asserted end-to-end on real drafts.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SendCollectionReminderExecutorTest {

  private static final GreenMail greenMail = GreenMailTestSupport.getInstance();

  private static final String ORG_ID = "org_collections_send_test";
  private static final String RL_ORG_ID = "org_collections_send_rl_test";

  private static final String STUB_SUBJECT = "Payment reminder: INV-STUB-0001";
  private static final String STUB_BODY_PHRASE = "arrange payment at your earliest convenience";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CollectionsScanService scanService;
  @Autowired private CollectionActivityRepository activityRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private AiExecutionGateRepository gateRepository;
  @Autowired private AiExecutionGateService gateService;
  @Autowired private AiFirmProfileRepository firmProfileRepository;
  @Autowired private EmailDeliveryLogRepository deliveryLogRepository;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private EmailRateLimiter rateLimiter;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String tenantSchema;
  private UUID memberId;
  private String rlTenantSchema;
  private UUID rlMemberId;

  private record ProvisionedTenant(String schema, UUID memberId) {}

  @BeforeAll
  void setup() throws Exception {
    var main = provisionCollectionsTenant(ORG_ID, "Collections Send Test Org", "user_send");
    tenantSchema = main.schema();
    memberId = main.memberId();
    var rl = provisionCollectionsTenant(RL_ORG_ID, "Collections Send RL Test Org", "user_send_rl");
    rlTenantSchema = rl.schema();
    rlMemberId = rl.memberId();
  }

  private ProvisionedTenant provisionCollectionsTenant(
      String orgId, String orgName, String userSubject) throws Exception {
    provisioningService.provisionTenant(orgId, orgName, null);
    UUID member =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, orgId, userSubject, userSubject + "@test.com", "Owner", "owner"));
    String schema =
        orgSchemaMappingRepository.findByClerkOrgId(orgId).orElseThrow().getSchemaName();
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
        .where(RequestScopes.MEMBER_ID, member)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
              settings.getCollections().updateCollectionsSettings(true, 7, 21, 45, 60);
              orgSettingsRepository.save(settings);
              if (firmProfileRepository.findAll().isEmpty()) {
                firmProfileRepository.save(new AiFirmProfile(member));
              }
            });
    return new ProvisionedTenant(schema, member);
  }

  @BeforeEach
  void purgeMailbox() throws Exception {
    greenMail.purgeEmailFromAllMailboxes();
  }

  private void runInTenant(Runnable body) {
    runIn(tenantSchema, ORG_ID, memberId, body);
  }

  private void runInRlTenant(Runnable body) {
    runIn(rlTenantSchema, RL_ORG_ID, rlMemberId, body);
  }

  private static void runIn(String schema, String orgId, UUID member, Runnable body) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
        .where(RequestScopes.MEMBER_ID, member)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(body);
  }

  /** Seeds a SENT invoice overdue by 10 days; returns its id. */
  private UUID seedSentInvoice(
      String schema, String orgId, UUID member, String name, String email, String invoiceNumber) {
    UUID[] holder = new UUID[1];
    runIn(
        schema,
        orgId,
        member,
        () -> {
          Customer customer = TestCustomerFactory.createActiveCustomer(name, email, member);
          var savedCustomer = customerRepository.save(customer);
          var invoice =
              new Invoice(savedCustomer.getId(), "ZAR", name, email, null, "Test Org", member);
          invoice.updateDraft(LocalDate.now().minusDays(10), null, null, BigDecimal.ZERO);
          invoice.recalculateTotals(BigDecimal.valueOf(1500), false, BigDecimal.ZERO, false);
          invoice.approve(invoiceNumber, member);
          invoice.markSent();
          holder[0] = invoiceRepository.save(invoice).getId();
        });
    return holder[0];
  }

  /** Runs the scan and returns the PROPOSED STAGE_1 activity for the invoice. */
  private CollectionActivity scanAndGetProposedActivity(UUID invoiceId) {
    runInTenant(scanService::scanForTenant);
    CollectionActivity[] holder = new CollectionActivity[1];
    runInTenant(
        () ->
            holder[0] =
                activityRepository
                    .findByInvoiceIdAndStage(invoiceId, CollectionStage.STAGE_1)
                    .orElseThrow());
    assertThat(holder[0].getStatus()).isEqualTo(CollectionActivityStatus.PROPOSED);
    assertThat(holder[0].getGateId()).isNotNull();
    return holder[0];
  }

  @Test
  void approve_sendsGreenMailObservedEmail_recordsDeliveryLog_marksSent_andAudits()
      throws Exception {
    UUID invoiceId =
        seedSentInvoice(
            tenantSchema, ORG_ID, memberId, "Full Loop Co", "fullloop@test.com", "INV-STUB-0001");
    var activity = scanAndGetProposedActivity(invoiceId);
    UUID gateId = activity.getGateId();

    runInTenant(() -> gateService.approve(gateId, memberId, "approved for send"));

    // GreenMail-observed: the drafted subject + the frame-owned facts and portalUrl CTA.
    assertThat(greenMail.waitForIncomingEmail(5_000L, 1)).isTrue();
    MimeMessage[] received = greenMail.getReceivedMessages();
    assertThat(received).hasSize(1);
    assertThat(received[0].getSubject()).isEqualTo(STUB_SUBJECT);
    assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo("fullloop@test.com");
    String body = extractBody(received[0]);
    assertThat(body).contains(STUB_BODY_PHRASE);
    assertThat(body).contains("INV-STUB-0001");
    // No PSP configured → paymentUrl null → Pay Now hidden; portalUrl fallback CTA renders.
    assertThat(body).contains("/invoices/" + invoiceId);

    runInTenant(
        () -> {
          var sent = activityRepository.findOneById(activity.getId()).orElseThrow();
          assertThat(sent.getStatus()).isEqualTo(CollectionActivityStatus.SENT);
          assertThat(sent.getEmailDeliveryLogId()).isNotNull();

          var deliveryLog =
              deliveryLogRepository.findById(sent.getEmailDeliveryLogId()).orElseThrow();
          assertThat(deliveryLog.getReferenceType()).isEqualTo("COLLECTION_REMINDER");
          assertThat(deliveryLog.getReferenceId()).isEqualTo(activity.getId());
          assertThat(deliveryLog.getStatus()).isEqualTo(EmailDeliveryStatus.SENT);

          var gate = gateRepository.findById(gateId).orElseThrow();
          assertThat(gate.getStatus()).isEqualTo("APPROVED");

          var audits =
              auditEventRepository
                  .findByFilter(
                      "collection_activity",
                      activity.getId(),
                      null,
                      "collections.reminder.sent",
                      null,
                      null,
                      PageRequest.of(0, 10))
                  .getContent();
          assertThat(audits).isNotEmpty();
          assertThat(audits.get(0).getDetails())
              .containsEntry("invoice_id", invoiceId.toString())
              .containsEntry("invoice_number", "INV-STUB-0001")
              .containsEntry("stage", "STAGE_1");
        });
  }

  @Test
  void providerFailure_marksSendFailed_thenNextScanReproposesFreshDraft() {
    UUID invoiceId =
        seedSentInvoice(
            tenantSchema,
            ORG_ID,
            memberId,
            "Provider Fail Co",
            "providerfail@test.com",
            "INV-590B-FAIL");
    var activity = scanAndGetProposedActivity(invoiceId);
    UUID gateId = activity.getGateId();

    // Simulate SMTP failure with a mid-test stop/start of the shared GreenMail singleton
    // (sanctioned by backend/CLAUDE.md when wrapped in try/finally).
    greenMail.stop();
    try {
      runInTenant(() -> gateService.approve(gateId, memberId, "approved during outage"));
    } finally {
      greenMail.start();
    }

    runInTenant(
        () -> {
          var failed = activityRepository.findOneById(activity.getId()).orElseThrow();
          assertThat(failed.getStatus()).isEqualTo(CollectionActivityStatus.SEND_FAILED);
          assertThat(failed.getReason()).isEqualTo("provider_failure");
          assertThat(failed.getEmailDeliveryLogId()).isNotNull();
          var deliveryLog =
              deliveryLogRepository.findById(failed.getEmailDeliveryLogId()).orElseThrow();
          assertThat(deliveryLog.getStatus()).isEqualTo(EmailDeliveryStatus.FAILED);
          // The gate itself stays APPROVED — the disposition lives on the activity.
          assertThat(gateRepository.findById(gateId).orElseThrow().getStatus())
              .isEqualTo("APPROVED");
        });

    // SEND_FAILED is scan-retryable: the next scan re-proposes the SAME row with a FRESH draft
    // (the email never left).
    runInTenant(scanService::scanForTenant);
    runInTenant(
        () -> {
          var reproposed = activityRepository.findOneById(activity.getId()).orElseThrow();
          assertThat(reproposed.getStatus()).isEqualTo(CollectionActivityStatus.PROPOSED);
          assertThat(reproposed.getGateId()).isNotNull().isNotEqualTo(gateId);
          assertThat(gateRepository.findById(reproposed.getGateId()).orElseThrow().getStatus())
              .isEqualTo("PENDING");
        });
  }

  @Test
  void rateLimited_marksSkippedRateLimited_retainsGateId_andRecordsRateLimitedLog() {
    // Dedicated tenant so exhausting the hourly counter cannot poison the sibling tests.
    UUID invoiceId =
        seedSentInvoice(
            rlTenantSchema,
            RL_ORG_ID,
            rlMemberId,
            "Rate Limit Co",
            "ratelimit590@test.com",
            "INV-590B-RL");
    runInRlTenant(scanService::scanForTenant);
    CollectionActivity[] holder = new CollectionActivity[1];
    runInRlTenant(
        () ->
            holder[0] =
                activityRepository
                    .findByInvoiceIdAndStage(invoiceId, CollectionStage.STAGE_1)
                    .orElseThrow());
    UUID gateId = holder[0].getGateId();
    assertThat(gateId).isNotNull();

    // Exhaust this tenant's hourly SMTP budget (default 50/h in the test profile).
    for (int i = 0; i < 60; i++) {
      rateLimiter.tryAcquire(rlTenantSchema, "smtp");
    }

    runInRlTenant(() -> gateService.approve(gateId, rlMemberId, "approved at limit"));

    runInRlTenant(
        () -> {
          var skipped = activityRepository.findOneById(holder[0].getId()).orElseThrow();
          assertThat(skipped.getStatus()).isEqualTo(CollectionActivityStatus.SKIPPED);
          assertThat(skipped.getReason()).isEqualTo("rate_limited");
          // gateId retained — the draft stays traceable (§2.2).
          assertThat(skipped.getGateId()).isEqualTo(gateId);

          var rateLimitedLogs =
              deliveryLogRepository.findAll().stream()
                  .filter(l -> holder[0].getId().equals(l.getReferenceId()))
                  .filter(l -> l.getStatus() == EmailDeliveryStatus.RATE_LIMITED)
                  .toList();
          assertThat(rateLimitedLogs).isNotEmpty();
          assertThat(rateLimitedLogs.getFirst().getReferenceType())
              .isEqualTo("COLLECTION_REMINDER");
        });

    // No email left the building.
    assertThat(greenMail.getReceivedMessages()).isEmpty();
  }

  @Test
  void reject_onRealDraftedGate_marksActivityRejected_terminalForStage() {
    UUID invoiceId =
        seedSentInvoice(
            tenantSchema, ORG_ID, memberId, "Reject Real Co", "rejectreal@test.com", "INV-590B-RJ");
    var activity = scanAndGetProposedActivity(invoiceId);
    UUID gateId = activity.getGateId();

    runInTenant(() -> gateService.reject(gateId, memberId, "not chasing this client"));

    runInTenant(
        () -> {
          var rejected = activityRepository.findOneById(activity.getId()).orElseThrow();
          assertThat(rejected.getStatus()).isEqualTo(CollectionActivityStatus.REJECTED);
        });

    // Terminal for the stage: the next scan must not re-propose it.
    runInTenant(scanService::scanForTenant);
    runInTenant(
        () -> {
          var still = activityRepository.findOneById(activity.getId()).orElseThrow();
          assertThat(still.getStatus()).isEqualTo(CollectionActivityStatus.REJECTED);
        });
    assertThat(greenMail.getReceivedMessages()).isEmpty();
  }

  @Test
  void gateExpiry_onRealDraftedGate_marksSkippedGateExpired_thenReproposedNextScan() {
    UUID invoiceId =
        seedSentInvoice(
            tenantSchema, ORG_ID, memberId, "Expiry Real Co", "expiryreal@test.com", "INV-590B-EX");
    var activity = scanAndGetProposedActivity(invoiceId);
    UUID gateId = activity.getGateId();

    // Backdate the REAL drafted gate's expiry so the hourly sweep reaps it.
    jdbcTemplate.update(
        "UPDATE \"%s\".ai_execution_gates SET expires_at = ? WHERE id = ?::uuid"
            .formatted(tenantSchema),
        Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)),
        gateId.toString());

    runInTenant(() -> gateService.expireStaleGates());

    runInTenant(
        () -> {
          var skipped = activityRepository.findOneById(activity.getId()).orElseThrow();
          assertThat(skipped.getStatus()).isEqualTo(CollectionActivityStatus.SKIPPED);
          assertThat(skipped.getReason()).isEqualTo("gate_expired");
          // gateId retained until re-proposal overwrites it.
          assertThat(skipped.getGateId()).isEqualTo(gateId);
          assertThat(gateRepository.findById(gateId).orElseThrow().getStatus())
              .isEqualTo("EXPIRED");
        });

    // Retryable: the next scan re-proposes the SAME row with a fresh draft (current context).
    runInTenant(scanService::scanForTenant);
    runInTenant(
        () -> {
          var reproposed = activityRepository.findOneById(activity.getId()).orElseThrow();
          assertThat(reproposed.getStatus()).isEqualTo(CollectionActivityStatus.PROPOSED);
          assertThat(reproposed.getGateId()).isNotNull().isNotEqualTo(gateId);
        });
    assertThat(greenMail.getReceivedMessages()).isEmpty();
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
