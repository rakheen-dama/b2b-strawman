package io.b2mash.b2b.b2bstrawman.verticals.legal.closure;

import io.b2mash.b2b.b2bstrawman.project.Project;

/**
 * Pure-function compliance gate evaluated on a matter before closure (Phase 67, Epic 489A,
 * architecture §67.3.4, ADR-248).
 *
 * <p>Each gate is a tiny Spring {@code @Component} discovered via constructor injection into a
 * {@code List<ClosureGate>} on the orchestrator (489B). Adding a new gate in a future phase is a
 * single class drop — no change to the service.
 *
 * <p>Implementations MUST be pure (no writes, no side effects, no events) and deterministic on the
 * project's current state.
 */
public interface ClosureGate {

  /**
   * Stable gate code (e.g. {@code "TRUST_BALANCE_ZERO"}). Used as a key in closure reports and as
   * the {@code code} field on {@link GateResult}.
   */
  String code();

  /**
   * Evaluates the gate against the given project and returns a {@link GateResult} describing
   * whether the project satisfies this rule.
   */
  GateResult evaluate(Project project);

  /**
   * Stable display order — lower values appear first in closure reports. Used to sort the {@code
   * List<ClosureGate>} in {@code MatterClosureService.evaluate} (489B).
   */
  int order();
}
