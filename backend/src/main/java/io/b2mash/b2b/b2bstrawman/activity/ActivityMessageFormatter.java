package io.b2mash.b2b.b2bstrawman.activity;

import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.template.VariableFormatter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Maps audit events to human-readable activity feed messages. The single sentence source for the
 * matter Activity feed, Home "Recent Activity" and the MCP {@code get_matter_activity} tool
 * (ADR-349: one formatter, Home delegates). Stored event keys and entity literals are keys, not
 * labels (ADR-342) — the legal words ("matter", "client", "fee note") live only here.
 */
@Component
public class ActivityMessageFormatter {

  /**
   * Which money a sentence may show the viewer. The sentences carry amounts and trust references
   * (625B), so they follow the same redaction as the matter list / Home widgets: amounts need
   * {@code FINANCIAL_VISIBILITY}, trust amounts and references need {@code VIEW_TRUST}, and
   * owner/admin always see both (614A home rule). A hidden detail drops its fragment (R-3).
   */
  public record AmountVisibility(boolean financial, boolean trust) {
    /** Everything visible — for callers that have already gated on both capabilities. */
    public static final AmountVisibility ALL = new AmountVisibility(true, true);
  }

  /** Detail keys whose values are money (rendered through {@link #money}). */
  private static final Set<String> MONEY_KEYS = Set.of("amount", "excess", "budget_amount");

  /** Formats with every amount visible ({@link AmountVisibility#ALL}). */
  public ActivityItem format(
      AuditEvent event, Map<UUID, Member> actorMap, Map<UUID, PortalContact> portalContactMap) {
    return format(event, actorMap, portalContactMap, AmountVisibility.ALL);
  }

