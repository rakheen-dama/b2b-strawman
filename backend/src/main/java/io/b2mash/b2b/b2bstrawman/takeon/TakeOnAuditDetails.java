package io.b2mash.b2b.b2bstrawman.takeon;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The only way to build {@code takeon.*} audit details (§17.6.4): ids, enum names, numbers,
 * booleans and short SAFE_TOKEN strings. There is no free-text {@code String} overload, so a batch
 * name, file name, header or cell value cannot reach an audit row (POPIA, no PII in audit params).
 */
public final class TakeOnAuditDetails {

  // Copied from McpAuditMetadata.java:40 (not imported — that class is MCP-scoped).
  private static final Pattern SAFE_TOKEN = Pattern.compile("^[A-Za-z0-9_.:-]{1,64}$");

  private final LinkedHashMap<String, Object> details = new LinkedHashMap<>();

  private TakeOnAuditDetails() {}

  public static TakeOnAuditDetails builder() {
    return new TakeOnAuditDetails();
  }

  public TakeOnAuditDetails put(String key, UUID value) {
    return add(key, value == null ? null : value.toString());
  }

  public TakeOnAuditDetails put(String key, Enum<?> value) {
    return add(key, value == null ? null : value.name());
  }

  public TakeOnAuditDetails put(String key, int value) {
    return add(key, value);
  }

  public TakeOnAuditDetails put(String key, long value) {
    return add(key, value);
  }

  public TakeOnAuditDetails put(String key, boolean value) {
    return add(key, value);
  }

  /** A short structural token (e.g. {@code reason_code} "USER"); anything else is refused. */
  public TakeOnAuditDetails token(String key, String value) {
    if (value == null || !SAFE_TOKEN.matcher(value).matches()) {
      throw new IllegalArgumentException("Not a safe audit token");
    }
    return add(key, value);
  }

  public Map<String, Object> build() {
    return new LinkedHashMap<>(details);
  }

  private TakeOnAuditDetails add(String key, Object value) {
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
