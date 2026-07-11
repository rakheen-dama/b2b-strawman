package io.b2mash.b2b.b2bstrawman.integration.ai.skill.collections;

import java.util.List;

/**
 * Structured output from the {@code cash-digest} AI skill (Phase 83, ADR-328). Parsed from JSON via
 * the shared {@code LlmJsonParser} + {@code tools.jackson} ObjectMapper.
 *
 * <p>The AI owns ONLY prose: a short owner-facing {@link #narrative} and up to three ranked {@link
 * #topRisks}. Every figure the digest shows comes from {@code CashDigestData}, rendered by the
 * email template — so a hallucinated number in the narrative cannot change what the authoritative
 * tables display (table-always-wins, ADR-328 A1).
 *
 * <p>Field names are camelCase to match every sibling skill's output schema.
 */
public record CashDigestOutput(String narrative, List<TopRisk> topRisks) {

  /** One AI-ranked risk: which customer, why it's a risk, and the suggested next action. */
  public record TopRisk(String customerName, String why, String suggestedAction) {}
}
