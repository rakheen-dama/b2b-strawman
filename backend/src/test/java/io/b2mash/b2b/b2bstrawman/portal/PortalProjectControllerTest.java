package io.b2mash.b2b.b2bstrawman.portal;

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
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
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
class PortalProjectControllerTest {

  private static final String ORG_ID = "org_portal_projdetail_test";
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
  private UUID projectId;
  private String portalToken;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Portal Project Detail Test Org");
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
                          "clerkUserId": "user_projdetail_owner",
                          "email": "projdetail_owner@test.com",
                          "name": "ProjDetail Owner",
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
                      "Detail Test Customer",
                      "detail-customer@test.com",
                      null,
                      null,
                      null,
                      memberId);
              customerId = customer.getId();

              portalContactService.createContact(
                  ORG_ID,
                  customerId,
                  "detail-contact@test.com",
                  "Detail Contact",
                  PortalContact.ContactRole.PRIMARY);
            });

    portalToken = portalJwtService.issueToken(customerId, ORG_ID);

    // Seed portal read-model data
    projectId = UUID.randomUUID();
    readModelRepo.upsertPortalProject(
        projectId,
        customerId,
        ORG_ID,
        "Website Redesign",
        "IN_PROGRESS",
        "Full redesign of corporate website",
        Instant.parse("2026-01-15T10:00:00Z"));

    // Set document and comment counts
    readModelRepo.setDocumentCount(projectId, customerId, 5);
    readModelRepo.incrementCommentCount(projectId, customerId);
    readModelRepo.incrementCommentCount(projectId, customerId);
    readModelRepo.incrementCommentCount(projectId, customerId);
  }

  @Test
  void getProjectDetailReturnsCorrectFields() throws Exception {
    mockMvc
        .perform(
            get("/portal/projects/{id}", projectId)
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(projectId.toString()))
        .andExpect(jsonPath("$.name").value("Website Redesign"))
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.description").value("Full redesign of corporate website"))
        .andExpect(jsonPath("$.createdAt").exists());
  }

  @Test
  void getProjectDetailReturns404ForUnlinkedProject() throws Exception {
    UUID unlinkedProjectId = UUID.randomUUID();

    mockMvc
        .perform(
            get("/portal/projects/{id}", unlinkedProjectId)
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void getProjectDetailIncludesDocumentAndCommentCounts() throws Exception {
    mockMvc
        .perform(
            get("/portal/projects/{id}", projectId)
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documentCount").value(5))
        .andExpect(jsonPath("$.commentCount").value(3));
  }
}
