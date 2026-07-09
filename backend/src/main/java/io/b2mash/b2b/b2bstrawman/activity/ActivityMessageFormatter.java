package io.b2mash.b2b.b2bstrawman.activity;

import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Maps audit events to human-readable activity feed messages. */
@Component
public class ActivityMessageFormatter {

  public ActivityItem format(
      AuditEvent event, Map<UUID, Member> actorMap, Map<UUID, PortalContact> portalContactMap) {
    String actorName =
        resolveActorName(
            event.getActorType(),
            event.getActorId(),
            actorMap,
            portalContactMap,
            event.getDetails());
    String actorAvatarUrl =
        resolveActorAvatarUrl(event.getActorType(), event.getActorId(), actorMap);
    Map<String, Object> details = event.getDetails() != null ? event.getDetails() : Map.of();

    String message = formatMessage(event.getEventType(), event.getEntityType(), actorName, details);
    String entityName = resolveEntityName(event.getEntityType(), details);

    return new ActivityItem(
        event.getId(),
        message,
        actorName,
        actorAvatarUrl,
        event.getEntityType(),
        event.getEntityId(),
        entityName,
        event.getEventType(),
        event.getOccurredAt());
  }

  private String formatMessage(
      String eventType, String entityType, String actorName, Map<String, Object> details) {
    return switch (eventType) {
      case "task.created" -> "%s created task \"%s\"".formatted(actorName, getTitle(details));
      case "task.updated" -> formatTaskUpdated(actorName, details);
      case "task.claimed" -> "%s claimed task \"%s\"".formatted(actorName, getTitle(details));
      case "task.released" -> "%s released task \"%s\"".formatted(actorName, getTitle(details));
      case "document.uploaded" ->
          "%s uploaded document \"%s\"".formatted(actorName, getFileName(details));
      case "document.updated" ->
          "%s updated document \"%s\"".formatted(actorName, getFileName(details));
      case "document.deleted" ->
          "%s deleted document \"%s\"".formatted(actorName, getFileName(details));
      case "document.generated", "docx_document.generated" ->
          "%s generated document \"%s\" from template \"%s\""
              .formatted(actorName, getFileName(details), getTemplateName(details));
      case "comment.created" ->
          "%s commented on %s \"%s\""
              .formatted(actorName, getParentType(details), getParentName(details));
      case "comment.updated" ->
          "%s edited a comment on %s \"%s\""
              .formatted(actorName, getParentType(details), getParentName(details));
      case "comment.deleted" ->
          "%s deleted a comment on %s \"%s\""
              .formatted(actorName, getParentType(details), getParentName(details));
      case "comment.visibility_changed" ->
          "%s changed comment visibility to %s"
              .formatted(actorName, details.getOrDefault("new_visibility", "unknown"));
      case "time_entry.created" ->
          "%s logged %s on task \"%s\""
              .formatted(actorName, formatDuration(details), getTaskTitle(details));
      case "time_entry.updated" ->
          "%s updated time entry on task \"%s\"".formatted(actorName, getTaskTitle(details));
      case "time_entry.deleted" ->
          "%s deleted time entry on task \"%s\"".formatted(actorName, getTaskTitle(details));
      case "project_member.added" ->
          "%s added %s to the project"
              .formatted(actorName, details.getOrDefault("name", "a member"));
      case "project_member.removed" ->
          "%s removed %s from the project"
              .formatted(actorName, details.getOrDefault("name", "a member"));
      case "proposal.created" ->
          "%s created proposal \"%s\"".formatted(actorName, getProposalNumber(details));
      case "proposal.updated" ->
          "%s updated proposal \"%s\"".formatted(actorName, getProposalNumber(details));
      case "proposal.deleted" ->
          "%s deleted proposal \"%s\"".formatted(actorName, getProposalNumber(details));
      case "proposal.sent" ->
          "%s sent proposal \"%s\"".formatted(actorName, getProposalNumber(details));
      case "proposal.accepted" ->
          "Proposal \"%s\" was accepted".formatted(getProposalNumber(details));
      case "proposal.declined" ->
          "Proposal \"%s\" was declined".formatted(getProposalNumber(details));
      case "proposal.expired" ->
          "Proposal \"%s\" has expired".formatted(getProposalNumber(details));
      case "proposal.withdrawn" ->
          "%s withdrew proposal \"%s\"".formatted(actorName, getProposalNumber(details));
      case "information_request.created" ->
          "%s created information request %s".formatted(actorName, getRequestNumber(details));
      case "information_request.sent" ->
          "Information request %s sent to %s"
              // OBS-504: never fall back to the actor name — a missing contact_name must
              // render neutrally so the sender is never misattributed as the recipient.
              .formatted(getRequestNumber(details), getContactName(details, "the client contact"));
      case "information_request.cancelled" ->
          "%s cancelled information request %s".formatted(actorName, getRequestNumber(details));
      case "information_request.completed" ->
          "%s completed — all items accepted".formatted(getRequestNumber(details));
      // LZKC-019: "information_request.item_submitted" kept as a zero-risk alias of the key
      // actually emitted by PortalInformationRequestService ("portal.request_item.submitted").
      case "information_request.item_submitted", "portal.request_item.submitted" ->
          "%s submitted \"%s\" for %s"
              .formatted(actorName, getItemName(details), getRequestNumber(details));
      case "information_request.item_accepted" ->
          "%s accepted \"%s\" for %s"
              .formatted(actorName, getItemName(details), getRequestNumber(details));
      case "information_request.item_rejected" ->
          "%s rejected \"%s\" — waiting for re-submission"
              .formatted(actorName, getItemName(details));
      case "information_request.updated" ->
          "%s updated information request %s".formatted(actorName, getRequestNumber(details));
      case "deal.created" -> "%s created a deal".formatted(actorName);
      case "deal.stage_changed" -> "%s moved a deal to a new stage".formatted(actorName);
      case "deal.won" -> "%s won a deal".formatted(actorName);
      case "deal.lost" -> "%s marked a deal as lost".formatted(actorName);
      case "deal.reopened" -> "%s re-opened a deal".formatted(actorName);
      case "mcp.write.correspondence_filed" ->
          "%s filed an email via the MCP connector".formatted(actorName);
      case "mcp.write.correspondence_refiled" ->
          "%s re-filed an already-filed email via the MCP connector".formatted(actorName);
      case "mcp.write.document_attached" ->
          "%s attached a document to a filed email via the MCP connector".formatted(actorName);
      case "mcp.write.task_proposed" ->
          "%s proposed a task from a filed email (awaiting approval)".formatted(actorName);
      // COLLECTIONS — Phase 83 (Epic 588B). POPIA: never interpolate client names/emails/subjects;
      // only the invoice number (a reference) is surfaced. System-emitted events (actorId == null)
      // render the actor as "System" via resolveActorName.
      case "collections.reminder.sent" ->
          "%s sent a collection reminder for %s"
              .formatted(actorName, getInvoiceNumber(details, "an overdue invoice"));
      case "collections.reminder.cancelled" ->
          "A collection reminder was cancelled for %s"
              .formatted(getInvoiceNumber(details, "an overdue invoice"));
      case "collections.escalation.flagged" ->
          "%s was flagged for collections escalation"
              .formatted(getInvoiceNumber(details, "An overdue invoice"));
      case "collections.digest.sent" -> "The weekly cash digest was sent";
      case "collections.policy.updated" -> "%s updated the collections policy".formatted(actorName);
      // LZKC-019: portal / document / statement / closure events used to fall through to the
      // raw default below ("<actor> performed portal.document.downloaded on document").
      case "portal.document.downloaded" ->
          "%s downloaded document \"%s\"".formatted(actorName, getFileName(details));
      case "portal.document.upload_initiated" ->
          "%s started uploading document \"%s\"".formatted(actorName, getFileName(details));
      case "portal.document.acknowledged" -> formatDocumentAcknowledged(actorName, details);
      case "portal.invoice.paid" -> formatInvoicePaid(actorName, details);
      case "statement.generated" ->
          "%s generated a statement of account \"%s\"".formatted(actorName, getFileName(details));
      case "document.generated_with_clauses" ->
          "%s generated a document with clauses from template \"%s\""
              .formatted(actorName, getTemplateName(details));
      case "document.created" ->
          "%s created document \"%s\"".formatted(actorName, getFileName(details));
      case "document.accessed" ->
          "%s downloaded document \"%s\"".formatted(actorName, getFileName(details));
      case "document.visibility_changed" ->
          "%s changed document visibility to %s"
              .formatted(actorName, extractNewValue(details.get("visibility")));
      case "matter_closure.closed" -> "%s closed the matter".formatted(actorName);
      case "matter_closure.reopened" -> "%s reopened the matter".formatted(actorName);
      case "matter.closure.override_used" ->
          "%s used an override to close the matter".formatted(actorName);
      default -> "%s performed %s on %s".formatted(actorName, eventType, entityType);
    };
  }

