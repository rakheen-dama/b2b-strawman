package io.b2mash.b2b.b2bstrawman.audit;

/**
 * Static metadata describing a single registered audit event type (or a prefix-matching family of
 * them).
 *
 * <p>The {@code eventType} field is either:
 *
 * <ul>
 *   <li>An <strong>exact</strong> event-type string (e.g. {@code security.permission.denied}), or
 *   <li>A <strong>glob prefix</strong> terminating in {@code .*} (e.g. {@code security.*}).
 * </ul>
 *
 * <p>When returned by {@link AuditEventTypeRegistry#resolve(String)}, the {@code eventType} field
 * carries the input event type unchanged (i.e. the resolver substitutes the caller's actual event
 * type into a metadata record that copies the matched prefix's classification).
 *
 * @param eventType exact event type or {@code .*} prefix
 * @param label human-readable label suitable for UI display
 * @param severity classification used for highlighting and filtering
 * @param group top-level bucket the event falls into
 */
public record AuditEventTypeMetadata(
    String eventType, String label, AuditSeverity severity, AuditEventGroup group) {}
