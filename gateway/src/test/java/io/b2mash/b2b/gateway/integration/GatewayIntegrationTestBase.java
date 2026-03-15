package io.b2mash.b2b.gateway.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.b2mash.b2b.gateway.SessionTestSupport;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base class for gateway integration tests.
 *
 * <p>Provides:
 *
 * <ul>
 *   <li>H2 in-memory database for Spring Session JDBC storage
 *   <li>WireMock server for backend API (verifying token relay)
 *   <li>Shared OidcUser builder methods
 *   <li>Session table initialization
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:integrationtest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.session.store-type=jdbc",
      "spring.session.jdbc.initialize-schema=always",
      "spring.session.jdbc.table-name=SPRING_SESSION",
      "spring.session.timeout=8h",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration"
    })
@Import(GatewayIntegrationTestBase.IntegrationTestConfig.class)
abstract class GatewayIntegrationTestBase {

  protected static final String DEFAULT_ORG_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
  protected static final String DEFAULT_ORG_SLUG = "acme-corp";

  static WireMockServer backendWireMock =
      new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

  @Autowired protected MockMvc mockMvc;

  @Autowired protected DataSource dataSource;

  @BeforeAll
  static void startWireMockServers() {
    backendWireMock.start();
  }

  @AfterAll
  static void stopWireMockServers() {
    backendWireMock.stop();
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    // Route /api/** to the backend WireMock
    registry.add("spring.cloud.gateway.server.webmvc.routes[0].id", () -> "backend-api");
    registry.add("spring.cloud.gateway.server.webmvc.routes[0].uri", backendWireMock::baseUrl);
    registry.add(
        "spring.cloud.gateway.server.webmvc.routes[0].predicates[0]", () -> "Path=/api/**");
  }

  @BeforeEach
  void setUp() {
    SessionTestSupport.initSessionSchema(dataSource);
    backendWireMock.resetAll();
  }

  // --- Shared OidcUser builders ---

  protected static DefaultOidcUser buildOidcUser(
      String subject, String email, String name, String role) {
    return buildOidcUser(subject, email, name, role, DEFAULT_ORG_SLUG, DEFAULT_ORG_ID);
  }

  protected static DefaultOidcUser buildOidcUser(
      String subject, String email, String name, String role, String orgSlug, String orgId) {
    OidcIdToken idToken =
        OidcIdToken.withTokenValue("mock-id-token")
            .subject(subject)
            .claim("email", email)
            .claim("name", name)
            .claim("picture", "https://example.com/photo.jpg")
            .claim("organization", Map.of(orgSlug, Map.of("id", orgId, "roles", List.of(role))))
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

  protected static DefaultOidcUser buildOwnerUser() {
    return buildOidcUser("user-uuid-123", "alice@example.com", "Alice Owner", "owner");
  }

  protected static DefaultOidcUser buildAdminUser() {
    return buildOidcUser("admin-uuid-456", "admin@example.com", "Admin User", "admin");
  }

  protected static DefaultOidcUser buildMemberUser() {
    return buildOidcUser("member-uuid-789", "member@example.com", "Member User", "member");
  }

  // --- Session count helper ---

  protected int getSessionCount() {
    var jdbcTemplate = new JdbcTemplate(dataSource);
    return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION", Integer.class);
  }

  @TestConfiguration
  @EnableJdbcHttpSession(tableName = "SPRING_SESSION")
  static class IntegrationTestConfig {

    @Bean
    ClientHttpRequestFactory clientHttpRequestFactory() {
      // Force HTTP/1.1 for gateway proxy RestClient — WireMock does not support HTTP/2
      var httpClient = HttpClient.newBuilder().version(Version.HTTP_1_1).build();
      return new JdkClientHttpRequestFactory(httpClient);
    }

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
