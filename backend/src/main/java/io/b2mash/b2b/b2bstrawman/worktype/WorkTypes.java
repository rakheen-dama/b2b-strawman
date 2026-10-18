package io.b2mash.b2b.b2bstrawman.worktype;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The one canonical form of a work type ({@code projects.work_type}, {@code
 * project_templates.work_type}, {@code field_groups.applicable_work_types} entries).
 *
 * <p>Work type stays free text, but work-type-scoped field groups match on it exactly, so every
 * write stores the canonical form: trimmed, each whitespace run collapsed to a single {@code _},
 * upper-cased with {@link Locale#ROOT}. {@code "Conveyancing"} becomes {@code CONVEYANCING}, {@code
 * " personal injury "} becomes {@code PERSONAL_INJURY}; {@code null} or blank becomes {@code null}.
 * Tenant migration {@code V3__normalise_work_types.sql} applies the same rule in SQL to rows
 * written before this existed. The Java and SQL rules are equivalent for ASCII input; Unicode
 * whitespace and case mapping may differ by database locale.
 */
public final class WorkTypes {

  /** Width of the {@code work_type varchar(50)} columns on projects and project templates. */
  public static final int MAX_LENGTH = 50;

  private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

  private WorkTypes() {}

  /**
   * {@link #normalize} for a value about to be stored in a {@code work_type varchar(50)} column.
   * Request DTOs cap the raw input at {@value #MAX_LENGTH} ({@code @Size}), but upper-casing can
   * lengthen it ({@code "ß"} becomes {@code "SS"}), so the canonical form is checked again here and
   * rejected with a 400 instead of failing at the database.
   *
   * @throws InvalidStateException when the canonical form is longer than {@value #MAX_LENGTH}
   */
  public static String normalizeForStorage(String workType) {
    String canonical = normalize(workType);
    if (canonical != null && canonical.length() > MAX_LENGTH) {
      throw new InvalidStateException(
          "Invalid work type",
          "workType must be at most " + MAX_LENGTH + " characters once normalised");
    }
    return canonical;
  }

  /** Returns the canonical form of {@code workType}, or {@code null} when it is null or blank. */
  public static String normalize(String workType) {
    if (workType == null) {
      return null;
    }
    String trimmed = workType.strip();
    if (trimmed.isEmpty()) {
      return null;
    }
    return WHITESPACE_RUN.matcher(trimmed).replaceAll("_").toUpperCase(Locale.ROOT);
  }
}
