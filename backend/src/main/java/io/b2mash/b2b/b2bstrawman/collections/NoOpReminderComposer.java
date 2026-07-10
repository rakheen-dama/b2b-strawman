package io.b2mash.b2b.b2bstrawman.collections;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGate;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Default {@link ReminderComposer} for slice 589: drafting is not yet wired, so it always returns
 * {@link Optional#empty()}. The scan records {@code SKIPPED(draft_unavailable)} for every due
 * reminder — a <em>retryable</em> outcome, so every activity is automatically re-proposed once
 * slice 590A's real {@code AiReminderComposer} deploys.
 *
 * <p>This bean is a plain {@code @Component} and intentionally carries no {@code @Primary} /
 * {@code @ConditionalOnMissingBean}. Slice 590A's {@code AiReminderComposer} MUST be annotated
 * {@code @Primary}; with both beans present, {@code @Primary} deterministically supersedes this one
 * for single-bean injection, and this class stays registered as the documented fallback.
 */
@Component
public class NoOpReminderComposer implements ReminderComposer {

  @Override
  public Optional<AiExecutionGate> compose(
      CollectionActivity activity, Invoice invoice, Customer customer) {
    return Optional.empty();
  }
}
