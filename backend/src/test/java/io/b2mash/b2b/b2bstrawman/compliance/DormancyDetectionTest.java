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
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class DormancyDetectionTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_dormancy_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private EntityManager entityManager;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID dormantCandidateId;
  private UUID recentCustomerId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Dormancy Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember(
                "user_dormancy_owner", "dormancy_owner@test.com", "Dormancy Owner", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create test customers
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      // Customer with old updatedAt (should be flagged as dormant candidate)
                      var oldCustomer =
                          new Customer(
                              "Old Inactive Corp",
                              "old_inactive@test.com",
                              "+1-555-0010",
                              "OLD-001",
                              "Old customer",
                              memberIdOwner);
                      // Set to ACTIVE lifecycle status
                      oldCustomer.transitionLifecycle(
                          "ONBOARDING", memberIdOwner, Instant.now(), null);
                      oldCustomer = customerRepository.save(oldCustomer);
                      oldCustomer.transitionLifecycle("ACTIVE", memberIdOwner, Instant.now(), null);
                      oldCustomer = customerRepository.save(oldCustomer);
                      dormantCandidateId = oldCustomer.getId();

                      // Manually backdate updatedAt via native query
                      entityManager.flush();
                      entityManager
                          .createNativeQuery(
                              "UPDATE customers SET updated_at = :oldDate WHERE id = :id")
                          .setParameter(
                              "oldDate",
                              java.sql.Timestamp.from(Instant.now().minus(120, ChronoUnit.DAYS)))
                          .setParameter("id", dormantCandidateId)
                          .executeUpdate();

                      // Customer with recent updatedAt (should NOT be flagged)
                      var recentCustomer =
                          new Customer(
                              "Recent Active Corp",
                              "recent_active@test.com",
                              "+1-555-0011",
                              "REC-001",
                              "Recent customer",
                              memberIdOwner);
                      recentCustomer.transitionLifecycle(
                          "ONBOARDING", memberIdOwner, Instant.now(), null);
                      recentCustomer = customerRepository.save(recentCustomer);
                      recentCustomer.transitionLifecycle(
                          "ACTIVE", memberIdOwner, Instant.now(), null);
                      recentCustomer = customerRepository.save(recentCustomer);
                      recentCustomerId = recentCustomer.getId();
                    }));
  }

  @Test
  void dormancyCheckReturnsCandidatesWithOldActivity() throws Exception {
    mockMvc
        .perform(post("/api/customers/dormancy-check").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.thresholdDays").value(90))
        .andExpect(jsonPath("$.candidates").isArray())
        .andExpect(jsonPath("$.candidates[?(@.id == '%s')]", dormantCandidateId).exists());
  }

  @Test
  void dormancyCheckExcludesRecentlyActiveCustomers() throws Exception {
    var result =
        mockMvc
            .perform(post("/api/customers/dormancy-check").with(ownerJwt()))
            .andExpect(status().isOk())
            .andReturn();

    String body = result.getResponse().getContentAsString();
    // The recent customer should NOT appear in candidates
    var candidateIds = (List<String>) JsonPath.read(body, "$.candidates[*].id");
    org.assertj.core.api.Assertions.assertThat(candidateIds)
        .doesNotContain(recentCustomerId.toString());
  }

  @Test
  void dormancyCheckEndpointReturnsCorrectStructure() throws Exception {
    mockMvc
        .perform(post("/api/customers/dormancy-check").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.thresholdDays").isNumber())
        .andExpect(jsonPath("$.candidates").isArray())
        .andExpect(jsonPath("$.candidates[0].id").exists())
        .andExpect(jsonPath("$.candidates[0].name").exists())
        .andExpect(jsonPath("$.candidates[0].lastActivityAt").exists())
        .andExpect(jsonPath("$.candidates[0].daysSinceActivity").isNumber())
        .andExpect(jsonPath("$.candidates[0].currentStatus").exists());
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
        .jwt(j -> j.subject("user_dormancy_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }
}
