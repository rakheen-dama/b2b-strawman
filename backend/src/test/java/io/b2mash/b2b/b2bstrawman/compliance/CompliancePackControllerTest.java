package io.b2mash.b2b.b2bstrawman.compliance;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompliancePackControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  private static final String ORG_ID = "org_pack_controller_test";

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Pack Controller Test Org");
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  @Test
  void getPackDefinition_returnsPackForGenericOnboarding() throws Exception {
    mockMvc
        .perform(get("/api/compliance-packs/generic-onboarding").with(adminJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.packId").value("generic-onboarding"))
        .andExpect(jsonPath("$.name").value("Generic Client Onboarding"))
        .andExpect(jsonPath("$.version").value("1.0.0"))
        .andExpect(jsonPath("$.checklistTemplate.items").isArray())
        .andExpect(jsonPath("$.checklistTemplate.items.length()").value(4));
  }

  @Test
  void getPackDefinition_returns404ForNonexistentPack() throws Exception {
    mockMvc
        .perform(get("/api/compliance-packs/nonexistent-pack").with(adminJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void getPackDefinition_returns401WithoutAuth() throws Exception {
    mockMvc
        .perform(get("/api/compliance-packs/generic-onboarding"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void getPackDefinition_returns403ForMember() throws Exception {
    var memberJwt =
        jwt()
            .jwt(j -> j.subject("user_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
            .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
    mockMvc
        .perform(get("/api/compliance-packs/generic-onboarding").with(memberJwt))
        .andExpect(status().isForbidden());
  }
}
