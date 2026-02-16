package io.b2mash.b2b.b2bstrawman.checklist;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
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
class ChecklistInstancePermissionTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID_1 = "org_ci_perm_1";
  private static final String ORG_ID_2 = "org_ci_perm_2";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema1;
  private String tenantSchema2;
  private UUID memberIdOwner1;
  private UUID memberIdOwner2;
  private UUID memberIdMember1;
  private UUID customer1Id;
  private UUID customer2Id;
  private String template1Id;
  private String instance1ItemId; // an item from org 1

  @BeforeAll
  void setup() throws Exception {
    // Setup org 1
    provisioningService.provisionTenant(ORG_ID_1, "Perm Test Org 1");
    planSyncService.syncPlan(ORG_ID_1, "pro-plan");
    memberIdOwner1 =
        UUID.fromString(
            syncMemberForOrg(
                ORG_ID_1, "user_perm1_owner", "perm1_owner@test.com", "Perm1 Owner", "owner"));
    memberIdMember1 =
        UUID.fromString(
            syncMemberForOrg(
                ORG_ID_1, "user_perm1_member", "perm1_member@test.com", "Perm1 Member", "member"));
    tenantSchema1 =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID_1).orElseThrow().getSchemaName();

    // Setup org 2
    provisioningService.provisionTenant(ORG_ID_2, "Perm Test Org 2");
    planSyncService.syncPlan(ORG_ID_2, "pro-plan");
    memberIdOwner2 =
        UUID.fromString(
            syncMemberForOrg(
                ORG_ID_2, "user_perm2_owner", "perm2_owner@test.com", "Perm2 Owner", "owner"));
    tenantSchema2 =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID_2).orElseThrow().getSchemaName();

    // Create customers
    customer1Id =
        createCustomer(
            tenantSchema1, ORG_ID_1, memberIdOwner1, "Perm Cust 1", "perm1_cust@test.com");
    customer2Id =
        createCustomer(
            tenantSchema2, ORG_ID_2, memberIdOwner2, "Perm Cust 2", "perm2_cust@test.com");

    // Create template and instance in org 1
    var templateResult =
        mockMvc
            .perform(
                post("/api/checklist-templates")
                    .with(owner1Jwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Perm Test Template",
                          "customerType": "ANY",
                          "autoInstantiate": false,
                          "sortOrder": 1,
                          "items": [
                            {
                              "name": "Perm Step 1",
                              "sortOrder": 1,
                              "required": true,
                              "requiresDocument": false
                            }
                          ]
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    template1Id = JsonPath.read(templateResult.getResponse().getContentAsString(), "$.template.id");

    // Create instance in org 1
    var instResult =
        mockMvc
            .perform(
                post("/api/customers/" + customer1Id + "/checklists")
                    .with(owner1Jwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"templateId\": \"" + template1Id + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();

    String instanceId = JsonPath.read(instResult.getResponse().getContentAsString(), "$.id");

    // Get instance item
    var itemsResult =
        mockMvc
            .perform(get("/api/checklist-instances/" + instanceId).with(owner1Jwt()))
            .andReturn();
    List<Map<String, Object>> items =
        JsonPath.read(itemsResult.getResponse().getContentAsString(), "$.items");
    instance1ItemId = (String) items.get(0).get("id");
  }

  @Test
  void shouldAllowMemberToCompleteItem() throws Exception {
    // Create a fresh customer + instance for the member test
    UUID memberCustId =
        createCustomer(
            tenantSchema1,
            ORG_ID_1,
            memberIdOwner1,
            "Member Test Cust",
            "memtest_" + UUID.randomUUID() + "@test.com");

    var instResult =
        mockMvc
            .perform(
                post("/api/customers/" + memberCustId + "/checklists")
                    .with(owner1Jwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"templateId\": \"" + template1Id + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();

    String instanceId = JsonPath.read(instResult.getResponse().getContentAsString(), "$.id");

    var itemsResult =
        mockMvc
            .perform(get("/api/checklist-instances/" + instanceId).with(member1Jwt()))
            .andReturn();
    List<Map<String, Object>> items =
        JsonPath.read(itemsResult.getResponse().getContentAsString(), "$.items");
    String itemId = (String) items.get(0).get("id");

    // Member completes the item
    mockMvc
        .perform(
            put("/api/checklist-items/" + itemId + "/complete")
                .with(member1Jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\": \"Member completed this\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void shouldRejectCompletionFromWrongTenant() throws Exception {
    // Try to complete an item from org 1 using org 2 JWT — should get 404
    mockMvc
        .perform(
            put("/api/checklist-items/" + instance1ItemId + "/complete")
                .with(owner2Jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\": \"Cross-tenant attempt\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldRejectSkipOfItemFromDifferentCustomer() throws Exception {
    // Try to skip an item from org 1 using org 2 JWT — should get 404
    mockMvc
        .perform(
            put("/api/checklist-items/" + instance1ItemId + "/skip")
                .with(owner2Jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Cross-tenant skip attempt\"}"))
        .andExpect(status().isNotFound());
  }

  // --- Helpers ---

  private UUID createCustomer(
      String schema, String orgId, UUID memberId, String name, String email) {
    return ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .call(
            () ->
                transactionTemplate.execute(
                    tx -> {
                      var customer =
                          new Customer(name, email, "+1-555-0001", "ID-001", "Test", memberId);
                      customer = customerRepository.save(customer);
                      return customer.getId();
                    }));
  }

  private JwtRequestPostProcessor owner1Jwt() {
    return jwt()
        .jwt(j -> j.subject("user_perm1_owner").claim("o", Map.of("id", ORG_ID_1, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor member1Jwt() {
    return jwt()
        .jwt(
            j -> j.subject("user_perm1_member").claim("o", Map.of("id", ORG_ID_1, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  private JwtRequestPostProcessor owner2Jwt() {
    return jwt()
        .jwt(j -> j.subject("user_perm2_owner").claim("o", Map.of("id", ORG_ID_2, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private String syncMemberForOrg(
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
                          "orgRole": "%s"
                        }
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }
}
