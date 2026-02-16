package io.b2mash.b2b.b2bstrawman.checklist;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class ChecklistDependencyTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_cl_dep_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private final AtomicInteger customerCounter = new AtomicInteger(0);
  private String templateId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "CL Dep Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    syncMember(ORG_ID, "user_dep_owner", "dep_owner@test.com", "Dep Owner", "owner");

    // Create template with two items first (no dependency)
    var t2Result =
        mockMvc
            .perform(
                post("/api/checklist-templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Dep Chain Template",
                          "customerType": "ANY",
                          "autoInstantiate": false,
                          "sortOrder": 0,
                          "items": [
                            {"name": "Chain Step A", "sortOrder": 1, "required": true, "requiresDocument": false},
                            {"name": "Chain Step B", "sortOrder": 2, "required": true, "requiresDocument": false}
                          ]
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    templateId = JsonPath.read(t2Result.getResponse().getContentAsString(), "$.template.id");
    String chainAId = JsonPath.read(t2Result.getResponse().getContentAsString(), "$.items[0].id");
    String chainBId = JsonPath.read(t2Result.getResponse().getContentAsString(), "$.items[1].id");

    // Update to add dependency: B depends on A
    mockMvc
        .perform(
            put("/api/checklist-templates/" + templateId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Dep Chain Template",
                      "autoInstantiate": false,
                      "sortOrder": 0,
                      "items": [
                        {"id": "%s", "name": "Chain Step A", "sortOrder": 1, "required": true, "requiresDocument": false},
                        {"id": "%s", "name": "Chain Step B", "sortOrder": 2, "required": true, "requiresDocument": false, "dependsOnItemId": "%s"}
                      ]
                    }
                    """
                        .formatted(chainAId, chainBId, chainAId)))
        .andExpect(status().isOk());
  }

  @Test
  void shouldStartDependentItemAsBlocked() throws Exception {
    String customerId = createUniqueCustomer();
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
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.items[0].status").value("PENDING"))
        .andExpect(jsonPath("$.items[1].status").value("BLOCKED"));
  }

  @Test
  void shouldUnblockDependentWhenDependencyCompleted() throws Exception {
    String customerId = createUniqueCustomer();
    var result =
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
            .andExpect(status().isCreated())
            .andReturn();

    String instanceId = JsonPath.read(result.getResponse().getContentAsString(), "$.instance.id");
    String stepAId = JsonPath.read(result.getResponse().getContentAsString(), "$.items[0].id");

    // Complete Step A
    mockMvc
        .perform(
            put("/api/checklist-items/" + stepAId + "/complete")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"notes": "Done"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    // Verify Step B is now PENDING
    mockMvc
        .perform(get("/api/checklist-instances/" + instanceId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[1].status").value("PENDING"));
  }

  @Test
  void shouldRejectCompleteBlockedItem() throws Exception {
    String customerId = createUniqueCustomer();
    var result =
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
            .andExpect(status().isCreated())
            .andReturn();

    String stepBId = JsonPath.read(result.getResponse().getContentAsString(), "$.items[1].id");

    // Attempt to complete blocked Step B
    mockMvc
        .perform(
            put("/api/checklist-items/" + stepBId + "/complete")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"notes": "Trying to complete blocked"}
                    """))
        .andExpect(status().isConflict());
  }

  @Test
  void shouldBlockDependentWhenDependencyReopened() throws Exception {
    String customerId = createUniqueCustomer();
    var result =
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
            .andExpect(status().isCreated())
            .andReturn();

    String instanceId = JsonPath.read(result.getResponse().getContentAsString(), "$.instance.id");
    String stepAId = JsonPath.read(result.getResponse().getContentAsString(), "$.items[0].id");

    // Complete Step A (unblocks Step B)
    mockMvc
        .perform(
            put("/api/checklist-items/" + stepAId + "/complete")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"notes": "Done"}
                    """))
        .andExpect(status().isOk());

    // Reopen Step A (should re-block Step B)
    mockMvc
        .perform(put("/api/checklist-items/" + stepAId + "/reopen").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"));

    // Verify Step B is BLOCKED again
    mockMvc
        .perform(get("/api/checklist-instances/" + instanceId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[1].status").value("BLOCKED"));
  }

  // --- Helpers ---

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
                        {"name": "Dep Customer %d", "email": "dep-%d@test.com"}
                        """
                            .formatted(n, n)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  // --- JWT Helpers ---

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_dep_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
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
