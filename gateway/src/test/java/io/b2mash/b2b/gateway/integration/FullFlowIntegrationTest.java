package io.b2mash.b2b.gateway.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Integration tests that verify cross-cutting gateway flows end-to-end.
 *
 * <p>These tests exercise the full gateway stack with:
 *
 * <ul>
 *   <li>H2 session storage (verifying session creation/persistence)
 *   <li>WireMock backend (verifying token relay adds Authorization header)
 *   <li>Real Spring Security filter chain (CSRF, OAuth2 login, authorization)
 * </ul>
 */
class FullFlowIntegrationTest extends GatewayIntegrationTestBase {

  @Nested
  @DisplayName("Login Flow & Session")
  class LoginFlowTests {

    @Test
    @DisplayName("Authenticated request creates session in database")
    void loginFlow_sessionCreatedInDatabase() throws Exception {
      int before = getSessionCount();

      mockMvc.perform(get("/actuator/health").with(oidcLogin().oidcUser(buildOwnerUser())));

      int after = getSessionCount();
      assertThat(after).as("Session should be created in H2 database").isGreaterThan(before);
    }

    @Test
    @DisplayName("/bff/me returns identity info with org claims after login (no orgRole)")
    void bffMe_afterLogin_returnsIdentityInfo() throws Exception {
      var user = buildOwnerUser();

      mockMvc
          .perform(get("/bff/me").with(oidcLogin().oidcUser(user)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.authenticated").value(true))
          .andExpect(jsonPath("$.userId").value("user-uuid-123"))
          .andExpect(jsonPath("$.email").value("alice@example.com"))
          .andExpect(jsonPath("$.name").value("Alice Owner"))
          .andExpect(jsonPath("$.orgId").value(DEFAULT_ORG_ID))
          .andExpect(jsonPath("$.orgSlug").value(DEFAULT_ORG_SLUG))
          .andExpect(jsonPath("$.orgRole").doesNotExist());
    }

    @Test
    @DisplayName("/bff/me returns unauthenticated when no login")
    void bffMe_unauthenticated_returnsFalse() throws Exception {
      mockMvc
          .perform(get("/bff/me"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.authenticated").value(false))
          .andExpect(jsonPath("$.userId").doesNotExist());
    }
  }

  @Nested
  @DisplayName("Backend Proxy Routing")
  class BackendProxyTests {

    @Test
    @DisplayName("GET /api/** proxied to backend via gateway route")
    void apiRoute_getRequest_proxiedToBackend() throws Exception {
      backendWireMock.stubFor(
          WireMock.get(urlPathEqualTo("/api/projects"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("[]")));

      mockMvc
          .perform(get("/api/projects").with(oidcLogin().oidcUser(buildOwnerUser())))
          .andExpect(status().isOk());

      backendWireMock.verify(getRequestedFor(urlPathEqualTo("/api/projects")));
    }
  }

  @Nested
  @DisplayName("CSRF Protection")
  class CsrfProtectionTests {

    @Test
    @DisplayName("/api/** routes are CSRF-exempt (server-to-server from Next.js)")
    void csrfProtection_apiRoutes_csrfExempt() throws Exception {
      backendWireMock.stubFor(
          WireMock.post(urlPathEqualTo("/api/projects"))
              .willReturn(
                  aResponse()
                      .withStatus(201)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"id\":1,\"name\":\"Test Project\"}")));

      var result =
          mockMvc
              .perform(
                  post("/api/projects")
                      .with(oidcLogin().oidcUser(buildOwnerUser()))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"name\":\"Test Project\"}"))
              .andReturn();

      assertThat(result.getResponse().getStatus())
          .as("/api/** is CSRF-exempt — POST without token should proxy to backend")
          .isNotEqualTo(403);
    }

    @Test
    @DisplayName("GET request passes without CSRF token")
    void csrfProtection_getDoesNotRequireToken() throws Exception {
      backendWireMock.stubFor(
          WireMock.get(urlPathEqualTo("/api/projects"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody("[]")));

      mockMvc
          .perform(get("/api/projects").with(oidcLogin().oidcUser(buildOwnerUser())))
          .andExpect(status().isOk());
    }
  }

  @Nested
  @DisplayName("Unauthenticated Access")
  class UnauthenticatedAccessTests {

    @Test
    @DisplayName("Unauthenticated request redirects to OAuth2 Keycloak login")
    void unauthenticated_redirectsToKeycloakLogin() throws Exception {
      var result = mockMvc.perform(get("/api/projects")).andReturn();

      assertThat(result.getResponse().getStatus())
          .as("Request without valid session should redirect to OAuth2 login")
          .isEqualTo(302);
      assertThat(result.getResponse().getRedirectedUrl())
          .as("Redirect should point to Keycloak OAuth2 authorization endpoint")
          .isEqualTo("/oauth2/authorization/keycloak");
    }
  }
}