  private String formatTaskUpdated(String actorName, Map<String, Object> details) {
    String title = getTitle(details);
    // Check for assignment change (higher priority)
    if (details.containsKey("assignee_id")) {
      return "%s assigned task \"%s\"".formatted(actorName, title);
    }
    // Check for status change
    if (details.containsKey("status")) {
      Object statusVal = details.get("status");
      String newStatus = extractNewValue(statusVal);
      return "%s changed task \"%s\" status to %s".formatted(actorName, title, newStatus);
    }
    return "%s updated task \"%s\"".formatted(actorName, title);
  }

  private String getTitle(Map<String, Object> details) {
    Object title = details.get("title");
    if (title instanceof String s) {
      return s;
    }
    if (title instanceof Map<?, ?> m) {
      Object toVal = m.get("to");
      if (toVal instanceof String s && !s.isBlank()) {
        return s;
      }
      Object fromVal = m.get("from");
      if (fromVal instanceof String s) {
        return s;
      }
    }
    return "unknown";
  }

  private String getFileName(Map<String, Object> details) {
    Object fileName = details.get("file_name");
    return fileName instanceof String s ? s : "unknown";
  }

  private String formatDocumentAcknowledged(String actorName, Map<String, Object> details) {
    Object fileName = details.get("file_name");
    if (fileName instanceof String s && !s.isBlank()) {
      return "%s acknowledged document \"%s\"".formatted(actorName, s);
    }
    return "%s acknowledged a document".formatted(actorName);
  }

