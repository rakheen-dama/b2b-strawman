package io.b2mash.b2b.b2bstrawman.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SecurityIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void unauthenticatedRequest_toApi_returns401() throws Exception {
    mockMvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
  }

  @Test
  void authenticatedRequest_withValidJwt_doesNotReturn401() throws Exception {
    mockMvc
        .perform(
            get("/api/projects")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject("user_123")
                                    .claim("o", Map.of("id", "org_test", "rol", "member")))))
        .andExpect(status().isForbidden()); // 403 because org_test is not provisioned, not 401
  }

  @Test
  void actuatorHealth_isPublic() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  @Test
  void internalEndpoint_withoutApiKey_returns401() throws Exception {
    mockMvc.perform(get("/internal/test")).andExpect(status().isUnauthorized());
  }

  @Test
  void internalEndpoint_withValidApiKey_passesAuth() throws Exception {
    // Returns 404 because no controller exists, but auth passed (not 401/403)
    mockMvc
        .perform(get("/internal/test").header("X-API-KEY", "test-api-key"))
        .andExpect(status().isNotFound());
  }

  @Test
  void internalEndpoint_withInvalidApiKey_returns401() throws Exception {
    mockMvc
        .perform(get("/internal/test").header("X-API-KEY", "wrong-key"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void unknownPath_returns403() throws Exception {
    // anyRequest().denyAll() returns 403 for unknown paths
    mockMvc
        .perform(
            get("/unknown")
                .with(jwt().jwt(j -> j.subject("user_123").claim("o", Map.of("rol", "member")))))
        .andExpect(status().isForbidden());
  }
}
