package io.b2mash.b2b.b2bstrawman.project;

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
 * written before this existed.
 */
public final class WorkTypes {

  private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

  private WorkTypes() {}

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
