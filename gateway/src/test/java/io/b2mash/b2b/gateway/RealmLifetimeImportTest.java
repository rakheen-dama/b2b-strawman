package io.b2mash.b2b.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Asserts the committed realm session/token lifetimes (ADR-307). The gateway module is a direct
 * child of the repo root, so the realm export resolves at {@code ../compose/keycloak/...} from the
 * module working dir. No Keycloak / Spring context required.
 */
class RealmLifetimeImportTest {

  private static final Path REALM_EXPORT =
      Path.of("..", "compose", "keycloak", "realm-export.json");

  private JsonNode realm() throws Exception {
    assertThat(Files.exists(REALM_EXPORT))
        .as("realm-export.json should exist at %s", REALM_EXPORT.toAbsolutePath())
        .isTrue();
    return JsonMapper.builder().build().readTree(Files.readString(REALM_EXPORT));
  }

  @Test
  void accessTokenLifespan_is300() throws Exception {
    assertThat(realm().get("accessTokenLifespan").asInt()).isEqualTo(300);
  }

  @Test
  void ssoSessionIdleTimeout_is1800() throws Exception {
    assertThat(realm().get("ssoSessionIdleTimeout").asInt()).isEqualTo(1800);
  }

  @Test
  void ssoSessionMaxLifespan_is36000() throws Exception {
    assertThat(realm().get("ssoSessionMaxLifespan").asInt()).isEqualTo(36000);
  }

  @Test
  void noOfflineSessionKeys_present() throws Exception {
    JsonNode r = realm();
    assertThat(r.has("offlineSessionIdleTimeout"))
        .as("no offline_access for gateway-bff (ADR-307) — offlineSession* must not be set")
        .isFalse();
    assertThat(r.has("offlineSessionMaxLifespan")).isFalse();
  }
}
