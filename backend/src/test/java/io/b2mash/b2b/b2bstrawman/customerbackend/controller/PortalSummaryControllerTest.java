package io.b2mash.b2b.b2bstrawman.customerbackend.controller;

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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalSummaryControllerTest {

  private static final String ORG_ID = "org_portal_summary_test";
  private static final String API_KEY = "test-api-key";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private CustomerService customerService;
  @Autowired private PortalContactService portalContactService;
  @Autowired private PortalJwtService portalJwtService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private PortalReadModelRepository readModelRepo;

  private UUID customerId;
  private UUID projectWithSummary;
  private UUID projectWithoutSummary;
  private String portalToken;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Portal Summary Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    // Sync a member
    var syncResult =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "user_summary_owner",
                          "email": "summary_owner@test.com",
                          "name": "Summary Owner",
                          "avatarUrl": null,
                          "orgRole": "owner"
                        }
                        """
                            .formatted(ORG_ID)))
            .andReturn();

    String memberIdStr = JsonPath.read(syncResult.getResponse().getContentAsString(), "$.memberId");
    UUID memberId = UUID.fromString(memberIdStr);

    String tenantSchema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).get().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var customer =
                  customerService.createCustomer(
                      "Summary Test Customer",
                      "summary-customer@test.com",
                      null,
                      null,
                      null,
                      null,
                      memberId);
              customerId = customer.getId();

              portalContactService.createContact(
                  ORG_ID,
                  customerId,
                  "summary-contact@test.com",
                  "Summary Contact",
                  PortalContact.ContactRole.PRIMARY);
            });

    portalToken = portalJwtService.issueToken(customerId, ORG_ID);

    // Seed portal read-model data
    projectWithSummary = UUID.randomUUID();
    projectWithoutSummary = UUID.randomUUID();

    readModelRepo.upsertPortalProject(
        projectWithSummary,
        customerId,
        ORG_ID,
        "Project With Summary",
        "IN_PROGRESS",
        "Has time data",
        Instant.now());

    readModelRepo.upsertPortalProject(
        projectWithoutSummary,
        customerId,
        ORG_ID,
        "Project Without Summary",
        "ACTIVE",
        "No time data",
        Instant.now());

    // Seed summary data for one project
    readModelRepo.upsertPortalProjectSummary(
        projectWithSummary,
        customerId,
        ORG_ID,
        new BigDecimal("42.50"),
        new BigDecimal("38.00"),
        Instant.parse("2026-02-11T16:00:00Z"));
  }

  @Test
  void getSummaryReturnsSeededData() throws Exception {
    mockMvc
        .perform(
            get("/portal/projects/{projectId}/summary", projectWithSummary)
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projectId").value(projectWithSummary.toString()))
        .andExpect(jsonPath("$.totalHours").value(42.5))
        .andExpect(jsonPath("$.billableHours").value(38.0))
        .andExpect(jsonPath("$.lastActivityAt").value("2026-02-11T16:00:00Z"));
  }

  @Test
  void getSummaryReturnsZeroStubWhenNoSummaryExists() throws Exception {
    mockMvc
        .perform(
            get("/portal/projects/{projectId}/summary", projectWithoutSummary)
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projectId").value(projectWithoutSummary.toString()))
        .andExpect(jsonPath("$.totalHours").value(0))
        .andExpect(jsonPath("$.billableHours").value(0))
        .andExpect(jsonPath("$.lastActivityAt").isEmpty());
  }

  @Test
  void getSummaryReturns404ForUnlinkedProject() throws Exception {
    UUID unlinkedProjectId = UUID.randomUUID();
    mockMvc
        .perform(
            get("/portal/projects/{projectId}/summary", unlinkedProjectId)
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void getSummaryReturns401WithoutToken() throws Exception {
    mockMvc
        .perform(get("/portal/projects/{projectId}/summary", projectWithSummary))
        .andExpect(status().isUnauthorized());
  }
}
