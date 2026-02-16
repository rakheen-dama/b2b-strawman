package io.b2mash.b2b.b2bstrawman.checklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
class AutoInstantiationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_ci_auto_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "CI Auto-Instantiation Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember("user_ci_auto_owner", "ci_auto_owner@test.com", "Auto Owner", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void shouldAutoInstantiateOnTransitionToOnboarding() throws Exception {
    // Create an auto-instantiate template
    mockMvc
        .perform(
            post("/api/checklist-templates")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Auto-Instantiate Onboarding",
                      "description": "Should auto-create on ONBOARDING",
                      "customerType": "ANY",
                      "autoInstantiate": true,
                      "sortOrder": 1,
                      "items": [
                        {
                          "name": "Auto Step 1",
                          "sortOrder": 1,
                          "required": true,
                          "requiresDocument": false
                        }
                      ]
                    }
                    """))
        .andExpect(status().isCreated());

    // Create a PROSPECT customer
    UUID custId = createCustomer("Auto Cust 1", "auto1_" + UUID.randomUUID() + "@test.com");

    // Transition to ONBOARDING
    mockMvc
        .perform(
            post("/api/customers/" + custId + "/transition")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\": \"ONBOARDING\", \"notes\": \"Starting onboarding\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lifecycleStatus").value("ONBOARDING"));

    // Verify instance was auto-created
    var listResult =
        mockMvc
            .perform(get("/api/customers/" + custId + "/checklists").with(ownerJwt()))
            .andExpect(status().isOk())
            .andReturn();

    List<Object> instances = JsonPath.read(listResult.getResponse().getContentAsString(), "$");
    assertThat(instances).isNotEmpty();
  }

  @Test
  void shouldNotAutoInstantiateIfNoMatchingTemplates() throws Exception {
    // Use a different org with no auto-instantiate templates
    String otherOrgId = "org_ci_auto_no_match";
    provisioningService.provisionTenant(otherOrgId, "No Match Org");
    planSyncService.syncPlan(otherOrgId, "pro-plan");

    UUID otherMemberId =
        UUID.fromString(
            syncMemberForOrg(
                otherOrgId,
                "user_ci_auto_nomatch",
                "auto_nomatch@test.com",
                "NoMatch Owner",
                "owner"));

    String otherSchema =
        orgSchemaMappingRepository.findByClerkOrgId(otherOrgId).orElseThrow().getSchemaName();

    // Create customer in the other org
    UUID custId =
        ScopedValue.where(RequestScopes.TENANT_ID, otherSchema)
            .where(RequestScopes.ORG_ID, otherOrgId)
            .where(RequestScopes.MEMBER_ID, otherMemberId)
            .where(RequestScopes.ORG_ROLE, "owner")
            .call(
                () ->
                    transactionTemplate.execute(
                        tx -> {
                          var customer =
                              new Customer(
                                  "No Match Cust",
                                  "nomatch_" + UUID.randomUUID() + "@test.com",
                                  "+1-555-0001",
                                  "ID-001",
                                  "Test",
                                  otherMemberId);
                          customer = customerRepository.save(customer);
                          return customer.getId();
                        }));

    var otherOwnerJwt =
        jwt()
            .jwt(
                j ->
                    j.subject("user_ci_auto_nomatch")
                        .claim("o", Map.of("id", otherOrgId, "rol", "owner")))
            .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));

    // Transition to ONBOARDING (no auto-instantiate templates in this org)
    mockMvc
        .perform(
            post("/api/customers/" + custId + "/transition")
                .with(otherOwnerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\": \"ONBOARDING\", \"notes\": \"Starting\"}"))
        .andExpect(status().isOk());

    // Verify no instances created
    var listResult =
        mockMvc
            .perform(get("/api/customers/" + custId + "/checklists").with(otherOwnerJwt))
            .andExpect(status().isOk())
            .andReturn();

    List<Object> instances = JsonPath.read(listResult.getResponse().getContentAsString(), "$");
    assertThat(instances).isEmpty();
  }

  // --- Helpers ---

  private UUID createCustomer(String name, String email) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .call(
            () ->
                transactionTemplate.execute(
                    tx -> {
                      var customer =
                          new Customer(name, email, "+1-555-0001", "ID-001", "Test", memberIdOwner);
                      customer = customerRepository.save(customer);
                      return customer.getId();
                    }));
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_ci_auto_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private String syncMember(String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    return syncMemberForOrg(ORG_ID, clerkUserId, email, name, orgRole);
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
