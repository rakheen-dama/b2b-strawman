package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
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
class PortalResyncIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_resync_test";
  private static final String EMPTY_ORG_ID = "org_resync_empty";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private PortalResyncService resyncService;
  @Autowired private PortalReadModelRepository readModelRepo;

  private String projectId;
  private String project2Id;
  private String customerId;
  private String customer2Id;

  @BeforeAll
  void provisionAndSeedData() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Resync Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    syncMember(ORG_ID, "user_resync_owner", "resync_owner@test.com", "Resync Owner", "owner");

    // Provision a separate empty org (no projects, customers, or documents)
    provisioningService.provisionTenant(EMPTY_ORG_ID, "Resync Empty Org");
    planSyncService.syncPlan(EMPTY_ORG_ID, "pro-plan");

    // Create two projects
    projectId = createProject("Resync Project A", "First project for resync");
    project2Id = createProject("Resync Project B", "Second project for resync");

    // Create two customers
    customerId = createCustomer("Resync Customer A", "resync_a@test.com");
    customer2Id = createCustomer("Resync Customer B", "resync_b@test.com");

    // Link customer A to project A
    mockMvc
        .perform(post("/api/customers/" + customerId + "/projects/" + projectId).with(ownerJwt()))
        .andExpect(status().isCreated());

    // Link customer B to project B
    mockMvc
        .perform(post("/api/customers/" + customer2Id + "/projects/" + project2Id).with(ownerJwt()))
        .andExpect(status().isCreated());

    // Upload and confirm a document on project A, then make it SHARED
    var docId = uploadAndConfirmDocument(projectId);
    toggleVisibility(docId, "SHARED");
  }

  @Test
  void resyncEmptyOrgDoesNotError() {
    // Use a genuinely empty org (provisioned but no projects, customers, or documents)
    var result = resyncService.resyncOrg(EMPTY_ORG_ID);
    assertThat(result.projectsProjected()).isEqualTo(0);
    assertThat(result.documentsProjected()).isEqualTo(0);
  }

  @Test
  void resyncOrgWithProjectsAndDocuments() {
    var result = resyncService.resyncOrg(ORG_ID);

    assertThat(result.projectsProjected()).isGreaterThanOrEqualTo(2);

    // Verify portal projects exist after resync
    var projectsA = readModelRepo.findProjectsByCustomer(ORG_ID, UUID.fromString(customerId));
    assertThat(projectsA).isNotEmpty();
    assertThat(projectsA).anyMatch(p -> p.name().equals("Resync Project A"));

    var projectsB = readModelRepo.findProjectsByCustomer(ORG_ID, UUID.fromString(customer2Id));
    assertThat(projectsB).isNotEmpty();
    assertThat(projectsB).anyMatch(p -> p.name().equals("Resync Project B"));
  }

  @Test
  void resyncIsIdempotent() {
    var result1 = resyncService.resyncOrg(ORG_ID);
    var result2 = resyncService.resyncOrg(ORG_ID);

    // Both runs should produce the same result
    assertThat(result1.projectsProjected()).isEqualTo(result2.projectsProjected());
    assertThat(result1.documentsProjected()).isEqualTo(result2.documentsProjected());

    // Verify data is intact after double resync
    var projects = readModelRepo.findProjectsByCustomer(ORG_ID, UUID.fromString(customerId));
    assertThat(projects).isNotEmpty();
  }

  @Test
  void resyncRecreatesDataAfterManualDelete() {
    // First resync to ensure data exists
    resyncService.resyncOrg(ORG_ID);

    // Manually delete portal projects for this org
    readModelRepo.deletePortalDocumentsByOrg(ORG_ID);
    readModelRepo.deletePortalProjectsByOrg(ORG_ID);

    // Verify data is gone
    var projectsBefore = readModelRepo.findProjectsByCustomer(ORG_ID, UUID.fromString(customerId));
    assertThat(projectsBefore).isEmpty();

    // Resync should recreate the data
    var result = resyncService.resyncOrg(ORG_ID);
    assertThat(result.projectsProjected()).isGreaterThanOrEqualTo(1);

    var projectsAfter = readModelRepo.findProjectsByCustomer(ORG_ID, UUID.fromString(customerId));
    assertThat(projectsAfter).isNotEmpty();
  }

  @Test
  void resyncWithNonExistentOrgThrows() {
    assertThatThrownBy(() -> resyncService.resyncOrg("org_nonexistent_999"))
        .isInstanceOf(ResourceNotFoundException.class);
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
    completeChecklistItems(customerId, ownerJwt());
  }

  @SuppressWarnings("unchecked")
  private void completeChecklistItems(String customerId, JwtRequestPostProcessor jwt)
      throws Exception {
    var result =
        mockMvc
            .perform(get("/api/customers/" + customerId + "/checklists").with(jwt))
            .andExpect(status().isOk())
            .andReturn();
    String json = result.getResponse().getContentAsString();
    List<Map<String, Object>> instances = JsonPath.read(json, "$[*]");
    for (Map<String, Object> instance : instances) {
      List<Map<String, Object>> items = (List<Map<String, Object>>) instance.get("items");
      if (items == null) continue;
      for (Map<String, Object> item : items) {
        String itemId = (String) item.get("id");
        boolean requiresDocument = Boolean.TRUE.equals(item.get("requiresDocument"));
        if (requiresDocument) {
          mockMvc
              .perform(
                  put("/api/checklist-items/" + itemId + "/skip")
                      .with(jwt)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"reason\": \"skipped for test\"}"))
              .andExpect(status().isOk());
        } else {
          mockMvc
              .perform(
                  put("/api/checklist-items/" + itemId + "/complete")
                      .with(jwt)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"notes\": \"auto-completed for test\"}"))
              .andExpect(status().isOk());
        }
      }
    }
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
                        {"fileName": "resync-test.pdf", "contentType": "application/pdf", "size": 2048}
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
        .jwt(j -> j.subject("user_resync_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }
}
