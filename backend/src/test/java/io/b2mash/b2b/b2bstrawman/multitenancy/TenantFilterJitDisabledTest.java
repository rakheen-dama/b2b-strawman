package io.b2mash.b2b.b2bstrawman.multitenancy;

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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class TenantFilterJitDisabledTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void jitDisabled_unprovisionedOrg_returns403() throws Exception {
    // JIT is disabled by default (app.jit-provisioning.enabled=false)
    // An unprovisioned org should get 403
    mockMvc
        .perform(get("/api/projects").with(jwtForUnprovisionedOrg()))
        .andExpect(status().isForbidden());
  }

  private JwtRequestPostProcessor jwtForUnprovisionedOrg() {
    return jwt()
        .jwt(
            j ->
                j.subject("user_jit_disabled_test")
                    .claim(
                        "o",
                        Map.of("id", "org_not_provisioned_disabled", "rol", "owner", "slg", "np")));
  }
}
