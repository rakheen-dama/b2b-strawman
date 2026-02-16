package io.b2mash.b2b.b2bstrawman.checklist;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
class ChecklistInstancePermissionTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_cl_perm_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private final AtomicInteger customerCounter = new AtomicInteger(0);
  private String templateId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "CL Perm Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    syncMember(ORG_ID, "user_perm_owner", "perm_owner@test.com", "Perm Owner", "owner");
    syncMember(ORG_ID, "user_perm_member", "perm_member@test.com", "Perm Member", "member");

    // Create template
    var templateResult =
        mockMvc
            .perform(
                post("/api/checklist-templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Perm Test Template",
                          "customerType": "ANY",
                          "autoInstantiate": false,
                          "sortOrder": 0,
                          "items": [
                            {"name": "Perm Step 1", "sortOrder": 1, "required": true, "requiresDocument": false}
                          ]
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    templateId = JsonPath.read(templateResult.getResponse().getContentAsString(), "$.template.id");
  }

  @Test
  void shouldAllowAdminToInstantiate() throws Exception {
    String customerId = createUniqueCustomer();
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/checklists")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"templateId": "%s"}
                    """
                        .formatted(templateId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.instance.status").value("IN_PROGRESS"));
  }

  @Test
  void shouldRejectMemberInstantiate() throws Exception {
    String customerId = createUniqueCustomer();
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/checklists")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"templateId": "%s"}
                    """
                        .formatted(templateId)))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldAllowMemberToViewInstances() throws Exception {
    String customerId = createUniqueCustomer();
    // First instantiate as owner
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/checklists")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"templateId": "%s"}
                    """
                        .formatted(templateId)))
        .andExpect(status().isCreated());

    // Member can list instances
    mockMvc
        .perform(get("/api/customers/" + customerId + "/checklists").with(memberJwt()))
        .andExpect(status().isOk());
  }

  private String createUniqueCustomer() throws Exception {
    int n = customerCounter.incrementAndGet();
    var result =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Perm Customer %d", "email": "perm-%d@test.com"}
                        """
                            .formatted(n, n)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  // --- JWT Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_perm_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_perm_owner").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_perm_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
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
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }
}
