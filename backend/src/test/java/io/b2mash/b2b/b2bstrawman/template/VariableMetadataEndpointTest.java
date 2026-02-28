package io.b2mash.b2b.b2bstrawman.template;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VariableMetadataEndpointTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_var_meta_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Variable Metadata Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    syncMember(ORG_ID, "user_vm_owner", "vm_owner@test.com", "VM Owner", "owner");
    syncMember(ORG_ID, "user_vm_member", "vm_member@test.com", "VM Member", "member");
  }

  @Test
  void getVariables_project_returnsProjectGroups() throws Exception {
    mockMvc
        .perform(
            get("/api/templates/variables")
                .param("entityType", "PROJECT")
                .with(memberJwt())
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.groups").isArray())
        .andExpect(jsonPath("$.groups.length()").value(6))
        .andExpect(jsonPath("$.groups[0].label").value("Project"))
        .andExpect(jsonPath("$.groups[0].prefix").value("project"))
        .andExpect(jsonPath("$.groups[0].variables[0].key").value("project.id"))
        .andExpect(jsonPath("$.groups[0].variables[1].key").value("project.name"))
        .andExpect(jsonPath("$.groups[1].label").value("Customer"))
        .andExpect(jsonPath("$.groups[2].label").value("Lead"))
        .andExpect(jsonPath("$.groups[3].label").value("Budget"))
        .andExpect(jsonPath("$.groups[4].label").value("Organization"))
        .andExpect(jsonPath("$.groups[5].label").value("Generated"));
  }

  @Test
  void getVariables_customer_returnsCustomerGroups() throws Exception {
    mockMvc
        .perform(
            get("/api/templates/variables")
                .param("entityType", "CUSTOMER")
                .with(memberJwt())
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.groups").isArray())
        .andExpect(jsonPath("$.groups.length()").value(3))
        .andExpect(jsonPath("$.groups[0].label").value("Customer"))
        .andExpect(jsonPath("$.groups[0].variables.length()").value(5))
        .andExpect(jsonPath("$.groups[0].variables[3].key").value("customer.phone"))
        .andExpect(jsonPath("$.groups[0].variables[4].key").value("customer.status"))
        .andExpect(jsonPath("$.groups[1].label").value("Organization"))
        .andExpect(jsonPath("$.groups[2].label").value("Generated"));
  }

  @Test
  void getVariables_invoice_returnsInvoiceGroups() throws Exception {
    mockMvc
        .perform(
            get("/api/templates/variables")
                .param("entityType", "INVOICE")
                .with(memberJwt())
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.groups").isArray())
        .andExpect(jsonPath("$.groups.length()").value(5))
        .andExpect(jsonPath("$.groups[0].label").value("Invoice"))
        .andExpect(jsonPath("$.groups[0].variables.length()").value(10))
        .andExpect(jsonPath("$.groups[0].variables[0].key").value("invoice.id"))
        .andExpect(jsonPath("$.groups[0].variables[1].key").value("invoice.invoiceNumber"))
        .andExpect(jsonPath("$.groups[1].label").value("Customer"))
        .andExpect(jsonPath("$.groups[2].label").value("Project"))
        .andExpect(jsonPath("$.groups[3].label").value("Organization"))
        .andExpect(jsonPath("$.groups[4].label").value("Generated"));
  }

  @Test
  void getVariables_loopSourcesFilteredByEntityType() throws Exception {
    // PROJECT has members and tags loop sources
    mockMvc
        .perform(
            get("/api/templates/variables")
                .param("entityType", "PROJECT")
                .with(memberJwt())
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.loopSources").isArray())
        .andExpect(jsonPath("$.loopSources.length()").value(2))
        .andExpect(jsonPath("$.loopSources[0].key").value("members"))
        .andExpect(jsonPath("$.loopSources[0].label").value("Project Members"))
        .andExpect(jsonPath("$.loopSources[0].fields.length()").value(4))
        .andExpect(jsonPath("$.loopSources[1].key").value("tags"));

    // INVOICE has only lines loop source
    mockMvc
        .perform(
            get("/api/templates/variables")
                .param("entityType", "INVOICE")
                .with(memberJwt())
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.loopSources.length()").value(1))
        .andExpect(jsonPath("$.loopSources[0].key").value("lines"))
        .andExpect(jsonPath("$.loopSources[0].label").value("Invoice Lines"))
        .andExpect(jsonPath("$.loopSources[0].fields.length()").value(4));

    // CUSTOMER has projects and tags loop sources
    mockMvc
        .perform(
            get("/api/templates/variables")
                .param("entityType", "CUSTOMER")
                .with(memberJwt())
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.loopSources.length()").value(2))
        .andExpect(jsonPath("$.loopSources[0].key").value("projects"))
        .andExpect(jsonPath("$.loopSources[1].key").value("tags"));
  }

  @Test
  void getVariables_invalidEntityType_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/templates/variables")
                .param("entityType", "INVALID")
                .with(memberJwt())
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getVariables_anyMemberCanAccess() throws Exception {
    // Member (not admin/owner) can access the variables endpoint
    mockMvc
        .perform(
            get("/api/templates/variables")
                .param("entityType", "PROJECT")
                .with(memberJwt())
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.groups").isArray());
  }

  // --- JWT Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_vm_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_vm_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  // --- Helpers ---

  private String syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "%s",
                          "email": "%s",
                          "name": "%s",
                          "avatarUrl": null,
                          "orgRole": "%s"
                        }
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return com.jayway.jsonpath.JsonPath.read(
        result.getResponse().getContentAsString(), "$.memberId");
  }
}
