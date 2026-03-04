package io.b2mash.b2b.gateway.config;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "spring.session.store-type=none",
      "spring.cloud.gateway.server.webmvc.routes[0].id=test-route",
      "spring.cloud.gateway.server.webmvc.routes[0].uri=http://localhost:8080",
      "spring.cloud.gateway.server.webmvc.routes[0].predicates[0]=Path=/test/**",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration"
    })
@Import(GatewaySecurityConfigTest.MockOAuth2Config.class)
class GatewaySecurityConfigTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void publicEndpoints_actuatorHealthIsAccessible() throws Exception {
    // Health endpoint is public (no redirect to login). May return 503 if no DB in test context.
    var result = mockMvc.perform(get("/actuator/health")).andReturn();
    int statusCode = result.getResponse().getStatus();
    assertNotEquals(302, statusCode, "Health endpoint should not redirect to login");
  }

  @Test
  void publicEndpoints_errorIsAccessible() throws Exception {
    // /error is permitAll — should not redirect to login
    var result = mockMvc.perform(get("/error")).andReturn();
    int statusCode = result.getResponse().getStatus();
    assertNotEquals(302, statusCode, "Error endpoint should not redirect to login");
  }

  @Test
  void protectedEndpoints_bffMeRedirectsToLogin() throws Exception {
    mockMvc
        .perform(get("/bff/me"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/oauth2/authorization/keycloak"));
  }

  @Test
  void protectedEndpoints_apiRequiresAuth() throws Exception {
    mockMvc
        .perform(get("/api/projects"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/oauth2/authorization/keycloak"));
  }

  @Test
  void protectedEndpoints_internalRequiresAuth() throws Exception {
    mockMvc
        .perform(get("/internal/test"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/oauth2/authorization/keycloak"));
  }

  @Test
  void authenticatedUser_canAccessProtectedEndpoint() throws Exception {
    // /bff/me has no controller yet (268C), so expect 404 — but NOT a redirect to login
    var result = mockMvc.perform(get("/bff/me").with(oauth2Login())).andReturn();
    int statusCode = result.getResponse().getStatus();
    assertNotEquals(302, statusCode, "Authenticated user should not be redirected to login");
    assertNotEquals(401, statusCode, "Authenticated user should not get 401");
  }

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
