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
class PortalCommentControllerTest {

  private static final String ORG_ID = "org_portal_comment_test";
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
  private UUID projectWithNoComments;
  private String portalToken;
  private String tenantSchema;
  private UUID otherCustomerProjectId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Portal Comment Test Org");
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
                          "clerkUserId": "user_comment_owner",
                          "email": "comment_owner@test.com",
                          "name": "Comment Owner",
                          "avatarUrl": null,
                          "orgRole": "owner"
                        }
                        """
                            .formatted(ORG_ID)))
            .andReturn();

    String memberIdStr = JsonPath.read(syncResult.getResponse().getContentAsString(), "$.memberId");
    UUID memberId = UUID.fromString(memberIdStr);

    tenantSchema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).get().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var customer =
                  customerService.createCustomer(
                      "Comment Test Customer",
                      "comment-customer@test.com",
                      null,
                      null,
                      null,
                      memberId);
              customerId = customer.getId();

              portalContactService.createContact(
                  ORG_ID,
                  customerId,
                  "comment-contact@test.com",
                  "Comment Contact",
                  PortalContact.ContactRole.PRIMARY);
            });

    portalToken = portalJwtService.issueToken(customerId, ORG_ID);

    // Seed portal read-model data
    projectId = UUID.randomUUID();
    projectWithNoComments = UUID.randomUUID();

    readModelRepo.upsertPortalProject(
        projectId,
        customerId,
        ORG_ID,
        "Commented Project",
        "IN_PROGRESS",
        "A project",
        Instant.now());

    readModelRepo.upsertPortalProject(
        projectWithNoComments,
        customerId,
        ORG_ID,
        "Empty Project",
        "ACTIVE",
        "No comments",
        Instant.now());

    // Add comments to the first project
    readModelRepo.upsertPortalComment(
        UUID.randomUUID(),
        ORG_ID,
        projectId,
        "Alice",
        "First comment",
        Instant.parse("2026-02-01T10:00:00Z"));
    readModelRepo.upsertPortalComment(
        UUID.randomUUID(),
        ORG_ID,
        projectId,
        "Bob",
        "Second comment",
        Instant.parse("2026-02-02T12:00:00Z"));
    readModelRepo.upsertPortalComment(
        UUID.randomUUID(),
        ORG_ID,
        projectId,
        "Charlie",
        "Third comment",
        Instant.parse("2026-02-03T14:00:00Z"));

    // Seed a second customer with its own project (for cross-customer isolation test)
    UUID[] otherCustomerIdHolder = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var otherCustomer =
                  customerService.createCustomer(
                      "Other Customer",
                      "other-customer@test.com",
                      null,
                      null,
                      null,
                      UUID.fromString(memberIdStr));
              otherCustomerIdHolder[0] = otherCustomer.getId();

              portalContactService.createContact(
                  ORG_ID,
                  otherCustomer.getId(),
                  "other-contact@test.com",
                  "Other Contact",
                  PortalContact.ContactRole.GENERAL);
            });

    otherCustomerProjectId = UUID.randomUUID();
    readModelRepo.upsertPortalProject(
        otherCustomerProjectId,
        otherCustomerIdHolder[0],
        ORG_ID,
        "Other Customer Project",
        "ACTIVE",
        "Belongs to other customer",
        Instant.now());
  }

  @Test
  void listCommentsReturnsCommentsForLinkedProject() throws Exception {
    mockMvc
        .perform(
            get("/portal/projects/{projectId}/comments", projectId)
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].authorName").exists())
        .andExpect(jsonPath("$[0].content").exists())
        .andExpect(jsonPath("$[0].createdAt").exists());
  }

  @Test
  void listCommentsReturnsEmptyListForProjectWithNoComments() throws Exception {
    mockMvc
        .perform(
            get("/portal/projects/{projectId}/comments", projectWithNoComments)
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void listCommentsReturns404ForProjectNotLinkedToCustomer() throws Exception {
    UUID unlinkedProjectId = UUID.randomUUID();

    mockMvc
        .perform(
            get("/portal/projects/{projectId}/comments", unlinkedProjectId)
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void listCommentsReturns401WithoutToken() throws Exception {
    mockMvc
        .perform(get("/portal/projects/{projectId}/comments", projectId))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void listCommentsAreSortedByCreatedAtDesc() throws Exception {
    var result =
        mockMvc
            .perform(
                get("/portal/projects/{projectId}/comments", projectId)
                    .header("Authorization", "Bearer " + portalToken))
            .andExpect(status().isOk())
            .andReturn();

    String json = result.getResponse().getContentAsString();
    String first = JsonPath.read(json, "$[0].createdAt");
    String second = JsonPath.read(json, "$[1].createdAt");
    String third = JsonPath.read(json, "$[2].createdAt");

    // Verify DESC order: newest first
    org.assertj.core.api.Assertions.assertThat(Instant.parse(first)).isAfter(Instant.parse(second));
    org.assertj.core.api.Assertions.assertThat(Instant.parse(second)).isAfter(Instant.parse(third));
  }

  @Test
  void listCommentsReturns404ForOtherCustomersProject() throws Exception {
    // Customer A's token should not be able to access Customer B's project
    mockMvc
        .perform(
            get("/portal/projects/{projectId}/comments", otherCustomerProjectId)
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isNotFound());
  }
}
