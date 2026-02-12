package io.b2mash.b2b.b2bstrawman.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests verifying security audit events: access denied (403), auth failed (401), and
 * customer-scoped document access logging.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.junit.jupiter.api.extension.ExtendWith(OutputCaptureExtension.class)
class SecurityAuditTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_sec_audit_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private AuditService auditService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;

  private String schemaName;
  private UUID projectId;
  private String memberIdStr;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Security Audit Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    schemaName =
        provisioningService.provisionTenant(ORG_ID, "Security Audit Test Org").schemaName();

    syncMember(ORG_ID, "user_sec_owner", "sec_owner@test.com", "Sec Owner", "owner");
    memberIdStr =
        syncMember(ORG_ID, "user_sec_member", "sec_member@test.com", "Sec Member", "member");

    // Create a project as owner
    var result =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Sec Audit Project", "description": "for security tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    projectId = UUID.fromString(extractIdFromLocation(result));

    // Add the member to the project with "member" role (not lead), so they can VIEW but NOT EDIT
    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/members")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"memberId": "%s", "projectRole": "member"}
                    """
                        .formatted(memberIdStr)))
        .andExpect(status().isCreated());
  }

  @Test
  void accessDenied_preAuthorize_producesAuditEvent() throws Exception {
    // Member tries to DELETE a project (requires ROLE_ORG_OWNER) -- triggers AccessDeniedException
    mockMvc
        .perform(delete("/api/projects/" + projectId).with(memberJwt()))
        .andExpect(status().isForbidden());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "security", null, null, "security.access_denied", null, null),
                      PageRequest.of(0, 50));

              // Find an event matching this specific path
              var matchingEvents =
                  page.getContent().stream()
                      .filter(
                          e -> {
                            var details = e.getDetails();
                            return details != null
                                && ("/api/projects/" + projectId).equals(details.get("path"))
                                && "DELETE".equals(details.get("method"));
                          })
                      .toList();

              assertThat(matchingEvents).hasSize(1);
              var event = matchingEvents.getFirst();
              assertThat(event.getEventType()).isEqualTo("security.access_denied");
              assertThat(event.getEntityType()).isEqualTo("security");
              assertThat(event.getDetails()).containsEntry("reason", "insufficient_role");
              assertThat(event.getSource()).isEqualTo("API");
            });
  }

  @Test
  void accessDenied_forbiddenException_producesAuditEvent() throws Exception {
    // Member can view the project but NOT edit it (not a lead).
    // PUT update triggers ForbiddenException from ProjectAccessService.requireEditAccess()
    mockMvc
        .perform(
            put("/api/projects/" + projectId)
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Hacked Name", "description": "should fail"}
                    """))
        .andExpect(status().isForbidden());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "security", null, null, "security.access_denied", null, null),
                      PageRequest.of(0, 50));

              var matchingEvents =
                  page.getContent().stream()
                      .filter(
                          e -> {
                            var details = e.getDetails();
                            return details != null
                                && ("/api/projects/" + projectId).equals(details.get("path"))
                                && "PUT".equals(details.get("method"));
                          })
                      .toList();

              assertThat(matchingEvents).hasSize(1);
              var event = matchingEvents.getFirst();
              assertThat(event.getEventType()).isEqualTo("security.access_denied");
              assertThat(event.getEntityType()).isEqualTo("security");
              assertThat(event.getDetails())
                  .containsKey("reason"); // ForbiddenException detail message
              assertThat(event.getSource()).isEqualTo("API");
            });
  }

  @Test
  void authFailed_noJwt_logsWarning(CapturedOutput output) throws Exception {
    // Unauthenticated request -- triggers AuditAuthenticationEntryPoint log.warn()
    mockMvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());

    assertThat(output.getOut() + output.getErr()).contains("security.auth_failed");
    assertThat(output.getOut() + output.getErr()).contains("/api/projects");
  }

  @Test
  void authFailed_notLoggedOnValidJwt(CapturedOutput output) throws Exception {
    // Authenticated request -- should NOT trigger auth failure logging
    mockMvc.perform(get("/api/projects").with(ownerJwt())).andExpect(status().isOk());

    assertThat(output.getOut() + output.getErr()).doesNotContain("security.auth_failed");
  }

  @Test
  void customerDocDownload_producesSecurityEvent() throws Exception {
    // Create a customer
    var customerResult =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Security Test Customer", "email": "sec-cust@test.com"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var customerId = UUID.fromString(extractIdFromLocation(customerResult));

    // Initiate customer-scoped document upload
    var initResult =
        mockMvc
            .perform(
                post("/api/customers/" + customerId + "/documents/upload-init")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "sec-customer-doc.pdf", "contentType": "application/pdf", "size": 2048}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var documentId =
        UUID.fromString(
            JsonPath.read(initResult.getResponse().getContentAsString(), "$.documentId"));

    // Confirm upload
    mockMvc
        .perform(post("/api/documents/" + documentId + "/confirm").with(ownerJwt()))
        .andExpect(status().isOk());

    // Download the customer-scoped document
    mockMvc
        .perform(get("/api/documents/" + documentId + "/presign-download").with(ownerJwt()))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var page =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "document", documentId, null, "security.document_accessed", null, null),
                      PageRequest.of(0, 10));

              assertThat(page.getTotalElements()).isEqualTo(1);
              var event = page.getContent().getFirst();
              assertThat(event.getEventType()).isEqualTo("security.document_accessed");
              assertThat(event.getEntityType()).isEqualTo("document");
              assertThat(event.getEntityId()).isEqualTo(documentId);
              assertThat(event.getDetails()).containsEntry("scope", "CUSTOMER");
              assertThat(event.getDetails()).containsEntry("customer_id", customerId.toString());
              assertThat(event.getDetails()).containsEntry("file_name", "sec-customer-doc.pdf");
            });
  }

  @Test
  void projectDocDownload_noSecurityEvent() throws Exception {
    // Initiate project-scoped document upload
    var initResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/documents/upload-init")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "proj-no-sec.pdf", "contentType": "application/pdf", "size": 1024}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    var documentId =
        UUID.fromString(
            JsonPath.read(initResult.getResponse().getContentAsString(), "$.documentId"));

    // Confirm upload
    mockMvc
        .perform(post("/api/documents/" + documentId + "/confirm").with(ownerJwt()))
        .andExpect(status().isOk());

    // Download the project-scoped document
    mockMvc
        .perform(get("/api/documents/" + documentId + "/presign-download").with(ownerJwt()))
        .andExpect(status().isOk());

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              // security.document_accessed should NOT be produced for project-scoped docs
              var secPage =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "document", documentId, null, "security.document_accessed", null, null),
                      PageRequest.of(0, 10));

              assertThat(secPage.getTotalElements()).isZero();

              // But the regular document.accessed domain event should still exist
              var domainPage =
                  auditService.findEvents(
                      new AuditEventFilter(
                          "document", documentId, null, "document.accessed", null, null),
                      PageRequest.of(0, 10));

              assertThat(domainPage.getTotalElements()).isEqualTo(1);
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
        .jwt(j -> j.subject("user_sec_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_sec_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }
}
