package io.b2mash.b2b.b2bstrawman.projecttemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.HashMap;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Matters created from a project template inherit the template's work type. Before the fix the
 * legal-za pack's {@code matterType} was parsed but never persisted, so a matter created from
 * "Property Transfer (Conveyancing)" without an explicit work type got {@code work_type = NULL} and
 * the work-type-scoped {@code conveyancing_za_matter} field group never auto-applied.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectTemplateWorkTypeIntegrationTest {

  private static final String ORG_ID = "org_template_work_type_test";
  private static final String OWNER = "user_tmpl_wt_owner";
  private static final String CONVEYANCING_TEMPLATE = "Property Transfer (Conveyancing)";

  /** legal-za.json {@code matterType} per template name. */
  private static final Map<String, String> PACK_MATTER_TYPES =
      Map.of(
          "Litigation (Road Accident Fund -- RAF)",
          "LITIGATION",
          "Litigation (Personal Injury / General)",
          "LITIGATION",
          "Deceased Estate Administration",
          "ESTATES",
          "Collections (Debt Recovery)",
          "COLLECTIONS",
          "Commercial (Corporate & Contract)",
          "COMMERCIAL",
          CONVEYANCING_TEMPLATE,
          "CONVEYANCING");

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private FieldGroupRepository fieldGroupRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private String conveyancingTemplateId;
  private String conveyancingGroupId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Template Work Type Test Org", "legal-za");
    TestMemberHelper.syncMember(mockMvc, ORG_ID, OWNER, "tmpl_wt_owner@test.com", "Owner", "owner");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    String templates = listTemplates();
    List<String> ids =
        JsonPath.read(templates, "$[?(@.name == '" + CONVEYANCING_TEMPLATE + "')].id");
    assertThat(ids).as("seeded conveyancing template").hasSize(1);
    conveyancingTemplateId = ids.getFirst();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, UUID.randomUUID())
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                conveyancingGroupId =
                    transactionTemplate.execute(
                        tx ->
                            fieldGroupRepository
                                .findByEntityTypeAndSlug(
                                    EntityType.PROJECT, "conveyancing_za_matter")
                                .orElseThrow()
                                .getId()
                                .toString()));
  }

  @Test
  void seededTemplatesCarryTheirPackMatterType() throws Exception {
    String templates = listTemplates();
    List<Map<String, Object>> seeded = JsonPath.read(templates, "$[?(@.source == 'SEEDER')]");
    Map<String, Object> actual = new HashMap<>();
    seeded.forEach(t -> actual.put((String) t.get("name"), t.get("workType")));

    assertThat(actual).containsExactlyInAnyOrderEntriesOf(PACK_MATTER_TYPES);
  }

  @Test
  void instantiateWithoutWorkTypeInheritsTemplateWorkTypeAndConveyancingGroup() throws Exception {
    MvcResult result = instantiate("{\"name\": \"Transfer of Erf 123 (inherited)\"}");
    String body = result.getResponse().getContentAsString();

    assertThat((String) JsonPath.read(body, "$.workType")).isEqualTo("CONVEYANCING");
    List<String> groups = JsonPath.read(body, "$.appliedFieldGroups");
    assertThat(groups).contains(conveyancingGroupId);
  }

  @Test
  void explicitWorkTypeWinsOverTemplateWorkType() throws Exception {
    MvcResult result =
        instantiate("{\"name\": \"Transfer of Erf 456 (explicit)\", \"workType\": \"LITIGATION\"}");
    String body = result.getResponse().getContentAsString();

    assertThat((String) JsonPath.read(body, "$.workType")).isEqualTo("LITIGATION");
    List<String> groups = JsonPath.read(body, "$.appliedFieldGroups");
    assertThat(groups).doesNotContain(conveyancingGroupId);
  }

  @Test
  void blankWorkTypeFallsBackToTemplateWorkType() throws Exception {
    MvcResult result =
        instantiate("{\"name\": \"Transfer of Erf 789 (blank)\", \"workType\": \"  \"}");
    String body = result.getResponse().getContentAsString();

    assertThat((String) JsonPath.read(body, "$.workType")).isEqualTo("CONVEYANCING");
    List<String> groups = JsonPath.read(body, "$.appliedFieldGroups");
    assertThat(groups).contains(conveyancingGroupId);
  }

  @Test
  void createAndUpdateTemplateRoundTripWorkType() throws Exception {
    MvcResult created =
        mockMvc
            .perform(
                post("/api/project-templates")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name": "WT Manual", "namePattern": "WT {customer}",
                         "billableDefault": true, "tasks": [], "tagIds": [],
                         "workType": "COLLECTIONS"}
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.workType").value("COLLECTIONS"))
            .andReturn();
    String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            put("/api/project-templates/" + id)
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "WT Manual", "namePattern": "WT {customer}",
                     "billableDefault": true, "tasks": [], "tagIds": [],
                     "workType": "ESTATES"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workType").value("ESTATES"));

    mockMvc
        .perform(
            post("/api/project-templates/" + id + "/duplicate")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.workType").value("ESTATES"));

    mockMvc
        .perform(
            put("/api/project-templates/" + id)
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "WT Manual", "namePattern": "WT {customer}",
                     "billableDefault": true, "tasks": [], "tagIds": [], "workType": ""}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workType").doesNotExist());
  }

  @Test
  void createTemplateRejectsWorkTypeLongerThan50() throws Exception {
    mockMvc
        .perform(
            post("/api/project-templates")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "WT Too Long", "namePattern": "WT", "billableDefault": true,
                     "workType": "%s"}
                    """
                        .formatted("X".repeat(51))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void saveFromProjectCopiesProjectWorkType() throws Exception {
    MvcResult result =
        instantiate("{\"name\": \"Source matter for save-as\", \"workType\": \"COMMERCIAL\"}");
    String projectId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            post("/api/project-templates/from-project/" + projectId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Saved From Commercial", "namePattern": "{customer} - Commercial",
                     "taskIds": [], "tagIds": []}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.workType").value("COMMERCIAL"));
  }

  private String listTemplates() throws Exception {
    return mockMvc
        .perform(get("/api/project-templates").with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private MvcResult instantiate(String json) throws Exception {
    return mockMvc
        .perform(
            post("/api/project-templates/" + conveyancingTemplateId + "/instantiate")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isCreated())
        .andReturn();
  }
}
