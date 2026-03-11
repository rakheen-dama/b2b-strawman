package io.b2mash.b2b.b2bstrawman.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Fluent builder for constructing audit delta maps that track old-vs-new field changes. Only
 * includes fields where the value actually changed.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * var deltas = new AuditDeltaBuilder()
 *     .track("name", oldName, newName)
 *     .track("email", oldEmail, newEmail)
 *     .track("phone", oldPhone, newPhone)
 *     .build();
 * }</pre>
 *
 * <p>Returns {@code null} when no fields changed, which is the convention expected by {@link
 * AuditEventBuilder#details(Map)}.
 */
public class AuditDeltaBuilder {

  private final Map<String, Object> deltas = new LinkedHashMap<>();

  /**
   * Tracks a field change. If {@code oldVal} and {@code newVal} are equal (via {@link
   * Objects#equals}), the field is skipped. Null values are converted to empty strings for
   * consistency with existing audit conventions.
   */
  public AuditDeltaBuilder track(String field, Object oldVal, Object newVal) {
    if (!Objects.equals(oldVal, newVal)) {
      deltas.put(
          field,
          Map.of(
              "from", oldVal != null ? oldVal : "",
              "to", newVal != null ? newVal : ""));
    }
    return this;
  }

  /**
   * Tracks a field change using string conversion. Convenience for fields where {@code
   * String.valueOf()} or {@code .toString()} is the desired serialization.
   */
  public AuditDeltaBuilder trackAsString(String field, Object oldVal, Object newVal) {
    if (!Objects.equals(oldVal, newVal)) {
      deltas.put(
          field,
          Map.of(
              "from", oldVal != null ? String.valueOf(oldVal) : "",
              "to", newVal != null ? String.valueOf(newVal) : ""));
    }
    return this;
  }

  /**
   * Returns the delta map, or {@code null} if no fields changed. The null convention is used by
   * audit event logging to skip empty updates.
   */
  public Map<String, Object> build() {
    return deltas.isEmpty() ? null : Map.copyOf(deltas);
  }

  /**
   * Returns the delta map as a mutable {@link LinkedHashMap}. Useful when additional non-delta
   * entries need to be added to the details map before passing to the audit service.
   */
  public LinkedHashMap<String, Object> buildMutable() {
    return new LinkedHashMap<>(deltas);
  }
}
