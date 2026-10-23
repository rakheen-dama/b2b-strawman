package io.b2mash.b2b.b2bstrawman.security;

import java.util.Locale;
import java.util.Set;

/**
 * Centralized role constants used across authentication, authorization, and access control.
 *
 * <p>Org roles come from Clerk JWT v2 {@code o.rol} claim. Project roles are application-defined in
 * the {@code project_members} table. Authorization uses {@code @RequiresCapability} with
 * capabilities resolved from the member's {@code OrgRole} entity.
 */
public final class Roles {

  // Org-level roles (Clerk JWT v2 "o.rol" values)
  public static final String ORG_OWNER = "owner";
  public static final String ORG_ADMIN = "admin";
  public static final String ORG_MEMBER = "member";

  // Project-level roles (project_members.project_role values)
  public static final String PROJECT_LEAD = "lead";
  public static final String PROJECT_MEMBER = "member";

  /** The only values {@code project_members.project_role} may hold (ADR-345). */
  public static final Set<String> PROJECT_ROLES = Set.of(PROJECT_LEAD, PROJECT_MEMBER);

  // Spring Security granted authority for internal API authentication
  public static final String AUTHORITY_INTERNAL = "ROLE_INTERNAL_SERVICE";

  private Roles() {}

  /**
   * Canonical form of a project role: trimmed and lower-cased with {@link Locale#ROOT}; anything
   * outside {@link #PROJECT_ROLES} (null, blank, {@code "CONTRIBUTOR"}) becomes {@link
   * #PROJECT_MEMBER}.
   */
  public static String normaliseProjectRole(String role) {
    if (role == null || role.isBlank()) {
      return PROJECT_MEMBER;
    }
    var v = role.trim().toLowerCase(Locale.ROOT);
    return PROJECT_ROLES.contains(v) ? v : PROJECT_MEMBER;
  }
}
