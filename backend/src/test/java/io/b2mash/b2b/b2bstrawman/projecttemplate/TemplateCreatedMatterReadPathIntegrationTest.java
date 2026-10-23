package io.b2mash.b2b.b2bstrawman.projecttemplate;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionFailure;
import io.b2mash.b2b.b2bstrawman.automation.config.ActionSuccess;
import io.b2mash.b2b.b2bstrawman.automation.config.AssignMemberActionConfig;
import io.b2mash.b2b.b2bstrawman.automation.config.AssignTo;
import io.b2mash.b2b.b2bstrawman.automation.config.CreateTaskActionConfig;
import io.b2mash.b2b.b2bstrawman.automation.executor.AssignMemberActionExecutor;
import io.b2mash.b2b.b2bstrawman.automation.executor.CreateTaskActionExecutor;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.member.ProjectMember;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberRepository;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberService;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.notification.NotificationRepository;
import io.b2mash.b2b.b2bstrawman.orgrole.Capability;
import io.b2mash.b2b.b2bstrawman.project.MatterListQuery;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
import io.b2mash.b2b.b2bstrawman.projecttemplate.dto.InstantiateTemplateRequest;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.task.TaskService;
import io.b2mash.b2b.b2bstrawman.testutil.TenantTestSupport;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 88 U1 (ADR-345): a matter created through the REAL template write path ({@link
 * ProjectTemplateService#instantiateTemplate}) with a chosen lead must surface that lead on every
 * reader the capstone found empty — Matters list fee earner, the lead's edit access, lead
 * notifications, and automation PROJECT_OWNER. {@code MatterListRowIntegrationTest} stayed green
 * while template matters showed "Fee earner —" because it never instantiated a template.
 *
 * <p>Assertions are id-scoped (the tenant is shared across methods); none depend on the matter's
 * total team size, so a later creator auto-add does not affect them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TemplateCreatedMatterReadPathIntegrationTest {
  private static final String ORG_ID = "org_template_read_path";
  private static final String OWNER = "user_trp_owner";
  private static final String LEAD = "user_trp_lead";
  private static final String CLAIMANT = "user_trp_claimant";
  private static final String LEAD_NAME = "TRP Lead";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ProjectTemplateService templateService;
  @Autowired private ProjectTemplateRepository templateRepository;
  @Autowired private TemplateTaskRepository templateTaskRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TaskService taskService;
  @Autowired private ProjectMemberRepository projectMemberRepository;
  @Autowired private ProjectMemberService projectMemberService;
  @Autowired private ProjectService projectService;
  @Autowired private ProjectAccessService projectAccessService;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private AssignMemberActionExecutor assignMemberActionExecutor;
  @Autowired private CreateTaskActionExecutor createTaskActionExecutor;

  private String tenantSchema;
  private UUID ownerId;
  private UUID leadId;
  private UUID claimantId;
  private UUID matterId;
  private UUID openTaskId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Template Read Path Org", null).schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "trp_owner@test.com", "TRP Owner", "owner"));
    // Org role "member": an owner/admin would get canEdit regardless of the lead row.
    leadId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, LEAD, "trp_lead@test.com", LEAD_NAME, "member"));
    claimantId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, CLAIMANT, "trp_claimant@test.com", "TRP Claimant", "member"));

    UUID[] ids =
        TenantTestSupport.callAsActor(
            tenantSchema,
            ORG_ID,
            ownerId,
            "owner",
            Capability.ALL_NAMES,
            () -> {
              UUID templateId =
                  transactionTemplate.execute(
                      tx -> {
                        var template =
                            templateRepository.saveAndFlush(
                                new ProjectTemplate(
                                    "TRP Template",
                                    "{customer}",
                                    null,
                                    true,
                                    "MANUAL",
                                    null,
                                    ownerId));
                        templateTaskRepository.saveAndFlush(
                            new TemplateTask(
                                template.getId(),
                                "TRP Open Task",
                                null,
                                null,
                                0,
                                true,
                                "UNASSIGNED"));
                        return template.getId();
                      });
              // The real write path, outside any outer transaction so it commits.
              var request =
                  new InstantiateTemplateRequest(
                      "TRP Template Matter", null, leadId, null, null, null, null);
              UUID matter =
                  templateService.instantiateTemplate(templateId, request, ownerId).getId();
              UUID task = taskRepository.findByProjectId(matter).getFirst().getId();
              return new UUID[] {matter, task};
            });
    matterId = ids[0];
    openTaskId = ids[1];
  }

  @Test
  void templateWritesTheCanonicalLeadRow() throws Exception {
    var role =
        TenantTestSupport.callInTenant(
            tenantSchema,
            () ->
                projectMemberRepository
                    .findByProjectIdAndMemberId(matterId, leadId)
                    .map(ProjectMember::getProjectRole)
                    .orElse(null));
    assertThat(role).isEqualTo(Roles.PROJECT_LEAD);
  }

  // (1) G-D11 a — Matters list "Fee earner"
  @Test
  void mattersListShowsTheTemplateLeadAsFeeEarner() throws Exception {
    var ownerRows =
        TenantTestSupport.callAsActor(
            tenantSchema,
            ORG_ID,
            ownerId,
            "owner",
            Capability.ALL_NAMES,
            () ->
                projectService.listMatterRows(
                    defaultQuery(false), new ActorContext(ownerId, "owner")));
    assertThat(ownerRows)
        .filteredOn(r -> r.id().equals(matterId))
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.feeEarnerId()).isEqualTo(leadId);
              assertThat(r.feeEarnerName()).isEqualTo(LEAD_NAME);
            });

    // The lead's own "mine" list carries the matter with matterRole "lead".
    var leadRows =
        TenantTestSupport.callAsActor(
            tenantSchema,
            ORG_ID,
            leadId,
            "member",
            Set.of(),
            () ->
                projectService.listMatterRows(
                    defaultQuery(true), new ActorContext(leadId, "member")));
    assertThat(leadRows)
        .filteredOn(r -> r.id().equals(matterId))
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.matterRole()).isEqualTo(Roles.PROJECT_LEAD);
              assertThat(r.feeEarnerId()).isEqualTo(leadId);
            });
  }

  // (2) G-D13 — the lead (an org Member) can edit / manage the matter
  @Test
  void leadHasEditAccessToTheTemplateMatter() throws Exception {
    var access =
        TenantTestSupport.callAsActor(
            tenantSchema,
            ORG_ID,
            leadId,
            "member",
            () -> projectAccessService.checkAccess(matterId, new ActorContext(leadId, "member")));
    assertThat(access.canView()).isTrue();
    assertThat(access.canEdit()).isTrue();
    assertThat(access.canManageMembers()).isTrue();
    assertThat(access.projectRole()).isEqualTo(Roles.PROJECT_LEAD);
  }

  // (3) N-1 — a lead notification. Task CLAIMED is the task event that notifies leads
  // (NotificationService.handleTaskClaimed); task ASSIGNED notifies only the assignee.
  @Test
  void claimingATaskOnTheTemplateMatterNotifiesTheLead() throws Exception {
    // The claimant must be on the matter to claim (TaskService.claimTask).
    TenantTestSupport.runAsActor(
        tenantSchema,
        ORG_ID,
        ownerId,
        "owner",
        Capability.ALL_NAMES,
        () -> projectMemberService.addMember(matterId, claimantId, ownerId));
    // Real service: commits, then NotificationEventHandler.onTaskClaimed (AFTER_COMMIT) ->
    // NotificationService.handleTaskClaimed -> leads via findByProjectIdAndProjectRole(lead).
    TenantTestSupport.runAsActor(
        tenantSchema,
        ORG_ID,
        claimantId,
        "member",
        () -> taskService.claimTask(openTaskId, new ActorContext(claimantId, "member")));

    var leadInbox =
        TenantTestSupport.callInTenant(
            tenantSchema,
            () ->
                notificationRepository
                    .findByRecipientMemberId(leadId, PageRequest.of(0, 100))
                    .getContent());
    assertThat(leadInbox)
        .as("lead must receive TASK_CLAIMED for the task claimed on their template matter")
        .anyMatch(
            n ->
                "TASK_CLAIMED".equals(n.getType())
                    && openTaskId.equals(n.getReferenceEntityId())
                    && matterId.equals(n.getReferenceProjectId()));
  }

  // (4) N-1 / G-B13 — automation ASSIGN_MEMBER PROJECT_OWNER resolves to the template lead
  @Test
  void assignMemberProjectOwnerResolvesToTheTemplateLead() throws Exception {
    var result =
        TenantTestSupport.callAsActor(
            tenantSchema,
            ORG_ID,
            ownerId,
            "owner",
            Capability.ALL_NAMES,
            () ->
                assignMemberActionExecutor.execute(
                    new AssignMemberActionConfig(AssignTo.PROJECT_OWNER, null, null),
                    automationContext(),
                    UUID.randomUUID()));
    // PROJECT_OWNER resolved the lead, who is already on the team, so addMember's 409 names them.
    // An unresolved lead (the pre-ADR-345 symptom) fails earlier with "could not resolve member".
    // The always-conflict outcome is pre-existing executor behaviour, recorded, not changed here.
    assertThat(result).isInstanceOf(ActionFailure.class);
    assertThat(((ActionFailure) result).errorMessage())
        .doesNotContain("could not resolve member")
        .contains(leadId.toString())
        .contains("already a member");
  }

  // (4b) same PROJECT_OWNER reader, positive path: CREATE_TASK assigns the template lead
  @Test
  void createTaskProjectOwnerAssignsTheTemplateLead() throws Exception {
    var result =
        TenantTestSupport.callAsActor(
            tenantSchema,
            ORG_ID,
            ownerId,
            "owner",
            Capability.ALL_NAMES,
            () ->
                createTaskActionExecutor.execute(
                    new CreateTaskActionConfig(
                        "TRP automation task", "d", AssignTo.PROJECT_OWNER, null, null),
                    automationContext(),
                    UUID.randomUUID()));
    assertThat(result).isInstanceOf(ActionSuccess.class);
    var taskId =
        UUID.fromString(((ActionSuccess) result).resultData().get("createdTaskId").toString());
    var assignee =
        TenantTestSupport.callInTenant(
            tenantSchema, () -> taskRepository.findById(taskId).orElseThrow().getAssigneeId());
    assertThat(assignee).isEqualTo(leadId);
  }

  private static MatterListQuery defaultQuery(boolean mine) {
    return new MatterListQuery(null, null, null, null, mine, false, null, Map.of());
  }

  private Map<String, Map<String, Object>> automationContext() {
    Map<String, Map<String, Object>> context = new LinkedHashMap<>();
    context.put("project", new LinkedHashMap<>(Map.of("id", matterId.toString())));
    context.put(
        "actor", new LinkedHashMap<>(Map.of("id", ownerId.toString(), "name", "TRP Owner")));
    return context;
  }
}
