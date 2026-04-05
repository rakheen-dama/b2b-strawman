package io.b2mash.b2b.b2bstrawman.document;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentIntegrationTest {

  private static final String ORG_ID = "org_document_test";
  private static final String ORG_B_ID = "org_document_test_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  private String projectId;
  private String projectBId;
  private String memberMemberId;

  @BeforeAll
  void provisionTenantsAndProjects() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Document Test Org", null);
    provisioningService.provisionTenant(ORG_B_ID, "Document Test Org B", null);

    // Sync members so MemberContext is populated and we know member IDs
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_owner", "doc_owner@test.com", "Owner", "owner");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_admin", "doc_admin@test.com", "Admin", "admin");
    memberMemberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, "user_member", "doc_member@test.com", "Member", "member");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_nonmember", "doc_nonmember@test.com", "NonMember", "member");

    // Create a project in tenant A (owner becomes lead)
    var result =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Doc Test Project", "description": "For document tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectId = TestEntityHelper.extractIdFromLocation(result);

    // Add member user to the project so they can access documents
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\": \"%s\"}".formatted(memberMemberId)))
        .andExpect(status().isCreated());

    // Create a project in tenant B
    var resultB =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_B_ID, "user_tenant_b_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Tenant B Project", "description": "For tenant B"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectBId = TestEntityHelper.extractIdFromLocation(resultB);
  }

  // --- Upload flow (init → confirm) ---

  @Test
  void shouldInitiateUploadAndReturnPresignedUrl() throws Exception {
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/documents/upload-init")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fileName": "report.pdf", "contentType": "application/pdf", "size": 1024}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.documentId").exists())
        .andExpect(jsonPath("$.presignedUrl").exists())
        .andExpect(jsonPath("$.expiresInSeconds").value(3600));
  }

  @Test
  void shouldConfirmUpload() throws Exception {
    var initResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/documents/upload-init")
                    .with(TestJwtFactory.memberJwt(ORG_ID, "user_member"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "confirm-test.pdf", "contentType": "application/pdf", "size": 2048}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var documentId = extractJsonField(initResult, "documentId");

    mockMvc
        .perform(
            post("/api/documents/" + documentId + "/confirm")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_member")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UPLOADED"))
        .andExpect(jsonPath("$.uploadedAt").exists());
  }

  @Test
  void confirmShouldBeIdempotent() throws Exception {
    var initResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/documents/upload-init")
                    .with(TestJwtFactory.memberJwt(ORG_ID, "user_member"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "idempotent.pdf", "contentType": "application/pdf", "size": 512}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var documentId = extractJsonField(initResult, "documentId");

    // First confirm
    mockMvc
        .perform(
            post("/api/documents/" + documentId + "/confirm")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_member")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UPLOADED"));

    // Second confirm — idempotent
    mockMvc
        .perform(
            post("/api/documents/" + documentId + "/confirm")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_member")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UPLOADED"));
  }

  // --- Document listing ---

  @Test
  void shouldListDocumentsForProject() throws Exception {
    // Upload two documents
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/documents/upload-init")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fileName": "list-1.pdf", "contentType": "application/pdf", "size": 100}
                    """))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/documents/upload-init")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fileName": "list-2.pdf", "contentType": "application/pdf", "size": 200}
                    """))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/documents")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_member")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(2)));
  }

  @Test
  void shouldReturn404WhenListingDocumentsForNonexistentProject() throws Exception {
    mockMvc
        .perform(
            get("/api/projects/00000000-0000-0000-0000-000000000000/documents")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_member")))
        .andExpect(status().isNotFound());
  }

  // --- Presigned download ---

  @Test
  void shouldReturnPresignedDownloadUrlForUploadedDocument() throws Exception {
    var initResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/documents/upload-init")
                    .with(TestJwtFactory.memberJwt(ORG_ID, "user_member"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "download-test.pdf", "contentType": "application/pdf", "size": 4096}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var documentId = extractJsonField(initResult, "documentId");

    // Confirm the upload first
    mockMvc
        .perform(
            post("/api/documents/" + documentId + "/confirm")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_member")))
        .andExpect(status().isOk());

    // Now get the presigned download URL
    mockMvc
        .perform(
            get("/api/documents/" + documentId + "/presign-download")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_member")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.presignedUrl").exists())
        .andExpect(jsonPath("$.expiresInSeconds").value(3600));
  }

  @Test
  void shouldReturn400WhenDownloadingPendingDocument() throws Exception {
    var initResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/documents/upload-init")
                    .with(TestJwtFactory.memberJwt(ORG_ID, "user_member"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "pending.pdf", "contentType": "application/pdf", "size": 512}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var documentId = extractJsonField(initResult, "documentId");

    // Try to download without confirming
    mockMvc
        .perform(
            get("/api/documents/" + documentId + "/presign-download")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_member")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturn404WhenDownloadingNonexistentDocument() throws Exception {
    mockMvc
        .perform(
            get("/api/documents/00000000-0000-0000-0000-000000000000/presign-download")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_member")))
        .andExpect(status().isNotFound());
  }

  // --- Authorization ---

  @Test
  void unauthenticatedUserCannotAccessDocumentEndpoints() throws Exception {
    mockMvc
        .perform(get("/api/projects/" + projectId + "/documents"))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/documents/upload-init")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fileName": "unauth.pdf", "contentType": "application/pdf", "size": 100}
                    """))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void memberCanUploadAndDownload() throws Exception {
    var initResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/documents/upload-init")
                    .with(TestJwtFactory.memberJwt(ORG_ID, "user_member"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "member-test.pdf", "contentType": "application/pdf", "size": 100}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var documentId = extractJsonField(initResult, "documentId");

    mockMvc
        .perform(
            post("/api/documents/" + documentId + "/confirm")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_member")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/documents/" + documentId + "/presign-download")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_member")))
        .andExpect(status().isOk());
  }

  @Test
  void adminCanUploadAndDownload() throws Exception {
    var initResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/documents/upload-init")
                    .with(TestJwtFactory.adminJwt(ORG_ID, "user_admin"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "admin-test.pdf", "contentType": "application/pdf", "size": 100}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var documentId = extractJsonField(initResult, "documentId");

    mockMvc
        .perform(
            post("/api/documents/" + documentId + "/confirm")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_admin")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/documents/" + documentId + "/presign-download")
                .with(TestJwtFactory.adminJwt(ORG_ID, "user_admin")))
        .andExpect(status().isOk());
  }

  // --- Tenant isolation ---

  @Test
  void documentsAreIsolatedBetweenTenants() throws Exception {
    // Upload in tenant A
    var initResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/documents/upload-init")
                    .with(TestJwtFactory.memberJwt(ORG_ID, "user_member"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "isolation-test.pdf", "contentType": "application/pdf", "size": 100}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var documentId = extractJsonField(initResult, "documentId");

    // Confirm from tenant A
    mockMvc
        .perform(
            post("/api/documents/" + documentId + "/confirm")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_member")))
        .andExpect(status().isOk());

    // Tenant B cannot see tenant A's documents via project listing
    // (tenant B can't even see tenant A's project — returns 404)
    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/documents")
                .with(TestJwtFactory.memberJwt(ORG_B_ID, "user_tenant_b")))
        .andExpect(status().isNotFound());

    // Tenant B cannot confirm tenant A's document
    mockMvc
        .perform(
            post("/api/documents/" + documentId + "/confirm")
                .with(TestJwtFactory.memberJwt(ORG_B_ID, "user_tenant_b")))
        .andExpect(status().isNotFound());

    // Tenant B cannot download tenant A's document
    mockMvc
        .perform(
            get("/api/documents/" + documentId + "/presign-download")
                .with(TestJwtFactory.memberJwt(ORG_B_ID, "user_tenant_b")))
        .andExpect(status().isNotFound());
  }

  @Test
  void uploadInitReturns404ForCrossTenantProject() throws Exception {
    // Tenant A tries to upload to tenant B's project
    mockMvc
        .perform(
            post("/api/projects/" + projectBId + "/documents/upload-init")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fileName": "cross-tenant.pdf", "contentType": "application/pdf", "size": 100}
                    """))
        .andExpect(status().isNotFound());
  }

  // --- Cancel upload ---

  @Test
  void shouldCancelPendingDocument() throws Exception {
    var initResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/documents/upload-init")
                    .with(TestJwtFactory.memberJwt(ORG_ID, "user_member"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "cancel-test.pdf", "contentType": "application/pdf", "size": 512}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var documentId = extractJsonField(initResult, "documentId");

    mockMvc
        .perform(
            delete("/api/documents/" + documentId + "/cancel")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_member")))
        .andExpect(status().isNoContent());

    // Document should no longer exist
    mockMvc
        .perform(
            get("/api/documents/" + documentId + "/presign-download")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_member")))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldReturn409WhenCancellingUploadedDocument() throws Exception {
    var initResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/documents/upload-init")
                    .with(TestJwtFactory.memberJwt(ORG_ID, "user_member"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "cancel-uploaded.pdf", "contentType": "application/pdf", "size": 512}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var documentId = extractJsonField(initResult, "documentId");

    // Confirm upload first
    mockMvc
        .perform(
            post("/api/documents/" + documentId + "/confirm")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_member")))
        .andExpect(status().isOk());

    // Cancel should fail with 409
    mockMvc
        .perform(
            delete("/api/documents/" + documentId + "/cancel")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_member")))
        .andExpect(status().isConflict());
  }

  @Test
  void shouldReturn404WhenCancellingNonexistentDocument() throws Exception {
    mockMvc
        .perform(
            delete("/api/documents/00000000-0000-0000-0000-000000000000/cancel")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_member")))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldReturn404WhenCancellingCrossTenantDocument() throws Exception {
    var initResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/documents/upload-init")
                    .with(TestJwtFactory.memberJwt(ORG_ID, "user_member"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "cross-cancel.pdf", "contentType": "application/pdf", "size": 100}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var documentId = extractJsonField(initResult, "documentId");

    // Tenant B cannot cancel tenant A's document
    mockMvc
        .perform(
            delete("/api/documents/" + documentId + "/cancel")
                .with(TestJwtFactory.memberJwt(ORG_B_ID, "user_tenant_b")))
        .andExpect(status().isNotFound());
  }

  // --- Validation ---

  @Test
  void shouldReject400WhenFileNameIsBlank() throws Exception {
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/documents/upload-init")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fileName": "", "contentType": "application/pdf", "size": 100}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReject400WhenContentTypeIsBlank() throws Exception {
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/documents/upload-init")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fileName": "test.pdf", "contentType": "", "size": 100}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReject400WhenSizeIsZero() throws Exception {
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/documents/upload-init")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fileName": "test.pdf", "contentType": "application/pdf", "size": 0}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturn404WhenConfirmingNonexistentDocument() throws Exception {
    mockMvc
        .perform(
            post("/api/documents/00000000-0000-0000-0000-000000000000/confirm")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_member")))
        .andExpect(status().isNotFound());
  }

  // --- Document access control ---

  @Test
  void nonMemberCannotInitiateUpload() throws Exception {
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/documents/upload-init")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_nonmember"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fileName": "denied.pdf", "contentType": "application/pdf", "size": 100}
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  void nonMemberCannotListDocuments() throws Exception {
    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/documents")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_nonmember")))
        .andExpect(status().isNotFound());
  }

  @Test
  void nonMemberCannotAccessDocumentById() throws Exception {
    // Owner uploads a document (owner has access as project lead)
    var initResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/documents/upload-init")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "access-test.pdf", "contentType": "application/pdf", "size": 100}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var documentId = extractJsonField(initResult, "documentId");

    // Non-member cannot confirm
    mockMvc
        .perform(
            post("/api/documents/" + documentId + "/confirm")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_nonmember")))
        .andExpect(status().isNotFound());

    // Non-member cannot download
    mockMvc
        .perform(
            get("/api/documents/" + documentId + "/presign-download")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_nonmember")))
        .andExpect(status().isNotFound());

    // Non-member cannot cancel
    mockMvc
        .perform(
            delete("/api/documents/" + documentId + "/cancel")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_nonmember")))
        .andExpect(status().isNotFound());
  }

  // --- Backward compatibility after V12 scope extension ---

  @Test
  void projectListingReturnsOnlyProjectScopedDocuments() throws Exception {
    // Upload a PROJECT-scoped document via the standard project upload-init
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/documents/upload-init")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fileName": "scope-compat.pdf", "contentType": "application/pdf", "size": 256}
                    """))
        .andExpect(status().isCreated());

    // Listing documents for the project should return documents with scope=PROJECT
    mockMvc
        .perform(
            get("/api/projects/" + projectId + "/documents")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_member")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].scope").value("PROJECT"))
        .andExpect(jsonPath("$[0].visibility").value("INTERNAL"));
  }

  @Test
  void confirmResponseIncludesScopeAndVisibilityFields() throws Exception {
    var initResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/documents/upload-init")
                    .with(TestJwtFactory.memberJwt(ORG_ID, "user_member"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "scope-fields.pdf", "contentType": "application/pdf", "size": 128}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var documentId = extractJsonField(initResult, "documentId");

    mockMvc
        .perform(
            post("/api/documents/" + documentId + "/confirm")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_member")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.scope").value("PROJECT"))
        .andExpect(jsonPath("$.customerId").isEmpty())
        .andExpect(jsonPath("$.visibility").value("INTERNAL"))
        .andExpect(jsonPath("$.status").value("UPLOADED"))
        .andExpect(jsonPath("$.projectId").value(projectId));
  }

  @Test
  void presignDownloadStillWorksAfterScopeExtension() throws Exception {
    var initResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/documents/upload-init")
                    .with(TestJwtFactory.memberJwt(ORG_ID, "user_member"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "compat-download.pdf", "contentType": "application/pdf", "size": 512}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var documentId = extractJsonField(initResult, "documentId");

    // Confirm
    mockMvc
        .perform(
            post("/api/documents/" + documentId + "/confirm")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_member")))
        .andExpect(status().isOk());

    // Presign-download should still work for PROJECT-scoped documents
    mockMvc
        .perform(
            get("/api/documents/" + documentId + "/presign-download")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_member")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.presignedUrl").exists())
        .andExpect(jsonPath("$.expiresInSeconds").value(3600));
  }

  // --- Helpers ---

  private String extractJsonField(MvcResult result, String field) throws Exception {
    String body = result.getResponse().getContentAsString();
    // Simple extraction: find "field":"value" pattern
    String search = "\"" + field + "\":\"";
    int start = body.indexOf(search) + search.length();
    int end = body.indexOf("\"", start);
    return body.substring(start, end);
  }
}
