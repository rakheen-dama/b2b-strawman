package io.b2mash.b2b.b2bstrawman.integration.ai.skill.collections;

import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGate;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfile;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfileService;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.AiSkill;
import io.b2mash.b2b.b2bstrawman.integration.ai.skill.SkillContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * AI skill that narrates the weekly cash digest (Phase 83, ADR-328). The model writes ONLY prose —
 * a short owner-facing narrative and up to three ranked top-debtor risks — over the deterministic
 * {@link io.b2mash.b2b.b2bstrawman.collections.CashDigestData} numbers handed to it. The email
 * template prints the authoritative figures from {@code CashDigestData} regardless, so a
 * hallucinated figure in prose cannot change what the tables show (table-always-wins, ADR-328 A1).
 *
 * <p>Informational — {@link #createGates} returns no gates: unlike {@code collection-reminder} the
 * digest proposes no action to approve. The service parses {@code CashDigestOutput} from the
 * execution's output content itself and degrades to numbers-only on any failure.
 *
 * <p>System-invoked from job context ({@code invokedBy = null}, §6.4); the serialized digest
 * numbers travel in {@link SkillContext#additionalContext()} under {@code "digest_data"} and are
 * echoed into an XML-tagged block of the user prompt.
 */
@Component
public class CashDigestSkill implements AiSkill {

  static final String SKILL_ID = "cash-digest";
  static final String DIGEST_DATA_KEY = "digest_data";

  private static final String SYSTEM_PROMPT_RESOURCE = "ai/skills/cash-digest/system.txt";
  private static final String OUTPUT_SCHEMA_RESOURCE = "ai/skills/cash-digest/output-schema.json";

  private final AiFirmProfileService firmProfileService;

  public CashDigestSkill(AiFirmProfileService firmProfileService) {
    this.firmProfileService = firmProfileService;
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
    Object digestData = context.additionalContext().get(DIGEST_DATA_KEY);
    if (digestData == null) {
      // Surfaces as a FAILED execution, which the service's numbers-only fallback already handles.
      throw new IllegalStateException(
          "Cash digest skill invoked without '" + DIGEST_DATA_KEY + "' context");
    }

    return """
        <cash-digest-data>
        %s
        </cash-digest-data>

        Write the weekly cash digest for the firm's owner from the figures above. Produce a short \
        narrative summarising the cash position (outstanding and its aging, billed vs collected for \
        the period, stale unbilled work, and what the collections engine did), then rank up to three \
        top-debtor risks. Reference ONLY figures present in the data above — never invent numbers. \
        Produce your response as valid JSON."""
        .formatted(digestData);
  }

  @Override
  public List<AiExecutionGate> createGates(
      AiExecution execution, String outputContent, SkillContext context) {
    // Informational skill — no proposed action to gate. The service parses CashDigestOutput from
    // the execution's output content itself (ADR-328).
    return List.of();
  }

  @Override
  public boolean requiresVision() {
    return false;
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
