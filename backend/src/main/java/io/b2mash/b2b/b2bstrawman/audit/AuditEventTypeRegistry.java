package io.b2mash.b2b.b2bstrawman.audit;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * In-code catalogue of audit-event metadata (label, severity, group) keyed by event type or {@code
 * .*} prefix.
 *
 * <p>Per ADR-261 the catalogue is maintained as Java code, not in the database — severity and group
 * are derived at read time so historical rows reclassify automatically whenever the catalogue
 * changes.
 *
 * <p>{@link #resolve(String)} implements longest-prefix-wins per architecture §12.3.3:
 *
 * <ol>
 *   <li>Exact match wins (e.g. {@code matter.closure.override_used} ⇒ CRITICAL).
 *   <li>Otherwise walk down on dots and probe {@code s + ".*"} — the deepest matching prefix wins.
 *   <li>If nothing matches, synthesise a default with a legal sentence-cased label ({@link
 *       #legalLabel}), {@code severity=INFO}, {@code group=STANDARD}.
 * </ol>
 */
@Component
public class AuditEventTypeRegistry {

  /**
   * D12 / ADR-342 (Phase 88 625C): legal display words for the FIRST dot-segment of a stored key.
   * Only the synthesised label changes — stored event types and entity literals are keys, never
   * renamed.
   */
  private static final Map<String, String> LEGAL_HEADS =
      Map.of(
          "project", "Matter",
          "customer", "Client",
          "invoice", "Fee note",
          "legal_disbursement", "Disbursement",
          "project_member", "Matter team member",
          "project_budget", "Matter budget");

  private final Map<String, AuditEventTypeMetadata> registry;
  private final List<AuditEventTypeMetadata> entriesList;

  public AuditEventTypeRegistry() {
    var entries =
        List.of(
            // SECURITY — auth and authz failures
            new AuditEventTypeMetadata(
                "security.login.failure",
                "Login Failed",
                AuditSeverity.WARNING,
                AuditEventGroup.SECURITY),
            new AuditEventTypeMetadata(
                "security.permission.denied",
                "Permission Denied",
                AuditSeverity.WARNING,
                AuditEventGroup.SECURITY),
            new AuditEventTypeMetadata(
                "security.*", "Security Event", AuditSeverity.NOTICE, AuditEventGroup.SECURITY),
            // COMPLIANCE — matter closure
            new AuditEventTypeMetadata(
                "matter.closure.override_used",
                "Matter Closure Override Used",
                AuditSeverity.CRITICAL,
                AuditEventGroup.COMPLIANCE),
            new AuditEventTypeMetadata(
                "matter.closure.*",
                "Matter Closure",
                AuditSeverity.NOTICE,
                AuditEventGroup.COMPLIANCE),
            // FINANCIAL — trust
            new AuditEventTypeMetadata(
                "trust.transaction.approved",
                "Trust Transaction Approved",
                AuditSeverity.NOTICE,
                AuditEventGroup.FINANCIAL),
            new AuditEventTypeMetadata(
                "trust.transaction.rejected",
                "Trust Transaction Rejected",
                AuditSeverity.WARNING,
                AuditEventGroup.FINANCIAL),
            new AuditEventTypeMetadata(
                "trust.*", "Trust Activity", AuditSeverity.NOTICE, AuditEventGroup.FINANCIAL),
            // DATA — data protection
            new AuditEventTypeMetadata(
                "dataprotection.dsar.*",
                "Data Subject Request",
                AuditSeverity.NOTICE,
                AuditEventGroup.DATA),
            new AuditEventTypeMetadata(
                "dataprotection.export.*",
                "Data Export",
                AuditSeverity.NOTICE,
                AuditEventGroup.DATA),
            new AuditEventTypeMetadata(
                "dataprotection.anonymization.*",
                "Data Anonymization",
                AuditSeverity.NOTICE,
                AuditEventGroup.DATA),
            new AuditEventTypeMetadata(
                "dataprotection.*", "Data Protection", AuditSeverity.NOTICE, AuditEventGroup.DATA),
            // COMPLIANCE — audit export
            new AuditEventTypeMetadata(
                "audit.export.generated",
                "Audit Log Exported",
                AuditSeverity.NOTICE,
                AuditEventGroup.COMPLIANCE),
            // SECURITY — role / capability changes
            new AuditEventTypeMetadata(
                "member.role_changed",
                "Member Role Changed",
                AuditSeverity.WARNING,
                AuditEventGroup.SECURITY),
            new AuditEventTypeMetadata(
                "orgrole.capability.changed",
                "Role Capabilities Changed",
                AuditSeverity.WARNING,
                AuditEventGroup.SECURITY),
            // FINANCIAL — fee note (stored key invoice.*; ADR-342: storage key, do not rename).
            // D12 (Phase 88 625C): legal display label — labels derive at read time, so history
            // relabels and exports regenerate on demand.
            new AuditEventTypeMetadata(
                "invoice.*", "Fee note", AuditSeverity.INFO, AuditEventGroup.FINANCIAL),
            // AI SKILL FAILURE — Phase 72 skill pipeline (AiExecutionPersistenceService) still
            // emits
            // ai.specialist.failed; the other Phase 70 ai.specialist.* entries were removed in
            // Phase 86
            // (Epic 601, D2). Renaming to ai.execution.failed is Phase 87.
            new AuditEventTypeMetadata(
                "ai.specialist.failed",
                "AI Specialist Failed",
                AuditSeverity.WARNING,
                AuditEventGroup.STANDARD),
            // AI EXECUTION GATES — Phase 81 (Epic 585). Gate creation/approval/rejection is a
            // compliance-relevant audit trail (an authorised member decides whether an AI-proposed
            // action runs). The exact ai.gate.created entry classifies the propose_task gate as
            // NOTICE/COMPLIANCE (an authorised member's decision on an AI-proposed action); the
            // prefix carries the remaining
            // gate lifecycle (approved/rejected/expired) so none fall through to default INFO.
            new AuditEventTypeMetadata(
                "ai.gate.created",
                "AI Execution Gate Created",
                AuditSeverity.NOTICE,
                AuditEventGroup.COMPLIANCE),
            new AuditEventTypeMetadata(
                "ai.gate.*",
                "AI Execution Gate Activity",
                AuditSeverity.NOTICE,
                AuditEventGroup.COMPLIANCE),
            // MCP write family (Phase 81)
            new AuditEventTypeMetadata(
                "mcp.write.correspondence_filed",
                "Correspondence Filed via MCP",
                AuditSeverity.NOTICE,
                AuditEventGroup.STANDARD),
            new AuditEventTypeMetadata(
                "mcp.write.correspondence_refiled",
                "Correspondence Re-filed via MCP",
                AuditSeverity.NOTICE,
                AuditEventGroup.STANDARD),
            new AuditEventTypeMetadata(
                "mcp.write.document_attached",
                "Document Attached via MCP",
                AuditSeverity.NOTICE,
                AuditEventGroup.STANDARD),
            new AuditEventTypeMetadata(
                "mcp.write.task_proposed",
                "Task Proposed via MCP",
                AuditSeverity.NOTICE,
                AuditEventGroup.STANDARD),
            new AuditEventTypeMetadata(
                "mcp.write.*", "MCP Write", AuditSeverity.NOTICE, AuditEventGroup.STANDARD),
            // COLLECTIONS — Phase 83 (Epic 588B). Dunning-engine lifecycle + policy changes.
            // Gate propose/approve/reject for reminders stay covered by ai.gate.* — not
            // re-registered.
            // All exact entries → no prefix-severity invariant participation.
            new AuditEventTypeMetadata(
                "collections.reminder.sent",
                "Collection Reminder Sent",
                AuditSeverity.NOTICE,
                AuditEventGroup.STANDARD),
            new AuditEventTypeMetadata(
                "collections.reminder.cancelled",
                "Collection Reminder Cancelled",
                AuditSeverity.NOTICE,
                AuditEventGroup.STANDARD),
            new AuditEventTypeMetadata(
                "collections.escalation.flagged",
                "Collection Escalation Flagged",
                AuditSeverity.NOTICE,
                AuditEventGroup.STANDARD),
            new AuditEventTypeMetadata(
                "collections.digest.sent",
                "Cash Digest Sent",
                AuditSeverity.NOTICE,
                AuditEventGroup.STANDARD),
            new AuditEventTypeMetadata(
                "collections.policy.updated",
                "Collections Policy Updated",
                AuditSeverity.NOTICE,
                AuditEventGroup.STANDARD),
            // DISBURSEMENTS — Phase 88 (Epic 621A, U14). Additive stored key (ADR-342: storage
            // key, do not rename; registry pin 29 → 30 per Phase 88 OQ #10). Classified exactly
            // as the unregistered disbursement.* siblings resolve (INFO/STANDARD) — only the
            // label is explicit.
            new AuditEventTypeMetadata(
                "disbursement.restored",
                "Disbursement restored",
                AuditSeverity.INFO,
                AuditEventGroup.STANDARD),
            // FEE-NOTE PAYMENTS — Phase 88 (Epic 622B, U15, ADR-347). Additive stored keys
            // (ADR-342: storage key, do not rename; registry pin 30 → 32 per Phase 88 OQ #10).
            // invoice.partially_paid keeps its invoice.* parent's INFO/FINANCIAL classification
            // with an explicit label; invoice.payment_excess is NOTICE — an external source moved
            // more money than the fee note owed and the excess must be reconciled.
            new AuditEventTypeMetadata(
                "invoice.partially_paid",
                "Fee note partially paid",
                AuditSeverity.INFO,
                AuditEventGroup.FINANCIAL),
            new AuditEventTypeMetadata(
                "invoice.payment_excess",
                "Fee note payment exceeded balance",
                AuditSeverity.NOTICE,
                AuditEventGroup.FINANCIAL),
            // ACCEPTANCE — Phase 88 (Epic 624F, U23). Additive stored key (ADR-342: storage key,
            // do not rename; registry pin 32 → 33 per Phase 88 OQ #10). Classified exactly as the
            // unregistered acceptance.* siblings resolve (INFO/STANDARD) — only the label is
            // explicit.
            new AuditEventTypeMetadata(
                "acceptance.declined",
                "Acceptance declined",
                AuditSeverity.INFO,
                AuditEventGroup.STANDARD));
    var map = new HashMap<String, AuditEventTypeMetadata>();
    for (var entry : entries) {
      var previous = map.put(entry.eventType(), entry);
      if (previous != null) {
        // Fail fast at bean construction — a duplicate key is a catalogue authoring bug; silently
        // overwriting would mask reclassifications and cause severity/group drift downstream.
        throw new IllegalStateException(
            "Duplicate audit event type in registry: " + entry.eventType());
      }
    }
    validatePrefixSeverityInvariant(entries);
    this.registry = Collections.unmodifiableMap(map);
    this.entriesList = List.copyOf(entries);
  }

  /**
   * Enforces that no nested prefix entry (e.g. {@code dataprotection.dsar.*}) carries a different
   * severity from a parent prefix that would otherwise subsume its match space (e.g. {@code
   * dataprotection.*}).
   *
   * <p>The severity pre-flight in {@link DatabaseAuditService#findEvents} excludes
   * <em>exact</em>-under-prefix mismatches via Step C, but cannot exclude prefix-under-prefix
   * mismatches without an exclude-prefix mechanism in the SQL filter. Allowing such a pair would
   * over-include rows under the child prefix when querying for the parent's severity. Failing
   * construction prevents the catalogue from being authored into that broken state.
   */
  static void validatePrefixSeverityInvariant(List<AuditEventTypeMetadata> entries) {
    var prefixEntries = entries.stream().filter(e -> e.eventType().endsWith(".*")).toList();
    for (var parent : prefixEntries) {
      var parentLiteral = parent.eventType().substring(0, parent.eventType().length() - 2) + ".";
      for (var child : prefixEntries) {
        if (child == parent) {
          continue;
        }
        var childLiteral = child.eventType().substring(0, child.eventType().length() - 2) + ".";
        if (childLiteral.startsWith(parentLiteral)
            && !childLiteral.equals(parentLiteral)
            && child.severity() != parent.severity()) {
          throw new IllegalStateException(
              "Audit event registry invariant violation: prefix entry '"
                  + child.eventType()
                  + "' (severity="
                  + child.severity()
                  + ") is nested under '"
                  + parent.eventType()
                  + "' (severity="
                  + parent.severity()
                  + ") with a different severity. Both must share a severity, or the severity "
                  + "pre-flight in DatabaseAuditService.findEvents must be extended with an "
                  + "exclude-prefix mechanism.");
        }
      }
    }
  }

  /**
   * Resolves the metadata for an event type using longest-prefix-wins semantics.
   *
   * <p>If the input matches a registered exact event type, that entry is returned verbatim. If the
   * input matches a registered {@code .*} prefix, a new {@link AuditEventTypeMetadata} is returned
   * that carries the caller's input as its {@code eventType} but copies the prefix entry's label,
   * severity, and group. If no entry matches, a default fallback metadata is synthesised with a
   * legal sentence-cased label ({@link #legalLabel}), {@code severity=INFO}, and {@code
   * group=STANDARD}.
   *
   * @param eventType the audit event type to classify (may be {@code null}, in which case a default
   *     fallback for the empty string is returned)
   * @return non-null metadata describing the input event type
   */
  public AuditEventTypeMetadata resolve(String eventType) {
    if (eventType == null) {
      return defaultFor("");
    }
    // 1) exact match
    var exact = registry.get(eventType);
    if (exact != null) {
      return exact;
    }
    // 2) longest matching prefix — walk down on dots
    var s = eventType;
    while (s.contains(".")) {
      s = s.substring(0, s.lastIndexOf('.'));
      var hit = registry.get(s + ".*");
      if (hit != null) {
        return new AuditEventTypeMetadata(eventType, hit.label(), hit.severity(), hit.group());
      }
    }
    // 3) default fallback
    return defaultFor(eventType);
  }

  /**
   * Returns the full catalogue (excludes the synthesised default fallback). The list is
   * unmodifiable and stable across the lifetime of the bean.
   */
  public List<AuditEventTypeMetadata> entries() {
    return entriesList;
  }

  /**
   * Returns the subset of catalogue entries whose severity is contained in the given set. Used by
   * the severity pre-flight in 502A. If {@code severities} is null or empty, returns all entries.
   */
  public List<AuditEventTypeMetadata> entriesMatching(Set<AuditSeverity> severities) {
    if (severities == null || severities.isEmpty()) {
      return entriesList;
    }
    return entriesList.stream().filter(entry -> severities.contains(entry.severity())).toList();
  }

  /** Synthesises the default metadata for an unregistered event type. */
  private static AuditEventTypeMetadata defaultFor(String eventType) {
    return new AuditEventTypeMetadata(
        eventType, legalLabel(eventType), AuditSeverity.INFO, AuditEventGroup.STANDARD);
  }

  /**
   * Sentence-cased legal label for a dotted/underscored key — the one label helper for synthesised
   * event-type labels and the entity-type facet labels ({@link DatabaseAuditService#facets}). The
   * first dot-segment goes through {@link #LEGAL_HEADS} (else its underscores become spaces), the
   * remaining segments follow with dots/underscores as spaces, and only the first character is
   * upper-cased: {@code "project.created_from_template"} ⇒ {@code "Matter created from template"};
   * {@code "customer.lifecycle.transitioned"} ⇒ {@code "Client lifecycle transitioned"}; {@code
   * "project_member.added"} ⇒ {@code "Matter team member added"}; {@code "foo.bar.baz"} ⇒ {@code
   * "Foo bar baz"}; entity literal {@code "trust_account"} ⇒ {@code "Trust account"}.
   */
  static String legalLabel(String key) {
    if (key == null || key.isBlank()) {
      return "";
    }
    int dot = key.indexOf('.');
    String head = dot < 0 ? key : key.substring(0, dot);
    String rest = dot < 0 ? "" : key.substring(dot + 1);
    String words = LEGAL_HEADS.getOrDefault(head, spaced(head));
    String tail = spaced(rest);
    String label = words.isEmpty() ? tail : tail.isEmpty() ? words : words + " " + tail;
    return label.isEmpty() ? "" : Character.toUpperCase(label.charAt(0)) + label.substring(1);
  }

  /** Dots and underscores become single spaces; empty segments are dropped. */
  private static String spaced(String s) {
    return Arrays.stream(s.split("[._]"))
        .filter(part -> !part.isEmpty())
        .collect(Collectors.joining(" "));
  }
}
