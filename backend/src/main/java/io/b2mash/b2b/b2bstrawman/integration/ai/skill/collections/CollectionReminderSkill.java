package io.b2mash.b2b.b2bstrawman.integration.ai.skill.collections;

import io.b2mash.b2b.b2bstrawman.collections.CollectionActivity;
import io.b2mash.b2b.b2bstrawman.collections.CollectionActivityRepository;
import io.b2mash.b2b.b2bstrawman.collections.CollectionStage;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGate;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfile;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfileService;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.AiSkill;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.LlmJsonParser;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.SkillContext;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * AI skill that drafts one overdue-invoice collection reminder (Phase 83, ADR-327). The model
 * writes ONLY the human-language letter (subject + body paragraphs); the invoice facts table and
 * payment CTA are rendered deterministically by the send-side Thymeleaf frame (frame-owns-facts).
 * {@code createGates} wraps the draft in exactly one PENDING {@code SEND_COLLECTION_REMINDER} gate
 * (72 h expiry) whose snake_case {@code proposed_action} carries the activity/invoice/customer ids
 * — written by this skill from {@link SkillContext}, never from model output.
 *
 * <p>System-invoked from the collections scan via {@code AiReminderComposer} with {@code invokedBy
 * = null}; advisor annotations (592A) are read from {@code additionalContext} when present and
 * tolerated when absent.
 */
@Component
public class CollectionReminderSkill implements AiSkill {

  private static final String SKILL_ID = "collection-reminder";
  private static final String SYSTEM_PROMPT_RESOURCE = "ai/skills/collection-reminder/system.txt";
  private static final String OUTPUT_SCHEMA_RESOURCE =
      "ai/skills/collection-reminder/output-schema.json";

  /** Keep identical to {@code CollectionsPaymentListener.GATE_TYPE_SEND_COLLECTION_REMINDER}. */
  static final String GATE_TYPE = "SEND_COLLECTION_REMINDER";

  private static final Duration GATE_TTL = Duration.ofHours(72);

  private final CollectionActivityRepository activityRepository;
  private final InvoiceRepository invoiceRepository;
  private final CustomerRepository customerRepository;
  private final AiFirmProfileService firmProfileService;
  private final ObjectMapper objectMapper;
  private final LlmJsonParser llmJsonParser;

  public CollectionReminderSkill(
      CollectionActivityRepository activityRepository,
      InvoiceRepository invoiceRepository,
      CustomerRepository customerRepository,
      AiFirmProfileService firmProfileService,
      ObjectMapper objectMapper,
      LlmJsonParser llmJsonParser) {
    this.activityRepository = activityRepository;
    this.invoiceRepository = invoiceRepository;
    this.customerRepository = customerRepository;
    this.firmProfileService = firmProfileService;
    this.objectMapper = objectMapper;
    this.llmJsonParser = llmJsonParser;
  }

  @Override
  public String skillId() {
    return SKILL_ID;
  }

  @Override
  public String assembleSystemPrompt(AiFirmProfile profile) {
    String systemTemplate = loadClasspathResource(SYSTEM_PROMPT_RESOURCE);
    String outputSchema = loadClasspathResource(OUTPUT_SCHEMA_RESOURCE);
    String profileBlock = firmProfileService.assembleProfileBlock();

    return systemTemplate
        .replace("{firm_profile_block}", profileBlock)
        .replace("{output_schema}", outputSchema);
  }

  @Override
  public String assembleUserPrompt(SkillContext context) {
    UUID activityId = context.entityId();
    CollectionActivity activity =
        activityRepository
            .findOneById(activityId)
            .orElseThrow(() -> new ResourceNotFoundException("CollectionActivity", activityId));
    Invoice invoice =
        invoiceRepository
            .findById(activity.getInvoiceId())
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", activity.getInvoiceId()));
    Customer customer =
        customerRepository
            .findById(activity.getCustomerId())
            .orElseThrow(() -> new ResourceNotFoundException("Customer", activity.getCustomerId()));

    long daysOverdue =
        invoice.getDueDate() != null
            ? ChronoUnit.DAYS.between(invoice.getDueDate(), LocalDate.now())
            : activity.getDaysOverdueAtAction();

    var prompt = new StringBuilder();
    prompt.append("<stage>\n");
    prompt.append("Stage: ").append(activity.getStage().name()).append("\n");
    prompt.append("Tone directive: ").append(toneBlockFor(activity.getStage())).append("\n");
    prompt.append("</stage>\n\n");

    prompt.append("<invoice-facts>\n");
    prompt.append("Invoice number: ").append(nullSafe(invoice.getInvoiceNumber())).append("\n");
    prompt.append("Total: ").append(invoice.getTotal()).append(" ");
    prompt.append(nullSafe(invoice.getCurrency())).append("\n");
    prompt
        .append("Due date: ")
        .append(invoice.getDueDate() != null ? invoice.getDueDate().toString() : "unknown")
        .append("\n");
    prompt.append("Days overdue: ").append(daysOverdue).append("\n");
    prompt.append("</invoice-facts>\n\n");

    prompt.append("<relationship>\n");
    prompt.append("Customer: ").append(nullSafe(customer.getName())).append("\n");
    if (customer.getCreatedAt() != null) {
      prompt
          .append("Customer since: ")
          .append(customer.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate())
          .append("\n");
    }
    appendBillingHistory(prompt, activity.getCustomerId());
    prompt.append("</relationship>\n\n");

    prompt.append("<chase-history>\n");
    for (CollectionActivity row : activityRepository.findByInvoiceId(activity.getInvoiceId())) {
      prompt.append("- ").append(row.getStage().name()).append(": ").append(row.getStatus().name());
      if (row.getReason() != null) {
        prompt.append(" (").append(row.getReason()).append(")");
      }
      prompt.append("\n");
    }
    prompt.append("</chase-history>\n\n");

    Object annotations = context.additionalContext().get("advisor_annotations");
    if (annotations != null) {
      prompt.append("<advisor-annotations>\n");
      prompt.append(annotations).append("\n");
      prompt.append("</advisor-annotations>\n\n");
    }

    prompt.append(
        "Draft the reminder letter for this invoice at the stage and tone directed above. "
            + "Remember: letter paragraphs and subject only — no amounts, dates or links. "
            + "Produce your response as valid JSON.");

    return prompt.toString();
  }

