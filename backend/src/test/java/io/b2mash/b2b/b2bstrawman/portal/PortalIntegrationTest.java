package io.b2mash.b2b.b2bstrawman.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
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
class PortalIntegrationTest {

  private static final String ORG_ID = "org_portal_integ";
  private static final String ORG_ID_B = "org_portal_integ_b";
  private static final String API_KEY = "test-api-key";

  @Autowired private MockMvc mockMvc;
  @Autowired private PortalJwtService portalJwtService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private CustomerService customerService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private PortalContactService portalContactService;
  @Autowired private CustomerProjectService customerProjectService;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  @Autowired
  private io.b2mash.b2b.b2bstrawman.compliance.CustomerLifecycleService customerLifecycleService;

  private UUID customerIdA;
  private UUID customerIdB;
  private UUID projectId;
  private UUID unlinkedProjectId;
  private UUID sharedDocId;
  private UUID internalDocId;
  private UUID orgSharedDocId;
  private UUID customerSharedDocId;
  private String tenantSchema;
  private UUID memberIdA;

  @BeforeAll
  void setup() throws Exception {
    // Provision org A
    provisioningService.provisionTenant(ORG_ID, "Portal Integ Org A");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    // Provision org B (for cross-tenant isolation tests)
    provisioningService.provisionTenant(ORG_ID_B, "Portal Integ Org B");
    planSyncService.syncPlan(ORG_ID_B, "pro-plan");

    // Sync a member for creating test data
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
                          "clerkUserId": "user_portal_integ_owner",
                          "email": "portal_integ_owner@test.com",
                          "name": "Portal Integ Owner",
                          "avatarUrl": null,
                          "orgRole": "owner"
                        }
                        """
                            .formatted(ORG_ID)))
            .andExpect(status().isCreated())
            .andReturn();

    String memberIdStr = JsonPath.read(syncResult.getResponse().getContentAsString(), "$.memberId");
    memberIdA = UUID.fromString(memberIdStr);

    // Sync a member for org B
    var syncResultB =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "user_portal_integ_b_owner",
                          "email": "portal_integ_b_owner@test.com",
                          "name": "Portal Integ Owner B",
                          "avatarUrl": null,
                          "orgRole": "owner"
                        }
                        """
                            .formatted(ORG_ID_B)))
            .andExpect(status().isCreated())
            .andReturn();

    UUID memberIdB =
        UUID.fromString(
            JsonPath.read(syncResultB.getResponse().getContentAsString(), "$.memberId"));

    // Resolve tenant schema
    tenantSchema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).get().getSchemaName();
    String tenantSchemaB =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID_B).get().getSchemaName();

    // Create test data in org A
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              // Create two customers
              // Create customers directly with ACTIVE lifecycle status (bypassing
              // PROSPECT -> ONBOARDING -> ACTIVE which auto-instantiates checklists)
              var custA =
                  customerRepository.save(
                      new Customer(
                          "Portal Customer A",
                          "portal-cust-a@test.com",
                          null,
                          null,
                          null,
                          memberIdA,
                          null,
                          LifecycleStatus.ACTIVE));
              customerIdA = custA.getId();

              var custB =
                  customerRepository.save(
                      new Customer(
                          "Other Customer B",
                          "portal-cust-b@test.com",
                          null,
                          null,
                          null,
                          memberIdA,
                          null,
                          LifecycleStatus.ACTIVE));
              customerIdB = custB.getId();

              // Create portal contacts for both customers
              portalContactService.createContact(
                  ORG_ID,
                  customerIdA,
                  "portal-cust-a@test.com",
                  "Portal Customer A",
                  PortalContact.ContactRole.PRIMARY);
              portalContactService.createContact(
                  ORG_ID,
                  customerIdB,
                  "portal-cust-b@test.com",
                  "Other Customer B",
                  PortalContact.ContactRole.PRIMARY);

              // Create projects
              var proj =
                  projectRepository.save(
                      new Project("Portal Project", "A test project", memberIdA));
              projectId = proj.getId();

              var unlinkedProj =
                  projectRepository.save(
                      new Project("Unlinked Project", "Not linked to customer", memberIdA));
              unlinkedProjectId = unlinkedProj.getId();

              // Link customer A to projectId
              customerProjectService.linkCustomerToProject(
                  customerIdA, projectId, memberIdA, memberIdA, "owner");

              // Create documents: project-scoped SHARED doc
              var sharedDoc =
                  new Document(
                      Document.Scope.PROJECT,
                      projectId,
                      null,
                      "shared-doc.pdf",
                      "application/pdf",
                      1024L,
                      memberIdA,
                      Document.Visibility.SHARED);
              sharedDoc.assignS3Key("org/" + ORG_ID + "/project/" + projectId + "/shared-doc");
              sharedDoc.confirmUpload();
              sharedDoc = documentRepository.save(sharedDoc);
              sharedDocId = sharedDoc.getId();

              // Create documents: project-scoped INTERNAL doc
              var internalDoc =
                  new Document(
                      Document.Scope.PROJECT,
                      projectId,
                      null,
                      "internal-doc.pdf",
                      "application/pdf",
                      2048L,
                      memberIdA,
                      Document.Visibility.INTERNAL);
              internalDoc.assignS3Key("org/" + ORG_ID + "/project/" + projectId + "/internal-doc");
              internalDoc.confirmUpload();
              internalDoc = documentRepository.save(internalDoc);
              internalDocId = internalDoc.getId();

              // Create ORG-scoped SHARED document
              var orgDoc =
                  new Document(
                      Document.Scope.ORG,
                      null,
                      null,
                      "org-shared.pdf",
                      "application/pdf",
                      512L,
                      memberIdA,
                      Document.Visibility.SHARED);
              orgDoc.assignS3Key("org/" + ORG_ID + "/org-docs/org-shared");
              orgDoc.confirmUpload();
              orgDoc = documentRepository.save(orgDoc);
              orgSharedDocId = orgDoc.getId();

              // Create CUSTOMER-scoped SHARED document for customer A
              var custDoc =
                  new Document(
                      Document.Scope.CUSTOMER,
                      null,
                      customerIdA,
                      "customer-shared.pdf",
                      "application/pdf",
                      256L,
                      memberIdA,
                      Document.Visibility.SHARED);
              custDoc.assignS3Key(
                  "org/" + ORG_ID + "/customer/" + customerIdA + "/customer-shared");
              custDoc.confirmUpload();
              custDoc = documentRepository.save(custDoc);
              customerSharedDocId = custDoc.getId();
            });

    // Create a customer in org B with the same email (cross-tenant test)
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchemaB)
        .where(RequestScopes.ORG_ID, ORG_ID_B)
        .run(
            () -> {
              customerService.createCustomer(
                  "Portal Customer A (Org B)",
                  "portal-cust-orgb@test.com",
                  null,
                  null,
                  null,
                  memberIdB);

              var projB =
                  projectRepository.save(
                      new Project("Org B Project", "Different tenant", memberIdB));
            });
  }

  /** Helper: issues a portal JWT for customer A in org A. */
  private String portalTokenForCustomerA() {
    return portalJwtService.issueToken(customerIdA, ORG_ID);
  }

  @Nested
  class MagicLinkExchange {

    @Test
    void shouldRequestMagicLinkForValidContact() throws Exception {
      mockMvc
          .perform(
              post("/portal/auth/request-link")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"email": "portal-cust-a@test.com", "orgId": "%s"}
                      """
                          .formatted(ORG_ID)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value("If an account exists, a link has been sent."))
          .andExpect(jsonPath("$.magicLink").isNotEmpty());
    }

    @Test
    void shouldReturnGenericMessageForUnknownEmail() throws Exception {
      // Anti-enumeration: same 200 response for unknown emails
      mockMvc
          .perform(
              post("/portal/auth/request-link")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"email": "nonexistent@test.com", "orgId": "%s"}
                      """
                          .formatted(ORG_ID)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value("If an account exists, a link has been sent."))
          .andExpect(jsonPath("$.magicLink").doesNotExist());
    }

    @Test
    void shouldReturnGenericMessageForUnknownOrg() throws Exception {
      // Anti-enumeration: same 200 response for unknown orgs
      mockMvc
          .perform(
              post("/portal/auth/request-link")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"email": "portal-cust-a@test.com", "orgId": "org_nonexistent"}
                      """))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.message").value("If an account exists, a link has been sent."))
          .andExpect(jsonPath("$.magicLink").doesNotExist());
    }

    @Test
    void shouldExchangeMagicLinkForPortalJwt() throws Exception {
      // First request a magic link
      var requestResult =
          mockMvc
              .perform(
                  post("/portal/auth/request-link")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                          {"email": "portal-cust-a@test.com", "orgId": "%s"}
                          """
                              .formatted(ORG_ID)))
              .andExpect(status().isOk())
              .andReturn();

      String magicLink =
          JsonPath.read(requestResult.getResponse().getContentAsString(), "$.magicLink");
      // Extract token from magic link URL: /portal/login?token=...&orgId=...
      String tokenParam = "token=";
      int tokenStart = magicLink.indexOf(tokenParam) + tokenParam.length();
      int tokenEnd = magicLink.indexOf("&", tokenStart);
      String rawToken =
          tokenEnd == -1
              ? magicLink.substring(tokenStart)
              : magicLink.substring(tokenStart, tokenEnd);

      // Exchange for portal JWT
      mockMvc
          .perform(
              post("/portal/auth/exchange")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"token": "%s", "orgId": "%s"}
                      """
                          .formatted(rawToken, ORG_ID)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.token").isNotEmpty())
          .andExpect(jsonPath("$.customerId").value(customerIdA.toString()))
          .andExpect(jsonPath("$.customerName").value("Portal Customer A"));
    }

    @Test
    void shouldRejectExpiredOrInvalidToken() throws Exception {
      mockMvc
          .perform(
              post("/portal/auth/exchange")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"token": "invalid.token.value", "orgId": "%s"}
                      """
                          .formatted(ORG_ID)))
          .andExpect(status().isUnauthorized());
    }
  }

  @Nested
  class PortalProjects {

    @Test
    void shouldListOnlyLinkedProjects() throws Exception {
      String token = portalTokenForCustomerA();

      var result =
          mockMvc
              .perform(get("/portal/projects").header("Authorization", "Bearer " + token))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.length()").value(1))
              .andExpect(jsonPath("$[0].name").value("Portal Project"))
              .andReturn();

      // Verify unlinked project is NOT in the response
      String body = result.getResponse().getContentAsString();
      assertThat(body).doesNotContain("Unlinked Project");
    }

    @Test
    void shouldIncludeDocumentCountInProjectResponse() throws Exception {
      String token = portalTokenForCustomerA();

      mockMvc
          .perform(get("/portal/projects").header("Authorization", "Bearer " + token))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].documentCount").value(1)); // only SHARED docs counted
    }

    @Test
    void shouldReturn401WithoutToken() throws Exception {
      mockMvc.perform(get("/portal/projects")).andExpect(status().isUnauthorized());
    }
  }

  @Nested
  class PortalDocuments {

    @Test
    void shouldListSharedProjectDocuments() throws Exception {
      String token = portalTokenForCustomerA();

      mockMvc
          .perform(
              get("/portal/projects/{projectId}/documents", projectId)
                  .header("Authorization", "Bearer " + token))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(1))
          .andExpect(jsonPath("$[0].fileName").value("shared-doc.pdf"));
    }

    @Test
    void shouldHideInternalDocuments() throws Exception {
      String token = portalTokenForCustomerA();

      var result =
          mockMvc
              .perform(
                  get("/portal/projects/{projectId}/documents", projectId)
                      .header("Authorization", "Bearer " + token))
              .andExpect(status().isOk())
              .andReturn();

      String body = result.getResponse().getContentAsString();
      assertThat(body).doesNotContain("internal-doc.pdf");
    }

    @Test
    void shouldRejectDocumentsForUnlinkedProject() throws Exception {
      String token = portalTokenForCustomerA();

      mockMvc
          .perform(
              get("/portal/projects/{projectId}/documents", unlinkedProjectId)
                  .header("Authorization", "Bearer " + token))
          .andExpect(status().isNotFound());
    }

    @Test
    void shouldListAllSharedCustomerDocuments() throws Exception {
      String token = portalTokenForCustomerA();

      mockMvc
          .perform(get("/portal/documents").header("Authorization", "Bearer " + token))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(2)) // org SHARED + customer SHARED
          .andReturn();
    }

    @Test
    void shouldPresignDownloadForSharedDoc() throws Exception {
      String token = portalTokenForCustomerA();

      mockMvc
          .perform(
              get("/portal/documents/{documentId}/presign-download", sharedDocId)
                  .header("Authorization", "Bearer " + token))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.presignedUrl").isNotEmpty())
          .andExpect(jsonPath("$.expiresInSeconds").isNumber());
    }

    @Test
    void shouldRejectDownloadForInternalDoc() throws Exception {
      String token = portalTokenForCustomerA();

      mockMvc
          .perform(
              get("/portal/documents/{documentId}/presign-download", internalDocId)
                  .header("Authorization", "Bearer " + token))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  class CrossTenantIsolation {

    @Test
    void shouldNotAccessOtherTenantData() throws Exception {
      // Portal JWT for customer A in org A — try to access org B's data
      // Since CustomerAuthFilter binds TENANT_ID from the JWT's org claim,
      // and org A's schema doesn't contain org B's data, isolation is enforced.
      String token = portalTokenForCustomerA();

      // org A customer can only see org A projects
      var result =
          mockMvc
              .perform(get("/portal/projects").header("Authorization", "Bearer " + token))
              .andExpect(status().isOk())
              .andReturn();

      String body = result.getResponse().getContentAsString();
      assertThat(body).doesNotContain("Org B Project");
    }
  }

  @Nested
  class OtherCustomerIsolation {

    @Test
    void shouldNotSeeOtherCustomerScopedDocs() throws Exception {
      // Issue token for customer B (not linked to any project)
      String tokenB = portalJwtService.issueToken(customerIdB, ORG_ID);

      // Customer B should get empty project list
      mockMvc
          .perform(get("/portal/projects").header("Authorization", "Bearer " + tokenB))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(0));

      // Customer B's document list should only contain org-scoped SHARED docs (not customer A's)
      var result =
          mockMvc
              .perform(get("/portal/documents").header("Authorization", "Bearer " + tokenB))
              .andExpect(status().isOk())
              .andReturn();

      String body = result.getResponse().getContentAsString();
      assertThat(body).doesNotContain("customer-shared.pdf");
      // org-scoped SHARED doc should still be visible
      assertThat(body).contains("org-shared.pdf");
    }
  }
}
