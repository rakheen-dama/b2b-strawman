package io.b2mash.b2b.b2bstrawman.collections;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationRegistry;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiProvider;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGate;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfileRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.AiSkillExecutionService;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.SkillContext;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.SkillExecutionResult;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * The real {@link ReminderComposer} (Phase 83, 590A): invokes the {@code collection-reminder} AI
 * skill <em>system-invoked from job context</em> ({@code invokedBy = null}, §6.4) and returns the
 * PENDING {@code SEND_COLLECTION_REMINDER} gate persisted by the skill-execution pipeline.
 *
 * <p>{@code @Primary} per the {@link ReminderComposer} bean-resolution contract — deterministically
 * supersedes the plain-{@code @Component} {@link NoOpReminderComposer}, which stays registered as
 * the documented fallback.
 *
 * <p><strong>Pre-flight (Findings 2–3).</strong> {@code AiSkillExecutionService.executeSkill} calls
 * {@code getOrCreateProfile()}, which on the no-profile path requires a bound MEMBER_ID — absent in
 * job context. So this composer verifies, WITHOUT creating one, that a firm-profile row exists, and
 * that the resolved AI provider is not the {@code "noop"} placeholder. Either failure throws {@link
 * ReminderComposer.AiUnavailableException} so the scan records {@code SKIPPED(ai_unavailable)}
 * (retryable) instead of crashing or mis-labelling the row.
 *
 * <p><strong>Transactionality (Finding 12).</strong> Called inside the scan's per-candidate
 * REQUIRES_NEW transaction; the skill pipeline's short REQUIRED transactions join it. The activity
 * may still be transient at compose time (the scan saves it after composing), but the skill
 * resolves it by id — so this composer persists it first, inside the same candidate transaction.
 */
@Component
@Primary
public class AiReminderComposer implements ReminderComposer {

  private static final Logger log = LoggerFactory.getLogger(AiReminderComposer.class);

  static final String SKILL_ID = "collection-reminder";

  private final AiSkillExecutionService skillExecutionService;
  private final AiFirmProfileRepository firmProfileRepository;
  private final IntegrationRegistry integrationRegistry;
  private final CollectionActivityRepository activityRepository;
  private final CollectionsTriageService triageService;

  public AiReminderComposer(
      AiSkillExecutionService skillExecutionService,
      AiFirmProfileRepository firmProfileRepository,
      IntegrationRegistry integrationRegistry,
      CollectionActivityRepository activityRepository,
      CollectionsTriageService triageService) {
    this.skillExecutionService = skillExecutionService;
    this.firmProfileRepository = firmProfileRepository;
    this.integrationRegistry = integrationRegistry;
    this.activityRepository = activityRepository;
    this.triageService = triageService;
  }

  @Override
  public Optional<AiExecutionGate> compose(
      CollectionActivity activity, Invoice invoice, Customer customer) {
    // Pre-flight 1: AI provider configured. resolve() never fails — it returns the NoOp adapter
    // (providerId "noop") for unconfigured tenants; the test-profile stub reports "stub".
    AiProvider provider = integrationRegistry.resolve(IntegrationDomain.AI, AiProvider.class);
    if ("noop".equals(provider.providerId())) {
      throw new AiUnavailableException("AI provider not configured for tenant");
    }

    // Pre-flight 2: a firm-profile row must exist — checked WITHOUT creating one, because
    // getOrCreateProfile() requires a bound MEMBER_ID that job context does not have.
    // Existence-only
    // count query (one per tenant) avoids materializing the profile rows we immediately discard.
    if (firmProfileRepository.count() == 0) {
      throw new AiUnavailableException("No AI firm profile exists for tenant");
    }

    // Seam-ownership tradeoff: the scan may hand us a not-yet-persisted (transient) activity row,
    // and the skill resolves that row by id (findOneById), so the composer MUST persist it first.
    // save() returns the managed instance; the scan's own later save (markProposed/markSkipped)
    // mutates that SAME managed instance within this one REQUIRES_NEW candidate transaction, so
    // there is no double-insert and no divergent copy. Idempotent for already-managed rows.
    CollectionActivity persisted = activityRepository.save(activity);

    var context =
        new SkillContext(
            persisted.getId(),
            "collection_activity",
            "Collection reminder draft for invoice "
                + (invoice.getInvoiceNumber() != null
                    ? invoice.getInvoiceNumber()
                    : invoice.getId())
                + " (stage "
                + persisted.getStage().name()
                + ")",
            advisorContext(persisted.getCustomerId()));

    // System invocation: invokedBy = null (§6.4) — metering rides the AiExecution row unchanged.
    SkillExecutionResult result =
        skillExecutionService.executeSkill(SKILL_ID, context, null, List.of());

    if (!"COMPLETED".equals(result.execution().getStatus()) || result.gates().isEmpty()) {
      // Provider failure / parse failure / zero gates — propagate as a compose failure so the
      // scan records SKIPPED(draft_failed). The FAILED execution row (with any metered cost) is
      // already durably persisted by the skill pipeline.
      throw new IllegalStateException(
          "Collection reminder draft failed for activity "
              + persisted.getId()
              + ": execution status "
              + result.execution().getStatus()
              + ", "
              + result.gates().size()
              + " gate(s)"
              + (result.execution().getErrorMessage() != null
                  ? " — " + result.execution().getErrorMessage()
                  : ""));
    }

    log.debug(
        "AiReminderComposer: drafted reminder for activity {} (gate {})",
        persisted.getId(),
        result.gates().getFirst().getId());
    return Optional.of(result.gates().getFirst());
  }

  /**
   * Builds the skill's {@code additionalContext} carrying advisor annotations (592A.4). Advice is
   * <em>acknowledgeable context only</em> — never an instruction to promise transfers (ADR-329);
   * the skill's {@code <advisor-annotations>} prompt block already treats it that way. Each {@link
   * CollectionsAdvisor.CollectionsAdvice} is formatted as one {@code SIGNAL: detail} line.
   *
   * <p>Advice is optional garnish: this runs inside the scan's per-candidate transaction, so any
   * failure is swallowed to {@link Map#of()} — a broken advisor can never turn a draftable reminder
   * into {@code SKIPPED(draft_failed)}. (The trust advisor already fails open internally; this is a
   * belt-and-braces guard for any other advisor.)
   */
  private Map<String, Object> advisorContext(UUID customerId) {
    try {
      List<CollectionsAdvisor.CollectionsAdvice> advice = triageService.adviceFor(customerId);
      if (advice.isEmpty()) {
        return Map.of();
      }
      String annotations =
          advice.stream()
              .map(a -> a.signal() + ": " + a.detail())
              .collect(Collectors.joining("\n"));
      return Map.of("advisor_annotations", annotations);
    } catch (RuntimeException e) {
      log.debug(
          "Advisor annotation assembly failed for customer {}: {}", customerId, e.getMessage());
      return Map.of();
    }
  }
}
