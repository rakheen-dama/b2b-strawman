package io.b2mash.b2b.gateway.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
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
      "app.self-service-org-creation.enabled=true",
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
@Import(BffControllerOrgCreationEnabledTest.MockOAuth2Config.class)
class BffControllerOrgCreationEnabledTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private KeycloakAdminClient keycloakAdminClient;

  @Test
  void createOrg_enabled_anyUser_returns201() throws Exception {
    when(keycloakAdminClient.createOrganization(anyString(), anyString(), anyString()))
        .thenReturn("org-new-123");

    OidcIdToken idToken =
        OidcIdToken.withTokenValue("mock-token")
            .subject("user-uuid-123")
            .claim("email", "alice@example.com")
            .claim("name", "Alice")
            .claim(
                "organization",
                Map.of("acme-corp", Map.of("id", "org-uuid-456", "roles", List.of("member"))))
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
                .content("{\"name\": \"Test Org\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.orgId").value("org-new-123"))
        .andExpect(jsonPath("$.slug").value("test-org"));
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
