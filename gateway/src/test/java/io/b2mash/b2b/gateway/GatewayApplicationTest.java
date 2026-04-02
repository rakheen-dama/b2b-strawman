package io.b2mash.b2b.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(
    properties = {
      "spring.session.store-type=none",
      "spring.cloud.gateway.server.webmvc.routes[0].id=test-route",
      "spring.cloud.gateway.server.webmvc.routes[0].uri=http://localhost:8080",
      "spring.cloud.gateway.server.webmvc.routes[0].predicates[0]=Path=/test/**",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration"
    })
@Import(GatewayApplicationTest.MockOAuth2Config.class)
class GatewayApplicationTest {

  @Test
  void contextLoads() {}

  @TestConfiguration
  static class MockOAuth2Config {

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
