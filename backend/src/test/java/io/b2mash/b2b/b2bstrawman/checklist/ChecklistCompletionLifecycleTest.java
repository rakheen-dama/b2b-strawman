package io.b2mash.b2b.b2bstrawman.checklist;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChecklistCompletionLifecycleTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_cl_comp_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private ChecklistTemplateRepository checklistTemplateRepository;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String templateId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "CL Comp Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    syncMember(ORG_ID, "user_comp_owner", "comp_owner@test.com", "Comp Owner", "owner");

    // Deactivate seeded generic-onboarding template to prevent auto-instantiation
    // interfering with this test's lifecycle assertions
    String tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, UUID.randomUUID())
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      checklistTemplateRepository
                          .findBySlug("generic-onboarding")
                          .ifPresent(
                              t -> {
                                t.deactivate();
                                checklistTemplateRepository.save(t);
                              });
                    }));

    // Create a simple template with 2 required items and 1 optional
    var templateResult =
        mockMvc
            .perform(
                post("/api/checklist-templates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Completion Test Template",
                          "customerType": "ANY",
                          "autoInstantiate": false,
                          "sortOrder": 0,
                          "items": [
                            {"name": "Required A", "sortOrder": 1, "required": true, "requiresDocument": false},
                            {"name": "Required B", "sortOrder": 2, "required": true, "requiresDocument": false},
                            {"name": "Optional C", "sortOrder": 3, "required": false, "requiresDocument": false}
                          ]
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    templateId = JsonPath.read(templateResult.getResponse().getContentAsString(), "$.template.id");
  }

  @Test
  void shouldCompleteInstanceWhenAllRequiredItemsCompleted() throws Exception {
    // Create customer
    String customerId = createCustomer("comp-complete@test.com", "Completion Test Customer");

    // Instantiate
    var instResult =
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

    String instanceId =
        JsonPath.read(instResult.getResponse().getContentAsString(), "$.instance.id");
    String itemAId = JsonPath.read(instResult.getResponse().getContentAsString(), "$.items[0].id");
    String itemBId = JsonPath.read(instResult.getResponse().getContentAsString(), "$.items[1].id");

    // Complete Required A
    mockMvc
        .perform(
            put("/api/checklist-items/" + itemAId + "/complete")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"notes": "Done A"}
                    """))
        .andExpect(status().isOk());

    // Instance should still be IN_PROGRESS
    mockMvc
        .perform(get("/api/checklist-instances/" + instanceId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.instance.status").value("IN_PROGRESS"));

    // Complete Required B
    mockMvc
        .perform(
            put("/api/checklist-items/" + itemBId + "/complete")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"notes": "Done B"}
                    """))
        .andExpect(status().isOk());

    // Instance should now be COMPLETED (all required items done, optional doesn't matter)
    mockMvc
        .perform(get("/api/checklist-instances/" + instanceId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.instance.status").value("COMPLETED"));
  }

  @Test
  void shouldTransitionCustomerToActiveWhenAllChecklistsCompleted() throws Exception {
    // Create customer (starts as PROSPECT)
    String customerId = createCustomer("comp-lifecycle@test.com", "Lifecycle Transition Customer");

    // Transition to ONBOARDING
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"targetStatus": "ONBOARDING", "notes": "Starting onboarding"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lifecycleStatus").value("ONBOARDING"));

    // Instantiate checklist
    var instResult =
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

    String itemAId = JsonPath.read(instResult.getResponse().getContentAsString(), "$.items[0].id");
    String itemBId = JsonPath.read(instResult.getResponse().getContentAsString(), "$.items[1].id");

    // Complete all required items
    mockMvc
        .perform(
            put("/api/checklist-items/" + itemAId + "/complete")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"notes": "Done"}
                    """))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            put("/api/checklist-items/" + itemBId + "/complete")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"notes": "Done"}
                    """))
        .andExpect(status().isOk());

    // Verify customer transitioned to ACTIVE by listing checklists (instance should be COMPLETED)
    // and checking the customer's lifecycle status via the transition response format
    mockMvc
        .perform(get("/api/customers/" + customerId + "/checklists").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("COMPLETED"));

    // Try to transition to DORMANT from ACTIVE - if this succeeds, customer was transitioned to
    // ACTIVE
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"targetStatus": "DORMANT", "notes": "Verifying was ACTIVE"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lifecycleStatus").value("DORMANT"));
  }

  @Test
  void shouldNotTransitionCustomerIfNotOnboarding() throws Exception {
    // Create customer (starts as PROSPECT, do NOT transition to ONBOARDING)
    String customerId = createCustomer("comp-nontransition@test.com", "No Transition Customer");

    // Instantiate checklist while customer is still PROSPECT
    var instResult =
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

    String itemAId = JsonPath.read(instResult.getResponse().getContentAsString(), "$.items[0].id");
    String itemBId = JsonPath.read(instResult.getResponse().getContentAsString(), "$.items[1].id");

    // Complete all required items
    mockMvc
        .perform(
            put("/api/checklist-items/" + itemAId + "/complete")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"notes": "Done"}
                    """))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            put("/api/checklist-items/" + itemBId + "/complete")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"notes": "Done"}
                    """))
        .andExpect(status().isOk());

    // Verify customer is still PROSPECT by transitioning to ONBOARDING (only valid from PROSPECT)
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"targetStatus": "ONBOARDING", "notes": "Checking was still PROSPECT"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lifecycleStatus").value("ONBOARDING"));
  }

  // --- Helpers ---

  private String createCustomer(String email, String name) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "%s", "email": "%s"}
                        """
                            .formatted(name, email)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_comp_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
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
