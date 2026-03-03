package io.b2mash.b2b.b2bstrawman.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/** Unit tests for {@link JwtClaimExtractor} — verifies extraction of nested "o" claim fields. */
class JwtClaimExtractorTest {

  @Test
  void extractOrgId_returnsIdFromNestedOClaim() {
    var jwt = buildJwt(Map.of("o", Map.of("id", "org_abc123", "rol", "owner", "slg", "my-org")));
    assertThat(JwtClaimExtractor.extractOrgId(jwt)).isEqualTo("org_abc123");
  }

  @Test
  void extractOrgRole_returnsRoleFromNestedOClaim() {
    var jwt = buildJwt(Map.of("o", Map.of("id", "org_abc123", "rol", "admin", "slg", "my-org")));
    assertThat(JwtClaimExtractor.extractOrgRole(jwt)).isEqualTo("admin");
  }

  @Test
  void extractOrgId_returnsNullWhenNoOClaim() {
    var jwt = buildJwt(Map.of("sub", "user_123"));
    assertThat(JwtClaimExtractor.extractOrgId(jwt)).isNull();
  }

  @Test
  void extractOrgRole_returnsNullWhenNoOClaim() {
    var jwt = buildJwt(Map.of("sub", "user_123"));
    assertThat(JwtClaimExtractor.extractOrgRole(jwt)).isNull();
  }

  @Test
  void extractOrgId_returnsNullWhenOClaimMissingIdKey() {
    var jwt = buildJwt(Map.of("o", Map.of("rol", "owner")));
    assertThat(JwtClaimExtractor.extractOrgId(jwt)).isNull();
  }

  @Test
  void extractOrgRole_returnsNullWhenOClaimMissingRolKey() {
    var jwt = buildJwt(Map.of("o", Map.of("id", "org_abc123")));
    assertThat(JwtClaimExtractor.extractOrgRole(jwt)).isNull();
  }

  @Test
  void extractOrgId_returnsNullWhenOClaimIsNotMap() {
    var jwt = buildJwt(Map.of("o", "not-a-map"));
    assertThat(JwtClaimExtractor.extractOrgId(jwt)).isNull();
  }

  @Test
  void extractOrgRole_returnsNullWhenOClaimIsNotMap() {
    var jwt = buildJwt(Map.of("o", "not-a-map"));
    assertThat(JwtClaimExtractor.extractOrgRole(jwt)).isNull();
  }

  @Test
  void extractOrgId_worksWithKeycloakFormattedToken() {
    // Keycloak uses the same "o" claim format via the custom SPI mapper
    var jwt =
        buildJwt(
            Map.of(
                "o", Map.of("id", "kc-uuid-org-id", "rol", "member", "slg", "kc-org"),
                "sub", "kc-user-uuid",
                "iss", "http://localhost:9090/realms/docteams"));
    assertThat(JwtClaimExtractor.extractOrgId(jwt)).isEqualTo("kc-uuid-org-id");
    assertThat(JwtClaimExtractor.extractOrgRole(jwt)).isEqualTo("member");
  }

  @Test
  void extractOrgId_returnsNullWhenIdValueIsNotString() {
    var jwt = buildJwt(Map.of("o", Map.of("id", 12345, "rol", "owner")));
    assertThat(JwtClaimExtractor.extractOrgId(jwt)).isNull();
  }

  private Jwt buildJwt(Map<String, Object> claims) {
    return Jwt.withTokenValue("mock-token")
        .header("alg", "RS256")
        .subject((String) claims.getOrDefault("sub", "user_default"))
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .claims(c -> c.putAll(claims))
        .build();
  }
}