  @Override
  public List<AiExecutionGate> createGates(
      AiExecution execution, String outputContent, SkillContext context) {
    CollectionReminderOutput output =
        llmJsonParser.parse(objectMapper, outputContent, CollectionReminderOutput.class);

    UUID activityId = context.entityId();
    CollectionActivity activity =
        activityRepository
            .findOneById(activityId)
            .orElseThrow(() -> new ResourceNotFoundException("CollectionActivity", activityId));

    // snake_case keys per the parseAction convention; ids come from the SkillContext-loaded
    // activity (deterministic), never from model output.
    Map<String, Object> proposedAction =
        Map.of(
            "collection_activity_id", activityId.toString(),
            "invoice_id", activity.getInvoiceId().toString(),
            "customer_id", activity.getCustomerId().toString(),
            "stage", activity.getStage().name(),
            "subject", nullSafe(output.subject()),
            "body_html", nullSafe(output.bodyHtml()),
            "body_text", nullSafe(output.bodyText()));

    var gate =
        new AiExecutionGate(
            execution,
            GATE_TYPE,
            proposedAction,
            output.reasoning() != null ? output.reasoning() : "AI-drafted collection reminder",
            Instant.now().plus(GATE_TTL));
    return List.of(gate);
  }

  @Override
  public boolean requiresVision() {
    return false;
  }

  /**
   * The per-stage tone directive included in the user prompt. STAGE_3 deliberately stops short of a
   * formal letter of demand — that is an attorney act, out of scope (§6.1).
   */
  static String toneBlockFor(CollectionStage stage) {
    return switch (stage) {
      case STAGE_1 ->
          "STAGE_1 friendly nudge — a warm, brief reminder assuming the delay is an oversight.";
      case STAGE_2 ->
          "STAGE_2 firm professional reminder — the invoice is materially overdue; request prompt"
              + " payment clearly while staying courteous.";
      case STAGE_3 ->
          "STAGE_3 final notice — convey gravity: this is the last reminder before internal"
              + " escalation. Stop short of a formal letter of demand; no legal threats.";
      case ESCALATION ->
          throw new IllegalArgumentException("ESCALATION stage never drafts a reminder");
    };
  }

  private void appendBillingHistory(StringBuilder prompt, UUID customerId) {
    List<Invoice> invoices = invoiceRepository.findByCustomerId(customerId);
    BigDecimal lifetimeBilled =
        invoices.stream()
            .filter(
                i -> i.getStatus() != InvoiceStatus.DRAFT && i.getStatus() != InvoiceStatus.VOID)
            .map(Invoice::getTotal)
            .filter(t -> t != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    prompt.append("Lifetime billed: ").append(lifetimeBilled).append("\n");

    List<Long> daysToPay =
        invoices.stream()
            .filter(i -> i.getStatus() == InvoiceStatus.PAID)
            .filter(i -> i.getPaidAt() != null && i.getDueDate() != null)
            .map(
                i ->
                    ChronoUnit.DAYS.between(
                        i.getDueDate(), i.getPaidAt().atZone(ZoneOffset.UTC).toLocalDate()))
            .sorted()
            .toList();
    if (!daysToPay.isEmpty()) {
      long median = daysToPay.get(daysToPay.size() / 2);
      prompt.append("Median days-to-pay (relative to due date): ").append(median).append("\n");
    }
  }

  private static String nullSafe(String value) {
    return value != null ? value : "";
  }

  private String loadClasspathResource(String path) {
    try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
      if (is == null) {
        throw new IllegalStateException("Classpath resource not found: " + path);
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load classpath resource: " + path, e);
    }
  }
}
