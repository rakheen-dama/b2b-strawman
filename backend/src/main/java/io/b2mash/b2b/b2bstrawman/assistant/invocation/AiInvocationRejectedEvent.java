package io.b2mash.b2b.b2bstrawman.assistant.invocation;

import java.time.Instant;
import java.util.UUID;

/**
 * Published after an invocation transitions to REJECTED.
 *
 * <p>515A only declares and publishes the event; subscribers belong to later slices.
 *
 * <p>Carries an immutable scalar snapshot rather than the JPA entity so consumers do not depend on
 * persistence lifecycle or mutable state.
 */
public record AiInvocationRejectedEvent(
    UUID invocationId,
    String specialistId,
    UUID actorId,
    UUID reviewedById,
    String contextEntityType,
    UUID contextEntityId,
    Instant reviewedAt,
    String reason) {

  public static AiInvocationRejectedEvent of(AiSpecialistInvocation inv, String reason) {
    return new AiInvocationRejectedEvent(
        inv.getId(),
        inv.getSpecialistId(),
        inv.getActorId(),
        inv.getReviewedById(),
        inv.getContextEntityType(),
        inv.getContextEntityId(),
        inv.getReviewedAt(),
        reason);
  }
}
