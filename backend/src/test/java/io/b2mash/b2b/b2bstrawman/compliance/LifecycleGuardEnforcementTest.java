package io.b2mash.b2b.b2bstrawman.compliance;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
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
class LifecycleGuardEnforcementTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_guard_enforce_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID onboardingCustomerId;
  private UUID prospectCustomerId;
  private UUID projectId;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Guard Enforcement Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(
            syncMember("user_guard_owner", "guard_owner@test.com", "Guard Owner", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      // Create ONBOARDING customer (cannot create invoices)
                      var onboardingCustomer =
                          new Customer(
                              "Onboarding Corp",
                              "onboarding_guard@test.com",
                              "+1-555-0020",
                              "ONB-001",
                              "Onboarding customer",
                              memberIdOwner);
                      onboardingCustomer.transitionLifecycle(
                          "ONBOARDING", memberIdOwner, java.time.Instant.now(), null);
                      onboardingCustomer = customerRepository.save(onboardingCustomer);
                      onboardingCustomerId = onboardingCustomer.getId();

                      // Create PROSPECT customer (cannot create projects)
                      var prospectCustomer =
                          new Customer(
                              "Prospect Corp",
                              "prospect_guard@test.com",
                              "+1-555-0021",
                              "PRO-001",
                              "Prospect customer",
                              memberIdOwner);
                      prospectCustomer = customerRepository.save(prospectCustomer);
                      prospectCustomerId = prospectCustomer.getId();

                      // Create a project for linking
                      var project =
                          new Project(
                              "Guard Test Project", "Project for guard tests", memberIdOwner);
                      project = projectRepository.save(project);
                      projectId = project.getId();
                    }));
  }

  @Test
  void creatingInvoiceForOnboardingCustomerReturns409() throws Exception {
    mockMvc
        .perform(
            post("/api/invoices")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"customerId": "%s", "currency": "ZAR"}
                    """
                        .formatted(onboardingCustomerId)))
        .andExpect(status().isConflict());
  }

  @Test
  void linkingProjectToProspectCustomerReturns409() throws Exception {
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/customers/" + prospectCustomerId)
                .with(ownerJwt()))
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
        .jwt(j -> j.subject("user_guard_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }
}
