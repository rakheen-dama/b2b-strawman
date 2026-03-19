package io.b2mash.b2b.b2bstrawman.datarequest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.Map;
import java.util.UUID;
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
class ProcessingActivityControllerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_proc_activity_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Processing Activity Controller Test Org", null);
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    syncMember(ORG_ID, "user_pa_owner", "pa_owner@test.com", "PA Owner", "owner");
    syncMember(ORG_ID, "user_pa_member", "pa_member@test.com", "PA Member", "member");
  }

  @Test
  void getList_returns200WithPage() throws Exception {
    mockMvc
        .perform(get("/api/settings/processing-activities").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.page").exists());
  }

  @Test
  void post_createsActivity_returns201() throws Exception {
    mockMvc
        .perform(
            post("/api/settings/processing-activities")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"category":"Test Category","description":"Test desc",
                     "legalBasis":"contractual_necessity","dataSubjects":"Clients",
                     "retentionPeriod":"5 years","recipients":"None"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.category").value("Test Category"));
  }

  @Test
  void put_updatesActivity_returns200() throws Exception {
    // Create one first
    var result =
        mockMvc
            .perform(
                post("/api/settings/processing-activities")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"category":"Update Me","description":"Initial",
                         "legalBasis":"consent","dataSubjects":"Clients",
                         "retentionPeriod":"1 year","recipients":"None"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            put("/api/settings/processing-activities/" + id)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"category":"Updated Category","description":"Updated desc",
                     "legalBasis":"legal_obligation","dataSubjects":"Employees",
                     "retentionPeriod":"7 years","recipients":"SARS"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.category").value("Updated Category"))
        .andExpect(jsonPath("$.legalBasis").value("legal_obligation"));
  }

  @Test
  void delete_removesActivity_returns204() throws Exception {
    // Create one first
    var result =
        mockMvc
            .perform(
                post("/api/settings/processing-activities")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"category":"Delete Me","description":"desc",
                         "legalBasis":"consent","dataSubjects":"Clients",
                         "retentionPeriod":"1 year","recipients":"None"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(delete("/api/settings/processing-activities/" + id).with(ownerJwt()))
        .andExpect(status().isNoContent());

    // Verify gone
    mockMvc
        .perform(get("/api/settings/processing-activities").with(ownerJwt()))
        .andExpect(status().isOk());
  }

  @Test
  void patchDataProtection_withZAJurisdiction_seeds6Activities() throws Exception {
    // Use a separate org to avoid cross-test contamination
    String seedOrgId = "org_pa_seed_test_" + UUID.randomUUID().toString().substring(0, 8);
    provisioningService.provisionTenant(seedOrgId, "Seed Test Org", null);
    planSyncService.syncPlan(seedOrgId, "pro-plan");
    syncMember(
        seedOrgId,
        "user_seed_owner_" + seedOrgId,
        "seed_owner_" + seedOrgId + "@test.com",
        "Seed Owner",
        "owner");

    // Set jurisdiction to ZA -- this triggers seedJurisdictionDefaults
    mockMvc
        .perform(
            patch("/api/settings/data-protection")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject("user_seed_owner_" + seedOrgId)
                                    .claim("o", Map.of("id", seedOrgId, "rol", "owner"))))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"dataProtectionJurisdiction":"ZA","retentionPolicyEnabled":false}
                    """))
        .andExpect(status().isOk());

    // Verify at least 6 processing activities were seeded
    mockMvc
        .perform(
            get("/api/settings/processing-activities")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject("user_seed_owner_" + seedOrgId)
                                    .claim("o", Map.of("id", seedOrgId, "rol", "owner")))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page.totalElements").value(6));
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_pa_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")));
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
