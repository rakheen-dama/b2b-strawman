package io.b2mash.b2b.gateway.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.gateway.service.KeycloakAdminClient;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
          + "org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration",
      "keycloak.admin.url=http://localhost:18080",
      "keycloak.admin.realm=docteams",
      "keycloak.admin.client-id=admin-cli",
      "keycloak.admin.client-secret=test-secret"
    })
@Import(BffControllerTest.MockOAuth2Config.class)
class BffControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private KeycloakAdminClient keycloakAdminClient;

  @Test
  void createOrg_disabled_nonAdmin_returns403() throws Exception {
    var oidcUser = buildOidcUser("user-uuid-123", "alice@example.com", "Alice", "owner");

    mockMvc
        .perform(
            post("/bff/orgs")
                .with(oidcLogin().oidcUser(oidcUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Test Org\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void createOrg_disabled_platformAdmin_passesThroughGate() throws Exception {
    when(keycloakAdminClient.createOrganization(anyString(), anyString(), anyString()))
        .thenReturn("org-admin-123");

    OidcIdToken idToken =
        OidcIdToken.withTokenValue("mock-token")
            .subject("admin-user")
            .claim("email", "admin@example.com")
            .claim("name", "Platform Admin")
            .claim("groups", List.of("platform-admins"))
            .issuer("https://keycloak.example.com/realms/docteams")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();

    var oidcUser = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken);

    mockMvc
        .perform(
            post("/bff/orgs")
                .with(oidcLogin().oidcUser(oidcUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Admin Org\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.orgId").value("org-admin-123"))
        .andExpect(jsonPath("$.slug").value("admin-org"));
  }

  @Test
  void me_authenticated_returnsUserInfo() throws Exception {
    var oidcUser = buildOidcUser("user-uuid-123", "alice@example.com", "Alice Owner", "owner");

    mockMvc
        .perform(get("/bff/me").with(oidcLogin().oidcUser(oidcUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.authenticated").value(true))
        .andExpect(jsonPath("$.userId").value("user-uuid-123"))
        .andExpect(jsonPath("$.email").value("alice@example.com"))
        .andExpect(jsonPath("$.name").value("Alice Owner"));
  }

  @Test
  void me_authenticated_returnsOrgId() throws Exception {
    var oidcUser = buildOidcUser("user-uuid-123", "alice@example.com", "Alice Owner", "owner");

    mockMvc
        .perform(get("/bff/me").with(oidcLogin().oidcUser(oidcUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orgId").value("org-uuid-456"));
  }

  @Test
  void me_authenticated_returnsOrgRole() throws Exception {
    var oidcUser = buildOidcUser("user-uuid-123", "alice@example.com", "Alice Owner", "admin");

    mockMvc
        .perform(get("/bff/me").with(oidcLogin().oidcUser(oidcUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orgRole").value("admin"))
        .andExpect(jsonPath("$.orgSlug").value("acme-corp"));
  }

  @Test
  void me_unauthenticated_returnsUnauthenticatedResponse() throws Exception {
    mockMvc
        .perform(get("/bff/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.authenticated").value(false))
        .andExpect(jsonPath("$.userId").doesNotExist())
        .andExpect(jsonPath("$.groups").isArray())
        .andExpect(jsonPath("$.groups").isEmpty());
  }

  @Test
  void me_noOrgClaim_returnsPartialInfo() throws Exception {
    OidcIdToken idToken =
        OidcIdToken.withTokenValue("mock-token")
            .subject("user-uuid-789")
            .claim("email", "bob@example.com")
            .claim("name", "Bob NoOrg")
            .issuer("https://keycloak.example.com/realms/docteams")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();

    var oidcUser = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken);

    mockMvc
        .perform(get("/bff/me").with(oidcLogin().oidcUser(oidcUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.authenticated").value(true))
        .andExpect(jsonPath("$.userId").value("user-uuid-789"))
        .andExpect(jsonPath("$.orgId").doesNotExist());
  }

  @Test
  void me_multipleOrgs_usesFirst() throws Exception {
    OidcIdToken idToken =
        OidcIdToken.withTokenValue("mock-token")
            .subject("user-uuid-multi")
            .claim("email", "multi@example.com")
            .claim("name", "Multi Org User")
            .claim(
                "organization",
                Map.of(
                    "first-org",
                    Map.of("id", "org-first", "roles", List.of("member")),
                    "second-org",
                    Map.of("id", "org-second", "roles", List.of("admin"))))
            .issuer("https://keycloak.example.com/realms/docteams")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();

    var oidcUser = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken);

    mockMvc
        .perform(get("/bff/me").with(oidcLogin().oidcUser(oidcUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.authenticated").value(true))
        .andExpect(jsonPath("$.orgId").isNotEmpty());
  }

  @Test
  void me_authenticated_returnsGroups() throws Exception {
    OidcIdToken idToken =
        OidcIdToken.withTokenValue("mock-token")
            .subject("admin-user")
            .claim("email", "admin@example.com")
            .claim("name", "Admin User")
            .claim("groups", List.of("platform-admins"))
            .claim(
                "organization",
                Map.of("acme-corp", Map.of("id", "org-uuid-456", "roles", List.of("owner"))))
            .issuer("https://keycloak.example.com/realms/docteams")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();

    var oidcUser = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken);

    mockMvc
        .perform(get("/bff/me").with(oidcLogin().oidcUser(oidcUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.groups[0]").value("platform-admins"));
  }

  @Test
  void me_authenticated_returnsEmptyGroupsWhenNoClaim() throws Exception {
    var oidcUser = buildOidcUser("user-uuid-123", "alice@example.com", "Alice", "owner");

    mockMvc
        .perform(get("/bff/me").with(oidcLogin().oidcUser(oidcUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.groups").isArray())
        .andExpect(jsonPath("$.groups").isEmpty());
  }

  private DefaultOidcUser buildOidcUser(String subject, String email, String name, String role) {
    OidcIdToken idToken =
        OidcIdToken.withTokenValue("mock-token")
            .subject(subject)
            .claim("email", email)
            .claim("name", name)
            .claim("picture", "https://example.com/photo.jpg")
            .claim(
                "organization",
                Map.of("acme-corp", Map.of("id", "org-uuid-456", "roles", List.of(role))))
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