  private String formatInvoicePaid(String actorName, Map<String, Object> details) {
    Object invoiceNumber = details.get("invoice_number");
    if (invoiceNumber instanceof String s && !s.isBlank()) {
      return "%s paid fee note %s".formatted(actorName, s);
    }
    return "%s paid a fee note".formatted(actorName);
  }

  private String getParentType(Map<String, Object> details) {
    Object entityType = details.get("entity_type");
    if (entityType instanceof String s) {
      return s.toLowerCase();
    }
    return "entity";
  }

  private String getParentName(Map<String, Object> details) {
    Object entityName = details.get("entity_name");
    if (entityName instanceof String s && !s.isBlank()) {
      return s;
    }
    return getParentType(details);
  }

  private String getTaskTitle(Map<String, Object> details) {
    // Time entries reference tasks; title may not be in details
    Object title = details.get("title");
    if (title instanceof String s) {
      return s;
    }
    return "a task";
  }

  private String formatDuration(Map<String, Object> details) {
    Object durationObj = details.get("duration_minutes");
    if (durationObj instanceof Number n) {
      int minutes = n.intValue();
      int hours = minutes / 60;
      int remainingMinutes = minutes % 60;
      if (hours > 0 && remainingMinutes > 0) {
        return "%dh %dm".formatted(hours, remainingMinutes);
      } else if (hours > 0) {
        return "%dh".formatted(hours);
      } else {
        return "%dm".formatted(remainingMinutes);
      }
    }
    return "some time";
  }

