package io.b2mash.b2b.b2bstrawman.security;

/**
 * Centralized role constants used across authentication, authorization, and access control.
 *
 * <p>Org roles come from Clerk JWT v2 {@code o.rol} claim. Project roles are application-defined in
 * the {@code project_members} table. Spring authorities are the {@code ROLE_} prefixed versions
 * used by {@code @PreAuthorize}.
 */
public final class Roles {

  // Org-level roles (Clerk JWT v2 "o.rol" values)
  public static final String ORG_OWNER = "owner";
  public static final String ORG_ADMIN = "admin";
  public static final String ORG_MEMBER = "member";

  // Project-level roles (project_members.project_role values)
  public static final String PROJECT_LEAD = "lead";
  public static final String PROJECT_MEMBER = "member";

  // Spring Security granted authorities
  public static final String AUTHORITY_ORG_OWNER = "ROLE_ORG_OWNER";
  public static final String AUTHORITY_ORG_ADMIN = "ROLE_ORG_ADMIN";
  public static final String AUTHORITY_ORG_MEMBER = "ROLE_ORG_MEMBER";
  public static final String AUTHORITY_INTERNAL = "ROLE_INTERNAL_SERVICE";

  private Roles() {}
}
