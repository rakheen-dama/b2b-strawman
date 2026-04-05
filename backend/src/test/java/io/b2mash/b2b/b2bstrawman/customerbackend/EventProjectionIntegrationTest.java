package io.b2mash.b2b.b2bstrawman.customerbackend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestPostgresConfiguration;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestChecklistHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end integration tests that verify the event pipeline: staff API call -> domain event ->
 * PortalEventHandler -> portal read-model rows. These are the definitive tests proving the event
 * projection pipeline works.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestPostgresConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventProjectionIntegrationTest {
  private static final String ORG_ID = "org_eventproj_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PortalReadModelRepository readModelRepo;

  private String projectId;
  private String customerId;
  private String documentId;

  @BeforeAll
  void provisionAndSeedData() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Event Projection Test Org", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_ep_owner", "ep_owner@test.com", "EP Owner", "owner");

    // Create a project
    projectId =
        TestEntityHelper.createProject(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_ep_owner"),
            "Event Projection Project",
            "For event tests");

    // Create a customer and transition to ACTIVE (required for project linking)
    customerId =
        TestEntityHelper.createCustomer(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_ep_owner"),
            "Event Projection Customer",
            "ep_cust@test.com");
    transitionCustomerToActive(customerId);
  }

  @Test
  @Order(1)
  void linkCustomerToProjectCreatesPortalProject() throws Exception {
    // Link customer to project
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/projects/" + projectId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ep_owner")))
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
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ep_owner"))
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
        .perform(
            delete("/api/customers/" + customerId + "/projects/" + projectId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ep_owner")))
        .andExpect(status().isNoContent());

    // Verify portal_project row was removed
    var portalProjects = readModelRepo.findProjectsByCustomer(ORG_ID, UUID.fromString(customerId));
    assertThat(portalProjects).noneMatch(p -> p.id().equals(UUID.fromString(projectId)));
  }

  @Test
  @Order(6)
  void fullRoundTripStaffCreatesDataThenPortalReflects() throws Exception {
    // Create fresh project and customer
    var freshProjectId =
        TestEntityHelper.createProject(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_ep_owner"),
            "Round Trip Project",
            "Full test");
    var freshCustomerId =
        TestEntityHelper.createCustomer(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_ep_owner"),
            "Round Trip Customer",
            "roundtrip@test.com");
    transitionCustomerToActive(freshCustomerId);

    // Link customer to project
    mockMvc
        .perform(
            post("/api/customers/" + freshCustomerId + "/projects/" + freshProjectId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ep_owner")))
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

  private void transitionCustomerToActive(String customerId) throws Exception {
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ep_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\": \"ONBOARDING\"}"))
        .andExpect(status().isOk());
    // Completing all checklist items auto-transitions ONBOARDING -> ACTIVE
    TestChecklistHelper.completeChecklistItems(
        mockMvc, customerId, TestJwtFactory.ownerJwt(ORG_ID, "user_ep_owner"));
  }

  private String uploadAndConfirmDocument(String projectId) throws Exception {
    var initResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/documents/upload-init")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ep_owner"))
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
        .perform(
            post("/api/documents/" + docId + "/confirm")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ep_owner")))
        .andExpect(status().isOk());

    return docId;
  }

  private void toggleVisibility(String documentId, String visibility) throws Exception {
    mockMvc
        .perform(
            patch("/api/documents/" + documentId + "/visibility")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_ep_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"visibility": "%s"}
                    """
                        .formatted(visibility)))
        .andExpect(status().isOk());
  }
}
