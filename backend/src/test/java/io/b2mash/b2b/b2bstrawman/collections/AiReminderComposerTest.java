package io.b2mash.b2b.b2bstrawman.collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.integration.ConnectionTestResult;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationRegistry;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiCompletionRequest;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiCompletionResponse;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiProvider;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiTextRequest;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiTextResult;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiVisionRequest;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGateRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfile;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfileRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.SkillContext;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.collections.CollectionReminderSkill;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.testutil.StubAiProvider;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 590A.4 — skill + composer tests. The scan drives the REAL {@link AiReminderComposer} (which
 * supersedes the 589 no-op via {@code @Primary}) against the canned {@link StubAiProvider} output,
 * proving: PROPOSED activity with a PENDING 72h {@code SEND_COLLECTION_REMINDER} gate carrying
 * snake_case ids + drafted subject/body; per-stage tone blocks; the retryable {@code
 * SKIPPED(ai_unavailable)} pre-flight disposition; {@code SKIPPED(draft_failed)} isolation (one bad
 * draft never sinks the batch); and system-invocation metering ({@code invokedBy} null).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AiReminderComposerTest {

  private static final String ORG_ID = "org_collections_composer_test";

  private static final String STUB_SUBJECT = "Payment reminder: INV-STUB-0001";
  private static final String STUB_BODY_PHRASE = "arrange payment at your earliest convenience";

  @MockitoBean private IntegrationRegistry integrationRegistry;

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CollectionsScanService scanService;
  @Autowired private CollectionActivityRepository activityRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private AiExecutionGateRepository gateRepository;
  @Autowired private AiFirmProfileRepository firmProfileRepository;
  @Autowired private CollectionReminderSkill skill;
  @Autowired private ReminderComposer composer;
  @Autowired private StubAiProvider stubAiProvider;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Collections Composer Test Org", null);
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_composer", "composer@test.com", "Composer", "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    runInTenant(
        () -> {
          var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
          settings.getCollections().updateCollectionsSettings(true, 7, 21, 45, 60);
          orgSettingsRepository.save(settings);
        });
    ensureFirmProfile();
  }

  @BeforeEach
  void stubAiProvider() {
    when(integrationRegistry.resolve(IntegrationDomain.AI, AiProvider.class))
        .thenReturn(stubAiProvider);
  }

  private void runInTenant(Runnable body) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(body);
  }

  private void ensureFirmProfile() {
    runInTenant(
        () -> {
          if (firmProfileRepository.findAll().isEmpty()) {
            firmProfileRepository.save(new AiFirmProfile(memberId));
          }
        });
  }

  /** Seeds a SENT invoice overdue by {@code daysOverdue}; returns {invoiceId, customerId}. */
  private UUID[] seedSentInvoice(
      String name, String email, int daysOverdue, String invoiceNumber, boolean exempt) {
    UUID[] holder = new UUID[2];
    runInTenant(
        () -> {
          Customer customer = TestCustomerFactory.createActiveCustomer(name, email, memberId);
          customer.setCollectionsExempt(exempt);
          var savedCustomer = customerRepository.save(customer);
          var invoice =
              new Invoice(savedCustomer.getId(), "ZAR", name, email, null, "Test Org", memberId);
          invoice.updateDraft(LocalDate.now().minusDays(daysOverdue), null, null, BigDecimal.ZERO);
          invoice.recalculateTotals(BigDecimal.valueOf(1500), false, BigDecimal.ZERO, false);
          invoice.approve(invoiceNumber, memberId);
          invoice.markSent();
          holder[0] = invoiceRepository.save(invoice).getId();
          holder[1] = savedCustomer.getId();
        });
    return holder;
  }

  private String randomInvoiceNumber() {
    return "INV-" + UUID.randomUUID().toString().substring(0, 6);
  }

  @Test
  void scanWithStub_proposesActivity_withPending72hGate_andSnakeCaseProposedAction() {
    var ids = seedSentInvoice("Happy Draft Co", "happy@test.com", 10, "INV-STUB-0001", false);
    Instant before = Instant.now();

    runInTenant(scanService::scanForTenant);

    runInTenant(
        () -> {
          var activity =
              activityRepository
                  .findByInvoiceIdAndStage(ids[0], CollectionStage.STAGE_1)
                  .orElseThrow();
          assertThat(activity.getStatus()).isEqualTo(CollectionActivityStatus.PROPOSED);
          assertThat(activity.getGateId()).isNotNull();

          var gate = gateRepository.findById(activity.getGateId()).orElseThrow();
          assertThat(gate.getStatus()).isEqualTo("PENDING");
          assertThat(gate.getGateType()).isEqualTo("SEND_COLLECTION_REMINDER");
          // 72h expiry (± the test's own runtime).
          assertThat(gate.getExpiresAt())
              .isAfter(before.plus(Duration.ofHours(71)))
              .isBefore(before.plus(Duration.ofHours(73)));

          Map<String, Object> action = gate.getProposedAction();
          assertThat(action)
              .containsEntry("collection_activity_id", activity.getId().toString())
              .containsEntry("invoice_id", ids[0].toString())
              .containsEntry("customer_id", ids[1].toString())
              .containsEntry("stage", "STAGE_1")
              .containsEntry("subject", STUB_SUBJECT);
          assertThat((String) action.get("body_html")).contains(STUB_BODY_PHRASE);
          assertThat((String) action.get("body_text")).contains(STUB_BODY_PHRASE);
        });
  }

  @Test
  void stageToneBlock_selectedPerStage_inUserPrompt() {
    // Exempt customer → the scan never touches these hand-seeded rows.
    var ids = seedSentInvoice("Tone Co", "tone@test.com", 50, randomInvoiceNumber(), true);

    runInTenant(
        () -> {
          UUID s1 = seedActivity(ids, CollectionStage.STAGE_1);
          UUID s2 = seedActivity(ids, CollectionStage.STAGE_2);
          UUID s3 = seedActivity(ids, CollectionStage.STAGE_3);

          String p1 = skill.assembleUserPrompt(contextFor(s1));
          String p2 = skill.assembleUserPrompt(contextFor(s2));
          String p3 = skill.assembleUserPrompt(contextFor(s3));

          assertThat(p1).contains("Stage: STAGE_1").contains("friendly nudge");
          assertThat(p1).doesNotContain("final notice");
          assertThat(p2).contains("Stage: STAGE_2").contains("firm professional reminder");
          assertThat(p3).contains("Stage: STAGE_3").contains("final notice");
          assertThat(p3).contains("Stop short of a formal letter of demand");

          // System prompt carries the profile block + embedded output schema.
          var profile = firmProfileRepository.findAll().getFirst();
          String system = skill.assembleSystemPrompt(profile);
          assertThat(system).contains("<firm-profile").contains("\"bodyHtml\"");
        });
  }

  private UUID seedActivity(UUID[] invoiceAndCustomer, CollectionStage stage) {
    var activity =
        new CollectionActivity(
            invoiceAndCustomer[0],
            invoiceAndCustomer[1],
            stage,
            CollectionActivityStatus.SKIPPED,
            50,
            null);
    return activityRepository.save(activity).getId();
  }

  private static SkillContext contextFor(UUID activityId) {
    return new SkillContext(activityId, "collection_activity", "tone test", Map.of());
  }

  @Test
  void aiUnavailable_whenProviderIsNoop_recordsSkippedAiUnavailable() {
    when(integrationRegistry.resolve(IntegrationDomain.AI, AiProvider.class))
        .thenReturn(new NoopProvider());
    var ids =
        seedSentInvoice("No Provider Co", "noprov@test.com", 10, randomInvoiceNumber(), false);

    runInTenant(scanService::scanForTenant);

    runInTenant(
        () -> {
          var activity =
              activityRepository
                  .findByInvoiceIdAndStage(ids[0], CollectionStage.STAGE_1)
                  .orElseThrow();
          assertThat(activity.getStatus()).isEqualTo(CollectionActivityStatus.SKIPPED);
          assertThat(activity.getReason()).isEqualTo("ai_unavailable");
        });
  }

  @Test
  void aiUnavailable_whenNoFirmProfile_recordsSkippedAiUnavailable_thenRetryable() {
    var ids =
        seedSentInvoice("No Profile Co", "noprofile@test.com", 10, randomInvoiceNumber(), false);
    try {
      runInTenant(() -> firmProfileRepository.deleteAll());

      runInTenant(scanService::scanForTenant);

      runInTenant(
          () -> {
            var activity =
                activityRepository
                    .findByInvoiceIdAndStage(ids[0], CollectionStage.STAGE_1)
                    .orElseThrow();
            assertThat(activity.getStatus()).isEqualTo(CollectionActivityStatus.SKIPPED);
            assertThat(activity.getReason()).isEqualTo("ai_unavailable");
          });
    } finally {
      ensureFirmProfile();
    }

    // Retryable: with the profile restored, the next scan re-proposes the SAME row in place.
    runInTenant(scanService::scanForTenant);
    runInTenant(
        () -> {
          var activity =
              activityRepository
                  .findByInvoiceIdAndStage(ids[0], CollectionStage.STAGE_1)
                  .orElseThrow();
          assertThat(activity.getStatus()).isEqualTo(CollectionActivityStatus.PROPOSED);
        });
  }

  @Test
  void providerException_recordsDraftFailed_andSiblingCandidateStillProposed() {
    String failingNumber = "INV-FAIL-590A";
    var failing = seedSentInvoice("Failing Draft Co", "failing@test.com", 10, failingNumber, false);
    var sibling =
        seedSentInvoice("Sibling Draft Co", "sibling@test.com", 10, randomInvoiceNumber(), false);

    when(integrationRegistry.resolve(IntegrationDomain.AI, AiProvider.class))
        .thenReturn(new FailForInvoiceProvider(stubAiProvider, failingNumber));

    runInTenant(scanService::scanForTenant);

    runInTenant(
        () -> {
          var failed =
              activityRepository
                  .findByInvoiceIdAndStage(failing[0], CollectionStage.STAGE_1)
                  .orElseThrow();
          assertThat(failed.getStatus()).isEqualTo(CollectionActivityStatus.SKIPPED);
          assertThat(failed.getReason()).isEqualTo("draft_failed");

          var proposed =
              activityRepository
                  .findByInvoiceIdAndStage(sibling[0], CollectionStage.STAGE_1)
                  .orElseThrow();
          assertThat(proposed.getStatus()).isEqualTo(CollectionActivityStatus.PROPOSED);
        });
  }

  @Test
  void executionRow_isMetered_withNullInvokedBy() {
    var ids = seedSentInvoice("Metered Co", "metered@test.com", 10, randomInvoiceNumber(), false);

    runInTenant(scanService::scanForTenant);

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var activity =
                      activityRepository
                          .findByInvoiceIdAndStage(ids[0], CollectionStage.STAGE_1)
                          .orElseThrow();
                  var gate = gateRepository.findById(activity.getGateId()).orElseThrow();
                  var execution = gate.getExecution();
                  assertThat(execution.getSkillId()).isEqualTo("collection-reminder");
                  assertThat(execution.getStatus()).isEqualTo("COMPLETED");
                  assertThat(execution.getInvokedBy()).isNull();
                  assertThat(execution.getCostCents()).isGreaterThan(0);
                  assertThat(execution.getEntityType()).isEqualTo("collection_activity");
                  assertThat(execution.getEntityId()).isEqualTo(activity.getId());
                }));
  }

  @Test
  void primaryAiComposer_winsSingleBeanInjection_overNoOp() {
    assertThat(composer).isInstanceOf(AiReminderComposer.class);
  }

  /** Minimal unconfigured provider — providerId "noop" like the production NoOpAiProvider. */
  private static final class NoopProvider implements AiProvider {
    @Override
    public String providerId() {
      return "noop";
    }

    @Override
    public AiTextResult generateText(AiTextRequest request) {
      return new AiTextResult(true, "", null, 0);
    }

    @Override
    public AiTextResult summarize(String content, int maxLength) {
      return new AiTextResult(true, "", null, 0);
    }

    @Override
    public List<String> suggestCategories(String content, List<String> existingCategories) {
      return List.of();
    }

    @Override
    public ConnectionTestResult testConnection() {
      return new ConnectionTestResult(true, "noop", null);
    }

    @Override
    public AiCompletionResponse complete(AiCompletionRequest request) {
      return new AiCompletionResponse("{}", "noop", 0, 0, 0, 0, "end_turn", 0L);
    }

    @Override
    public AiCompletionResponse completeWithVision(AiVisionRequest request) {
      return new AiCompletionResponse("{}", "noop", 0, 0, 0, 0, "end_turn", 0L);
    }
  }

  /** Delegates to the stub, but throws for prompts that mention the failing invoice number. */
  private record FailForInvoiceProvider(StubAiProvider delegate, String failingInvoiceNumber)
      implements AiProvider {
    @Override
    public String providerId() {
      return "stub";
    }

    @Override
    public AiTextResult generateText(AiTextRequest request) {
      return delegate.generateText(request);
    }

    @Override
    public AiTextResult summarize(String content, int maxLength) {
      return delegate.summarize(content, maxLength);
    }

    @Override
    public List<String> suggestCategories(String content, List<String> existingCategories) {
      return delegate.suggestCategories(content, existingCategories);
    }

    @Override
    public ConnectionTestResult testConnection() {
      return delegate.testConnection();
    }

    @Override
    public AiCompletionResponse complete(AiCompletionRequest request) {
      if (request.userPrompt() != null && request.userPrompt().contains(failingInvoiceNumber)) {
        throw new IllegalStateException("simulated provider outage");
      }
      return delegate.complete(request);
    }

    @Override
    public AiCompletionResponse completeWithVision(AiVisionRequest request) {
      return delegate.completeWithVision(request);
    }
  }
}
