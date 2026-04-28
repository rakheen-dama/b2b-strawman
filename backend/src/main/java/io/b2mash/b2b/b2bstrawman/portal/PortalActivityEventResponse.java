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

  /**
   * Maps an audit event type to a client-friendly human label.
   *
   * <p>Covers two visibility classes:
   *
   * <ul>
   *   <li>PORTAL_CONTACT-emitted events (MINE tab) -- second-person phrasing ("You ...").
   *   <li>Firm-emitted events on the {@link
   *       PortalActivityEventTypes#PORTAL_VISIBLE_FIRM_EVENT_TYPES} allow-list (FIRM tab) --
   *       third-person, client-facing phrasing.
   * </ul>
   *
   * The {@code default -> eventType} arm is dead code for FIRM rows (everything reaching it is
   * allow-listed and mapped) but is retained as a defensive fallback in case a new allow-list entry
   * is added without a matching label.
   */
  private static String summaryFor(String eventType) {
    if (eventType == null) {
      return "";
    }
    return switch (eventType) {
      // PORTAL_CONTACT-emitted (MINE tab)
      case "portal.document.downloaded" -> "You downloaded a document";
      case "portal.document.upload_initiated" -> "You started uploading a document";
      case "portal.document.acknowledged" -> "You acknowledged a document";
      case "portal.request_item.submitted" -> "You submitted an information request item";
      case "portal.invoice.paid" -> "You paid a fee note";
      // Firm-emitted, allow-listed (FIRM tab)
      case "information_request.created" -> "Information request created";
      case "information_request.sent" -> "Information request sent to you";
      case "information_request.cancelled" -> "Information request cancelled";
      case "information_request.completed" -> "Information request completed";
      case "information_request.item_accepted" -> "Information request item accepted";
      case "information_request.item_rejected" -> "Information request item needs re-submission";
      case "information_request.reminder_sent" -> "Reminder sent for information request";
      case "proposal.sent" -> "Engagement letter sent to you";
      case "proposal.accepted" -> "Engagement letter accepted";
      case "proposal.declined" -> "Engagement letter declined";
      case "proposal.expired" -> "Engagement letter expired";
      case "proposal.withdrawn" -> "Engagement letter withdrawn";
      case "proposal.acceptance.completed" -> "Proposal accepted";
      case "invoice.sent" -> "Fee note sent to you";
      case "invoice.payment_recorded" -> "Payment recorded";
      case "invoice.payment_reversed" -> "Payment reversed";
      case "invoice.payment_partially_reversed" -> "Payment partially reversed";
      case "trust_transaction.approved" -> "Trust transaction recorded";
      case "trust_transaction.reversed" -> "Trust transaction reversed";
      case "document.generated", "docx_document.generated" -> "Document generated for you";
      case "statement.generated" -> "Statement of Account generated";
      case "matter_closure.closed" -> "Matter closed";
      case "matter_closure.reopened" -> "Matter reopened";
      default -> eventType;
    };
  }
}
