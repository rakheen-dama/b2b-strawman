package io.b2mash.b2b.b2bstrawman.customerbackend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestChecklistHelper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
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

/**
 * End-to-end integration tests that verify the event pipeline: staff API call -> domain event ->
 * PortalEventHandler -> portal read-model rows. These are the definitive tests proving the event
 * projection pipeline works.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventProjectionIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_eventproj_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private PortalReadModelRepository readModelRepo;

  private String projectId;
  private String customerId;
  private String documentId;

  @BeforeAll
  void provisionAndSeedData() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Event Projection Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    syncMember(ORG_ID, "user_ep_owner", "ep_owner@test.com", "EP Owner", "owner");

    // Create a project
    projectId = createProject("Event Projection Project", "For event tests");

    // Create a customer
    customerId = createCustomer("Event Projection Customer", "ep_cust@test.com");
  }

  @Test
  @Order(1)
  void linkCustomerToProjectCreatesPortalProject() throws Exception {
    // Link customer to project
    mockMvc
        .perform(post("/api/customers/" + customerId + "/projects/" + projectId).with(ownerJwt()))
        .andExpect(status().isCreated());

    // Verify portal_project row was created by the event handler
    var portalProjects = readModelRepo.findProjectsByCustomer(ORG_ID, UUID.fromString(customerId));
    assertThat(portalProjects).isNotEmpty();
    assertThat(portalProjects)
        .anyMatch(
            p ->
                p.id().equals(UUID.fromString(projectId))
                    && p.name().equals("Event Projection Project"));
  }

  @Test
  @Order(2)
  void uploadSharedDocumentCreatesPortalDocument() throws Exception {
    // Upload and confirm a document on the project
    documentId = uploadAndConfirmDocument(projectId);

    // Initial visibility is INTERNAL, so the handler should NOT project
    var docsBeforeToggle = readModelRepo.findDocumentsByProject(UUID.fromString(projectId), ORG_ID);
    assertThat(docsBeforeToggle).noneMatch(d -> d.id().equals(UUID.fromString(documentId)));

    // Toggle visibility to SHARED
    toggleVisibility(documentId, "SHARED");

    // Now the handler should have projected the document
    var docsAfterToggle = readModelRepo.findDocumentsByProject(UUID.fromString(projectId), ORG_ID);
    assertThat(docsAfterToggle).anyMatch(d -> d.id().equals(UUID.fromString(documentId)));

    // Verify document_count was incremented
    var project =
        readModelRepo.findProjectDetail(
            UUID.fromString(projectId), UUID.fromString(customerId), ORG_ID);
    assertThat(project).isPresent();
    assertThat(project.get().documentCount()).isGreaterThanOrEqualTo(1);
  }

  @Test
  @Order(3)
  void toggleVisibilityToInternalRemovesPortalDocument() throws Exception {
    // Toggle visibility from SHARED back to INTERNAL
    toggleVisibility(documentId, "INTERNAL");

    // The handler should have removed the portal document
    var docs = readModelRepo.findDocumentsByProject(UUID.fromString(projectId), ORG_ID);
    assertThat(docs).noneMatch(d -> d.id().equals(UUID.fromString(documentId)));

    // Verify document_count was decremented
    var project =
        readModelRepo.findProjectDetail(
            UUID.fromString(projectId), UUID.fromString(customerId), ORG_ID);
    assertThat(project).isPresent();
    assertThat(project.get().documentCount()).isEqualTo(0);
  }

  @Test
  @Order(4)
  void updateProjectNameUpdatesPortalProject() throws Exception {
    // Update the project name
    mockMvc
        .perform(
            put("/api/projects/" + projectId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Updated EP Project", "description": "Updated description"}
                    """))
        .andExpect(status().isOk());

    // Verify portal project name was updated
    var portalProjects = readModelRepo.findProjectsByCustomer(ORG_ID, UUID.fromString(customerId));
    assertThat(portalProjects)
        .anyMatch(
            p ->
                p.id().equals(UUID.fromString(projectId)) && p.name().equals("Updated EP Project"));
  }

  @Test
  @Order(5)
  void unlinkCustomerFromProjectRemovesPortalProject() throws Exception {
    // Unlink customer from project
    mockMvc
        .perform(delete("/api/customers/" + customerId + "/projects/" + projectId).with(ownerJwt()))
        .andExpect(status().isNoContent());

    // Verify portal_project row was removed
    var portalProjects = readModelRepo.findProjectsByCustomer(ORG_ID, UUID.fromString(customerId));
    assertThat(portalProjects).noneMatch(p -> p.id().equals(UUID.fromString(projectId)));
  }

  @Test
  @Order(6)
  void fullRoundTripStaffCreatesDataThenPortalReflects() throws Exception {
    // Create fresh project and customer
    var freshProjectId = createProject("Round Trip Project", "Full test");
    var freshCustomerId = createCustomer("Round Trip Customer", "roundtrip@test.com");

    // Link customer to project
    mockMvc
        .perform(
            post("/api/customers/" + freshCustomerId + "/projects/" + freshProjectId)
                .with(ownerJwt()))
        .andExpect(status().isCreated());

    // Upload and share a document
    var freshDocId = uploadAndConfirmDocument(freshProjectId);
    toggleVisibility(freshDocId, "SHARED");

    // Verify full portal state
    var portalProjects =
        readModelRepo.findProjectsByCustomer(ORG_ID, UUID.fromString(freshCustomerId));
    assertThat(portalProjects).hasSize(1);
    assertThat(portalProjects.getFirst().name()).isEqualTo("Round Trip Project");
    assertThat(portalProjects.getFirst().documentCount()).isEqualTo(1);

    var portalDocs = readModelRepo.findDocumentsByProject(UUID.fromString(freshProjectId), ORG_ID);
    assertThat(portalDocs).hasSize(1);
    assertThat(portalDocs.getFirst().id()).isEqualTo(UUID.fromString(freshDocId));
  }

  // --- Helpers ---

  private String createProject(String name, String description) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "%s", "description": "%s"}
                        """
                            .formatted(name, description)))
            .andExpect(status().isCreated())
            .andReturn();
    return extractIdFromLocation(result);
  }

  private String createCustomer(String name, String email) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "%s", "email": "%s"}
                        """
                            .formatted(name, email)))
            .andExpect(status().isCreated())
            .andReturn();
    var customerId = extractIdFromLocation(result);
    transitionCustomerToActive(customerId);
    return customerId;
  }

  private void transitionCustomerToActive(String customerId) throws Exception {
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\": \"ONBOARDING\"}"))
        .andExpect(status().isOk());
    // Completing all checklist items auto-transitions ONBOARDING -> ACTIVE
    TestChecklistHelper.completeChecklistItems(mockMvc, customerId, ownerJwt());
  }

  private String uploadAndConfirmDocument(String projectId) throws Exception {
    var initResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/documents/upload-init")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "ep-test.pdf", "contentType": "application/pdf", "size": 1024}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String docId =
        JsonPath.read(initResult.getResponse().getContentAsString(), "$.documentId").toString();

    mockMvc
        .perform(post("/api/documents/" + docId + "/confirm").with(ownerJwt()))
        .andExpect(status().isOk());

    return docId;
  }

  private void toggleVisibility(String documentId, String visibility) throws Exception {
    mockMvc
        .perform(
            patch("/api/documents/" + documentId + "/visibility")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"visibility": "%s"}
                    """
                        .formatted(visibility)))
        .andExpect(status().isOk());
  }

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
        .jwt(j -> j.subject("user_ep_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }
}
