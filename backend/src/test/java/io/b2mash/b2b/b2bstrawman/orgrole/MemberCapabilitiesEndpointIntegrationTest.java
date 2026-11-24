package io.b2mash.b2b.b2bstrawman.orgrole;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * {@code GET /api/members/{id}/capabilities} as the owner, for a member on each kind of role: the
 * system Admin and Owner roles (whose capabilities resolve without reading the member's overrides)
 * and a custom role carrying an override. Every one answers 200 with the role, the overrides and
 * the effective capabilities (found while testing FR-4: the Admin case answered 500).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemberCapabilitiesEndpointIntegrationTest {

  private static final String ORG_ID = "org_member_capabilities_endpoint_test";
  private static final String OWNER = "user_mce_owner";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  private String ownerId;
  private String adminId;
  private String customId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Member Capabilities Attorneys", "legal-za");
    ownerId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, OWNER, "mce_owner@test.com", "Caps Owner", "owner");
    adminId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_mce_admin", "mce_admin@test.com", "Caps Admin", "admin");
    customId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_mce_custom", "mce_custom@test.com", "Caps Custom", "member");
  }

  @Test
  void aMemberOnTheSystemAdminRole() throws Exception {
    capabilities(adminId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.memberId").value(adminId))
        .andExpect(jsonPath("$.roleName").value("Admin"))
        .andExpect(jsonPath("$.overrides").isEmpty())
        .andExpect(jsonPath("$.effectiveCapabilities").value(hasItem("TEAM_OVERSIGHT")))
        .andExpect(jsonPath("$.effectiveCapabilities").value(not(hasItem("FINANCIAL_RECONCILE"))));
  }

  @Test
  void theOwner() throws Exception {
    capabilities(ownerId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roleName").value("Owner"))
        .andExpect(jsonPath("$.overrides").isEmpty())
        .andExpect(jsonPath("$.effectiveCapabilities").value(hasItem("SIGN_OFF_TRUST_TAKE_ON")));
  }

  @Test
  void aMemberOnACustomRoleWithAnOverride() throws Exception {
    var created =
        mockMvc
            .perform(
                post("/api/org-roles")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\": \"Caps Bookkeeper\", \"description\": \"Test role\","
                            + " \"capabilities\": [\"INVOICING\"]}"))
            .andExpect(status().isCreated())
            .andReturn();
    String roleId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    mockMvc
        .perform(
            put("/api/members/" + customId + "/role")
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"orgRoleId\": \"%s\", \"capabilityOverrides\": [\"+VIEW_TRUST\"]}"
                        .formatted(roleId)))
        .andExpect(status().isOk());

    capabilities(customId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roleName").value("Caps Bookkeeper"))
        .andExpect(jsonPath("$.overrides[0]").value("+VIEW_TRUST"))
        .andExpect(
            jsonPath("$.effectiveCapabilities")
                .value(containsInAnyOrder("INVOICING", "VIEW_TRUST")));
  }

  private ResultActions capabilities(String memberId) throws Exception {
    return mockMvc
        .perform(get("/api/members/" + memberId + "/capabilities").with(owner()))
        .andDo(
            result -> {
              // a 500's cause chain in the test output (the ProblemDetail hides it)
              if (result.getResolvedException() != null) {
                result.getResolvedException().printStackTrace(System.out);
              }
            });
  }

  private RequestPostProcessor owner() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER);
  }
}
