package io.b2mash.b2b.b2bstrawman.crm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleService;
import io.b2mash.b2b.b2bstrawman.orgrole.dto.OrgRoleDtos.CreateOrgRoleRequest;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for {@link PipelineStageController} (Phase 80, slice 578B). Drives the HTTP
 * layer end-to-end against a provisioned tenant whose pipeline is seeded with the default pack (3
 * OPEN, 1 WON, 1 LOST). Covers CRUD, reorder, archive, the last-active-of-type guard (400),
 * delete-with-deals (409), and capability gating (403).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PipelineStageControllerIntegrationTest {

  private static final String ORG_ID = "org_pipeline_stage_test";
  private static final String OWNER_SUBJECT = "user_pipeline_owner";
  private static final String VIEWER_SUBJECT = "user_pipeline_viewer"; // VIEW_DEALS only

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgRoleService orgRoleService;
  @Autowired private OrgRoleRepository orgRoleRepository;
  @Autowired private MemberRepository memberRepository;

  private String tenantSchema;
  private String customerId;
  private String seededWonStageId;

  private JwtRequestPostProcessor owner() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUBJECT);
  }

  private JwtRequestPostProcessor viewer() {
    return TestJwtFactory.memberJwt(ORG_ID, VIEWER_SUBJECT);
  }

  @BeforeAll
  void provisionTenantAndSeedData() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Pipeline Stage Test Org", null);
    String ownerMemberId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, OWNER_SUBJECT, "pipeline_owner@test.com", "Pipeline Owner", "owner");
    UUID viewerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, VIEWER_SUBJECT, "pipeline_viewer@test.com", "Viewer", "member"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    customerId =
        TestEntityHelper.createCustomer(mockMvc, owner(), "PipelineCo", "pipelineco@test.com");

    // Give the viewer a custom role with VIEW_DEALS but NOT MANAGE_PIPELINE — used for the 403
    // test.
    UUID ownerUuid = UUID.fromString(ownerMemberId);
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerUuid)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var viewerRole =
                  orgRoleService.createRole(
                      new CreateOrgRoleRequest(
                          "Pipeline Viewer", "Can view deals only", Set.of("VIEW_DEALS")));
              var viewer = memberRepository.findById(viewerMemberId).orElseThrow();
              viewer.setOrgRoleEntity(orgRoleRepository.findById(viewerRole.id()).orElseThrow());
              memberRepository.save(viewer);
            });

    seededWonStageId = stageIdOfType("WON");
  }

  /** Reads the seeded stages via the GET endpoint and returns the first id of the given type. */
  private String stageIdOfType(String type) throws Exception {
    var body =
        mockMvc
            .perform(get("/api/pipeline/stages").with(owner()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    java.util.List<String> ids = JsonPath.read(body, "$[?(@.stageType=='" + type + "')].id");
    return ids.getFirst();
  }

  private String createStage(String name, int position, int prob, String stageType)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/pipeline/stages")
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"%s","position":%d,"defaultProbabilityPct":%d,"stageType":"%s"}
                        """
                            .formatted(name, position, prob, stageType)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  // 1
  @Test
  void listStages_returns200WithSeededStages() throws Exception {
    mockMvc
        .perform(get("/api/pipeline/stages").with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].id").exists())
        .andExpect(jsonPath("$[0].name").isString())
        .andExpect(jsonPath("$[0].stageType").isString());
  }

  // 2
  @Test
  void createStage_returns201WithLocationAndBody() throws Exception {
    mockMvc
        .perform(
            post("/api/pipeline/stages")
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Discovery","position":1,"defaultProbabilityPct":25,"stageType":"OPEN"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string("Location", org.hamcrest.Matchers.startsWith("/api/pipeline/stages/")))
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.name").value("Discovery"))
        .andExpect(jsonPath("$.position").value(1))
        .andExpect(jsonPath("$.defaultProbabilityPct").value(25))
        .andExpect(jsonPath("$.stageType").value("OPEN"))
        .andExpect(jsonPath("$.archived").value(false));
  }

  // 3
  @Test
  void updateStage_changesNameProbabilityType_andReGetConfirms() throws Exception {
    String id = createStage("Editable", 7, 30, "OPEN");
    mockMvc
        .perform(
            put("/api/pipeline/stages/" + id)
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Renamed","defaultProbabilityPct":55,"stageType":"OPEN"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Renamed"))
        .andExpect(jsonPath("$.defaultProbabilityPct").value(55))
        .andExpect(jsonPath("$.stageType").value("OPEN"));

    String body =
        mockMvc
            .perform(get("/api/pipeline/stages").with(owner()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    java.util.List<String> names = JsonPath.read(body, "$[?(@.id=='" + id + "')].name");
    org.assertj.core.api.Assertions.assertThat(names.getFirst()).isEqualTo("Renamed");
    java.util.List<Integer> probs =
        JsonPath.read(body, "$[?(@.id=='" + id + "')].defaultProbabilityPct");
    org.assertj.core.api.Assertions.assertThat(probs.getFirst()).isEqualTo(55);
  }

  // 4
  @Test
  void reorderStage_returns200_andReGetConfirmsNewPosition() throws Exception {
    String id = createStage("Mover", 9, 20, "OPEN");
    mockMvc
        .perform(
            post("/api/pipeline/stages/" + id + "/reorder")
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"newPosition":3}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.position").value(3));

    String body =
        mockMvc
            .perform(get("/api/pipeline/stages").with(owner()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    java.util.List<Integer> positions = JsonPath.read(body, "$[?(@.id=='" + id + "')].position");
    org.assertj.core.api.Assertions.assertThat(positions.getFirst()).isEqualTo(3);
  }

  // 5
  @Test
  void archiveStage_returns200WithArchivedTrue() throws Exception {
    // A second OPEN stage so archiving it does not trip the last-active-of-type guard.
    String id = createStage("Archivable", 11, 15, "OPEN");
    mockMvc
        .perform(post("/api/pipeline/stages/" + id + "/archive").with(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archived").value(true));
  }

  // 6
  @Test
  void archiveLastActiveOfType_returns400() throws Exception {
    // The seeded WON stage is the only active WON stage — archiving it violates the invariant.
    mockMvc
        .perform(post("/api/pipeline/stages/" + seededWonStageId + "/archive").with(owner()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").isString())
        .andExpect(jsonPath("$.detail").isString());
  }

  // 7
  @Test
  void deleteStageWithAttachedDeal_returns409() throws Exception {
    String stageId = createStage("HasDeal", 12, 50, "OPEN");
    // Attach a deal to the stage.
    mockMvc
        .perform(
            post("/api/deals")
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"customerId":"%s","title":"Deal On Stage","stageId":"%s"}
                    """
                        .formatted(customerId, stageId)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(delete("/api/pipeline/stages/" + stageId).with(owner()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.title").isString())
        .andExpect(jsonPath("$.detail").isString());
  }

  // 8
  @Test
  void deleteArchivedStageWithNoDeals_returns204() throws Exception {
    String id = createStage("ToDelete", 13, 10, "OPEN");
    mockMvc
        .perform(post("/api/pipeline/stages/" + id + "/archive").with(owner()))
        .andExpect(status().isOk());
    mockMvc
        .perform(delete("/api/pipeline/stages/" + id).with(owner()))
        .andExpect(status().isNoContent());
  }

  // 9
  @Test
  void createStage_asViewerWithoutManagePipeline_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/pipeline/stages")
                .with(viewer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Forbidden","position":2,"defaultProbabilityPct":10,"stageType":"OPEN"}
                    """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").isString())
        .andExpect(jsonPath("$.detail").isString());
  }
}
