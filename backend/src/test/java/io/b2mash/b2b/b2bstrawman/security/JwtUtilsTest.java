package io.b2mash.b2b.b2bstrawman.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtUtilsTest {

  private static Jwt.Builder baseJwt() {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(300))
        .subject("user-1");
  }

  // ── extractOrgId ──────────────────────────────────────────────────────

  @Test
  void extractOrgId_clerkFormat_returnsOrgId() {
    Jwt jwt = baseJwt().claim("o", Map.of("id", "org_123", "slg", "my-org")).build();

    assertThat(JwtUtils.extractOrgId(jwt)).isEqualTo("org_123");
  }

  @Test
  void extractOrgId_keycloakListFormat_returnsFirstAlias() {
    Jwt jwt = baseJwt().claim("organization", List.of("my-org")).build();

    assertThat(JwtUtils.extractOrgId(jwt)).isEqualTo("my-org");
  }

  @Test
  void extractOrgId_keycloakRichFormat_returnsAlias() {
    Jwt jwt =
        baseJwt()
            .claim(
                "organization",
                Map.of("my-org", Map.of("id", "uuid-123", "roles", List.of("owner"))))
            .build();

    assertThat(JwtUtils.extractOrgId(jwt)).isEqualTo("my-org");
  }

  @Test
  void extractOrgId_noClaim_returnsNull() {
    Jwt jwt = baseJwt().build();

    assertThat(JwtUtils.extractOrgId(jwt)).isNull();
  }

  // ── extractOrgSlug ────────────────────────────────────────────────────

  @Test
  void extractOrgSlug_clerkFormat_returnsSlug() {
    Jwt jwt = baseJwt().claim("o", Map.of("id", "org_123", "slg", "my-slug")).build();

    assertThat(JwtUtils.extractOrgSlug(jwt)).isEqualTo("my-slug");
  }

  @Test
  void extractOrgSlug_keycloakFormat_returnsAlias() {
    Jwt jwt = baseJwt().claim("organization", List.of("kc-org")).build();

    assertThat(JwtUtils.extractOrgSlug(jwt)).isEqualTo("kc-org");
  }

  // ── extractEmail ──────────────────────────────────────────────────────

  @Test
  void extractEmail_present_returnsEmail() {
    Jwt jwt = baseJwt().claim("email", "alice@example.com").build();

    assertThat(JwtUtils.extractEmail(jwt)).isEqualTo("alice@example.com");
  }

  @Test
  void extractEmail_absent_returnsNull() {
    Jwt jwt = baseJwt().build();

    assertThat(JwtUtils.extractEmail(jwt)).isNull();
  }

  // ── extractGroups ─────────────────────────────────────────────────────

  @Test
  void extractGroups_keycloakJwt_returnsGroups() {
    Jwt jwt = baseJwt().claim("groups", List.of("platform-admins", "other-group")).build();

    Set<String> groups = JwtUtils.extractGroups(jwt);

    assertThat(groups).containsExactlyInAnyOrder("platform-admins", "other-group");
  }

  @Test
  void extractGroups_clerkJwt_returnsEmpty() {
    Jwt jwt = baseJwt().claim("o", Map.of("id", "org_123", "rol", "owner")).build();

    Set<String> groups = JwtUtils.extractGroups(jwt);

    assertThat(groups).isEmpty();
  }

  @Test
  void extractGroups_missingClaim_returnsEmpty() {
    Jwt jwt = baseJwt().claim("sub", "user-1").build();

    Set<String> groups = JwtUtils.extractGroups(jwt);

    assertThat(groups).isEmpty();
  }

  @Test
  void extractGroups_nullClaim_returnsEmpty() {
    Jwt jwt = baseJwt().build();

    Set<String> groups = JwtUtils.extractGroups(jwt);

    assertThat(groups).isEmpty();
  }

  // ── isPlatformAdmin (via RequestScopes) ───────────────────────────────

  @Test
  void isPlatformAdmin_withGroup_returnsTrue() {
    Set<String> groups = Set.of("platform-admins", "other");

    ScopedValue.where(RequestScopes.GROUPS, groups)
        .run(
            () -> {
              assertThat(RequestScopes.isPlatformAdmin()).isTrue();
            });
  }

  @Test
  void isPlatformAdmin_withoutGroup_returnsFalse() {
    Set<String> groups = Set.of("other-group");

    ScopedValue.where(RequestScopes.GROUPS, groups)
        .run(
            () -> {
              assertThat(RequestScopes.isPlatformAdmin()).isFalse();
            });
  }
}
