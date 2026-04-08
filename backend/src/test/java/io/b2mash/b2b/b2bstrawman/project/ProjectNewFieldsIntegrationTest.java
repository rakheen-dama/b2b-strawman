package io.b2mash.b2b.b2bstrawman.project;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectNewFieldsIntegrationTest {
  private static final String ORG_ID = "org_proj_new_fields";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Project New Fields Org", null);
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_pnf_owner", "pnf_owner@test.com", "PNF Owner", "owner");
  }

  @Test
  void createProjectWithAllNewFieldsReturns201() throws Exception {
    mockMvc
        .perform(
            post("/api/projects")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pnf_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Full Fields Project",
                      "description": "A project with all new fields",
                      "referenceNumber": "REF-001",
                      "priority": "HIGH",
                      "workType": "TAX_RETURN"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Full Fields Project"))
        .andExpect(jsonPath("$.referenceNumber").value("REF-001"))
        .andExpect(jsonPath("$.priority").value("HIGH"))
        .andExpect(jsonPath("$.workType").value("TAX_RETURN"));
  }

  @Test
  void createTaskWithEstimatedHoursReturns201() throws Exception {
    var projectId =
        TestEntityHelper.createProject(
            mockMvc, TestJwtFactory.ownerJwt(ORG_ID, "user_pnf_owner"), "Task Hours Project");

    mockMvc
        .perform(
            post("/api/projects/" + projectId + "/tasks")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pnf_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Task with hours",
                      "estimatedHours": 12.50
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("Task with hours"))
        .andExpect(jsonPath("$.estimatedHours").value(12.50));
  }

  @Test
  void updateProjectWorkTypeReturns200() throws Exception {
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pnf_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Update WorkType Project",
                          "workType": "AUDIT"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var projectId = TestEntityHelper.extractIdFromLocation(projectResult);

    mockMvc
        .perform(
            put("/api/projects/" + projectId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pnf_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Update WorkType Project",
                      "workType": "LITIGATION"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workType").value("LITIGATION"));
  }

  @Test
  void updateProjectWithoutNewFieldsRetainsExistingValues() throws Exception {
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pnf_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Retain Fields Project",
                          "referenceNumber": "REF-KEEP",
                          "priority": "MEDIUM",
                          "workType": "ADVISORY"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var projectId = TestEntityHelper.extractIdFromLocation(projectResult);

    // Update only name — new fields should be retained
    mockMvc
        .perform(
            put("/api/projects/" + projectId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pnf_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Retain Fields Project Renamed"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Retain Fields Project Renamed"))
        .andExpect(jsonPath("$.referenceNumber").value("REF-KEEP"))
        .andExpect(jsonPath("$.priority").value("MEDIUM"))
        .andExpect(jsonPath("$.workType").value("ADVISORY"));
  }
}
