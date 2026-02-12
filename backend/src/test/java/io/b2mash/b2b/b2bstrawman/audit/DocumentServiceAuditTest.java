package io.b2mash.b2b.b2bstrawman.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Integration tests verifying audit events produced by {@code DocumentService} operations. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentServiceAuditTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_doc_audit_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private AuditService auditService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String schemaName;
  private UUID projectId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Document Audit Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    schemaName =
        provisioningService.provisionTenant(ORG_ID, "Document Audit Test Org").schemaName();

    syncMember(ORG_ID, "user_doc_owner", "doc_owner@test.com", "Doc Owner", "owner");

    // Create a project for document tests
    var result =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Doc Audit Project", "description": "for doc tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    projectId = UUID.fromString(extractIdFromLocation(result));
  }

  @Test
  void initiateUploadProducesAuditEvent() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/documents/upload-init")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "audit-test.pdf", "contentType": "application/pdf", "size": 1024}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var documentId =
        UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.documentId"));

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "document", documentId, null, "document.created", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("document.created");
              assertThat(event.getEntityType()).isEqualTo("document");
              assertThat(event.getEntityId()).isEqualTo(documentId);
              assertThat(event.getDetails()).containsEntry("scope", "PROJECT");
              assertThat(event.getDetails()).containsEntry("file_name", "audit-test.pdf");
              assertThat(event.getActorType()).isEqualTo("USER");
              assertThat(event.getSource()).isEqualTo("API");
            });
  }

  @Test
  void confirmUploadProducesAuditEvent() throws Exception {
    // Initiate an upload
    var initResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/documents/upload-init")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "confirm-test.pdf", "contentType": "application/pdf", "size": 2048}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var documentId =
        UUID.fromString(
            JsonPath.read(initResult.getResponse().getContentAsString(), "$.documentId"));

    // Confirm the upload
    mockMvc
        .perform(post("/api/documents/" + documentId + "/confirm").with(ownerJwt()))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "document", documentId, null, "document.uploaded", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("document.uploaded");
              assertThat(event.getEntityId()).isEqualTo(documentId);
            });
  }

  @Test
  void cancelUploadProducesAuditEvent() throws Exception {
    // Initiate an upload
    var initResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/documents/upload-init")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "cancel-test.pdf", "contentType": "application/pdf", "size": 512}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var documentId =
        UUID.fromString(
            JsonPath.read(initResult.getResponse().getContentAsString(), "$.documentId"));

    // Cancel the upload
    mockMvc
        .perform(delete("/api/documents/" + documentId + "/cancel").with(ownerJwt()))
        .andExpect(status().isNoContent());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "document", documentId, null, "document.deleted", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("document.deleted");
              assertThat(event.getDetails()).containsEntry("file_name", "cancel-test.pdf");
            });
  }

  @Test
  void presignDownloadProducesAuditEvent() throws Exception {
    // Initiate and confirm an upload
    var initResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/documents/upload-init")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "download-test.pdf", "contentType": "application/pdf", "size": 4096}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var documentId =
        UUID.fromString(
            JsonPath.read(initResult.getResponse().getContentAsString(), "$.documentId"));

    // Confirm upload first
    mockMvc
        .perform(post("/api/documents/" + documentId + "/confirm").with(ownerJwt()))
        .andExpect(status().isOk());

    // Get presigned download URL
    mockMvc
        .perform(get("/api/documents/" + documentId + "/presign-download").with(ownerJwt()))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "document", documentId, null, "document.accessed", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("document.accessed");
              assertThat(event.getDetails()).containsEntry("scope", "PROJECT");
              assertThat(event.getDetails()).containsEntry("file_name", "download-test.pdf");
            });
  }

  @Test
  void toggleVisibilityProducesAuditEvent() throws Exception {
    // Initiate an upload
    var initResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/documents/upload-init")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "visibility-test.pdf", "contentType": "application/pdf", "size": 1024}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var documentId =
        UUID.fromString(
            JsonPath.read(initResult.getResponse().getContentAsString(), "$.documentId"));

    // Toggle visibility from INTERNAL to SHARED
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

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "document", documentId, null, "document.visibility_changed", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("document.visibility_changed");

              @SuppressWarnings("unchecked")
              var visibilityChange = (Map<String, Object>) event.getDetails().get("visibility");
              assertThat(visibilityChange).containsEntry("from", "INTERNAL");
              assertThat(visibilityChange).containsEntry("to", "SHARED");
            });
  }

  // --- Helpers ---

  private String extractIdFromLocation(MvcResult result) {
    String location = result.getResponse().getHeader("Location");
    return location.substring(location.lastIndexOf('/') + 1);
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
        .jwt(j -> j.subject("user_doc_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }
}
