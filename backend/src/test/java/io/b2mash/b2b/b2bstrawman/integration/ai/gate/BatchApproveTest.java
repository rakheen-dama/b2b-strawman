package io.b2mash.b2b.b2bstrawman.integration.ai.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.util.GreenMail;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.collections.CollectionActivityRepository;
import io.b2mash.b2b.b2bstrawman.collections.CollectionActivityStatus;
import io.b2mash.b2b.b2bstrawman.collections.CollectionStage;
import io.b2mash.b2b.b2bstrawman.collections.CollectionsScanService;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfile;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfileRepository;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.testutil.GreenMailTestSupport;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 591A.1 / §8.4 row 5 — batch approval of collection-reminder gates. Mixed batch (2 PENDING + 1
 * EXPIRED) → 200 with per-gate dispositions (2 {@code APPROVED_EXECUTED}, 1 {@code FAILED}); the
 * two winners send GreenMail-observed emails and their activities flip to {@code SENT}; the expired
 * sibling stays {@code SKIPPED(gate_expired)} — per-gate transactions mean one paid/expired gate
 * doesn't block the others (ADR-326 B1). A non-{@code AI_REVIEW} member is 403; over-cap is 400.
 * Gates are seeded via the real scan+stub path (not hand-rolled JSON), reusing the 590B harness.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BatchApproveTest {

  private static final GreenMail greenMail = GreenMailTestSupport.getInstance();

  private static final String ORG_ID = "org_batch_approve_test";

  private final ObjectMapper objectMapper = new ObjectMapper();

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
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String tenantSchema;
  private UUID ownerMemberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Batch Approve Test Org", null);
    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_batch_owner", "batch_owner@test.com", "Owner", "owner"));
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_batch_member", "batch_member@test.com", "Member", "member");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    runInTenant(
        () -> {
          var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
          settings.getCollections().updateCollectionsSettings(true, 7, 21, 45, 60);
          orgSettingsRepository.save(settings);
          if (firmProfileRepository.findAll().isEmpty()) {
            firmProfileRepository.save(new AiFirmProfile(ownerMemberId));
          }
        });
  }

  @BeforeEach
  void purgeMailbox() throws Exception {
    greenMail.purgeEmailFromAllMailboxes();
  }

  private void runInTenant(Runnable body) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(body);
  }

  /** Seeds a SENT invoice overdue by {@code daysOverdue} days; returns its id. */
  private UUID seedSentInvoice(String name, String email, String invoiceNumber, int daysOverdue) {
    UUID[] holder = new UUID[1];
    runInTenant(
        () -> {
          Customer customer = TestCustomerFactory.createActiveCustomer(name, email, ownerMemberId);
          var savedCustomer = customerRepository.save(customer);
          var invoice =
              new Invoice(
                  savedCustomer.getId(), "ZAR", name, email, null, "Batch Org", ownerMemberId);
          invoice.updateDraft(LocalDate.now().minusDays(daysOverdue), null, null, BigDecimal.ZERO);
          invoice.recalculateTotals(BigDecimal.valueOf(1500), false, BigDecimal.ZERO, false);
          invoice.approve(invoiceNumber, ownerMemberId);
          invoice.markSent();
          holder[0] = invoiceRepository.save(invoice).getId();
        });
    return holder[0];
  }

  private UUID gateIdForInvoice(UUID invoiceId) {
    UUID[] holder = new UUID[1];
    runInTenant(
        () ->
            holder[0] =
                activityRepository
                    .findByInvoiceIdAndStage(invoiceId, CollectionStage.STAGE_1)
                    .orElseThrow()
                    .getGateId());
    assertThat(holder[0]).isNotNull();
    return holder[0];
  }

  @Test
  void mixedBatch_twoPendingOneExpired_sends2_dispositionsCorrect_siblingsUnaffected()
      throws Exception {
    UUID inv1 = seedSentInvoice("Batch Naidoo Co", "batch1@test.com", "INV-BATCH-0001", 10);
    UUID inv2 = seedSentInvoice("Batch Peters Co", "batch2@test.com", "INV-BATCH-0002", 12);
    UUID inv3 = seedSentInvoice("Batch Expired Co", "batch3@test.com", "INV-BATCH-0003", 15);

    runInTenant(scanService::scanForTenant);

    UUID gate1 = gateIdForInvoice(inv1);
    UUID gate2 = gateIdForInvoice(inv2);
    UUID gate3 = gateIdForInvoice(inv3);

    // Backdate gate3's expiry, then run the sweep so it becomes EXPIRED and its activity
    // transitions to SKIPPED(gate_expired).
    jdbcTemplate.update(
        "UPDATE \"%s\".ai_execution_gates SET expires_at = ? WHERE id = ?::uuid"
            .formatted(tenantSchema),
        Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)),
        gate3.toString());
    runInTenant(gateService::expireStaleGates);
    runInTenant(
        () ->
            assertThat(gateRepository.findById(gate3).orElseThrow().getStatus())
                .isEqualTo("EXPIRED"));

    String body =
        objectMapper.writeValueAsString(
            Map.of("gateIds", List.of(gate1, gate2, gate3), "notes", "Weekly collections batch"));

    mockMvc
        .perform(
            post("/api/ai/gates/batch-approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_batch_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.results.length()").value(3))
        // Dispositions preserve the request order (gate1, gate2, gate3).
        .andExpect(jsonPath("$.results[0].gateId").value(gate1.toString()))
        .andExpect(jsonPath("$.results[0].outcome").value("APPROVED_EXECUTED"))
        .andExpect(jsonPath("$.results[1].gateId").value(gate2.toString()))
        .andExpect(jsonPath("$.results[1].outcome").value("APPROVED_EXECUTED"))
        .andExpect(jsonPath("$.results[2].gateId").value(gate3.toString()))
        .andExpect(jsonPath("$.results[2].outcome").value("FAILED"))
        .andExpect(
            jsonPath("$.results[2].error").value(org.hamcrest.Matchers.containsString("EXPIRED")));

    // Two GreenMail-observed sends (the two winners), none from the expired sibling.
    assertThat(greenMail.waitForIncomingEmail(5_000L, 2)).isTrue();
    assertThat(greenMail.getReceivedMessages()).hasSize(2);

    runInTenant(
        () -> {
          assertThat(
                  activityRepository
                      .findByInvoiceIdAndStage(inv1, CollectionStage.STAGE_1)
                      .orElseThrow()
                      .getStatus())
              .isEqualTo(CollectionActivityStatus.SENT);
          assertThat(
                  activityRepository
                      .findByInvoiceIdAndStage(inv2, CollectionStage.STAGE_1)
                      .orElseThrow()
                      .getStatus())
              .isEqualTo(CollectionActivityStatus.SENT);
          var expiredActivity =
              activityRepository
                  .findByInvoiceIdAndStage(inv3, CollectionStage.STAGE_1)
                  .orElseThrow();
          assertThat(expiredActivity.getStatus()).isEqualTo(CollectionActivityStatus.SKIPPED);
          assertThat(expiredActivity.getReason()).isEqualTo("gate_expired");

          assertThat(gateRepository.findById(gate1).orElseThrow().getStatus())
              .isEqualTo("APPROVED");
          assertThat(gateRepository.findById(gate2).orElseThrow().getStatus())
              .isEqualTo("APPROVED");
          assertThat(gateRepository.findById(gate3).orElseThrow().getStatus()).isEqualTo("EXPIRED");

          // ai.gate.approved audit exists for the two winners, not for the expired loser.
          assertThat(approvedAuditCount(gate1)).isEqualTo(1);
          assertThat(approvedAuditCount(gate2)).isEqualTo(1);
          assertThat(approvedAuditCount(gate3)).isZero();
        });
  }

  @Test
  void batchApprove_forbiddenForNonAiReviewMember() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of("gateIds", List.of(UUID.randomUUID())));
    mockMvc
        .perform(
            post("/api/ai/gates/batch-approve")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_batch_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.detail").value("Insufficient permissions for this operation"));
  }

  @Test
  void batchApprove_overCapReturns400() throws Exception {
    var tooMany = IntStream.range(0, 101).mapToObj(i -> UUID.randomUUID()).toList();
    String body = objectMapper.writeValueAsString(Map.of("gateIds", tooMany));
    mockMvc
        .perform(
            post("/api/ai/gates/batch-approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_batch_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Batch-approve cap exceeded"));
  }

  @Test
  void batchApprove_emptyListReturns400() throws Exception {
    String body = objectMapper.writeValueAsString(Map.of("gateIds", List.of()));
    mockMvc
        .perform(
            post("/api/ai/gates/batch-approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_batch_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Empty batch-approve"));
  }

  private int approvedAuditCount(UUID gateId) {
    return auditEventRepository
        .findByFilter(
            "ai_execution_gate",
            gateId,
            null,
            "ai.gate.approved",
            null,
            null,
            PageRequest.of(0, 10))
        .getContent()
        .size();
  }
}
