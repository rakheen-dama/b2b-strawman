package io.b2mash.b2b.b2bstrawman.datarequest;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.regex.Pattern;
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
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataProtectionControllerTest {
  private static final String ORG_ID = "org_dp_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Data Protection Controller Test Org", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_dp_owner", "dp_owner@test.com", "DP Owner", "owner");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_dp_member", "dp_member@test.com", "DP Member", "member");
  }

  @Test
  void generatePaiaManual_returnsGeneratedDocument() throws Exception {
    // Set up ZA jurisdiction first (seeds retention defaults, processing activities, compliance
    // pack)
    mockMvc
        .perform(
            patch("/api/settings/data-protection")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_dp_owner"))
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
    String body =
        mockMvc
            .perform(
                post("/api/settings/paia-manual/generate")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_dp_owner")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(
                jsonPath(
                    "$.fileName",
                    matchesPattern("^paia-section-51-manual-\\d{4}-\\d{2}-\\d{2}\\.pdf$")))
            .andExpect(jsonPath("$.fileSize").isNumber())
            .andExpect(jsonPath("$.generatedAt").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // G88-F33: the manual is stored under org/{tenant}/generated/{uuid}/{fileName} and presigns
    String id = JsonPath.read(body, "$.id");
    String fileName = JsonPath.read(body, "$.fileName");
    mockMvc
        .perform(
            get("/api/generated-documents/{id}/download", id)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_dp_owner")))
        .andExpect(status().isFound())
        .andExpect(
            header()
                .string(
                    "Location",
                    matchesPattern(
                        "^.*/org/[^/]+/generated/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/"
                            + Pattern.quote(fileName)
                            + "\\?download=true$")));
  }

  @Test
  void generatePaiaManual_memberRole_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/settings/paia-manual/generate")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_dp_member")))
        .andExpect(status().isForbidden());
  }
}
