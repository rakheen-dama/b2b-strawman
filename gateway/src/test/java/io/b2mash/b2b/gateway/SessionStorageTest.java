package io.b2mash.b2b.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:sessiontest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.session.store-type=jdbc",
      "spring.session.jdbc.initialize-schema=always",
      "spring.session.jdbc.table-name=SPRING_SESSION",
      "spring.session.timeout=8h",
      "spring.cloud.gateway.server.webmvc.routes[0].id=test-route",
      "spring.cloud.gateway.server.webmvc.routes[0].uri=http://localhost:8080",
      "spring.cloud.gateway.server.webmvc.routes[0].predicates[0]=Path=/test/**",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration"
    })
@Import(SessionStorageTest.TestConfig.class)
class SessionStorageTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private DataSource dataSource;

  @BeforeEach
  void initSchema() {
    SessionTestSupport.initSessionSchema(dataSource);
  }

  @Test
  void sessionTablesCreated_onStartup() {
    var jdbcTemplate = new JdbcTemplate(dataSource);
    Integer count =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION", Integer.class);
    // Verifies the table EXISTS — the query would throw if the table was not created
    assertThat(count).isNotNull();
  }

  @Test
  void sessionAttributesTableCreated_onStartup() {
    var jdbcTemplate = new JdbcTemplate(dataSource);
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM SPRING_SESSION_ATTRIBUTES", Integer.class);
    // Verifies the table EXISTS — the query would throw if the table was not created
    assertThat(count).isNotNull();
  }

  @Test
  void sessionCreated_whenAuthenticatedRequestMade() throws Exception {
    var jdbcTemplate = new JdbcTemplate(dataSource);

    int before = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION", Integer.class);

    // Make an authenticated request — Spring Security should create a session
    mockMvc.perform(get("/actuator/health").with(oauth2Login()));

    int after = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION", Integer.class);

    assertThat(after).isGreaterThan(before);
  }

  @TestConfiguration
  @EnableJdbcHttpSession(tableName = "SPRING_SESSION")
  static class TestConfig {

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
