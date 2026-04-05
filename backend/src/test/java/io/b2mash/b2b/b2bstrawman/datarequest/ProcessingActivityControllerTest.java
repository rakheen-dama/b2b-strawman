package io.b2mash.b2b.b2bstrawman.datarequest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProcessingActivityControllerTest {
  private static final String ORG_ID = "org_proc_activity_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Processing Activity Controller Test Org", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_pa_owner", "pa_owner@test.com", "PA Owner", "owner");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_pa_member", "pa_member@test.com", "PA Member", "member");
  }

  @Test
  void getList_returns200WithPage() throws Exception {
    mockMvc
        .perform(
            get("/api/settings/processing-activities")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pa_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.page").exists());
  }

  @Test
  void list_memberRole_returns403() throws Exception {
    mockMvc
        .perform(
            get("/api/settings/processing-activities")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_pa_member")))
        .andExpect(status().isForbidden());
  }

  @Test
  void post_createsActivity_returns201() throws Exception {
    mockMvc
        .perform(
            post("/api/settings/processing-activities")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pa_owner"))
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
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pa_owner"))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pa_owner"))
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
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pa_owner"))
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
        .perform(
            delete("/api/settings/processing-activities/" + id)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pa_owner")))
        .andExpect(status().isNoContent());

    // Verify gone — the deleted ID should not appear in the list
    mockMvc
        .perform(
            get("/api/settings/processing-activities")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pa_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.id == '%s')]".formatted(id)).doesNotExist());
  }

  @Test
  void patchDataProtection_withZAJurisdiction_seeds6Activities() throws Exception {
    // Use a separate org to avoid cross-test contamination
    String seedOrgId = "org_pa_seed_test_" + UUID.randomUUID().toString().substring(0, 8);
    provisioningService.provisionTenant(seedOrgId, "Seed Test Org", null);
    TestMemberHelper.syncMember(
        mockMvc,
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
}
