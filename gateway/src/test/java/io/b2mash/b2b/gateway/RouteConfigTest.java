package io.b2mash.b2b.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
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
      "spring.datasource.url=jdbc:h2:mem:routetest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.session.store-type=jdbc",
      "spring.session.jdbc.initialize-schema=always",
      "spring.cloud.gateway.server.webmvc.routes[0].id=backend-api",
      "spring.cloud.gateway.server.webmvc.routes[0].uri=http://localhost:8080",
      "spring.cloud.gateway.server.webmvc.routes[0].predicates[0]=Path=/api/**",
      "gateway.frontend-url=http://localhost:3000",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration"
    })
@Import(RouteConfigTest.MockOAuth2Config.class)
class RouteConfigTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void apiRoute_unauthenticatedReturns401() throws Exception {
    // /api/** paths use a custom HttpStatusEntryPoint returning 401 (not a 302 redirect) so the
    // Next.js BFF can reliably detect auth failures from fetch responses. See commit d6643210
    // (OBS-AN-006 / GAP-AN-003).
    mockMvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
  }

  @Test
  void internalRoute_deniedEvenWhenAuthenticated() throws Exception {
    var result =
        mockMvc
            .perform(get("/internal/sync").with(oauth2Login()))
            .andExpect(status().isForbidden())
            .andReturn();

    // Verify no backend content leaks in the response body
    String body = result.getResponse().getContentAsString();
    assertThat(body).doesNotContain("internal", "sync", "backend");
  }

  @Test
  void unknownRoute_unauthenticatedIsDenied() throws Exception {
    // Unknown (non-/api/**) routes fall through to `anyRequest().authenticated()`. Under this
    // test slice (OAuth2ClientAutoConfiguration excluded) the default entry point returns 401
    // rather than redirecting. In production the oauth2Login DSL wires
    // LoginUrlAuthenticationEntryPoint so the browser is redirected to the KC authorization
    // endpoint — either way the request is denied without leaking backend data.
    mockMvc.perform(get("/unknown/path")).andExpect(status().isUnauthorized());
  }

  @Test
  void corsHeaders_presentOnPreflightRequest() throws Exception {
    var result =
        mockMvc
            .perform(
                options("/api/projects")
                    .header("Origin", "http://localhost:3000")
                    .header("Access-Control-Request-Method", "GET")
                    .header("Access-Control-Request-Headers", "X-XSRF-TOKEN"))
            .andReturn();

    assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin"))
        .isEqualTo("http://localhost:3000");
    assertThat(result.getResponse().getHeader("Access-Control-Allow-Credentials"))
        .isEqualTo("true");
  }

  @Test
  void corsHeaders_notPresentForDisallowedOrigin() throws Exception {
    var result =
        mockMvc
            .perform(
                options("/api/projects")
                    .header("Origin", "http://evil.com")
                    .header("Access-Control-Request-Method", "GET"))
            .andReturn();

    assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin")).isNull();
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
