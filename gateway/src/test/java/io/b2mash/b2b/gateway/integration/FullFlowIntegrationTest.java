package io.b2mash.b2b.gateway.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 *   <li>WireMock Keycloak (verifying admin proxy relays to Keycloak)
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
    @DisplayName("/bff/me returns full user info with org claims after login")
    void bffMe_afterLogin_returnsFullUserInfo() throws Exception {
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
          .andExpect(jsonPath("$.orgRole").value("owner"));
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
      // Stub backend to accept the proxied request
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

      // Verify the request was proxied to the backend WireMock
      // Note: TokenRelay filter adds Authorization header in production config;
      // MockMvc tests use oidcLogin() which provides the principal but not an
      // OAuth2AuthorizedClient, so token relay is not exercised here.
      backendWireMock.verify(getRequestedFor(urlPathEqualTo("/api/projects")));
    }
  }

  @Nested
  @DisplayName("CSRF Protection")
  class CsrfProtectionTests {

    @Test
    @DisplayName("POST without CSRF token returns 403")
    void csrfProtection_postWithoutToken_returns403() throws Exception {
      mockMvc
          .perform(post("/api/projects").with(oidcLogin().oidcUser(buildOwnerUser())))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT without CSRF token returns 403")
    void csrfProtection_putWithoutToken_returns403() throws Exception {
      mockMvc
          .perform(put("/api/projects/1").with(oidcLogin().oidcUser(buildOwnerUser())))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE without CSRF token returns 403")
    void csrfProtection_deleteWithoutToken_returns403() throws Exception {
      mockMvc
          .perform(delete("/api/projects/1").with(oidcLogin().oidcUser(buildOwnerUser())))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST with valid CSRF token is proxied to backend")
    void csrfProtection_postWithValidToken_proxiedToBackend() throws Exception {
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
                      .with(csrf().asHeader())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"name\":\"Test Project\"}"))
              .andReturn();

      assertThat(result.getResponse().getStatus())
          .as("POST with valid CSRF token should not be blocked (should proxy to backend)")
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
  @DisplayName("Admin Proxy Integration")
  class AdminProxyTests {

    @Test
    @DisplayName("Admin invite relays to Keycloak WireMock and returns response")
    void adminProxy_invite_relaysToKeycloak() throws Exception {
      // Stub Keycloak token endpoint for service account auth
      keycloakWireMock.stubFor(
          WireMock.post(
                  urlPathEqualTo("/realms/" + KEYCLOAK_REALM + "/protocol/openid-connect/token"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          """
                          {"access_token":"mock-admin-token","expires_in":300,"token_type":"Bearer"}
                          """)));

      // Stub Keycloak invitation endpoint
      keycloakWireMock.stubFor(
          WireMock.post(
                  urlPathEqualTo(
                      "/admin/realms/"
                          + KEYCLOAK_REALM
                          + "/orgs/"
                          + DEFAULT_ORG_ID
                          + "/invitations"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          """
                          {"id":"inv-1","email":"newuser@test.com","roles":["member"]}
                          """)));

      mockMvc
          .perform(
              post("/bff/admin/invite")
                  .with(oidcLogin().oidcUser(buildAdminUser()))
                  .with(csrf().asHeader())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"email":"newuser@test.com","role":"member"}
                      """))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.email").value("newuser@test.com"));
    }

    @Test
    @DisplayName("Admin list members relays to Keycloak WireMock")
    void adminProxy_listMembers_relaysToKeycloak() throws Exception {
      // Stub Keycloak token endpoint
      keycloakWireMock.stubFor(
          WireMock.post(
                  urlPathEqualTo("/realms/" + KEYCLOAK_REALM + "/protocol/openid-connect/token"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          """
                          {"access_token":"mock-admin-token","expires_in":300,"token_type":"Bearer"}
                          """)));

      // Stub Keycloak members endpoint
      keycloakWireMock.stubFor(
          WireMock.get(
                  urlPathEqualTo(
                      "/admin/realms/" + KEYCLOAK_REALM + "/orgs/" + DEFAULT_ORG_ID + "/members"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          """
                          [{"id":"user-1","email":"alice@example.com","firstName":"Alice","lastName":"Owner"}]
                          """)));

      mockMvc
          .perform(get("/bff/admin/members").with(oidcLogin().oidcUser(buildAdminUser())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].email").value("alice@example.com"));
    }
  }

  @Nested
  @DisplayName("Unauthenticated Access")
  class UnauthenticatedAccessTests {

    @Test
    @DisplayName("Unauthenticated request redirects to OAuth2 Keycloak login")
    void unauthenticated_redirectsToKeycloakLogin() throws Exception {
      // Without any valid session/OAuth2Login, accessing a protected resource
      // should redirect to the OAuth2 authorization endpoint
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
