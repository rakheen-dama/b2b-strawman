package io.b2mash.b2b.b2bstrawman.collections;

import java.util.List;
import java.util.UUID;

/**
 * Vertical contribution point for per-customer collections context (Phase 83, §6.5, ADR-329). Core
 * ships a no-op ({@link NoOpCollectionsAdvisor}); verticals register additional beans by existence
 * — the platform's standard list-injection idiom (like {@code AiSkill} / {@code JobHandler}).
 * {@code CollectionsTriageService} collects every {@code CollectionsAdvisor} bean and merges their
 * advice into the deterministic triage signals.
 *
 * <p><strong>Inform only, never conduct.</strong> Advice is decoration on a human-reviewed flow: it
 * surfaces as a debtor-page signal and as acknowledgeable drafting context. An advisor NEVER
 * suppresses a reminder, NEVER proposes a trust transaction, and NEVER moves money (ADR-329).
 *
 * <p>Core code carries no imports from {@code verticals/legal} — enforced by {@code
 * CollectionsCoreBoundaryTest}.
 */
public interface CollectionsAdvisor {

  /** Advice for one customer; empty when the advisor has nothing to say. */
  List<CollectionsAdvice> adviseFor(UUID customerId);

  /**
   * One contributed piece of collections context: a machine-readable {@code signal} name (merged
   * into the debtor-page signal list) and a human-readable {@code detail} string (drafting context
   * / badge tooltip). e.g. {@code ("TRUST_FUNDS_AVAILABLE", "R 84 200,00 held in trust")}.
   */
  record CollectionsAdvice(String signal, String detail) {}
}
