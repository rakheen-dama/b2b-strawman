package io.b2mash.b2b.b2bstrawman.setupstatus;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerType;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CustomerReadinessControllerTest {
  private static final String ORG_ID = "org_readiness_test";
  private static final String ORG_ID_B = "org_readiness_test_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID prospectCustomerId;
  private UUID activeCustomerId;
  private UUID activeCustomerWithProjectId;

  private String tenantSchemaB;
  private UUID memberIdOwnerB;
  private UUID customerIdB;

  @BeforeAll
  void setup() throws Exception {
    // --- Tenant A ---
    provisioningService.provisionTenant(ORG_ID, "Readiness Test Org", null);

    memberIdOwner =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_readiness_owner",
                "readiness_owner@test.com",
                "Readiness Owner",
                "owner"));

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
                      // Create a PROSPECT customer (no linked projects)
                      var prospect =
                          TestCustomerFactory.createCustomerWithStatus(
                              "Prospect Corp",
                              "prospect@readiness.com",
                              memberIdOwner,
                              LifecycleStatus.PROSPECT);
                      prospect = customerRepository.save(prospect);
                      prospectCustomerId = prospect.getId();

                      // Create an ACTIVE customer without linked projects
                      var active =
                          TestCustomerFactory.createActiveCustomer(
                              "Active Corp",
                              "active@readiness.com",
                              memberIdOwner,
                              CustomerType.COMPANY);
                      active = customerRepository.save(active);
                      activeCustomerId = active.getId();

                      // Create an ACTIVE customer with a linked project
                      var activeWithProject =
                          TestCustomerFactory.createActiveCustomer(
                              "Active Linked Corp",
                              "activelinked@readiness.com",
                              memberIdOwner,
                              CustomerType.COMPANY);
                      activeWithProject = customerRepository.save(activeWithProject);
                      activeCustomerWithProjectId = activeWithProject.getId();

                      // Create a project and link it
                      var project = new Project("Test Project", "Desc", memberIdOwner);
                      project = projectRepository.save(project);
                      entityManager.flush();

                      var link =
                          new CustomerProject(
                              activeCustomerWithProjectId, project.getId(), memberIdOwner);
                      customerProjectRepository.save(link);
                      entityManager.flush();
                    }));

    // --- Tenant B (isolation) ---
    provisioningService.provisionTenant(ORG_ID_B, "Readiness Test Org B", null);

    memberIdOwnerB =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID_B,
                "user_readiness_owner_b",
                "readiness_owner_b@test.com",
                "Readiness Owner B",
                "owner"));

    tenantSchemaB =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID_B).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaB)
        .where(RequestScopes.ORG_ID, ORG_ID_B)
        .where(RequestScopes.MEMBER_ID, memberIdOwnerB)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var customer =
                          createActiveCustomer(
                              "Tenant B Customer", "tenantb@readiness.com", memberIdOwnerB);
                      customer = customerRepository.save(customer);
                      customerIdB = customer.getId();
                    }));
  }

  // Note: checklistProgress is null because no checklist instances are seeded in integration tests.
  // Phase 14 Java entity stubs do not expose a save method for checklist_instances.
  // The native SQL query path returning null is tested in CustomerReadinessServiceTest (unit).

  @Test
  @Order(1)
  void getReadiness_returns200_withExpectedShape() throws Exception {
    mockMvc
        .perform(
            get("/api/customers/" + activeCustomerId + "/readiness")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_readiness_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customerId").value(activeCustomerId.toString()))
        .andExpect(jsonPath("$.lifecycleStatus").value("ACTIVE"))
        .andExpect(jsonPath("$.requiredFields.filled").isNumber())
        .andExpect(jsonPath("$.requiredFields.total").isNumber())
        .andExpect(jsonPath("$.hasLinkedProjects").isBoolean())
        .andExpect(jsonPath("$.overallReadiness").isString());
  }

  @Test
  @Order(2)
  void getReadiness_prospectCustomer_noProjects_needsAttention() throws Exception {
    mockMvc
        .perform(
            get("/api/customers/" + prospectCustomerId + "/readiness")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_readiness_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lifecycleStatus").value("PROSPECT"))
        .andExpect(jsonPath("$.overallReadiness").value("Needs Attention"))
        .andExpect(jsonPath("$.hasLinkedProjects").value(false));
  }

  @Test
  @Order(3)
  void getReadiness_activeCustomer_withLinkedProject_hasLinkedProjectsTrue() throws Exception {
    mockMvc
        .perform(
            get("/api/customers/" + activeCustomerWithProjectId + "/readiness")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_readiness_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasLinkedProjects").value(true));
  }

  @Test
  @Order(4)
  void getReadiness_withoutAuth_returns401() throws Exception {
    mockMvc
        .perform(get("/api/customers/" + activeCustomerId + "/readiness"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @Order(5)
  void getReadiness_crossTenant_returns404() throws Exception {
    // Tenant B's JWT trying to access Tenant A's customer
    mockMvc
        .perform(
            get("/api/customers/" + prospectCustomerId + "/readiness")
                .with(TestJwtFactory.ownerJwt(ORG_ID_B, "user_readiness_owner_b")))
        .andExpect(status().isNotFound());
  }
}
