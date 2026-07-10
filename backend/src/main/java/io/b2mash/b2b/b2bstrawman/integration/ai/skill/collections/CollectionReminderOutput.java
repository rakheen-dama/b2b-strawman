package io.b2mash.b2b.b2bstrawman.integration.ai.skill.collections;

/**
 * Structured output from the {@code collection-reminder} AI skill (Phase 83, ADR-327). Parsed from
 * JSON via the shared {@code LlmJsonParser} + {@code tools.jackson} ObjectMapper.
 *
 * <p>The AI owns ONLY the human-language part of a reminder — the letter {@link #subject} and body
 * paragraphs ({@link #bodyHtml} / {@link #bodyText}) — plus its own {@link #reasoning}. The invoice
 * facts table and the payment CTA are template-rendered by {@code CollectionReminderSendService}
 * from context values (frame-owns-facts): a wrong amount or a broken payment link is a worse
 * failure than a clumsy sentence, and both are preventable by keeping facts out of the model's
 * output.
 *
 * <p>Field names are camelCase to match every sibling skill's output schema; the snake_case keys
 * ({@code body_html}, …) live only on the gate's {@code proposed_action} JSONB, written by the
 * skill itself in {@code createGates}.
 */
public record CollectionReminderOutput(
    String subject, String bodyHtml, String bodyText, String reasoning) {}