  public ActivityItem format(
      AuditEvent event,
      Map<UUID, Member> actorMap,
      Map<UUID, PortalContact> portalContactMap,
      AmountVisibility visibility) {
    String actorName =
        resolveActorName(
            event.getActorType(),
            event.getActorId(),
            actorMap,
            portalContactMap,
            event.getDetails());
    String actorAvatarUrl =
        resolveActorAvatarUrl(event.getActorType(), event.getActorId(), actorMap);
    Map<String, Object> details =
        redact(
            event.getEventType(),
            event.getEntityType(),
            event.getDetails() != null ? event.getDetails() : Map.of(),
            visibility);

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
    if (eventType == null) {
      return "%s %s %s".formatted(actorName, Verbs.FALLBACK, Nouns.of(entityType));
    }
    if (isTrustApprovalKey(eventType)) {
      return formatTrustApproval(eventType, actorName, details);
    }
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
          "%s added %s to the matter team"
              .formatted(actorName, details.getOrDefault("name", "a member"));
      case "project_member.removed" ->
          "%s removed %s from the matter team"
              .formatted(actorName, details.getOrDefault("name", "a member"));
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
              .formatted(actorName, getInvoiceNumber(details, "an overdue fee note"));
      case "collections.reminder.cancelled" ->
          "A collection reminder was cancelled for %s"
              .formatted(getInvoiceNumber(details, "an overdue fee note"));
      case "collections.escalation.flagged" ->
          "%s was flagged for collections escalation"
              .formatted(getInvoiceNumber(details, "An overdue fee note"));
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
      // --- 625B (U5, ADR-349): legal templates. Money via VariableFormatter (plain text, en-ZA
      // unless the details carry a currency); a missing detail drops its fragment (R-3). ---
      // Matter
      case "project.created" ->
          "%s opened the matter%s".formatted(actorName, quotedSuffix(details, "name"));
      case "project.created_from_template" -> {
        String template = str(details, "template_name");
        yield template != null
            ? "%s opened the matter from template \"%s\"".formatted(actorName, template)
            : "%s opened the matter".formatted(actorName);
      }
      case "project.updated" -> "%s updated the matter".formatted(actorName);
      case "project.completed" -> "%s completed the matter".formatted(actorName);
      case "project.archived" -> "%s archived the matter".formatted(actorName);
      case "project.reopened" -> "%s reopened the matter".formatted(actorName);
      case "project.deleted" ->
          "%s deleted the matter%s".formatted(actorName, quotedSuffix(details, "name"));
      case "project_member.role_changed" -> formatRoleChanged(actorName, details);
      case "budget.created" ->
          "%s set the matter budget%s"
              .formatted(
                  actorName, prefixed(" of ", money(details, "budget_amount", "budget_currency")));
      case "budget.updated" ->
          "%s updated the matter budget%s"
              .formatted(
                  actorName, prefixed(" to ", money(details, "budget_amount", "budget_currency")));
      case "budget.deleted" -> "%s removed the matter budget".formatted(actorName);
      case "customer.linked" -> "%s linked a client to the matter".formatted(actorName);
      case "customer.unlinked" -> "%s unlinked a client from the matter".formatted(actorName);
      // Disbursements
      case "disbursement.created" ->
          "%s recorded a disbursement%s%s"
              .formatted(
                  actorName, ofMoney(details, "amount"), paren(humanise(str(details, "category"))));
      case "disbursement.updated" -> "%s updated a disbursement".formatted(actorName);
      case "disbursement.submitted" ->
          "%s submitted a disbursement for approval".formatted(actorName);
      case "disbursement.approved" -> "%s approved a disbursement".formatted(actorName);
      case "disbursement.rejected" -> "%s rejected a disbursement".formatted(actorName);
      case "disbursement.written_off" -> "%s wrote off a disbursement".formatted(actorName);
      case "disbursement.restored" -> "%s restored a written-off disbursement".formatted(actorName);
      case "disbursement.billed" -> "%s billed a disbursement on a fee note".formatted(actorName);
      case "disbursement.unmarked_billed" ->
          "%s removed a disbursement from a fee note".formatted(actorName);
      case "disbursement.receipt_attached" -> {
        String fileName = str(details, "file_name");
        yield fileName != null
            ? "%s attached receipt \"%s\" to a disbursement".formatted(actorName, fileName)
            : "%s attached a receipt to a disbursement".formatted(actorName);
      }
      // Fee notes
      case "invoice.created" -> "%s created a draft fee note".formatted(actorName);
      case "invoice.approved" -> "%s approved %s".formatted(actorName, feeNote(details));
      case "invoice.sent" -> "%s sent %s".formatted(actorName, feeNote(details));
      case "invoice.partially_paid" ->
          "%s recorded a part payment%s on %s"
              .formatted(actorName, ofMoney(details, "amount"), feeNote(details));
      case "invoice.paid" ->
          "%s recorded a payment%s on %s — paid in full"
              .formatted(actorName, ofMoney(details, "amount"), feeNote(details));
      case "invoice.payment_excess" ->
          "%s recorded an overpayment%s on %s"
              .formatted(actorName, ofMoney(details, "excess"), feeNote(details));
      case "invoice.payment_reversed", "invoice.payment_partially_reversed" ->
          "%s reversed a payment%s on %s"
              .formatted(actorName, ofMoney(details, "amount"), feeNote(details));
      case "invoice.voided" -> "%s voided %s".formatted(actorName, feeNote(details));
      case "payment.completed" -> "Online payment%s received".formatted(ofMoney(details, "amount"));
      case "payment.failed" -> "Online payment failed for %s".formatted(feeNote(details));
      case "payment.session.created" ->
          "An online payment link was created%s"
              .formatted(prefixed(" for ", money(details, "amount", null)));
      case "payment.session.cancelled" -> "An online payment link was cancelled";
      case "payment.session.expired" -> {
        String number = str(details, "invoice_number");
        yield number != null
            ? "The online payment link for fee note %s expired".formatted(number)
            : "An online payment link expired";
      }
      // Acceptance (R-4: template_name > document_name > document_file_name, else "a document")
      case "acceptance.created" ->
          "%s created an acceptance request for %s"
              .formatted(actorName, orElse(acceptanceDocument(details), "a document"));
      case "acceptance.sent" -> {
        String document = orElse(acceptanceDocument(details), "a document");
        String contact = str(details, "contact_name");
        // OBS-504: a missing recipient renders neutrally, never as the actor.
        yield contact != null
            ? "%s sent %s to %s for acceptance".formatted(actorName, document, contact)
            : "%s sent %s for acceptance".formatted(actorName, document);
      }
      case "acceptance.viewed" -> "%s viewed %s".formatted(actorName, acceptedDocument(details));
      case "acceptance.accepted" ->
          "%s accepted %s".formatted(actorName, acceptedDocument(details));
      case "acceptance.declined" ->
          "%s declined %s".formatted(actorName, acceptedDocument(details));
      case "acceptance.certificate_generated" ->
          "Certificate of acceptance generated%s"
              .formatted(prefixed(" for ", acceptanceDocument(details)));
      case "acceptance.reminded" ->
          "%s sent an acceptance reminder%s"
              .formatted(actorName, prefixed(" for ", acceptanceDocument(details)));
      case "acceptance.revoked" ->
          "auto_revoked".equals(str(details, "reason"))
              ? "An earlier acceptance request was withdrawn"
              : "%s revoked an acceptance request%s"
                  .formatted(actorName, prefixed(" for ", acceptanceDocument(details)));
      case "acceptance.expired" ->
          "An acceptance request expired%s"
              .formatted(prefixed(" for ", acceptanceDocument(details)));
      // Trust (approval keys are computed — see formatTrustApproval)
      case "trust_deposit.recorded" -> formatTrustRecorded(actorName, "a trust deposit", details);
      case "trust_payment.recorded" -> formatTrustRecorded(actorName, "a trust payment", details);
      case "trust_transfer.recorded" -> formatTrustRecorded(actorName, "a trust transfer", details);
      case "trust_refund.recorded" -> formatTrustRecorded(actorName, "a trust refund", details);
      case "trust_fee_transfer.recorded" ->
          "%s recorded a fee transfer%s from trust%s"
              .formatted(actorName, ofMoney(details, "amount"), ref(details, "reference"));
      case "trust_transaction.reversed" ->
          "%s reversed %s%s"
              .formatted(
                  actorName,
                  withArticle(trustTypeNoun(str(details, "original_type"))),
                  ofMoney(details, "amount"));
      // Expenses
      case "expense.created" ->
          "%s recorded an expense%s%s"
              .formatted(
                  actorName,
                  prefixed(" of ", money(details, "amount", "currency")),
                  paren(humanise(str(details, "category"))));
      case "expense.updated" -> "%s updated an expense".formatted(actorName);
      case "expense.deleted" -> "%s deleted an expense".formatted(actorName);
      case "expense.restored" -> "%s restored an expense".formatted(actorName);
      case "expense.written_off" -> "%s wrote off an expense".formatted(actorName);
      // Court calendar / prescription
      case "court_date.created" ->
          "%s diarised a court date%s"
              .formatted(actorName, prefixed(" for ", date(details, "scheduled_date")));
      case "court_date.updated" -> "%s updated a court date".formatted(actorName);
      case "court_date.postponed" ->
          "%s postponed a court date%s"
              .formatted(actorName, prefixed(" to ", date(details, "new_date")));
      case "court_date.cancelled" -> "%s cancelled a court date".formatted(actorName);
      case "court_date.outcome_recorded" ->
          "%s recorded the outcome of a court date".formatted(actorName);
      case "prescription_tracker.created" -> {
        String prescribes = date(details, "prescription_date");
        yield prescribes != null
            ? "%s started tracking prescription (prescribes %s)".formatted(actorName, prescribes)
            : "%s started tracking prescription".formatted(actorName);
      }
      case "prescription_tracker.updated" ->
          "%s updated a prescription tracker".formatted(actorName);
      case "prescription_tracker.interrupted" ->
          "%s recorded an interruption of prescription".formatted(actorName);
      // Adverse parties
      case "adverse_party.linked" -> {
        String name = str(details, "adverse_party_name");
        yield name != null
            ? "%s linked adverse party \"%s\" to the matter".formatted(actorName, name)
            : "%s linked an adverse party to the matter".formatted(actorName);
      }
      case "adverse_party.unlinked" ->
          "%s unlinked an adverse party from the matter".formatted(actorName);
      case "adverse_party.created" -> "%s added %s".formatted(actorName, adverseParty(details));
      case "adverse_party.updated" -> "%s updated %s".formatted(actorName, adverseParty(details));
      case "adverse_party.deleted" -> "%s removed %s".formatted(actorName, adverseParty(details));
      // Tasks / task items / time
      case "task.completed" -> "%s completed %s".formatted(actorName, task(details));
      case "task.cancelled" -> "%s cancelled %s".formatted(actorName, task(details));
      case "task.reopened" -> "%s reopened %s".formatted(actorName, task(details));
      case "task.deleted" -> "%s deleted %s".formatted(actorName, task(details));
      case "task.recurrence_created" ->
          "%s created the next occurrence of %s".formatted(actorName, task(details));
      case "task_item.created" -> {
        String title = str(details, "title");
        yield title != null
            ? "%s added item \"%s\" to a task".formatted(actorName, title)
            : "%s added an item to a task".formatted(actorName);
      }
      case "task_item.deleted" -> {
        String title = str(details, "title");
        yield title != null
            ? "%s removed item \"%s\" from a task".formatted(actorName, title)
            : "%s removed an item from a task".formatted(actorName);
      }
      case "task_item.toggled" ->
          Boolean.TRUE.equals(deltaTo(details, "completed"))
              ? "%s ticked off a task item".formatted(actorName)
              : "%s unticked a task item".formatted(actorName);
      case "task_item.updated" -> "%s updated a task item".formatted(actorName);
      case "task_item.reordered" -> "%s reordered a task's items".formatted(actorName);
      case "time_entry.rate_re_snapshot" -> formatRateReSnapshot(actorName, details);
      case "information_request.reminder_sent" -> {
        String number = str(details, "request_number");
        yield number != null
            ? "Reminder sent for information request %s".formatted(number)
            : "Reminder sent for an information request";
      }
      // Client-tab keys (checklist details are camelCase at the publisher)
      case "checklist.item.completed" ->
          "%s completed %s".formatted(actorName, checklistItem(details));
      case "checklist.item.skipped" -> "%s skipped %s".formatted(actorName, checklistItem(details));
      case "checklist.item.reopened" ->
          "%s reopened %s".formatted(actorName, checklistItem(details));
      case "checklist.instance.created" -> {
        String template = str(details, "templateName");
        yield template != null
            ? "%s started checklist \"%s\"".formatted(actorName, template)
            : "%s started a checklist".formatted(actorName);
      }
      case "checklist.instance.completed" -> "%s completed a checklist".formatted(actorName);
      case "customer.lifecycle.transitioned" -> formatLifecycleTransition(actorName, details);
      // Legal generic (ADR-349): no key — present or future — prints "performed" or a raw key.
      default -> "%s %s %s".formatted(actorName, Verbs.of(eventType), Nouns.of(entityType));
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
      // ADR-342: the stored parent type is a key; the rendered word is legal (R-8).
      return switch (s.toUpperCase(Locale.ROOT)) {
        case "PROJECT" -> "matter";
        case "CUSTOMER" -> "client";
        case "INVOICE" -> "fee note";
        default -> s.toLowerCase(Locale.ROOT);
      };
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
      case "information_request" -> getRequestNumber(details);
      case "request_item" -> getItemName(details);
      case "generated_document" -> getFileName(details);
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

  // --- 625B helpers (U5, ADR-349) ---

  /** Drops the money (and trust reference) keys the viewer may not read; never mutates input. */
  private static Map<String, Object> redact(
      String eventType,
      String entityType,
      Map<String, Object> details,
      AmountVisibility visibility) {
    boolean trustEvent =
        "trust_transaction".equals(entityType)
            || (eventType != null && eventType.startsWith("trust_"));
    Set<String> hidden;
    if (trustEvent) {
      hidden = visibility.trust() ? Set.of() : Set.of("amount", "reference");
    } else {
      hidden = visibility.financial() ? Set.of() : MONEY_KEYS;
    }
    if (hidden.isEmpty() || hidden.stream().noneMatch(details::containsKey)) {
      return details;
    }
    var visible = new HashMap<String, Object>(details);
    visible.keySet().removeAll(hidden);
    return visible;
  }

  /** Sentence dates: VariableFormatter's "d MMMM yyyy" pattern, plain text (never HTML-escaped). */
  private static final DateTimeFormatter SENTENCE_DATE =
      DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

  /** R-4: acceptance document name keys, most human first. */
  private static final String[] ACCEPTANCE_DOCUMENT_KEYS = {
    "template_name", "document_name", "document_file_name"
  };

  private String formatRoleChanged(String actorName, Map<String, Object> details) {
    Object to = deltaTo(details, "role");
    if ("lead".equals(to)) {
      return "%s made a team member the matter lead".formatted(actorName);
    }
    if (to instanceof String role && !role.isBlank()) {
      return "%s changed a matter team member's role to %s".formatted(actorName, role);
    }
    return "%s changed a matter team member's role".formatted(actorName);
  }

  private String formatTrustRecorded(String actorName, String noun, Map<String, Object> details) {
    return "%s recorded %s%s%s"
        .formatted(actorName, noun, ofMoney(details, "amount"), ref(details, "reference"));
  }

  /**
   * Approval-gated trust keys are computed at the publisher ({@code "trust_" + type.toLowerCase() +
   * ".approved" | ".rejected" | ".first_approved"}), so one branch covers every type, present and
   * future (R-10).
   */
  private static boolean isTrustApprovalKey(String eventType) {
    return eventType.startsWith("trust_")
        && (eventType.endsWith(".approved")
            || eventType.endsWith(".rejected")
            || eventType.endsWith(".first_approved"));
  }

  private String formatTrustApproval(
      String eventType, String actorName, Map<String, Object> details) {
    String type = str(details, "transaction_type");
    if (type == null) {
      type = eventType.substring("trust_".length(), eventType.indexOf('.'));
    }
    String what = "the " + trustTypeNoun(type) + ofMoney(details, "amount");
    if (eventType.endsWith(".first_approved")) {
      return "%s gave first approval to %s".formatted(actorName, what);
    }
    if (eventType.endsWith(".approved")) {
      return "%s approved %s".formatted(actorName, what);
    }
    return "%s rejected %s".formatted(actorName, what);
  }

  private static String trustTypeNoun(String transactionType) {
    if (transactionType == null) {
      return "trust transaction";
    }
    return switch (transactionType.toUpperCase(Locale.ROOT)) {
      case "DEPOSIT" -> "trust deposit";
      case "PAYMENT" -> "trust payment";
      case "TRANSFER", "TRANSFER_OUT", "TRANSFER_IN" -> "trust transfer";
      case "FEE_TRANSFER" -> "fee transfer";
      case "REFUND" -> "trust refund";
      case "REVERSAL" -> "trust reversal";
      case "INTEREST_CREDIT" -> "interest credit";
      default -> "trust transaction";
    };
  }

  private String formatRateReSnapshot(String actorName, Map<String, Object> details) {
    Integer count = null;
    String raw = str(details, "entries_updated");
    if (raw != null) {
      try {
        count = Integer.valueOf(raw);
      } catch (NumberFormatException e) {
        count = null;
      }
    }
    if (count == null) {
      return "%s refreshed billing rates on time entries".formatted(actorName);
    }
    return "%s refreshed billing rates on %d %s"
        .formatted(actorName, count, count == 1 ? "time entry" : "time entries");
  }

  private String formatLifecycleTransition(String actorName, Map<String, Object> details) {
    String from = humanise(str(details, "oldStatus"));
    String to = humanise(str(details, "newStatus"));
    if (from != null && to != null) {
      return "%s moved the client from %s to %s".formatted(actorName, from, to);
    }
    if (to != null) {
      return "%s moved the client to %s".formatted(actorName, to);
    }
    return "%s changed the client's status".formatted(actorName);
  }

  /** A scalar detail as trimmed text; null when absent, blank, or structured (delta map / list). */
  private static String str(Map<String, Object> details, String key) {
    Object value = details.get(key);
    if (value == null || value instanceof Map<?, ?> || value instanceof Collection<?>) {
      return null;
    }
    String text = String.valueOf(value).strip();
    return text.isEmpty() ? null : text;
  }

  /** The {@code to} side of an AuditDeltaBuilder {@code {from, to}} delta; null if absent. */
  private static Object deltaTo(Map<String, Object> details, String key) {
    return details.get(key) instanceof Map<?, ?> delta ? delta.get("to") : null;
  }

  private static String orElse(String value, String fallback) {
    return value != null ? value : fallback;
  }

  /** {@code prefix + value}, or {@code ""} when the value is missing (R-3: drop the fragment). */
  private static String prefixed(String prefix, String value) {
    return value != null ? prefix + value : "";
  }

  /** {@code " (value)"}, or {@code ""} when missing — never {@code "()"}. */
  private static String paren(String value) {
    return value != null ? " (" + value + ")" : "";
  }

  /** R-12: a reference rendered raw as {@code " (REF)"}, omitted when blank. */
  private static String ref(Map<String, Object> details, String key) {
    return paren(str(details, key));
  }

  private static String quotedSuffix(Map<String, Object> details, String key) {
    return prefixed(" ", quoted(str(details, key)));
  }

  private static String quoted(String value) {
    return value != null ? "\"" + value + "\"" : null;
  }

  /**
   * R-1/R-2: an amount as plain-text currency through {@link VariableFormatter} — en-ZA unless the
   * details carry a currency code. Null when the amount is absent or not numeric (never "R0,00").
   */
  private static String money(Map<String, Object> details, String amountKey, String currencyKey) {
    String raw = str(details, amountKey);
    if (raw == null) {
      return null;
    }
    BigDecimal amount;
    try {
      amount = new BigDecimal(raw);
    } catch (NumberFormatException e) {
      return null;
    }
    String currency = currencyKey != null ? str(details, currencyKey) : null;
    return VariableFormatter.formatCurrencyText(amount, VariableFormatter.resolveLocale(currency));
  }

  /** {@code " of R…"} for a ZAR amount (trust, disbursement, payment details carry no currency). */
  private static String ofMoney(Map<String, Object> details, String amountKey) {
    return prefixed(" of ", money(details, amountKey, null));
  }

  /** R-15: an ISO date (or date-time) as "d MMMM yyyy"; null when absent or unparsable. */
  private static String date(Map<String, Object> details, String key) {
    String raw = str(details, key);
    if (raw == null || raw.length() < 10) {
      return null;
    }
    try {
      return LocalDate.parse(raw.substring(0, 10)).format(SENTENCE_DATE);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  /** {@code "fee note INV-0003"}, or {@code "a fee note"} when the number is missing (R-3). */
  private static String feeNote(Map<String, Object> details) {
    String number = str(details, "invoice_number");
    return number != null ? "fee note " + number : "a fee note";
  }

  /** R-4: the most human document name the publisher wrote, quoted; null when there is none. */
  private static String acceptanceDocument(Map<String, Object> details) {
    for (String key : ACCEPTANCE_DOCUMENT_KEYS) {
      String name = str(details, key);
      if (name != null) {
        return quoted(name);
      }
    }
    return null;
  }

  /** viewed / accepted / declined publishers write no document name yet (follow-up). */
  private static String acceptedDocument(Map<String, Object> details) {
    return orElse(acceptanceDocument(details), "a document sent for acceptance");
  }

  private static String task(Map<String, Object> details) {
    String title = str(details, "title");
    return title != null ? "task " + quoted(title) : "a task";
  }

  private static String adverseParty(Map<String, Object> details) {
    String name = str(details, "name");
    return name != null ? "adverse party " + quoted(name) : "an adverse party";
  }

  private static String checklistItem(Map<String, Object> details) {
    String name = str(details, "itemName");
    return name != null ? "checklist item " + quoted(name) : "a checklist item";
  }

  /** {@code COUNSEL_FEES} → {@code "Counsel fees"}; null stays null. */
  private static String humanise(String enumName) {
    if (enumName == null) {
      return null;
    }
    String words = enumName.replace('_', ' ').strip().toLowerCase(Locale.ROOT);
    return words.isEmpty() ? null : Character.toUpperCase(words.charAt(0)) + words.substring(1);
  }

  private static String withArticle(String words) {
    boolean vowel = "aeiou".indexOf(Character.toLowerCase(words.charAt(0))) >= 0;
    return (vowel ? "an " : "a ") + words;
  }

  /**
   * Legal generic default — verb from the key's last {@code .}-segment (ADR-349). Unknown verbs
   * read "updated", so the default can never print "performed" or echo a raw key.
   */
  private static final class Verbs {

    static final String FALLBACK = "updated";

    private static final Map<String, String> MAP =
        Map.ofEntries(
            Map.entry("created", "created"),
            Map.entry("updated", "updated"),
            Map.entry("deleted", "deleted"),
            Map.entry("approved", "approved"),
            Map.entry("rejected", "rejected"),
            Map.entry("sent", "sent"),
            Map.entry("completed", "completed"),
            Map.entry("archived", "archived"),
            Map.entry("unarchived", "restored"),
            Map.entry("reopened", "reopened"),
            Map.entry("cancelled", "cancelled"),
            Map.entry("recorded", "recorded"),
            Map.entry("linked", "linked"),
            Map.entry("unlinked", "unlinked"),
            Map.entry("uploaded", "uploaded"),
            Map.entry("generated", "generated"),
            Map.entry("voided", "voided"),
            Map.entry("restored", "restored"),
            Map.entry("submitted", "submitted"),
            Map.entry("added", "added"),
            Map.entry("removed", "removed"),
            Map.entry("closed", "closed"),
            Map.entry("accessed", "opened"),
            Map.entry("downloaded", "downloaded"),
            Map.entry("cloned", "copied"),
            Map.entry("duplicated", "copied"),
            Map.entry("transitioned", "changed the status of"),
            Map.entry("configured", "configured"),
            Map.entry("published", "published"),
            Map.entry("imported", "imported"),
            Map.entry("enabled", "enabled"),
            Map.entry("disabled", "disabled"),
            Map.entry("accepted", "accepted"),
            Map.entry("declined", "declined"),
            Map.entry("reversed", "reversed"),
            Map.entry("written_off", "wrote off"),
            Map.entry("placed", "placed"),
            Map.entry("withdrawn", "withdrew"),
            // conflict_check.performed — the key's own verb must never reach the sentence.
            Map.entry("performed", "ran"));

    private Verbs() {}

    static String of(String eventType) {
      String last = eventType.substring(eventType.lastIndexOf('.') + 1);
      return MAP.getOrDefault(last, FALLBACK);
    }
  }

  /**
   * Legal nouns for the stored entity literals (ADR-342: the keys never change, only the words).
   * Unknown literals read "a/an &lt;words&gt;" ({@code tariff_item} → "a tariff item").
   */
  private static final class Nouns {

    private static final Map<String, String> MAP =
        Map.ofEntries(
            Map.entry("project", "the matter"),
            Map.entry("customer", "the client"),
            Map.entry("invoice", "a fee note"),
            Map.entry("legal_disbursement", "a disbursement"),
            Map.entry("trust_transaction", "a trust transaction"),
            Map.entry("project_budget", "the matter budget"),
            Map.entry("project_member", "a matter team member"),
            Map.entry("acceptance_request", "an acceptance request"),
            Map.entry("task", "a task"),
            Map.entry("task_item", "a task item"),
            Map.entry("document", "a document"),
            Map.entry("generated_document", "a document"),
            Map.entry("comment", "a comment"),
            Map.entry("time_entry", "a time entry"),
            Map.entry("expense", "an expense"),
            Map.entry("court_date", "a court date"),
            Map.entry("prescription_tracker", "a prescription tracker"),
            Map.entry("adverse_party", "an adverse party"),
            Map.entry("adverse_party_link", "an adverse party link"),
            Map.entry("information_request", "an information request"),
            Map.entry("request_item", "a request item"),
            Map.entry("checklist_instance", "a checklist"),
            Map.entry("checklist_instance_item", "a checklist item"),
            Map.entry("matter_closure", "the matter closure"),
            Map.entry("trust_account", "a trust account"),
            Map.entry("trust_investment", "a trust investment"),
            Map.entry("trust_reconciliation", "a trust reconciliation"),
            Map.entry("org_settings", "the firm settings"),
            Map.entry("collection_activity", "a collection reminder"));

    private Nouns() {}

    static String of(String entityType) {
      if (entityType == null) {
        return "a record";
      }
      String noun = MAP.get(entityType);
      if (noun != null) {
        return noun;
      }
      String words = entityType.replaceAll("[^A-Za-z0-9]+", " ").strip().toLowerCase(Locale.ROOT);
      return words.isEmpty() ? "a record" : withArticle(words);
    }
  }
}
