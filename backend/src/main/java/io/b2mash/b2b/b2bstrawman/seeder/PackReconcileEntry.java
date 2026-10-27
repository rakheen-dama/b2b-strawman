package io.b2mash.b2b.b2bstrawman.seeder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.b2mash.b2b.b2bstrawman.packs.ContentHashUtil;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;

/**
 * One entry of a pack JSON's {@code reconcile} block (ADR-356): set {@code field} of the
 * pack-seeded row identified by ({@code entity}, {@code key}) to {@code to}, but only where its
 * current value equals {@code from} — the <b>one rule</b>. Equality is type-strict for strings and
 * booleans, by value for numbers, and for {@code sha256:<hex>} values it compares the hash of the
 * current value computed with {@link ContentHashUtil#hashOf} (Tiptap bodies). Rows whose value
 * differs were edited by the firm and are left alone (INFO log with tenant, pack, key); clones and
 * tenant-authored rows are never resolved as targets (each seeder's resolver returns pack-seeded
 * rows only). Re-running is a no-op: a row already at {@code to} is never written.
 *
 * <p>{@code from} may be {@code null} only for entities whose seeder overrides {@link
 * AbstractPackSeeder#applyReconcileEntry} (e.g. additive {@code clause_template_association}
 * upserts); under the one rule a {@code null from} matches only a {@code null} current value.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PackReconcileEntry(String entity, String key, String field, Object from, Object to) {

  /**
   * Prefix of a body-hash value: {@code sha256:} + 64 lower-case hex of {@link
   * ContentHashUtil#hashOf}.
   */
  public static final String HASH_PREFIX = "sha256:";

  private static final Pattern HEX64 = Pattern.compile("[0-9a-f]{64}");

  /**
   * The targeted row's current field value and how to write it. Resolved per entity by a seeder
   * ({@link AbstractPackSeeder#resolveReconcileTarget}).
   *
   * <p><b>{@code sha256:} contract (Tiptap bodies, 626D).</b> When {@code to} is a {@code
   * sha256:<hex>} value, {@link #currentValue()} must return the body JSON (the one rule hashes it
   * with {@link ContentHashUtil#hashOf}), and {@link #apply(Object)} receives that hash
   * <em>string</em>, not a body: the target must ignore the argument, load the new body from the
   * pack {@code Resource} passed to {@code resolveReconcileTarget}, write it, and re-pin any stored
   * install-time hash of the row ({@code document_template.content_hash}, recomputed the way its
   * {@code PackInstaller} computes it — not the body-only {@code sha256:}) so the row still reads
   * as unedited afterwards.
   */
  public interface Target {
    Object currentValue();

    /**
     * Writes the new value. For a {@code sha256:} {@code to} the argument is the hash string — see
     * the interface Javadoc.
     */
    void apply(Object to);
  }

  /** Result of applying one entry under the one rule. */
  public enum Outcome {
    APPLIED,
    ALREADY_CURRENT,
    LEFT_ALONE,
    NO_TARGET
  }

  /**
   * Validates a pack's raw {@code reconcile} array.
   *
   * @return the entries, or an empty list when the block is absent/empty
   * @throws IllegalStateException naming the pack and entry index when an entry is malformed
   */
  public static List<PackReconcileEntry> parse(String packId, List<PackReconcileEntry> raw) {
    if (raw == null || raw.isEmpty()) {
      return List.of();
    }
    for (int i = 0; i < raw.size(); i++) {
      var e = raw.get(i);
      String where = "Pack " + packId + " reconcile[" + i + "]";
      if (e == null) {
        throw new IllegalStateException(where + " is null");
      }
      if (isBlank(e.entity()) || isBlank(e.key()) || isBlank(e.field())) {
        throw new IllegalStateException(where + ": entity, key and field are required");
      }
      if (e.to() == null) {
        throw new IllegalStateException(where + ": 'to' is required");
      }
      if (e.from() != null
          && !(e.from() instanceof String
              || e.from() instanceof Boolean
              || e.from() instanceof Number)) {
        throw new IllegalStateException(where + ": 'from' must be a string, boolean or number");
      }
      checkHash(where, "from", e.from());
      checkHash(where, "to", e.to());
      checkFinite(where, "from", e.from());
      checkFinite(where, "to", e.to());
    }
    return List.copyOf(raw);
  }

  /** ADR-356 one rule. Never writes when the current value already equals {@code to}. */
  public Outcome applyTo(Target target) {
    Object current = target.currentValue();
    if (matches(to, current)) {
      return Outcome.ALREADY_CURRENT;
    }
    if (!matches(from, current)) {
      return Outcome.LEFT_ALONE;
    }
    target.apply(to);
    return Outcome.APPLIED;
  }

  /** Applies under the one rule and logs the outcome (tenant, pack, entity, key, field). */
  public Outcome applyAndLog(
      Optional<? extends Target> target, Logger log, String tenantId, String packId) {
    if (target.isEmpty()) {
      log.info(
          "Reconcile skipped (no pack-seeded row): tenant={} pack={} {} '{}' field {}",
          tenantId,
          packId,
          entity,
          key,
          field);
      return Outcome.NO_TARGET;
    }
    Outcome outcome = applyTo(target.get());
    switch (outcome) {
      case APPLIED ->
          log.info(
              "Reconcile applied: tenant={} pack={} {} '{}' field {} updated",
              tenantId,
              packId,
              entity,
              key,
              field);
      case LEFT_ALONE ->
          log.info(
              "Reconcile skipped (edited): tenant={} pack={} {} '{}' field {} differs from the"
                  + " shipped value — left alone",
              tenantId,
              packId,
              entity,
              key,
              field);
      default ->
          log.debug(
              "Reconcile no-op: tenant={} pack={} {} '{}' field {} already current",
              tenantId,
              packId,
              entity,
              key,
              field);
    }
    return outcome;
  }

  static boolean matches(Object expected, Object current) {
    if (expected instanceof String s && s.startsWith(HASH_PREFIX)) {
      return current != null
          && s.substring(HASH_PREFIX.length()).equals(ContentHashUtil.hashOf(current));
    }
    if (expected == null || current == null) {
      return expected == current;
    }
    if (expected instanceof Number a && current instanceof Number b) {
      // Non-finite (NaN/Infinity) or unparseable numbers never match — never throw at boot.
      BigDecimal x = toDecimal(a);
      BigDecimal y = toDecimal(b);
      return x != null && y != null && x.compareTo(y) == 0;
    }
    // String/String, Boolean/Boolean; mismatched types never match (type-strict)
    return expected.equals(current);
  }

  private static void checkHash(String where, String name, Object value) {
    if (value instanceof String s
        && s.startsWith(HASH_PREFIX)
        && !HEX64.matcher(s.substring(HASH_PREFIX.length())).matches()) {
      throw new IllegalStateException(where + ": '" + name + "' is not sha256:<64 lower-case hex>");
    }
  }

  /** Exact decimal value, or {@code null} for NaN/Infinity or an unparseable {@link Number}. */
  private static BigDecimal toDecimal(Number n) {
    if (!isFinite(n)) {
      return null;
    }
    try {
      return new BigDecimal(n.toString());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static boolean isFinite(Number n) {
    return switch (n) {
      case Double d -> Double.isFinite(d);
      case Float f -> Float.isFinite(f);
      default -> true;
    };
  }

  private static void checkFinite(String where, String name, Object value) {
    if (value instanceof Number n && !isFinite(n)) {
      throw new IllegalStateException(where + ": '" + name + "' must be a finite number");
    }
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
