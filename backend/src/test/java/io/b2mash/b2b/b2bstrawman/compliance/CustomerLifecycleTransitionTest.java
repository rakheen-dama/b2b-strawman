package io.b2mash.b2b.b2bstrawman.compliance;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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
class CustomerLifecycleTransitionTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_lifecycle_transition_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Lifecycle Transition Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(
                "user_lc_trans_owner", "lc_trans_owner@test.com", "Transition Owner", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  private UUID createCustomerWithStatus(String name, String email, String lifecycleStatus) {
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
                      if (!"PROSPECT".equals(lifecycleStatus)) {
                        // Manually set lifecycle status for test setup
                        customer.transitionLifecycle(
                            lifecycleStatus, memberIdOwner, java.time.Instant.now(), null);
                        customer = customerRepository.save(customer);
                      }
                      return customer.getId();
                    }));
  }

  @Test
  void prospectToOnboardingSucceeds() throws Exception {
    UUID customerId =
        createCustomerWithStatus("P2O Corp", "p2o_" + UUID.randomUUID() + "@test.com", "PROSPECT");

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
        .andExpect(jsonPath("$.lifecycleStatus").value("ONBOARDING"))
        .andExpect(jsonPath("$.lifecycleStatusChangedAt").isNotEmpty())
        .andExpect(jsonPath("$.lifecycleStatusChangedBy").value(memberIdOwner.toString()));
  }

  @Test
  void onboardingToActiveSucceeds() throws Exception {
    UUID customerId =
        createCustomerWithStatus(
            "O2A Corp", "o2a_" + UUID.randomUUID() + "@test.com", "ONBOARDING");

    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"targetStatus": "ACTIVE", "notes": "Onboarding complete"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lifecycleStatus").value("ACTIVE"));
  }

  @Test
  void activeToDormantSucceeds() throws Exception {
    UUID customerId =
        createCustomerWithStatus("A2D Corp", "a2d_" + UUID.randomUUID() + "@test.com", "ACTIVE");

    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"targetStatus": "DORMANT", "notes": "No activity"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lifecycleStatus").value("DORMANT"));
  }

  @Test
  void activeToOffboardedSetsOffboardedAt() throws Exception {
    UUID customerId =
        createCustomerWithStatus(
            "A2Off Corp", "a2off_" + UUID.randomUUID() + "@test.com", "ACTIVE");

    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"targetStatus": "OFFBOARDED", "notes": "Client leaving"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lifecycleStatus").value("OFFBOARDED"))
        .andExpect(jsonPath("$.offboardedAt").isNotEmpty());
  }

  @Test
  void offboardedToActiveClearsOffboardedAt() throws Exception {
    UUID customerId =
        createCustomerWithStatus(
            "Off2A Corp", "off2a_" + UUID.randomUUID() + "@test.com", "ACTIVE");

    // First transition to OFFBOARDED
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"targetStatus": "OFFBOARDED", "notes": "Leaving"}
                    """))
        .andExpect(status().isOk());

    // Then reactivate with notes
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"targetStatus": "ACTIVE", "notes": "Client returning, re-engagement approved"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lifecycleStatus").value("ACTIVE"))
        .andExpect(jsonPath("$.offboardedAt").doesNotExist());
  }

  @Test
  void prospectToActiveReturns409() throws Exception {
    UUID customerId =
        createCustomerWithStatus("P2A Corp", "p2a_" + UUID.randomUUID() + "@test.com", "PROSPECT");

    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"targetStatus": "ACTIVE", "notes": "Skip onboarding"}
                    """))
        .andExpect(status().isConflict());
  }

  @Test
  void offboardedToActiveWithoutNotesReturns409() throws Exception {
    UUID customerId =
        createCustomerWithStatus(
            "Off2ANoNotes Corp", "off2ann_" + UUID.randomUUID() + "@test.com", "ACTIVE");

    // First transition to OFFBOARDED
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"targetStatus": "OFFBOARDED", "notes": "Leaving"}
                    """))
        .andExpect(status().isOk());

    // Then try to reactivate without notes
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"targetStatus": "ACTIVE", "notes": ""}
                    """))
        .andExpect(status().isConflict());
  }

  private String syncMember(String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var syncRequest =
        """
        {
          "clerkOrgId": "%s",
          "clerkUserId": "%s",
          "email": "%s",
          "name": "%s",
          "orgRole": "%s"
        }
        """
            .formatted(ORG_ID, clerkUserId, email, name, orgRole);

    var syncResult =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(syncRequest))
            .andExpect(status().isCreated())
            .andReturn();

    return JsonPath.read(syncResult.getResponse().getContentAsString(), "$.memberId").toString();
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_lc_trans_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }
}
