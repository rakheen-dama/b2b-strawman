package io.b2mash.b2b.b2bstrawman.datarequest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataProtectionControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_dp_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Data Protection Controller Test Org", null);
    syncMember(ORG_ID, "user_dp_owner", "dp_owner@test.com", "DP Owner", "owner");
    syncMember(ORG_ID, "user_dp_member", "dp_member@test.com", "DP Member", "member");
  }

  @Test
  void generatePaiaManual_returnsGeneratedDocument() throws Exception {
    // Set up ZA jurisdiction first (seeds retention defaults, processing activities, compliance
    // pack)
    mockMvc
        .perform(
            patch("/api/settings/data-protection")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "dataProtectionJurisdiction": "ZA",
                      "retentionPolicyEnabled": true,
                      "informationOfficerName": "Test Officer",
                      "informationOfficerEmail": "officer@test.com"
                    }
                    """))
        .andExpect(status().isOk());

    // Generate the PAIA manual
    mockMvc
        .perform(post("/api/settings/paia-manual/generate").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.fileName").isNotEmpty())
        .andExpect(jsonPath("$.fileSize").isNumber())
        .andExpect(jsonPath("$.generatedAt").isNotEmpty());
  }

  @Test
  void generatePaiaManual_memberRole_returns403() throws Exception {
    mockMvc
        .perform(post("/api/settings/paia-manual/generate").with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_dp_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_dp_member").claim("o", Map.of("id", ORG_ID, "rol", "member")));
  }

  private String syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"clerkOrgId":"%s","clerkUserId":"%s","email":"%s","name":"%s","avatarUrl":null,"orgRole":"%s"}
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }
}
