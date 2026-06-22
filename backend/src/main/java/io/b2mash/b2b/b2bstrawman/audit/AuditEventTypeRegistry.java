package io.b2mash.b2b.b2bstrawman.audit;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 *   <li>If nothing matches, synthesise a default with title-cased label, {@code severity=INFO},
 *       {@code group=STANDARD}.
 * </ol>
 */
@Component
public class AuditEventTypeRegistry {

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
            // FINANCIAL — invoice / proposal
            new AuditEventTypeMetadata(
                "invoice.*", "Invoice", AuditSeverity.INFO, AuditEventGroup.FINANCIAL),
            new AuditEventTypeMetadata(
                "proposal.*", "Proposal", AuditSeverity.INFO, AuditEventGroup.FINANCIAL),
            // SALES — CRM deal lifecycle (Phase 80)
            new AuditEventTypeMetadata(
                "deal.created", "Deal created", AuditSeverity.INFO, AuditEventGroup.SALES),
            new AuditEventTypeMetadata(
                "deal.stage_changed",
                "Deal stage changed",
                AuditSeverity.INFO,
                AuditEventGroup.SALES),
            new AuditEventTypeMetadata(
                "deal.won", "Deal won", AuditSeverity.INFO, AuditEventGroup.SALES),
            new AuditEventTypeMetadata(
                "deal.lost", "Deal lost", AuditSeverity.INFO, AuditEventGroup.SALES),
            new AuditEventTypeMetadata(
                "deal.reopened", "Deal re-opened", AuditSeverity.NOTICE, AuditEventGroup.SALES),
            // AI SPECIALIST — Phase 70
            new AuditEventTypeMetadata(
                "ai.specialist.invoked",
                "AI Specialist Invoked",
                AuditSeverity.INFO,
                AuditEventGroup.STANDARD),
            new AuditEventTypeMetadata(
                "ai.specialist.approved",
                "AI Specialist Output Approved",
                AuditSeverity.NOTICE,
                AuditEventGroup.COMPLIANCE),
            new AuditEventTypeMetadata(
                "ai.specialist.rejected",
                "AI Specialist Output Rejected",
                AuditSeverity.NOTICE,
                AuditEventGroup.COMPLIANCE),
            new AuditEventTypeMetadata(
                "ai.specialist.failed",
                "AI Specialist Failed",
                AuditSeverity.WARNING,
                AuditEventGroup.STANDARD),
            new AuditEventTypeMetadata(
                "ai.specialist.auto_applied",
                "AI Specialist Output Auto-Applied",
                AuditSeverity.NOTICE,
                AuditEventGroup.COMPLIANCE),
            new AuditEventTypeMetadata(
                "ai.specialist.expired",
                "AI Specialist Output Expired",
                AuditSeverity.INFO,
                AuditEventGroup.STANDARD),
            new AuditEventTypeMetadata(
                "ai.specialist.*",
                "AI Specialist Activity",
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
   * title-cased label, {@code severity=INFO}, and {@code group=STANDARD}.
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
        eventType, titleCase(eventType), AuditSeverity.INFO, AuditEventGroup.STANDARD);
  }

  /**
   * Converts a dotted/underscored event-type string to a title-cased human label. {@code
   * "foo.bar.baz"} ⇒ {@code "Foo Bar Baz"}; {@code "matter.closure_override"} ⇒ {@code "Matter
   * Closure_override"} (only the first character of each dot/underscore segment is forced
   * upper-case to keep the transformation reversible-ish for camel-cased segments).
   */
  private static String titleCase(String s) {
    if (s == null || s.isEmpty()) {
      return "";
    }
    var parts = s.split("[._]");
    var out = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
      if (parts[i].isEmpty()) {
        continue;
      }
      if (out.length() > 0) {
        out.append(' ');
      }
      out.append(Character.toUpperCase(parts[i].charAt(0)));
      if (parts[i].length() > 1) {
        out.append(parts[i].substring(1));
      }
    }
    return out.toString();
  }
}
