package io.b2mash.b2b.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
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

  private static final Set<Integer> PUBLIC_OK_STATUSES = Set.of(200, 404, 500, 503);

  @Autowired private MockMvc mockMvc;

  @Test
  void publicEndpoints_actuatorHealthIsAccessible() throws Exception {
    var result = mockMvc.perform(get("/actuator/health")).andReturn();
    int statusCode = result.getResponse().getStatus();
    assertThat(statusCode)
        .as("Health endpoint should return a non-redirect status")
        .isIn(PUBLIC_OK_STATUSES);
  }

  @Test
  void publicEndpoints_errorIsAccessible() throws Exception {
    var result = mockMvc.perform(get("/error")).andReturn();
    int statusCode = result.getResponse().getStatus();
    assertThat(statusCode)
        .as("Error endpoint should return a non-redirect status")
        .isIn(PUBLIC_OK_STATUSES);
  }

  @Test
  void publicEndpoints_bffMeIsAccessibleUnauthenticated() throws Exception {
    var result = mockMvc.perform(get("/bff/me")).andReturn();
    int statusCode = result.getResponse().getStatus();
    assertThat(statusCode)
        .as("/bff/me should be publicly accessible (returns {authenticated: false})")
        .isIn(PUBLIC_OK_STATUSES);
  }

  @Test
  void protectedEndpoints_apiRequiresAuth() throws Exception {
    mockMvc
        .perform(get("/api/projects"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/oauth2/authorization/keycloak"));
  }

  @Test
  void protectedEndpoints_internalIsDeniedUnauthenticated() throws Exception {
    // Unauthenticated requests to /internal/** get redirected to login first (Spring Security
    // evaluates authentication before authorization), but this is fine — the key is that
    // authenticated users are also denied (see next test).
    mockMvc
        .perform(get("/internal/test"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/oauth2/authorization/keycloak"));
  }

  @Test
  void protectedEndpoints_internalDeniedEvenWhenAuthenticated() throws Exception {
    // /internal/** must be denied for ALL users — even authenticated ones
    mockMvc.perform(get("/internal/test").with(oauth2Login())).andExpect(status().isForbidden());
  }

  @Test
  void authenticatedUser_canAccessProtectedEndpoint() throws Exception {
    // /bff/me has no controller yet (268C), so expect 404 — but NOT a redirect or 401
    var result = mockMvc.perform(get("/bff/me").with(oauth2Login())).andReturn();
    int statusCode = result.getResponse().getStatus();
    assertThat(statusCode)
        .as("Authenticated user should not be redirected or unauthorized")
        .isNotIn(302, 401);
  }

  @Test
  void csrf_postWithoutTokenReturns403() throws Exception {
    // POST without CSRF token should be rejected even for authenticated users
    mockMvc.perform(post("/bff/me").with(oauth2Login())).andExpect(status().isForbidden());
  }

  @Test
  void csrf_postWithValidTokenSucceeds() throws Exception {
    // With CSRF token, the POST should pass CSRF filter (may get 404 since no controller exists)
    var result =
        mockMvc.perform(post("/bff/me").with(oauth2Login()).with(csrf().asHeader())).andReturn();
    int statusCode = result.getResponse().getStatus();
    assertThat(statusCode)
        .as("POST with valid CSRF token should not be blocked by CSRF filter")
        .isNotEqualTo(403);
  }

  @Test
  void logout_invalidatesSessionAndRedirects() throws Exception {
    mockMvc
        .perform(post("/logout").with(oauth2Login()).with(csrf().asHeader()))
        .andExpect(status().is3xxRedirection());
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
