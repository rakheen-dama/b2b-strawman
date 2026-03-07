package io.b2mash.b2b.b2bstrawman.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class ClerkJwtUtilsGroupsTest {

  private static Jwt.Builder baseJwt() {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(300))
        .subject("user-1");
  }

  @Test
  void extractGroups_keycloakJwt_returnsGroups() {
    Jwt jwt = baseJwt().claim("groups", List.of("platform-admins", "other-group")).build();

    Set<String> groups = ClerkJwtUtils.extractGroups(jwt);

    assertThat(groups).containsExactlyInAnyOrder("platform-admins", "other-group");
  }

  @Test
  void extractGroups_clerkJwt_returnsEmpty() {
    Jwt jwt = baseJwt().claim("o", java.util.Map.of("id", "org_123", "rol", "owner")).build();

    Set<String> groups = ClerkJwtUtils.extractGroups(jwt);

    assertThat(groups).isEmpty();
  }

  @Test
  void extractGroups_missingClaim_returnsEmpty() {
    Jwt jwt = baseJwt().claim("sub", "user-1").build();

    Set<String> groups = ClerkJwtUtils.extractGroups(jwt);

    assertThat(groups).isEmpty();
  }

  @Test
  void extractGroups_nullClaim_returnsEmpty() {
    // Jwt.Builder does not allow null claim values, so a missing claim is the equivalent
    Jwt jwt = baseJwt().build();

    Set<String> groups = ClerkJwtUtils.extractGroups(jwt);

    assertThat(groups).isEmpty();
  }

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
