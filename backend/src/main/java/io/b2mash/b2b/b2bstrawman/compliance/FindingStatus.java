package io.b2mash.b2b.b2bstrawman.compliance;

/**
 * Status lifecycle for compliance audit findings. Stored as VARCHAR in the database.
 *
 * <p>Valid transitions:
 *
 * <pre>
 * OPEN -> ACKNOWLEDGED
 * ACKNOWLEDGED -> IN_PROGRESS
 * IN_PROGRESS -> RESOLVED (requires resolution notes)
 * IN_PROGRESS -> FALSE_POSITIVE (requires resolution notes)
 * </pre>
 *
 * No backward transitions allowed. RESOLVED and FALSE_POSITIVE are terminal states.
 */
public enum FindingStatus {
  OPEN,
  ACKNOWLEDGED,
  IN_PROGRESS,
  RESOLVED,
  FALSE_POSITIVE
}