  private String extractNewValue(Object val) {
    if (val instanceof String s) {
      return s;
    }
    if (val instanceof Map<?, ?> m) {
      Object toVal = m.get("to");
      if (toVal instanceof String s) {
        return s;
      }
    }
    return "unknown";
  }

  private String resolveActorName(
      String actorType,
      UUID actorId,
      Map<UUID, Member> actorMap,
      Map<UUID, PortalContact> portalContactMap,
      Map<String, Object> details) {
    if (actorId == null) {
      return "System";
    }
    if ("PORTAL_CONTACT".equals(actorType)) {
      PortalContact pc = portalContactMap.get(actorId);
      if (pc != null) {
        if (pc.getDisplayName() != null && !pc.getDisplayName().isBlank()) {
          return pc.getDisplayName();
        }
        if (pc.getEmail() != null && !pc.getEmail().isBlank()) {
          return pc.getEmail();
        }
      }
      // anonymized / archived / orphan portal_contact
      if (details != null && details.get("actor_name") instanceof String name) {
        return name;
      }
      return "Portal user";
    }
    // existing USER path
    Member member = actorMap.get(actorId);
    if (member != null) {
      return member.getName();
    }
    if (details != null && details.get("actor_name") instanceof String name) {
      return name;
    }
    return "Unknown";
  }

  private String resolveActorAvatarUrl(String actorType, UUID actorId, Map<UUID, Member> actorMap) {
    if (actorId == null || "PORTAL_CONTACT".equals(actorType)) {
      // portal contacts have no avatar; FE falls back to initials
      return null;
    }
    Member member = actorMap.get(actorId);
    return member != null ? member.getAvatarUrl() : null;
  }

  private String getProposalNumber(Map<String, Object> details) {
    Object num = details.get("proposal_number");
    return num instanceof String s ? s : "unknown";
  }

  private String getRequestNumber(Map<String, Object> details) {
    Object num = details.get("request_number");
    return num instanceof String s ? s : "unknown";
  }

  private String getItemName(Map<String, Object> details) {
    Object name = details.get("item_name");
    return name instanceof String s ? s : "an item";
  }

  private String getContactName(Map<String, Object> details, String fallback) {
    Object name = details.get("contact_name");
    return name instanceof String s ? s : fallback;
  }

  private String getTemplateName(Map<String, Object> details) {
    Object name = details.get("template_name");
    return name instanceof String s ? s : "unknown template";
  }

  private String getInvoiceNumber(Map<String, Object> details, String fallback) {
    Object num = details.get("invoice_number");
    return (num instanceof String s && !s.isBlank()) ? s : fallback;
  }

  private String resolveEntityName(String entityType, Map<String, Object> details) {
    return switch (entityType) {
      case "task" -> getTitle(details);
      case "document" -> getFileName(details);
      case "comment" -> getParentName(details);
      case "time_entry" -> getTaskTitle(details);
      case "project_member" -> {
        Object name = details.get("name");
        yield name instanceof String s ? s : "member";
      }
      case "proposal" -> getProposalNumber(details);
      case "information_request" -> getRequestNumber(details);
      case "request_item" -> getItemName(details);
      case "generated_document" -> getFileName(details);
      case "DEAL", "deal" -> {
        Object title = details.get("title");
        yield (title instanceof String s && !s.isBlank()) ? s : "deal";
      }
      case "correspondence" -> {
        Object subj = details.get("subject");
        yield (subj instanceof String s && !s.isBlank()) ? s : "correspondence";
      }
      case "collection_activity" -> {
        Object num = details.get("invoice_number");
        yield (num instanceof String s && !s.isBlank()) ? s : "collection activity";
      }
      default -> "unknown";
    };
  }
}
