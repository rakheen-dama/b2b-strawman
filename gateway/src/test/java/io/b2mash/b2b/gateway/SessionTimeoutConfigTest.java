package io.b2mash.b2b.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.context.TestPropertySource;
import org.yaml.snakeyaml.Yaml;

/**
 * Verifies the gateway Spring session timeout is anchored to the Keycloak SSO max lifespan (10h,
 * ADR-307) and that the production profile inherits it.
 *
 * <p>The default-profile assertion reads the actual bound {@code spring.session.timeout} {@link
 * Duration} from the {@link Environment} (NOT a {@code @TestPropertySource} override — that would
 * defeat the test). The production-inherits assertion parses {@code application-production.yml} and
 * confirms it sets no {@code timeout} override, so it inherits 10h from {@code application.yml}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:timeouttest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.session.store-type=jdbc",
      "spring.session.jdbc.initialize-schema=always",
      "spring.session.jdbc.table-name=SPRING_SESSION",
      // NOTE: deliberately NO spring.session.timeout override — read the real value.
      "spring.cloud.gateway.server.webmvc.routes[0].id=test-route",
      "spring.cloud.gateway.server.webmvc.routes[0].uri=http://localhost:8080",
      "spring.cloud.gateway.server.webmvc.routes[0].predicates[0]=Path=/test/**",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration"
    })
@Import(SessionTimeoutConfigTest.TestConfig.class)
class SessionTimeoutConfigTest {

  @Autowired private Environment environment;

  @Test
  void sessionTimeout_defaultProfile_is10h() {
    Duration timeout = environment.getProperty("spring.session.timeout", Duration.class);
    assertThat(timeout)
        .as("gateway session must be anchored to the SSO max lifespan (10h, ADR-307)")
        .isEqualTo(Duration.ofHours(10));
  }

  @Test
  @SuppressWarnings("unchecked")
  void productionProfile_doesNotOverrideTimeout_soItInherits10h() throws Exception {
    try (InputStream in = getClass().getResourceAsStream("/application-production.yml")) {
      assertThat(in).as("application-production.yml must be on the classpath").isNotNull();
      Map<String, Object> root = new Yaml().load(in);
      Map<String, Object> spring = (Map<String, Object>) root.get("spring");
      Map<String, Object> session =
          spring == null ? null : (Map<String, Object>) spring.get("session");
      boolean hasTimeout = session != null && session.containsKey("timeout");
      assertThat(hasTimeout)
          .as(
              "production must NOT override spring.session.timeout — it inherits 10h from"
                  + " application.yml (Redis-prod parity, ADR-307)")
          .isFalse();
    }
  }

  @TestConfiguration
  static class TestConfig {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      ClientRegistration registration =
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("test")
              .clientSecret("test")
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
              .scope("openid", "profile", "email")
              .authorizationUri("https://example.com/auth")
              .tokenUri("https://example.com/token")
              .jwkSetUri("https://example.com/jwks")
              .userInfoUri("https://example.com/userinfo")
              .userNameAttributeName("sub")
              .build();
      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
