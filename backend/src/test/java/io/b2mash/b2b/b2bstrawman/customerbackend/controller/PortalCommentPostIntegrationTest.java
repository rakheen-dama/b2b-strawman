package io.b2mash.b2b.b2bstrawman.customerbackend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.integration.storage.PresignedUrl;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactService;
import io.b2mash.b2b.b2bstrawman.portal.PortalJwtService;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalCommentPostIntegrationTest {

  private static final String ORG_ID = "org_portal_comment_post_test";
  private static final String API_KEY = "test-api-key";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private CustomerService customerService;
  @Autowired private PortalContactService portalContactService;
  @Autowired private PortalJwtService portalJwtService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private PortalReadModelRepository readModelRepo;
  @Autowired private ProjectService projectService;
  @MockitoBean private StorageService storageService;

  private UUID customerId;
  private UUID projectId;
  private String portalToken;
  private String tenantSchema;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Portal Comment Post Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

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
                          "clerkUserId": "user_comment_post_owner",
                          "email": "comment_post_owner@test.com",
                          "name": "Comment Post Owner",
                          "avatarUrl": null,
                          "orgRole": "owner"
                        }
                        """
                            .formatted(ORG_ID)))
            .andReturn();

    String memberIdStr = JsonPath.read(syncResult.getResponse().getContentAsString(), "$.memberId");
    UUID memberId = UUID.fromString(memberIdStr);
    tenantSchema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).get().getSchemaName();

    // Create customer + portal contact + real project in tenant schema
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () -> {
              var customer =
                  customerService.createCustomer(
                      "Comment Post Customer", "comment-post@test.com", null, null, null, memberId);
              customerId = customer.getId();
              portalContactService.createContact(
                  ORG_ID,
                  customerId,
                  "comment-post-contact@test.com",
                  "Comment Contact",
                  PortalContact.ContactRole.PRIMARY);

              // Create a real project in tenant schema (needed for comments FK)
              var project =
                  projectService.createProject("Test Project", "A test project", memberId);
              projectId = project.getId();
            });

    portalToken = portalJwtService.issueToken(customerId, ORG_ID);

    // Seed project in the portal read-model (using the real project ID)
    readModelRepo.upsertPortalProject(
        projectId, customerId, ORG_ID, "Test Project", "ACTIVE", "A test project", Instant.now());

    // Mock StorageService
    when(storageService.generateDownloadUrl(any(String.class), any()))
        .thenReturn(
            new PresignedUrl("https://s3.example.com/logo.png", Instant.now().plusSeconds(3600)));
  }

  @Test
  void post_comment_returns_201_with_comment() throws Exception {
    mockMvc
        .perform(
            post("/portal/projects/{projectId}/comments", projectId)
                .header("Authorization", "Bearer " + portalToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"content": "Hello from the portal!"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.authorName").value("Comment Contact"))
        .andExpect(jsonPath("$.content").value("Hello from the portal!"))
        .andExpect(jsonPath("$.createdAt").exists());
  }

  @Test
  void post_comment_empty_content_returns_400() throws Exception {
    mockMvc
        .perform(
            post("/portal/projects/{projectId}/comments", projectId)
                .header("Authorization", "Bearer " + portalToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"content": ""}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void post_comment_exceeds_2000_chars_returns_400() throws Exception {
    String longContent = "x".repeat(2001);
    mockMvc
        .perform(
            post("/portal/projects/{projectId}/comments", projectId)
                .header("Authorization", "Bearer " + portalToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"content": "%s"}
                    """
                        .formatted(longContent)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void post_comment_wrong_project_returns_404() throws Exception {
    UUID unlinkedProjectId = UUID.randomUUID();
    mockMvc
        .perform(
            post("/portal/projects/{projectId}/comments", unlinkedProjectId)
                .header("Authorization", "Bearer " + portalToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"content": "Should fail"}
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  void post_comment_without_auth_returns_401() throws Exception {
    mockMvc
        .perform(
            post("/portal/projects/{projectId}/comments", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"content": "No auth"}
                    """))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void post_comment_appears_in_get_list() throws Exception {
    // Post a comment
    mockMvc
        .perform(
            post("/portal/projects/{projectId}/comments", projectId)
                .header("Authorization", "Bearer " + portalToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"content": "Visible in list"}
                    """))
        .andExpect(status().isCreated());

    // Verify it appears in the GET list
    mockMvc
        .perform(
            get("/portal/projects/{projectId}/comments", projectId)
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.content == 'Visible in list')]").exists());
  }
}
