package io.b2mash.b2b.b2bstrawman.document;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentScopeIntegrationTest {

  private static final String ORG_ID = "org_doc_scope_test";
  private static final String API_KEY = "test-api-key";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String customerId;

  @BeforeAll
  void provisionTenantAndCustomer() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Doc Scope Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    // Sync members
    syncMember(ORG_ID, "user_scope_owner", "scope_owner@test.com", "Owner", "owner");
    syncMember(ORG_ID, "user_scope_admin", "scope_admin@test.com", "Admin", "admin");
    syncMember(ORG_ID, "user_scope_member", "scope_member@test.com", "Member", "member");

    // Create a customer for customer-scoped document tests
    var customerResult =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Test Customer", "email": "customer@scope-test.com"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    customerId = extractJsonField(customerResult, "id");
  }

  // --- ORG-scoped upload-init ---

  @Test
  void shouldInitiateOrgScopedUpload() throws Exception {
    mockMvc
        .perform(
            post("/api/documents/upload-init")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fileName": "org-doc.pdf", "contentType": "application/pdf", "size": 1024}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.documentId").exists())
        .andExpect(jsonPath("$.presignedUrl").exists())
        .andExpect(jsonPath("$.expiresInSeconds").value(3600));
  }

  @Test
  void adminCanInitiateOrgScopedUpload() throws Exception {
    mockMvc
        .perform(
            post("/api/documents/upload-init")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fileName": "admin-org-doc.pdf", "contentType": "application/pdf", "size": 512}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.documentId").exists());
  }

  @Test
  void memberCannotInitiateOrgScopedUpload() throws Exception {
    mockMvc
        .perform(
            post("/api/documents/upload-init")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fileName": "member-org-doc.pdf", "contentType": "application/pdf", "size": 100}
                    """))
        .andExpect(status().isForbidden());
  }

  // --- CUSTOMER-scoped upload-init ---

  @Test
  void shouldInitiateCustomerScopedUpload() throws Exception {
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/documents/upload-init")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fileName": "customer-doc.pdf", "contentType": "application/pdf", "size": 2048}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.documentId").exists())
        .andExpect(jsonPath("$.presignedUrl").exists())
        .andExpect(jsonPath("$.expiresInSeconds").value(3600));
  }

  @Test
  void memberCannotInitiateCustomerScopedUpload() throws Exception {
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/documents/upload-init")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fileName": "member-cust-doc.pdf", "contentType": "application/pdf", "size": 100}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void customerScopedUploadReturns404ForNonexistentCustomer() throws Exception {
    mockMvc
        .perform(
            post("/api/customers/00000000-0000-0000-0000-000000000000/documents/upload-init")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fileName": "no-customer.pdf", "contentType": "application/pdf", "size": 100}
                    """))
        .andExpect(status().isNotFound());
  }

  // --- ORG-scoped document listing ---

  @Test
  void shouldListOrgScopedDocuments() throws Exception {
    // Upload an ORG-scoped document
    mockMvc
        .perform(
            post("/api/documents/upload-init")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fileName": "org-list-test.pdf", "contentType": "application/pdf", "size": 256}
                    """))
        .andExpect(status().isCreated());

    // List ORG-scoped documents
    mockMvc
        .perform(get("/api/documents?scope=ORG").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].scope").value("ORG"));
  }

  @Test
  void memberCanListOrgScopedDocuments() throws Exception {
    mockMvc
        .perform(get("/api/documents?scope=ORG").with(memberJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  // --- CUSTOMER-scoped document listing ---

  @Test
  void shouldListCustomerScopedDocuments() throws Exception {
    // Upload a CUSTOMER-scoped document
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/documents/upload-init")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fileName": "cust-list-test.pdf", "contentType": "application/pdf", "size": 128}
                    """))
        .andExpect(status().isCreated());

    // List CUSTOMER-scoped documents
    mockMvc
        .perform(get("/api/documents?scope=CUSTOMER&customerId=" + customerId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].scope").value("CUSTOMER"))
        .andExpect(jsonPath("$[0].customerId").value(customerId));
  }

  @Test
  void customerScopedListingRequiresCustomerId() throws Exception {
    mockMvc
        .perform(get("/api/documents?scope=CUSTOMER").with(ownerJwt()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void invalidScopeReturnsBadRequest() throws Exception {
    mockMvc
        .perform(get("/api/documents?scope=INVALID").with(ownerJwt()))
        .andExpect(status().isBadRequest());
  }

  // --- Visibility toggle ---

  @Test
  void shouldToggleVisibilityToShared() throws Exception {
    // Upload an ORG-scoped document
    var initResult =
        mockMvc
            .perform(
                post("/api/documents/upload-init")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "visibility-test.pdf", "contentType": "application/pdf", "size": 512}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var documentId = extractJsonField(initResult, "documentId");

    // Toggle visibility to SHARED
    mockMvc
        .perform(
            patch("/api/documents/" + documentId + "/visibility")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"visibility": "SHARED"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.visibility").value("SHARED"))
        .andExpect(jsonPath("$.id").value(documentId));
  }

  @Test
  void shouldToggleVisibilityBackToInternal() throws Exception {
    // Upload and set to SHARED
    var initResult =
        mockMvc
            .perform(
                post("/api/documents/upload-init")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "toggle-back.pdf", "contentType": "application/pdf", "size": 128}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var documentId = extractJsonField(initResult, "documentId");

    mockMvc
        .perform(
            patch("/api/documents/" + documentId + "/visibility")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"visibility": "SHARED"}
                    """))
        .andExpect(status().isOk());

    // Toggle back to INTERNAL
    mockMvc
        .perform(
            patch("/api/documents/" + documentId + "/visibility")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"visibility": "INTERNAL"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.visibility").value("INTERNAL"));
  }

  @Test
  void memberCannotToggleVisibility() throws Exception {
    var initResult =
        mockMvc
            .perform(
                post("/api/documents/upload-init")
                    .with(adminJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "member-vis.pdf", "contentType": "application/pdf", "size": 64}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var documentId = extractJsonField(initResult, "documentId");

    mockMvc
        .perform(
            patch("/api/documents/" + documentId + "/visibility")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"visibility": "SHARED"}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void invalidVisibilityReturnsBadRequest() throws Exception {
    var initResult =
        mockMvc
            .perform(
                post("/api/documents/upload-init")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "bad-vis.pdf", "contentType": "application/pdf", "size": 64}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var documentId = extractJsonField(initResult, "documentId");

    mockMvc
        .perform(
            patch("/api/documents/" + documentId + "/visibility")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"visibility": "PUBLIC"}
                    """))
        .andExpect(status().isBadRequest());
  }

  // --- Confirm flow for non-project docs ---

  @Test
  void shouldConfirmOrgScopedDocument() throws Exception {
    var initResult =
        mockMvc
            .perform(
                post("/api/documents/upload-init")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "confirm-org.pdf", "contentType": "application/pdf", "size": 256}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var documentId = extractJsonField(initResult, "documentId");

    mockMvc
        .perform(post("/api/documents/" + documentId + "/confirm").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UPLOADED"))
        .andExpect(jsonPath("$.scope").value("ORG"))
        .andExpect(jsonPath("$.projectId").isEmpty());
  }

  @Test
  void shouldConfirmCustomerScopedDocument() throws Exception {
    var initResult =
        mockMvc
            .perform(
                post("/api/customers/" + customerId + "/documents/upload-init")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "confirm-cust.pdf", "contentType": "application/pdf", "size": 256}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var documentId = extractJsonField(initResult, "documentId");

    mockMvc
        .perform(post("/api/documents/" + documentId + "/confirm").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UPLOADED"))
        .andExpect(jsonPath("$.scope").value("CUSTOMER"))
        .andExpect(jsonPath("$.customerId").value(customerId));
  }

  // --- S3 key format verification ---

  @Test
  void orgScopedDocumentHasCorrectS3KeyPrefix() throws Exception {
    var initResult =
        mockMvc
            .perform(
                post("/api/documents/upload-init")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "s3-key-org.pdf", "contentType": "application/pdf", "size": 64}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var documentId = extractJsonField(initResult, "documentId");
    var presignedUrl = extractJsonField(initResult, "presignedUrl");

    // Presigned URL should contain org-docs path
    org.assertj.core.api.Assertions.assertThat(presignedUrl).contains("/org-docs/");
  }

  @Test
  void customerScopedDocumentHasCorrectS3KeyPrefix() throws Exception {
    var initResult =
        mockMvc
            .perform(
                post("/api/customers/" + customerId + "/documents/upload-init")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "s3-key-cust.pdf", "contentType": "application/pdf", "size": 64}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var presignedUrl = extractJsonField(initResult, "presignedUrl");

    // Presigned URL should contain customer path
    org.assertj.core.api.Assertions.assertThat(presignedUrl).contains("/customer/");
  }

  // --- Helpers ---

  private String extractJsonField(MvcResult result, String field) throws Exception {
    String body = result.getResponse().getContentAsString();
    String search = "\"" + field + "\":\"";
    int start = body.indexOf(search) + search.length();
    int end = body.indexOf("\"", start);
    return body.substring(start, end);
  }

  private String syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "%s",
                          "email": "%s",
                          "name": "%s",
                          "avatarUrl": null,
                          "orgRole": "%s"
                        }
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();

    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_scope_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor adminJwt() {
    return jwt()
        .jwt(j -> j.subject("user_scope_admin").claim("o", Map.of("id", ORG_ID, "rol", "admin")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_scope_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }
}
