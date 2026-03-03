package io.b2mash.b2b.b2bstrawman.keycloak;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies that Keycloak beans are NOT created when {@code keycloak.admin.enabled} is absent or
 * false (the default in test profile).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class KeycloakConditionalActivationTest {

  @Autowired private ApplicationContext context;

  @Test
  void keycloakAdminService_notCreatedWithoutProperty() {
    assertThat(context.getBeanNamesForType(KeycloakAdminService.class)).isEmpty();
  }

  @Test
  void keycloakConfig_notCreatedWithoutProperty() {
    assertThat(context.getBeanNamesForType(KeycloakConfig.class)).isEmpty();
  }
}
