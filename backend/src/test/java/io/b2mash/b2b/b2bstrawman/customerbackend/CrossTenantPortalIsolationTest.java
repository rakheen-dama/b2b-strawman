package io.b2mash.b2b.b2bstrawman.customerbackend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactService;
import io.b2mash.b2b.b2bstrawman.portal.PortalJwtService;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Cross-tenant portal isolation tests. Verifies that a customer authenticated against Org A cannot
 * access any portal data belonging to Org B. Provisions two independent tenants with their own
 * customers, contacts, projects, comments, and summaries.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CrossTenantPortalIsolationTest {

  private static final String ORG_A_ID = "org_isolation_a";
  private static final String ORG_B_ID = "org_isolation_b";
  private static final String API_KEY = "test-api-key";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private CustomerService customerService;
  @Autowired private PortalContactService portalContactService;
  @Autowired private PortalJwtService portalJwtService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private PortalReadModelRepository readModelRepo;

  private String tokenA;
  private UUID projectB;

  @BeforeAll
  void setup() throws Exception {
    // Provision Org A
    provisioningService.provisionTenant(ORG_A_ID, "Isolation Org A");
    planSyncService.syncPlan(ORG_A_ID, "pro-plan");

    var syncA =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "user_iso_a_owner",
                          "email": "iso_a_owner@test.com",
                          "name": "Iso A Owner",
                          "avatarUrl": null,
                          "orgRole": "owner"
                        }
                        """
                            .formatted(ORG_A_ID)))
            .andReturn();

    String memberAIdStr = JsonPath.read(syncA.getResponse().getContentAsString(), "$.memberId");
    UUID memberAId = UUID.fromString(memberAIdStr);
    String schemaA = orgSchemaMappingRepository.findByClerkOrgId(ORG_A_ID).get().getSchemaName();

    UUID[] customerAHolder = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, schemaA)
        .where(RequestScopes.ORG_ID, ORG_A_ID)
        .run(
            () -> {
              var customer =
                  customerService.createCustomer(
                      "Customer A", "customer-a@test.com", null, null, null, memberAId);
              customerAHolder[0] = customer.getId();

              portalContactService.createContact(
                  ORG_A_ID,
                  customer.getId(),
                  "contact-a@test.com",
                  "Contact A",
                  PortalContact.ContactRole.PRIMARY);
            });

    UUID customerAId = customerAHolder[0];
    tokenA = portalJwtService.issueToken(customerAId, ORG_A_ID);

    // Seed Org A portal data
    UUID projectA = UUID.randomUUID();
    readModelRepo.upsertPortalProject(
        projectA, customerAId, ORG_A_ID, "Org A Project", "ACTIVE", "Belongs to A", Instant.now());
    readModelRepo.upsertPortalComment(
        UUID.randomUUID(),
        ORG_A_ID,
        projectA,
        "Alice",
        "Org A comment",
        Instant.parse("2026-02-01T10:00:00Z"));
    readModelRepo.upsertPortalProjectSummary(
        projectA,
        customerAId,
        ORG_A_ID,
        new BigDecimal("10.00"),
        new BigDecimal("8.00"),
        Instant.parse("2026-02-10T12:00:00Z"));

    // Provision Org B
    provisioningService.provisionTenant(ORG_B_ID, "Isolation Org B");
    planSyncService.syncPlan(ORG_B_ID, "pro-plan");

    var syncB =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "user_iso_b_owner",
                          "email": "iso_b_owner@test.com",
                          "name": "Iso B Owner",
                          "avatarUrl": null,
                          "orgRole": "owner"
                        }
                        """
                            .formatted(ORG_B_ID)))
            .andReturn();

    String memberBIdStr = JsonPath.read(syncB.getResponse().getContentAsString(), "$.memberId");
    UUID memberBId = UUID.fromString(memberBIdStr);
    String schemaB = orgSchemaMappingRepository.findByClerkOrgId(ORG_B_ID).get().getSchemaName();

    UUID[] customerBHolder = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, schemaB)
        .where(RequestScopes.ORG_ID, ORG_B_ID)
        .run(
            () -> {
              var customer =
                  customerService.createCustomer(
                      "Customer B", "customer-b@test.com", null, null, null, memberBId);
              customerBHolder[0] = customer.getId();

              portalContactService.createContact(
                  ORG_B_ID,
                  customer.getId(),
                  "contact-b@test.com",
                  "Contact B",
                  PortalContact.ContactRole.PRIMARY);
            });

    UUID customerBId = customerBHolder[0];

    // Seed Org B portal data
    projectB = UUID.randomUUID();
    readModelRepo.upsertPortalProject(
        projectB,
        customerBId,
        ORG_B_ID,
        "Org B Project",
        "IN_PROGRESS",
        "Belongs to B",
        Instant.now());
    readModelRepo.upsertPortalComment(
        UUID.randomUUID(),
        ORG_B_ID,
        projectB,
        "Bob",
        "Org B comment",
        Instant.parse("2026-02-05T14:00:00Z"));
    readModelRepo.upsertPortalProjectSummary(
        projectB,
        customerBId,
        ORG_B_ID,
        new BigDecimal("25.00"),
        new BigDecimal("20.00"),
        Instant.parse("2026-02-12T09:00:00Z"));
  }

  @Test
  void orgACustomerCannotViewOrgBProjectDetail() throws Exception {
    mockMvc
        .perform(get("/portal/projects/{id}", projectB).header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isNotFound());
  }

  @Test
  void orgACustomerCannotListOrgBComments() throws Exception {
    mockMvc
        .perform(
            get("/portal/projects/{projectId}/comments", projectB)
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isNotFound());
  }

  @Test
  void orgACustomerCannotGetOrgBSummary() throws Exception {
    mockMvc
        .perform(
            get("/portal/projects/{projectId}/summary", projectB)
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isNotFound());
  }

  @Test
  void orgACustomerProjectListDoesNotContainOrgBProjects() throws Exception {
    mockMvc
        .perform(get("/portal/projects").header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name == 'Org B Project')]").doesNotExist());
  }
}
