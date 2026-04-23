package io.b2mash.b2b.b2bstrawman.project;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.member.ProjectMember;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
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
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectCustomFieldIntegrationTest {
  private static final String ORG_ID = "org_project_cf_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private ProjectMemberRepository projectMemberRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Project CF Test Org", null);
    memberIdOwner =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_pcf_owner", "pcf_owner@test.com", "Owner", "owner"));
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_pcf_admin", "pcf_admin@test.com", "Admin", "admin");

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create field definitions for PROJECT entity type
    createFieldDefinition("Court", "court", "TEXT", "PROJECT");
    createFieldDefinition("Is Urgent", "is_urgent", "BOOLEAN", "PROJECT");
    createFieldDefinition(
        "Priority Level",
        "priority_level",
        "DROPDOWN",
        "PROJECT",
        """
        [{"value":"low","label":"Low"},{"value":"medium","label":"Medium"},{"value":"high","label":"High"}]
        """);
  }

  @Test
  void shouldCreateProjectWithCustomFields() throws Exception {
    mockMvc
        .perform(
            post("/api/projects")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pcf_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "CF Project",
                      "description": "Test",
                      "customFields": {
                        "court": "High Court",
                        "is_urgent": true
                      }
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("CF Project"))
        .andExpect(jsonPath("$.customFields.court").value("High Court"))
        .andExpect(jsonPath("$.customFields.is_urgent").value(true));
  }

  @Test
  void shouldUpdateProjectCustomFields() throws Exception {
    // Create a project first
    var createResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pcf_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "Update CF Project", "description": "Test"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String id = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Update with custom fields
    mockMvc
        .perform(
            put("/api/projects/" + id)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pcf_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Update CF Project",
                      "description": "Test",
                      "customFields": {
                        "court": "Supreme Court",
                        "priority_level": "high"
                      }
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customFields.court").value("Supreme Court"))
        .andExpect(jsonPath("$.customFields.priority_level").value("high"));
  }

  @Test
  void shouldReturn400ForInvalidCustomFieldValue() throws Exception {
    mockMvc
        .perform(
            post("/api/projects")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pcf_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Bad CF Project",
                      "description": "Test",
                      "customFields": {
                        "is_urgent": "not-a-boolean"
                      }
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldFilterProjectsByCustomField() throws Exception {
    // Create project with specific custom field
    mockMvc
        .perform(
            post("/api/projects")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pcf_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Filterable Project",
                      "description": "Test",
                      "customFields": {
                        "court": "Magistrate Court"
                      }
                    }
                    """))
        .andExpect(status().isCreated());

    // Filter by custom field
    mockMvc
        .perform(
            get("/api/projects")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pcf_owner"))
                .param("customField[court]", "Magistrate Court"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.customFields.court == 'Magistrate Court')]").exists());
  }

  @Test
  void shouldApplyFieldGroupsToProject() throws Exception {
    // Create a field group first
    var groupResult =
        mockMvc
            .perform(
                post("/api/field-groups")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pcf_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "entityType": "PROJECT",
                          "name": "Project CF Test Group",
                          "sortOrder": 0
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String groupId = JsonPath.read(groupResult.getResponse().getContentAsString(), "$.id");

    // Create a project
    var projectResult =
        mockMvc
            .perform(
                post("/api/projects")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pcf_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "FG Project", "description": "Test"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String projectId = JsonPath.read(projectResult.getResponse().getContentAsString(), "$.id");

    // Apply field groups
    mockMvc
        .perform(
            put("/api/projects/" + projectId + "/field-groups")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pcf_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"appliedFieldGroups": ["%s"]}
                    """
                        .formatted(groupId)))
        .andExpect(status().isOk());
  }

  @Test
  void shouldUpdateProjectCustomFieldsWhenLinkedCustomerIsProspect() throws Exception {
    // GAP-L-35: Save Custom Fields on a matter ALREADY LINKED to a PROSPECT
    // customer must succeed. The frontend's "Save Custom Fields" flow GETs
    // the current project, then PUTs back the full body with customFields
    // patched in — re-sending the existing customerId unchanged. Prior
    // behaviour re-ran the CREATE_PROJECT guard on every PUT and blocked
    // the re-sent PROSPECT link. Fix: when the incoming customerId matches
    // the existing link, use UPDATE_CUSTOM_FIELDS (only OFFBOARDED blocks).
    //
    // Seed both the PROSPECT customer and a project pre-linked to it
    // directly via repositories, inside a tenant-scoped transaction, to
    // mirror the real bug (matter already exists with a PROSPECT link)
    // rather than inventing a new link at update time.
    var prospectCustomerIdHolder = new UUID[1];
    var projectIdHolder = new UUID[1];

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var customer =
                          TestCustomerFactory.createCustomerWithStatus(
                              "L-35 Prospect Client",
                              "l35_prospect@test.com",
                              memberIdOwner,
                              LifecycleStatus.PROSPECT);
                      customer = customerRepository.save(customer);
                      prospectCustomerIdHolder[0] = customer.getId();

                      // Pre-seed a project already linked to the PROSPECT
                      // customer. This models the state where the matter was
                      // created via a template/internal flow and the customer
                      // then drifted into (or started in) PROSPECT status.
                      var project =
                          new Project("L-35 Matter", "Matter for prospect client", memberIdOwner);
                      project.setCustomerId(customer.getId());
                      project = projectRepository.save(project);
                      projectIdHolder[0] = project.getId();

                      // Grant the API caller edit access on the seeded project.
                      projectMemberRepository.save(
                          new ProjectMember(
                              project.getId(), memberIdOwner, Roles.PROJECT_LEAD, null));
                    }));

    UUID prospectCustomerId = prospectCustomerIdHolder[0];
    UUID projectId = projectIdHolder[0];

    // Save Custom Fields — the full PUT body carries the existing (unchanged)
    // PROSPECT customerId, exactly like updateEntityCustomFieldsAction in the
    // frontend does (GET the project, spread it, then overwrite customFields).
    mockMvc
        .perform(
            put("/api/projects/" + projectId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pcf_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "L-35 Matter",
                      "description": "Matter for prospect client",
                      "customerId": "%s",
                      "customFields": {
                        "court": "High Court",
                        "is_urgent": true
                      }
                    }
                    """
                        .formatted(prospectCustomerId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customFields.court").value("High Court"))
        .andExpect(jsonPath("$.customFields.is_urgent").value(true))
        .andExpect(jsonPath("$.customerId").value(prospectCustomerId.toString()));
  }

  // --- Helpers ---

  private void createFieldDefinition(String name, String slug, String fieldType, String entityType)
      throws Exception {
    createFieldDefinition(name, slug, fieldType, entityType, null);
  }

  private void createFieldDefinition(
      String name, String slug, String fieldType, String entityType, String optionsJson)
      throws Exception {
    String optionsPart = optionsJson != null ? ", \"options\": " + optionsJson.trim() : "";
    String body =
        """
        {
          "entityType": "%s",
          "name": "%s",
          "fieldType": "%s",
          "required": false,
          "sortOrder": 0%s
        }
        """
            .formatted(entityType, name, fieldType, optionsPart);

    mockMvc
        .perform(
            post("/api/field-definitions")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_pcf_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());
  }
}
