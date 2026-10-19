package io.b2mash.b2b.b2bstrawman.project;

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
import io.b2mash.b2b.b2bstrawman.worktype.WorkTypes;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Work type is free text, but field-group auto-apply matches on it. Before the fix a matter created
 * with {@code "workType": "Conveyancing"} was stored verbatim and the {@code ["CONVEYANCING"]}-
 * scoped {@code conveyancing_za_matter} group was not applied. Every write path now stores the
 * canonical form ({@link WorkTypes#normalize}) and matching compares canonical forms.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkTypeNormalisationIntegrationTest {

  private static final String ORG_ID = "org_work_type_normalise_test";
  private static final String OWNER = "user_wtn_owner";
  private static final String CONVEYANCING_TEMPLATE = "Property Transfer (Conveyancing)";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private FieldGroupRepository fieldGroupRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  @Autowired
  @Qualifier("migrationDataSource")
  private DataSource migrationDataSource;

  private String conveyancingGroupId;
  private String legacyLowerCaseGroupId;
  private String conveyancingTemplateId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Work Type Normalise Test Org", "legal-za");
    TestMemberHelper.syncMember(mockMvc, ORG_ID, OWNER, "wtn_owner@test.com", "Owner", "owner");
    String tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

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

    // A hand-entered / legacy group whose scope was stored in lower case, bypassing the entity
    // setter — matching must still treat it as CONVEYANCING.
    try (Connection c = migrationDataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                """
                INSERT INTO "%s".field_groups
                  (entity_type, name, slug, auto_apply, active, applicable_work_types)
                VALUES ('PROJECT', 'Legacy lower-case conveyancing', 'wtn_legacy_lc_conveyancing',
                        true, true, '[" conveyancing "]'::jsonb)
                RETURNING id
                """
                    .formatted(tenantSchema))) {
      rs.next();
      legacyLowerCaseGroupId = rs.getString("id");
    }

    String templates =
        mockMvc
            .perform(get("/api/matter-templates").with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> ids =
        JsonPath.read(templates, "$[?(@.name == '" + CONVEYANCING_TEMPLATE + "')].id");
    conveyancingTemplateId = ids.getFirst();
  }

  @Test
  void createProjectWithMixedCaseWorkTypeStoresCanonicalAndAppliesConveyancingGroup()
      throws Exception {
    String body = postProjectWithWorkType("Erf 1 (mixed case)", "Conveyancing");

    assertThat((String) JsonPath.read(body, "$.workType")).isEqualTo("CONVEYANCING");
    List<String> groups = JsonPath.read(body, "$.appliedFieldGroups");
    assertThat(groups).contains(conveyancingGroupId, legacyLowerCaseGroupId);
  }

  @Test
  void instantiateTemplateWithLowerCaseWorkTypeStoresCanonicalAndAppliesConveyancingGroup()
      throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/matter-templates/" + conveyancingTemplateId + "/instantiate")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\": \"Erf 2 (template, lower case)\", \"workType\":"
                            + " \"conveyancing\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat((String) JsonPath.read(body, "$.workType")).isEqualTo("CONVEYANCING");
    List<String> groups = JsonPath.read(body, "$.appliedFieldGroups");
    assertThat(groups).contains(conveyancingGroupId, legacyLowerCaseGroupId);
  }

  @Test
  void updateProjectNormalisesWorkTypeAndOmittedWorkTypeKeepsIt() throws Exception {
    String id = JsonPath.read(postProjectWithWorkType("Erf 3 (update)", "COLLECTIONS"), "$.id");

    mockMvc
        .perform(
            put("/api/matters/" + id)
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Erf 3 (update)\", \"workType\": \" litigation \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workType").value("LITIGATION"));

    mockMvc
        .perform(
            put("/api/matters/" + id)
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Erf 3 (update)\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workType").value("LITIGATION"));
  }

  @Test
  void createProjectWithMultiWordWorkTypeStoresUnderscoredUpperCase() throws Exception {
    String body = postProjectWithWorkType("PI matter", " Personal   Injury ");

    assertThat((String) JsonPath.read(body, "$.workType")).isEqualTo("PERSONAL_INJURY");
    List<String> groups = JsonPath.read(body, "$.appliedFieldGroups");
    assertThat(groups).doesNotContain(conveyancingGroupId, legacyLowerCaseGroupId);
  }

  @Test
  void createProjectWithFiftyCharacterWorkTypeContainingSpacesSucceeds() throws Exception {
    String fifty = "a b".repeat(16) + "cc";
    String body = postProjectWithWorkType("Fifty chars", fifty);

    assertThat((String) JsonPath.read(body, "$.workType")).isEqualTo(WorkTypes.normalize(fifty));
  }

  @Test
  void createProjectWhoseWorkTypeOutgrowsFiftyCharactersWhenUpperCasedIsRejectedWith400()
      throws Exception {
    // 26 chars passes @Size(max = 50); upper-casing turns each "ß" into "SS" (52 chars), which
    // must be a 400 validation error, not a varchar(50) failure at the database.
    String expanding = "ß".repeat(26);
    mockMvc
        .perform(
            post("/api/matters")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Expanding\", \"workType\": \"%s\"}".formatted(expanding)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void projectTemplateCreateNormalisesWorkType() throws Exception {
    mockMvc
        .perform(
            post("/api/matter-templates")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "WTN Manual", "namePattern": "WTN {customer}",
                     "billableDefault": true, "tasks": [], "tagIds": [],
                     "workType": " estates "}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.workType").value("ESTATES"));
  }

  private String postProjectWithWorkType(String name, String workType) throws Exception {
    return mockMvc
        .perform(
            post("/api/matters")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"%s\", \"workType\": \"%s\"}".formatted(name, workType)))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }
}
