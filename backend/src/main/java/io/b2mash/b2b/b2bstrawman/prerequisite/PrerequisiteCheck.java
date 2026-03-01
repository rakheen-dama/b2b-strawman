package io.b2mash.b2b.b2bstrawman.prerequisite;

import java.util.List;

/**
 * Result of evaluating prerequisites for a given context. Contains the pass/fail status and any
 * violations found.
 *
 * @param passed true if all prerequisites are satisfied
 * @param context the context in which the check was performed
 * @param violations list of violations found (empty if passed)
 */
public record PrerequisiteCheck(
    boolean passed, PrerequisiteContext context, List<PrerequisiteViolation> violations) {

  /** Creates a passed check with no violations for the given context. */
  public static PrerequisiteCheck passed(PrerequisiteContext context) {
    return new PrerequisiteCheck(true, context, List.of());
  }
}
