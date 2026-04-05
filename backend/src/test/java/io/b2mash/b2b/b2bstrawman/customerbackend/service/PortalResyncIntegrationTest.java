package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestChecklistHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalResyncIntegrationTest {
  private static final String ORG_ID = "org_resync_test";
  private static final String EMPTY_ORG_ID = "org_resync_empty";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PortalResyncService resyncService;
  @Autowired private PortalReadModelRepository readModelRepo;

  private String projectId;
  private String project2Id;
  private String customerId;
  private String customer2Id;

  @BeforeAll
  void provisionAndSeedData() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Resync Test Org", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_resync_owner", "resync_owner@test.com", "Resync Owner", "owner");

    // Provision a separate empty org (no projects, customers, or documents)
    provisioningService.provisionTenant(EMPTY_ORG_ID, "Resync Empty Org", null);

    // Create two projects
    projectId =
        TestEntityHelper.createProject(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_resync_owner"),
            "Resync Project A",
            "First project for resync");
    project2Id =
        TestEntityHelper.createProject(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_resync_owner"),
            "Resync Project B",
            "Second project for resync");

    // Create two customers
    customerId =
        TestEntityHelper.createCustomer(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_resync_owner"),
            "Resync Customer A",
            "resync_a@test.com");
    transitionCustomerToActive(customerId);
    customer2Id =
        TestEntityHelper.createCustomer(
            mockMvc,
            TestJwtFactory.ownerJwt(ORG_ID, "user_resync_owner"),
            "Resync Customer B",
            "resync_b@test.com");
    transitionCustomerToActive(customer2Id);

    // Link customer A to project A
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/projects/" + projectId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_resync_owner")))
        .andExpect(status().isCreated());

    // Link customer B to project B
    mockMvc
        .perform(
            post("/api/customers/" + customer2Id + "/projects/" + project2Id)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_resync_owner")))
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
    assertThat(result.tasksProjected()).isEqualTo(0);
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
    assertThat(result1.tasksProjected()).isEqualTo(result2.tasksProjected());

    // Verify data is intact after double resync
    var projects = readModelRepo.findProjectsByCustomer(ORG_ID, UUID.fromString(customerId));
    assertThat(projects).isNotEmpty();
  }

  @Test
  void resyncRecreatesDataAfterManualDelete() {
    // First resync to ensure data exists
    resyncService.resyncOrg(ORG_ID);

    // Manually delete portal data for this org
    readModelRepo.deletePortalTasksByOrg(ORG_ID);
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

  private void transitionCustomerToActive(String customerId) throws Exception {
    mockMvc
        .perform(
            post("/api/customers/" + customerId + "/transition")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_resync_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\": \"ONBOARDING\"}"))
        .andExpect(status().isOk());
    // Completing all checklist items auto-transitions ONBOARDING -> ACTIVE
    TestChecklistHelper.completeChecklistItems(
        mockMvc, customerId, TestJwtFactory.ownerJwt(ORG_ID, "user_resync_owner"));
  }

  private String uploadAndConfirmDocument(String projectId) throws Exception {
    var initResult =
        mockMvc
            .perform(
                post("/api/projects/" + projectId + "/documents/upload-init")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_resync_owner"))
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
        .perform(
            post("/api/documents/" + docId + "/confirm")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_resync_owner")))
        .andExpect(status().isOk());

    return docId;
  }

  private void toggleVisibility(String documentId, String visibility) throws Exception {
    mockMvc
        .perform(
            patch("/api/documents/" + documentId + "/visibility")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_resync_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"visibility": "%s"}
                    """
                        .formatted(visibility)))
        .andExpect(status().isOk());
  }
}
