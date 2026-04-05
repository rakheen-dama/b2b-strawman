package io.b2mash.b2b.b2bstrawman.testutil;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.Map;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/**
 * Shared test utility for creating mock JWT request processors. Replaces the private {@code
 * ownerJwt()}, {@code adminJwt()}, {@code memberJwt()} helpers that were duplicated across 195+
 * test files.
 *
 * <p>JWT claims follow the Clerk v2 format with org context nested under the {@code "o"} claim.
 * Subject strings are test-specific (e.g. {@code "user_ct_owner"}) — pass a prefix to keep them
 * unique per test class.
 */
public final class TestJwtFactory {

  private TestJwtFactory() {}

  // ── Core role builders (parameterized — covers all patterns) ──────────────

  /** Creates a JWT with the specified org ID, subject, and role. */
  public static JwtRequestPostProcessor jwtAs(String orgId, String subject, String role) {
    return jwt().jwt(j -> j.subject(subject).claim("o", Map.of("id", orgId, "rol", role)));
  }

  /** Creates an owner JWT with custom subject. */
  public static JwtRequestPostProcessor ownerJwt(String orgId, String subject) {
    return jwtAs(orgId, subject, "owner");
  }

  /** Creates an admin JWT with custom subject. */
  public static JwtRequestPostProcessor adminJwt(String orgId, String subject) {
    return jwtAs(orgId, subject, "admin");
  }

  /** Creates a member JWT with custom subject. */
  public static JwtRequestPostProcessor memberJwt(String orgId, String subject) {
    return jwtAs(orgId, subject, "member");
  }

  // ── Convenience builders (default subjects) ───────────────────────────────

  /** Creates an owner JWT with default subject {@code "user_owner"}. */
  public static JwtRequestPostProcessor ownerJwt(String orgId) {
    return ownerJwt(orgId, "user_owner");
  }

  /** Creates an admin JWT with default subject {@code "user_admin"}. */
  public static JwtRequestPostProcessor adminJwt(String orgId) {
    return adminJwt(orgId, "user_admin");
  }

  /** Creates a member JWT with default subject {@code "user_member"}. */
  public static JwtRequestPostProcessor memberJwt(String orgId) {
    return memberJwt(orgId, "user_member");
  }

  // ── Capability testing builders ───────────────────────────────────────────

  /**
   * Creates a JWT for a custom-role member. Used in capability/authorization tests where the member
   * has a non-standard role with specific capability overrides.
   */
  public static JwtRequestPostProcessor customRoleJwt(String orgId, String subject) {
    return jwtAs(orgId, subject, "member");
  }

  /**
   * Creates a JWT for a member with no capabilities. Used to verify authorization denials. Subject
   * defaults to {@code "user_nocap"}.
   */
  public static JwtRequestPostProcessor noCapabilityJwt(String orgId) {
    return jwtAs(orgId, "user_nocap", "member");
  }

  /** Creates a no-capability JWT with custom subject. */
  public static JwtRequestPostProcessor noCapabilityJwt(String orgId, String subject) {
    return jwtAs(orgId, subject, "member");
  }

  // ── JIT / provisioning test builders (with extra claims) ──────────────────

  /** Creates a JWT with email and name claims for JIT sync tests. */
  public static JwtRequestPostProcessor jwtWithEmailAndName(
      String orgId, String subject, String email, String name) {
    return jwt()
        .jwt(
            j ->
                j.subject(subject)
                    .claim("o", Map.of("id", orgId, "rol", "member"))
                    .claim("email", email)
                    .claim("name", name));
  }

  /** Creates a JWT with org slug for provisioning tests. */
  public static JwtRequestPostProcessor jwtWithSlug(
      String orgId, String slug, String subject, String role) {
    return jwt()
        .jwt(j -> j.subject(subject).claim("o", Map.of("id", orgId, "rol", role, "slg", slug)));
  }
}
