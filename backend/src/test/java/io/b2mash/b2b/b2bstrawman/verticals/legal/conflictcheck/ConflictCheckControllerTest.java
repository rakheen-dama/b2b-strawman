package io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
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
class ConflictCheckControllerTest {
  private static final String ORG_ID = "org_conflict_ctrl_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private AdversePartyRepository adversePartyRepository;
  @Autowired private AdversePartyLinkRepository adversePartyLinkRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private UUID projectId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Conflict Controller Test Org", null)
            .schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_conflict_ctrl_owner",
                "conflict_ctrl@test.com",
                "Conflict Ctrl Owner",
                "owner"));
    TestMemberHelper.syncMember(
        mockMvc,
        ORG_ID,
        "user_conflict_ctrl_member",
        "conflict_ctrl_member@test.com",
        "Conflict Ctrl Member",
        "member");

    // Enable the conflict_check module
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("conflict_check"));
                      orgSettingsRepository.save(settings);
                    }));

    // Create test customer and project
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var customer =
                          customerRepository.saveAndFlush(
                              createActiveCustomer(
                                  "Conflict Ctrl Corp", "conflict_ctrl_test@test.com", memberId));
                      customerId = customer.getId();

                      var project =
                          new Project("Controller Conflict Matter", "Controller test", memberId);
                      project.setCustomerId(customerId);
                      project = projectRepository.saveAndFlush(project);
                      projectId = project.getId();

                      // Create an adverse party for conflict testing
                      var ap =
                          new AdverseParty(
                              "Controller Test Adverse Party",
                              "7001015800088",
                              null,
                              "NATURAL_PERSON",
                              null,
                              null);
                      adversePartyRepository.saveAndFlush(ap);

                      var link =
                          new AdversePartyLink(
                              ap.getId(), projectId, customerId, "OPPOSING_PARTY", "Test link");
                      adversePartyLinkRepository.saveAndFlush(link);
                    }));
  }

  @Test
  void postConflictCheck_performsCheckAndReturnsResult() throws Exception {
    mockMvc
        .perform(
            post("/api/conflict-checks")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_conflict_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "checkedName": "Controller Test Adverse Party",
                      "checkedIdNumber": "7001015800088",
                      "checkType": "NEW_CLIENT"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.result").value("CONFLICT_FOUND"))
        .andExpect(jsonPath("$.checkedName").value("Controller Test Adverse Party"))
        .andExpect(jsonPath("$.conflictsFound").isArray())
        .andExpect(jsonPath("$.conflictsFound").isNotEmpty())
        .andExpect(jsonPath("$.id").isNotEmpty());
  }

  @Test
  void getConflictChecks_returnsPaginatedHistory() throws Exception {
    // First create a conflict check
    mockMvc
        .perform(
            post("/api/conflict-checks")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_conflict_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "checkedName": "History Test Name",
                      "checkType": "PERIODIC_REVIEW"
                    }
                    """))
        .andExpect(status().isCreated());

    // Then list
    mockMvc
        .perform(
            get("/api/conflict-checks")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_conflict_ctrl_owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content").isNotEmpty());
  }

  @Test
  void postResolve_updatesConflictCheckResolution() throws Exception {
    // Create a check first
    var createResult =
        mockMvc
            .perform(
                post("/api/conflict-checks")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_conflict_ctrl_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "checkedName": "Resolve Ctrl Test",
                          "checkedIdNumber": "7001015800088",
                          "checkType": "NEW_MATTER"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String checkId =
        com.jayway.jsonpath.JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    // Resolve it
    mockMvc
        .perform(
            post("/api/conflict-checks/" + checkId + "/resolve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_conflict_ctrl_owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "resolution": "PROCEED",
                      "resolutionNotes": "No material conflict"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resolution").value("PROCEED"))
        .andExpect(jsonPath("$.resolutionNotes").value("No material conflict"))
        .andExpect(jsonPath("$.resolvedBy").isNotEmpty())
        .andExpect(jsonPath("$.resolvedAt").isNotEmpty());
  }

  @Test
  void postConflictCheck_withViewLegalOnly_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/conflict-checks")
                .with(TestJwtFactory.memberJwt(ORG_ID, "user_conflict_ctrl_member"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "checkedName": "Auth Test Name",
                      "checkType": "NEW_CLIENT"
                    }
                    """))
        .andExpect(status().isForbidden());
  }
}
