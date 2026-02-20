package io.b2mash.b2b.b2bstrawman.customerbackend.event;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestChecklistHelper;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@RecordApplicationEvents
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalEventPublicationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_portal_event_pub_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private ApplicationEvents events;

  private String projectId;
  private String memberIdOwner;

  @BeforeAll
  void provisionTenantAndSeedData() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Portal Event Pub Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        syncMember(ORG_ID, "user_pep_owner", "pep_owner@test.com", "PEP Owner", "owner");

    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Portal Event Test Project", "description": "For portal event tests"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    projectId = extractIdFromLocation(projectResult);
  }

  @Test
  void createCustomer_publishesCustomerCreatedEvent() throws Exception {
    events.clear();

    mockMvc
        .perform(
            post("/api/customers")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Portal Event Customer", "email": "portal_event_create@test.com"}
                    """))
        .andExpect(status().isCreated());

    var createdEvents = events.stream(CustomerCreatedEvent.class).toList();
    assertThat(createdEvents).hasSize(1);

    var event = createdEvents.getFirst();
    assertThat(event.getName()).isEqualTo("Portal Event Customer");
    assertThat(event.getEmail()).isEqualTo("portal_event_create@test.com");
    assertThat(event.getOrgId()).isEqualTo(ORG_ID);
    assertThat(event.getTenantId()).isNotNull();
    assertThat(event.getOccurredAt()).isNotNull();
    assertThat(event.getCustomerId()).isNotNull();
  }

  @Test
  void updateProject_publishesProjectUpdatedEvent() throws Exception {
    events.clear();

    mockMvc
        .perform(
            put("/api/projects/" + projectId)
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Updated Portal Project", "description": "Updated description"}
                    """))
        .andExpect(status().isOk());

    var updatedEvents = events.stream(ProjectUpdatedEvent.class).toList();
    assertThat(updatedEvents).hasSize(1);

    var event = updatedEvents.getFirst();
    assertThat(event.getName()).isEqualTo("Updated Portal Project");
    assertThat(event.getDescription()).isEqualTo("Updated description");
    assertThat(event.getProjectId()).isEqualTo(UUID.fromString(projectId));
    assertThat(event.getOrgId()).isEqualTo(ORG_ID);
    assertThat(event.getTenantId()).isNotNull();
  }

  @Test
  void linkCustomerToProject_publishesCustomerProjectLinkedEvent() throws Exception {
    // Create a customer to link
    var customerResult =
        mockMvc
            .perform(
                post("/api/customers")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Link Test Customer", "email": "portal_event_link@test.com"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var customerId = extractIdFromLocation(customerResult);

    // Transition PROSPECT -> ONBOARDING -> ACTIVE so lifecycle guard permits linking
    transitionCustomerToActive(customerId);

    events.clear();

    mockMvc
        .perform(post("/api/customers/" + customerId + "/projects/" + projectId).with(ownerJwt()))
        .andExpect(status().isCreated());

    var linkedEvents = events.stream(CustomerProjectLinkedEvent.class).toList();
    assertThat(linkedEvents).hasSize(1);

    var event = linkedEvents.getFirst();
    assertThat(event.getCustomerId()).isEqualTo(UUID.fromString(customerId));
    assertThat(event.getProjectId()).isEqualTo(UUID.fromString(projectId));
    assertThat(event.getOrgId()).isEqualTo(ORG_ID);
    assertThat(event.getTenantId()).isNotNull();
  }

  @Test
  void confirmUpload_publishesDocumentCreatedEvent() throws Exception {
    var docInitResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/documents/upload-init")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "portal-event-test.pdf", "contentType": "application/pdf", "size": 2048}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var documentId = extractJsonField(docInitResult, "documentId");

    events.clear();

    mockMvc
        .perform(post("/api/documents/" + documentId + "/confirm").with(ownerJwt()))
        .andExpect(status().isOk());

    var createdEvents =
        events.stream(io.b2mash.b2b.b2bstrawman.customerbackend.event.DocumentCreatedEvent.class)
            .toList();
    assertThat(createdEvents).hasSize(1);

    var event = createdEvents.getFirst();
    assertThat(event.getDocumentId()).isEqualTo(UUID.fromString(documentId));
    assertThat(event.getFileName()).isEqualTo("portal-event-test.pdf");
    assertThat(event.getProjectId()).isEqualTo(UUID.fromString(projectId));
    assertThat(event.getOrgId()).isEqualTo(ORG_ID);
    assertThat(event.getTenantId()).isNotNull();
  }

  @Test
  void toggleVisibility_publishesDocumentVisibilityChangedEvent() throws Exception {
    // Create and confirm a document first
    var docInitResult =
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
    var documentId = extractJsonField(docInitResult, "documentId");

    mockMvc
        .perform(post("/api/documents/" + documentId + "/confirm").with(ownerJwt()))
        .andExpect(status().isOk());

    events.clear();

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

    var visibilityEvents = events.stream(DocumentVisibilityChangedEvent.class).toList();
    assertThat(visibilityEvents).hasSize(1);

    var event = visibilityEvents.getFirst();
    assertThat(event.getDocumentId()).isEqualTo(UUID.fromString(documentId));
    assertThat(event.getVisibility()).isEqualTo("SHARED");
    assertThat(event.getPreviousVisibility()).isEqualTo("INTERNAL");
    assertThat(event.getOrgId()).isEqualTo(ORG_ID);
    assertThat(event.getTenantId()).isNotNull();
  }

  @Test
  void cancelUpload_publishesDocumentDeletedEvent() throws Exception {
    var docInitResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/documents/upload-init")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fileName": "delete-test.pdf", "contentType": "application/pdf", "size": 512}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var documentId = extractJsonField(docInitResult, "documentId");

    events.clear();

    mockMvc
        .perform(delete("/api/documents/" + documentId + "/cancel").with(ownerJwt()))
        .andExpect(status().isNoContent());

    var deletedEvents = events.stream(DocumentDeletedEvent.class).toList();
    assertThat(deletedEvents).hasSize(1);

    var event = deletedEvents.getFirst();
    assertThat(event.getDocumentId()).isEqualTo(UUID.fromString(documentId));
    assertThat(event.getOrgId()).isEqualTo(ORG_ID);
    assertThat(event.getTenantId()).isNotNull();
  }

  // --- Helpers ---

  private String extractIdFromLocation(MvcResult result) {
    String location = result.getResponse().getHeader("Location");
    return location.substring(location.lastIndexOf('/') + 1);
  }

  private String extractJsonField(MvcResult result, String field) throws Exception {
    return JsonPath.read(result.getResponse().getContentAsString(), "$." + field).toString();
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

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_pep_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }
}
