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
 */
public interface ReminderComposer {

  /**
   * Composes the draft for one due reminder.
   *
   * @return the PENDING {@link AiExecutionGate} carrying the draft, or {@link Optional#empty()}
   *     when drafting is unavailable (the scan records {@code SKIPPED(draft_unavailable)}, which is
   *     retryable — the next scan re-evaluates the row).
   */
  Optional<AiExecutionGate> compose(
      CollectionActivity activity, Invoice invoice, Customer customer);
}
