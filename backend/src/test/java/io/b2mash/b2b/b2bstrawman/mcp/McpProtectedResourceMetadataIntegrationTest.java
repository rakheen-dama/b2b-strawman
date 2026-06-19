package io.b2mash.b2b.b2bstrawman.mcp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * RFC 9728 OAuth 2.0 Protected Resource Metadata for the Kazi MCP server (ADR-303).
 *
 * <p>The {@code /.well-known/oauth-protected-resource} endpoint MUST be PUBLIC (the Claude MCP
 * client fetches it BEFORE it has a token) and MUST advertise:
 *
 * <ul>
 *   <li>{@code resource} — the configured {@code kazi.mcp.resource-url}
 *   <li>{@code authorization_servers} — containing the configured JWT {@code issuer-uri} so the
 *       client can discover the Keycloak authorization server and run the authorization-code/PKCE
 *       flow.
 * </ul>
 *
 * <p>Regression guard: Spring Security 7 auto-registers a built-in {@code
 * OAuth2ProtectedResourceMetadataFilter} that, without explicit configuration, omits {@code
 * authorization_servers} entirely — which silently breaks remote MCP OAuth discovery.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class McpProtectedResourceMetadataIntegrationTest {

  // From application-test.yml: spring.security.oauth2.resourceserver.jwt.issuer-uri
  private static final String EXPECTED_ISSUER = "https://test-issuer.example.com";
  // kazi.mcp.resource-url is unset in the test profile, so it falls back to the documented default.
  private static final String EXPECTED_RESOURCE = "http://localhost:8080/mcp";

  @Autowired private MockMvc mockMvc;

  @Test
  void metadataIsPublicAndAdvertisesResourceAndAuthorizationServers() throws Exception {
    mockMvc
        .perform(get("/.well-known/oauth-protected-resource"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resource").value(EXPECTED_RESOURCE))
        .andExpect(jsonPath("$.authorization_servers", Matchers.hasItem(EXPECTED_ISSUER)));
  }
}
