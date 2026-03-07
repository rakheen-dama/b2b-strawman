package io.b2mash.b2b.b2bstrawman.accessrequest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PlatformAdminSecurityTest.TestConfig.class})
@ActiveProfiles("test")
class PlatformAdminSecurityTest {

  @Autowired private MockMvc mockMvc;

  @TestConfiguration
  static class TestConfig {

    @RestController
    @RequestMapping("/api/platform-admin/test")
    static class PlatformAdminTestController {

      @PreAuthorize("@platformSecurityService.isPlatformAdmin()")
      @GetMapping
      public ResponseEntity<String> adminOnly() {
        return ResponseEntity.ok("admin-access-granted");
      }
    }
  }

  @Test
  void platformAdminEndpoint_withGroupClaim_returns200() throws Exception {
    mockMvc
        .perform(
            get("/api/platform-admin/test")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject("user_admin")
                                    .claim("groups", List.of("platform-admins")))))
        .andExpect(status().isOk())
        .andExpect(content().string("admin-access-granted"));
  }

  @Test
  void platformAdminEndpoint_withoutGroupClaim_returns403() throws Exception {
    mockMvc
        .perform(get("/api/platform-admin/test").with(jwt().jwt(j -> j.subject("user_regular"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void platformAdminEndpoint_noAuth_returns401() throws Exception {
    mockMvc.perform(get("/api/platform-admin/test")).andExpect(status().isUnauthorized());
  }

  @Test
  void platformAdminEndpoint_wrongGroup_returns403() throws Exception {
    mockMvc
        .perform(
            get("/api/platform-admin/test")
                .with(
                    jwt()
                        .jwt(j -> j.subject("user_other").claim("groups", List.of("other-group")))))
        .andExpect(status().isForbidden());
  }

  @Test
  void regularApiEndpoint_unaffectedByGroupFilter() throws Exception {
    mockMvc
        .perform(
            get("/api/projects")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject("user_member")
                                    .claim("o", Map.of("id", "org_test", "rol", "member")))))
        .andExpect(status().is(403)); // 403 because org_test is not provisioned, not 401
  }
}
