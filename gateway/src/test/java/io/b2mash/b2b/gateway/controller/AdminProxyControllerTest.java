package io.b2mash.b2b.gateway.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
@Import(AdminProxyControllerTest.MockOAuth2Config.class)
class AdminProxyControllerTest {

  static WireMockServer wireMock =
      new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

  @Autowired private MockMvc mockMvc;

  private static final String ORG_ID = "org-uuid-456";
  private static final String REALM = "docteams";

  @BeforeAll
  static void startWireMock() {
    wireMock.start();
  }

  @AfterAll
  static void stopWireMock() {
    wireMock.stop();
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("keycloak.admin.url", () -> wireMock.baseUrl());
    registry.add("keycloak.admin.realm", () -> REALM);
    registry.add("keycloak.admin.client-id", () -> "admin-cli");
    registry.add("keycloak.admin.client-secret", () -> "test-secret");
  }

  @BeforeEach
  void setupTokenStub() {
    wireMock.resetAll();
    // Stub the token endpoint for service account authentication
    wireMock.stubFor(
        WireMock.post(urlPathEqualTo("/realms/" + REALM + "/protocol/openid-connect/token"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"access_token":"mock-admin-token","expires_in":300,"token_type":"Bearer"}
                        """)));
  }

  @Test
  void invite_asAdmin_succeeds() throws Exception {
    wireMock.stubFor(
        WireMock.post(urlPathEqualTo("/admin/realms/" + REALM + "/orgs/" + ORG_ID + "/invitations"))
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
  void invite_asMember_returns403() throws Exception {
    mockMvc
        .perform(
            post("/bff/admin/invite")
                .with(oidcLogin().oidcUser(buildMemberUser()))
                .with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"newuser@test.com","role":"member"}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void listInvitations_asAdmin_returnsList() throws Exception {
    wireMock.stubFor(
        WireMock.get(urlPathEqualTo("/admin/realms/" + REALM + "/orgs/" + ORG_ID + "/invitations"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        [{"id":"inv-1","email":"pending@test.com","status":"PENDING"}]
                        """)));

    mockMvc
        .perform(get("/bff/admin/invitations").with(oidcLogin().oidcUser(buildAdminUser())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].email").value("pending@test.com"));
  }

  @Test
  void revokeInvitation_asAdmin_succeeds() throws Exception {
    wireMock.stubFor(
        WireMock.delete(
                urlPathEqualTo("/admin/realms/" + REALM + "/orgs/" + ORG_ID + "/invitations/inv-1"))
            .willReturn(aResponse().withStatus(204)));

    mockMvc
        .perform(
            delete("/bff/admin/invitations/inv-1")
                .with(oidcLogin().oidcUser(buildAdminUser()))
                .with(csrf().asHeader()))
        .andExpect(status().isNoContent());
  }

  @Test
  void listMembers_asAdmin_returnsList() throws Exception {
    wireMock.stubFor(
        WireMock.get(urlPathEqualTo("/admin/realms/" + REALM + "/orgs/" + ORG_ID + "/members"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        [{"id":"user-1","username":"alice","email":"alice@test.com"}]
                        """)));

    mockMvc
        .perform(get("/bff/admin/members").with(oidcLogin().oidcUser(buildAdminUser())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].username").value("alice"));
  }

  @Test
  void changeRole_asAdmin_succeeds() throws Exception {
    wireMock.stubFor(
        WireMock.put(
                urlPathEqualTo(
                    "/admin/realms/" + REALM + "/orgs/" + ORG_ID + "/members/user-1/roles"))
            .withRequestBody(equalToJson("[\"admin\"]"))
            .willReturn(aResponse().withStatus(204)));

    mockMvc
        .perform(
            patch("/bff/admin/members/user-1/role")
                .with(oidcLogin().oidcUser(buildAdminUser()))
                .with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"role":"admin"}
                    """))
        .andExpect(status().isNoContent());
  }

  @Test
  void invite_duplicateEmail_returnsError() throws Exception {
    wireMock.stubFor(
        WireMock.post(urlPathEqualTo("/admin/realms/" + REALM + "/orgs/" + ORG_ID + "/invitations"))
            .willReturn(aResponse().withStatus(409).withBody("Invitation already exists")));

    mockMvc
        .perform(
            post("/bff/admin/invite")
                .with(oidcLogin().oidcUser(buildAdminUser()))
                .with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"existing@test.com","role":"member"}
                    """))
        .andExpect(status().isConflict());
  }

  @Test
  void invite_invalidRole_returnsError() throws Exception {
    wireMock.stubFor(
        WireMock.post(urlPathEqualTo("/admin/realms/" + REALM + "/orgs/" + ORG_ID + "/invitations"))
            .willReturn(aResponse().withStatus(400).withBody("Invalid role")));

    mockMvc
        .perform(
            post("/bff/admin/invite")
                .with(oidcLogin().oidcUser(buildAdminUser()))
                .with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"newuser@test.com","role":"invalid"}
                    """))
        .andExpect(status().isBadRequest());
  }

  private DefaultOidcUser buildAdminUser() {
    return buildOidcUser("admin-uuid", "admin@example.com", "Admin User", "admin");
  }

  private DefaultOidcUser buildMemberUser() {
    return buildOidcUser("member-uuid", "member@example.com", "Member User", "member");
  }

  private DefaultOidcUser buildOidcUser(String subject, String email, String name, String role) {
    OidcIdToken idToken =
        OidcIdToken.withTokenValue("mock-token")
            .subject(subject)
            .claim("email", email)
            .claim("name", name)
            .claim(
                "organization", Map.of("acme-corp", Map.of("id", ORG_ID, "roles", List.of(role))))
            .issuer("https://keycloak.example.com/realms/docteams")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();

    return new DefaultOidcUser(
        List.of(
            new SimpleGrantedAuthority("ROLE_USER"),
            new SimpleGrantedAuthority("ROLE_ORG_" + role.toUpperCase())),
        idToken);
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
