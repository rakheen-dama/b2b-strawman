package io.b2mash.b2b.b2bstrawman.collections;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGate;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import java.util.Optional;

/**
 * Seam that produces the gated draft for one due collection reminder (Phase 83, ADR-325).
 * Implementations decide <em>how</em> the draft is composed — the deterministic scan engine ({@link
 * CollectionsScanService}) only depends on this contract, never on AI machinery.
 *
 * <p><strong>Bean-resolution contract.</strong> Slice 589 registers exactly one implementation, the
 * plain {@code @Component} {@link NoOpReminderComposer}, so single-bean injection of {@code
 * ReminderComposer} resolves trivially. When slice 590A ships the real {@code AiReminderComposer},
 * that bean MUST be annotated {@code @Primary} — the no-op stays registered and unannotated, and
 * {@code @Primary} deterministically wins single-bean injection regardless of component-scan order.
 * (We deliberately avoid {@code @ConditionalOnMissingBean}: it only has order-independent semantics
 * inside auto-configuration classes, and would risk registering both beans and breaking startup for
 * a plain component-scanned seam.)
 *
 * <p><strong>Failure signalling contract (590A).</strong> Three distinct outcomes map to three
 * distinct scan dispositions:
 *
 * <ul>
 *   <li>{@link Optional#empty()} — drafting is not wired at all (the no-op composer): {@code
 *       SKIPPED(draft_unavailable)}, retryable.
 *   <li>{@link AiUnavailableException} — the AI pre-flight failed (provider unconfigured, or no
 *       firm-profile row exists to invoke a skill from job context): {@code
 *       SKIPPED(ai_unavailable)}, retryable — the jobs themselves never crash on AI unavailability.
 *   <li>any other {@code RuntimeException} — the draft attempt itself failed (provider error,
 *       FAILED execution, unparseable output): {@code SKIPPED(draft_failed)}, retryable.
 * </ul>
 */
public interface ReminderComposer {

  /**
   * Composes the draft for one due reminder.
   *
   * @return the PENDING {@link AiExecutionGate} carrying the draft, or {@link Optional#empty()}
   *     when drafting is unavailable (the scan records {@code SKIPPED(draft_unavailable)}, which is
   *     retryable — the next scan re-evaluates the row).
   * @throws AiUnavailableException when the AI pre-flight fails (the scan records {@code
   *     SKIPPED(ai_unavailable)}, retryable)
   */
  Optional<AiExecutionGate> compose(
      CollectionActivity activity, Invoice invoice, Customer customer);

  /**
   * Signals that AI drafting is unavailable for this tenant right now — the AI provider is not
   * configured (NoOp provider resolved) or no {@code AiFirmProfile} row exists (a skill cannot be
   * system-invoked from job context without one, Phase 83 §6.4). Distinct from a draft
   * <em>failure</em>: the scan maps this to the retryable {@code SKIPPED(ai_unavailable)} reason.
   */
  class AiUnavailableException extends RuntimeException {
    public AiUnavailableException(String message) {
      super(message);
    }
  }
}
