package io.b2mash.b2b.b2bstrawman.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A PII-safe audit-details builder (Phase 93; moved from {@code takeon} in 655A so the take-on,
 * client and compliance audit rows share one guard): ids, enum names, numbers, booleans and short
 * SAFE_TOKEN strings. There is no free-text {@code String} overload, so a name, email, file name,
 * header or cell value cannot reach an audit row (POPIA, no PII in audit params, §17.6.4). Used for
 * every {@code takeon.*} event, the take-on's {@code customer.created} and {@code
 * customer.fica_attested}.
 */
public final class SafeAuditDetails {

  // Copied from McpAuditMetadata.java:40 (not imported — that class is MCP-scoped).
  private static final Pattern SAFE_TOKEN = Pattern.compile("^[A-Za-z0-9_.:-]{1,64}$");

  private final LinkedHashMap<String, Object> details = new LinkedHashMap<>();

  private SafeAuditDetails() {}

  public static SafeAuditDetails builder() {
    return new SafeAuditDetails();
  }

  public SafeAuditDetails put(String key, UUID value) {
    return add(key, value == null ? null : value.toString());
  }

  public SafeAuditDetails put(String key, Enum<?> value) {
    return add(key, value == null ? null : value.name());
  }

  public SafeAuditDetails put(String key, int value) {
    return add(key, value);
  }

  public SafeAuditDetails put(String key, long value) {
    return add(key, value);
  }

  public SafeAuditDetails put(String key, boolean value) {
    return add(key, value);
  }

  /** A short structural token (e.g. {@code reason_code} "USER"); anything else is refused. */
  public SafeAuditDetails token(String key, String value) {
    if (value == null || !SAFE_TOKEN.matcher(value).matches()) {
      throw new IllegalArgumentException("Not a safe audit token");
    }
    return add(key, value);
  }

  public Map<String, Object> build() {
    return new LinkedHashMap<>(details);
  }

  private SafeAuditDetails add(String key, Object value) {
    if (key == null || !SAFE_TOKEN.matcher(key).matches()) {
      throw new IllegalArgumentException("Not a safe audit key");
    }
    if (value == null) {
      throw new IllegalArgumentException("Audit detail " + key + " has no value");
    }
    if (details.containsKey(key)) {
      throw new IllegalArgumentException("Audit detail " + key + " given twice");
    }
    details.put(key, value);
    return this;
  }
}
