package io.b2mash.b2b.b2bstrawman.portal;

import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Portal-facing projection of an {@link AuditEvent}. Surfaces only the fields a portal contact is
 * allowed to see -- ip address, user agent, raw actor IDs, and arbitrary detail keys are dropped.
 *
 * <p>The {@code summary} field is a human-readable label derived from the event type so the portal
 * can render rows without knowing the firm's internal vocabulary.
 *
 * <p>{@code projectId} is extracted from the JSONB {@code details->>'project_id'} field so the
 * portal can deep-link rows back to the matter detail page when present.
 */
public record PortalActivityEventResponse(
    UUID id,
    String eventType,
    String actorType,
    String actorName,
    UUID entityId,
    String entityType,
    UUID projectId,
    String summary,
    Instant occurredAt) {

  public static PortalActivityEventResponse from(AuditEvent e) {
    UUID projectId = null;
    String actorName = null;
    if (e.getDetails() != null) {
      Object pid = e.getDetails().get("project_id");
      if (pid instanceof String s) {
        try {
          projectId = UUID.fromString(s);
        } catch (IllegalArgumentException ignored) {
          // Malformed project_id in details -- leave projectId null rather than throwing.
        }
      }
      Object actor = e.getDetails().get("actor_name");
      if (actor instanceof String s) {
        actorName = s;
      }
    }
    return new PortalActivityEventResponse(
        e.getId(),
        e.getEventType(),
        e.getActorType(),
        actorName,
        e.getEntityId(),
        e.getEntityType(),
        projectId,
        summaryFor(e.getEventType()),
        e.getOccurredAt());
  }

  private static String summaryFor(String eventType) {
    if (eventType == null) {
      return "";
    }
    return switch (eventType) {
      case "invoice.sent" -> "Invoice sent";
      case "invoice.payment_recorded" -> "Payment recorded";
      case "invoice.payment_reversed" -> "Payment reversed";
      case "invoice.payment_partially_reversed" -> "Payment partially reversed";
      case "portal.document.downloaded" -> "Document downloaded";
      case "portal.document.acknowledged" -> "Document acknowledged";
      case "proposal.acceptance.completed" -> "Proposal accepted";
      default -> eventType;
    };
  }
}
