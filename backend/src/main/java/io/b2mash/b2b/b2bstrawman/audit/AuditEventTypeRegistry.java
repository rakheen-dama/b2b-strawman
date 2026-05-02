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
                "proposal.*", "Proposal", AuditSeverity.INFO, AuditEventGroup.FINANCIAL));
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
    this.registry = Collections.unmodifiableMap(map);
    this.entriesList = List.copyOf(entries);
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
