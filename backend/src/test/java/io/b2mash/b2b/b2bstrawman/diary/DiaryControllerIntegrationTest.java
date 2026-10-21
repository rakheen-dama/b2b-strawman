package io.b2mash.b2b.b2bstrawman.diary;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberService;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtDate;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtDateRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.PrescriptionTracker;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.PrescriptionTrackerRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code GET /api/diary} over real embedded Postgres (Phase 87, Epic 613A). Every assertion is id
 * containment or an id-scoped slice ({@code matterId=A}) — the tenant is shared across methods.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DiaryControllerIntegrationTest {

  private static final String ORG_ID = "org_diary_ctrl_test";
  private static final String NO_MODULE_ORG_ID = "org_diary_nomodule_test";
  private static final String ADMIN = "user_diary_admin";
  private static final String LEAD = "user_diary_lead";
  private static final String ASSIGNEE = "user_diary_assignee";
  private static final String OUTSIDER = "user_diary_outsider";
  private static final String NM_OWNER = "user_diary_nm_owner";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private ProjectService projectService;
  @Autowired private ProjectMemberService projectMemberService;
  @Autowired private TaskService taskService;
  @Autowired private CourtDateRepository courtDateRepository;
  @Autowired private PrescriptionTrackerRepository prescriptionTrackerRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private final LocalDate today = LocalDate.now();

  private UUID adminId;
  private UUID leadId;
  private UUID assigneeId;
  private UUID matterA;
  private UUID matterNoLead;
  private UUID draftHeads;
  private UUID fileNotice;
  private UUID overdueFiling;
  private UUID doneItem;
  private UUID courtTen;
  private UUID courtNoTime;
  private UUID courtNoLead;
  private UUID prescription;
  private UUID nmMatter;
  private UUID nmTask;

  @BeforeAll
  void setup() throws Exception {
    String tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Diary Ctrl Test Org", null).schemaName();
    adminId = sync(ORG_ID, ADMIN, "Diary Admin", "admin");
    leadId = sync(ORG_ID, LEAD, "Lead Attorney", "member");
    assigneeId = sync(ORG_ID, ASSIGNEE, "Assigned Clerk", "member");
    sync(ORG_ID, OUTSIDER, "Outside Member", "member");

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("court_calendar"));
                      orgSettingsRepository.save(settings);
                    }));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, adminId)
        .where(RequestScopes.ORG_ROLE, "admin")
        .run(this::seedModuleOrg);

    String nmSchema =
        provisioningService
            .provisionTenant(NO_MODULE_ORG_ID, "Diary No Module Org", null)
            .schemaName();
    UUID nmOwnerId = sync(NO_MODULE_ORG_ID, NM_OWNER, "No Module Owner", "owner");
    ScopedValue.where(RequestScopes.TENANT_ID, nmSchema)
        .where(RequestScopes.ORG_ID, NO_MODULE_ORG_ID)
        .where(RequestScopes.MEMBER_ID, nmOwnerId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              nmMatter =
                  projectService
                      .createProject(
                          "NM Matter", "d", nmOwnerId, null, null, null, today.plusDays(2))
                      .getId();
              nmTask =
                  taskService
                      .createTask(
                          nmMatter,
                          "NM task",
                          "d",
                          "MEDIUM",
                          "GENERAL",
                          today.plusDays(1),
                          new ActorContext(nmOwnerId, "owner"))
                      .getId();
            });
  }

  private void seedModuleOrg() {
    var actor = new ActorContext(adminId, "admin");
    UUID clientId =
        transactionTemplate.execute(
            tx ->
                customerRepository
                    .saveAndFlush(
                        createActiveCustomer("Diary Client", "diary_client@test.com", adminId))
                    .getId());

    // Matter A: lead = Lead Attorney (transferred from the creating admin); assignee is a member.
    matterA =
        projectService
            .createProject(
                "Diary Matter A", "desc", adminId, null, null, clientId, today.plusDays(10))
            .getId();
    projectMemberService.addMember(matterA, leadId, adminId);
    projectMemberService.addMember(matterA, assigneeId, adminId);
    projectMemberService.transferLead(matterA, adminId, leadId);

    // Matter NoLead: no project_members rows at all.
    matterNoLead =
        transactionTemplate.execute(
            tx -> {
              var p = new Project("Diary Matter NoLead", "d", adminId);
              p.setCustomerId(clientId);
              p.setDueDate(today.plusDays(12));
              return projectRepository.saveAndFlush(p).getId();
            });

    draftHeads = createTask("Draft heads", today.plusDays(3), actor);
    taskService.updateTask(
        draftHeads,
        "Draft heads",
        "d",
        "MEDIUM",
        "OPEN",
        "GENERAL",
        today.plusDays(3),
        assigneeId,
        actor);
    fileNotice = createTask("File notice", today.plusDays(3), actor);
    overdueFiling = createTask("Overdue filing", today.minusDays(5), actor);
    doneItem = createTask("Done item", today.plusDays(3), actor);
    taskService.updateTask(
        doneItem,
        "Done item",
        "d",
        "MEDIUM",
        "IN_PROGRESS",
        "GENERAL",
        today.plusDays(3),
        null,
        actor);
    taskService.updateTask(
        doneItem, "Done item", "d", "MEDIUM", "DONE", "GENERAL", today.plusDays(3), null, actor);

    transactionTemplate.executeWithoutResult(
        tx -> {
          courtTen =
              courtDateRepository
                  .saveAndFlush(
                      new CourtDate(
                          matterA,
                          clientId,
                          "HEARING",
                          today.plusDays(3),
                          LocalTime.of(10, 0),
                          "Johannesburg High Court",
                          null,
                          null,
                          "Rule 43 application",
                          7,
                          adminId))
                  .getId();
          courtNoTime =
              courtDateRepository
                  .saveAndFlush(
                      new CourtDate(
                          matterA,
                          clientId,
                          "TRIAL",
                          today.plusDays(3),
                          null,
                          "Pretoria High Court",
                          null,
                          null,
                          null,
                          7,
                          adminId))
                  .getId();
          courtNoLead =
              courtDateRepository
                  .saveAndFlush(
                      new CourtDate(
                          matterNoLead,
                          clientId,
                          "MOTION",
                          today.plusDays(4),
                          null,
                          "Durban High Court",
                          null,
                          null,
                          null,
                          7,
                          adminId))
                  .getId();
          prescription =
              prescriptionTrackerRepository
                  .saveAndFlush(
                      new PrescriptionTracker(
                          matterA,
                          clientId,
                          today.plusDays(3).minusYears(3),
                          "GENERAL_3Y",
                          null,
                          today.plusDays(3),
                          null,
                          adminId))
                  .getId();
        });
  }

  private UUID createTask(String title, LocalDate due, ActorContext actor) {
    Task t = taskService.createTask(matterA, title, "d", "MEDIUM", "GENERAL", due, actor);
    return t.getId();
  }

  private UUID sync(String orgId, String subject, String name, String role) throws Exception {
    return UUID.fromString(
        TestMemberHelper.syncMember(mockMvc, orgId, subject, subject + "@test.com", name, role));
  }

  private MockHttpServletRequestBuilder diary(String from, String to) {
    return get("/api/diary").param("from", from).param("to", to);
  }

  private String d(int offsetDays) {
    return today.plusDays(offsetDays).toString();
  }

  private static String byId(UUID id, String field) {
    return "$[?(@.id == '%s')].%s".formatted(id, field);
  }

  // ------------------------------------------------------------ window cap / bad params

  @Test
  void windowOver92Days_returns400WithTypedProblem() throws Exception {
    mockMvc
        .perform(diary(d(0), d(93)).with(TestJwtFactory.adminJwt(ORG_ID, ADMIN)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("diary.window-too-large"))
        .andExpect(jsonPath("$.title").value("Diary window too large"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").exists());

    mockMvc
        .perform(diary(d(0), d(92)).with(TestJwtFactory.adminJwt(ORG_ID, ADMIN)))
        .andExpect(status().isOk());
  }

  @Test
  void fromAfterTo_returns400() throws Exception {
    mockMvc
        .perform(diary(d(5), d(0)).with(TestJwtFactory.adminJwt(ORG_ID, ADMIN)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid date range"));
  }

  @Test
  void unknownTypesValue_returns400() throws Exception {
    mockMvc
        .perform(
            diary(d(0), d(7))
                .param("types", "TASK_DUE,BOGUS")
                .with(TestJwtFactory.adminJwt(ORG_ID, ADMIN)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid parameter value"))
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void inaccessibleOrUnknownMatter_returns404() throws Exception {
    mockMvc
        .perform(
            diary(d(0), d(7))
                .param("matterId", matterA.toString())
                .with(TestJwtFactory.memberJwt(ORG_ID, OUTSIDER)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));

    mockMvc
        .perform(
            diary(d(0), d(7))
                .param("matterId", UUID.randomUUID().toString())
                .with(TestJwtFactory.adminJwt(ORG_ID, ADMIN)))
        .andExpect(status().isNotFound());
  }

  // ------------------------------------------------------------ types gate

  @Test
  void typesGate_moduleEnabled_servesAllFourKinds() throws Exception {
    mockMvc
        .perform(diary(d(0), d(14)).with(TestJwtFactory.adminJwt(ORG_ID, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Diary-Types", "TASK_DUE,MATTER_DUE,COURT_DATE,PRESCRIPTION"))
        .andExpect(
            jsonPath(
                "$[*].id",
                hasItems(
                    courtTen.toString(),
                    prescription.toString(),
                    matterA.toString(),
                    fileNotice.toString())));
  }

  @Test
  void typesGate_moduleDisabled_omitsLegalKindsAndHeaderReflectsIt() throws Exception {
    mockMvc
        .perform(diary(d(0), d(14)).with(TestJwtFactory.ownerJwt(NO_MODULE_ORG_ID, NM_OWNER)))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Diary-Types", "TASK_DUE,MATTER_DUE"))
        .andExpect(jsonPath("$[*].id", hasItems(nmTask.toString(), nmMatter.toString())))
        .andExpect(jsonPath("$[*].kind", everyItem(in(List.of("TASK_DUE", "MATTER_DUE")))));

    mockMvc
        .perform(
            diary(d(0), d(14))
                .param("types", "COURT_DATE")
                .with(TestJwtFactory.ownerJwt(NO_MODULE_ORG_ID, NM_OWNER)))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Diary-Types", ""))
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void typesParam_narrowsToRequestedKinds() throws Exception {
    mockMvc
        .perform(
            diary(d(0), d(14))
                .param("types", "COURT_DATE,PRESCRIPTION")
                .param("matterId", matterA.toString())
                .with(TestJwtFactory.adminJwt(ORG_ID, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Diary-Types", "COURT_DATE,PRESCRIPTION"))
        .andExpect(jsonPath("$[*].kind", everyItem(in(List.of("COURT_DATE", "PRESCRIPTION")))))
        .andExpect(jsonPath("$[*].id", hasItems(courtTen.toString(), prescription.toString())));
  }

  @Test
  void typesParam_emptyElements_areIgnoredNotServerError() throws Exception {
    mockMvc
        .perform(
            diary(d(0), d(14))
                .param("types", "TASK_DUE,")
                .with(TestJwtFactory.adminJwt(ORG_ID, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Diary-Types", "TASK_DUE"))
        .andExpect(jsonPath("$[*].kind", everyItem(in(List.of("TASK_DUE")))));

    mockMvc
        .perform(
            diary(d(0), d(14))
                .param("types", "TASK_DUE,,COURT_DATE")
                .with(TestJwtFactory.adminJwt(ORG_ID, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Diary-Types", "TASK_DUE,COURT_DATE"))
        .andExpect(jsonPath("$[*].kind", everyItem(in(List.of("TASK_DUE", "COURT_DATE")))));
  }

  // ------------------------------------------------------------ fee earner

  @Test
  void feeEarnerFilter_fallsBackToMatterLead() throws Exception {
    mockMvc
        .perform(
            diary(d(0), d(14))
                .param("matterId", matterA.toString())
                .param("feeEarnerId", leadId.toString())
                .with(TestJwtFactory.adminJwt(ORG_ID, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$[*].id",
                hasItems(
                    fileNotice.toString(),
                    courtTen.toString(),
                    courtNoTime.toString(),
                    prescription.toString(),
                    matterA.toString())))
        .andExpect(jsonPath("$[*].id", not(hasItem(draftHeads.toString()))))
        .andExpect(jsonPath(byId(fileNotice, "feeEarnerId")).value(leadId.toString()))
        .andExpect(jsonPath(byId(fileNotice, "feeEarnerName")).value("Lead Attorney"));

    mockMvc
        .perform(
            diary(d(0), d(14))
                .param("matterId", matterA.toString())
                .param("feeEarnerId", assigneeId.toString())
                .with(TestJwtFactory.adminJwt(ORG_ID, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", contains(draftHeads.toString())))
        .andExpect(jsonPath(byId(draftHeads, "feeEarnerName")).value("Assigned Clerk"));
  }

  @Test
  void eventWithoutAssigneeOrLead_onlyInUnfilteredDiary() throws Exception {
    mockMvc
        .perform(diary(d(0), d(14)).with(TestJwtFactory.adminJwt(ORG_ID, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", hasItem(courtNoLead.toString())))
        .andExpect(jsonPath(byId(courtNoLead, "feeEarnerId")).isEmpty());

    mockMvc
        .perform(
            diary(d(0), d(14))
                .param("feeEarnerId", leadId.toString())
                .with(TestJwtFactory.adminJwt(ORG_ID, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", not(hasItem(courtNoLead.toString()))))
        .andExpect(jsonPath("$[*].id", hasItem(courtTen.toString())));
  }

  // ------------------------------------------------------------ overdue band

  @Test
  void overdueBand_appendsOpenItemsBeforeFrom() throws Exception {
    mockMvc
        .perform(
            diary(d(0), d(7))
                .param("matterId", matterA.toString())
                .with(TestJwtFactory.adminJwt(ORG_ID, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", hasItem(overdueFiling.toString())))
        .andExpect(jsonPath(byId(overdueFiling, "overdue")).value(true))
        .andExpect(jsonPath(byId(fileNotice, "overdue")).value(false))
        .andExpect(jsonPath("$[0].id").value(overdueFiling.toString()));

    mockMvc
        .perform(
            diary(d(0), d(7))
                .param("matterId", matterA.toString())
                .param("includeOverdue", "false")
                .with(TestJwtFactory.adminJwt(ORG_ID, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", not(hasItem(overdueFiling.toString()))));
  }

  @Test
  void overdueBand_futureWeek_includesOnlyGenuinelyOverdueItems() throws Exception {
    mockMvc
        .perform(
            diary(d(7), d(14))
                .param("matterId", matterA.toString())
                .with(TestJwtFactory.adminJwt(ORG_ID, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", hasItem(overdueFiling.toString())))
        .andExpect(jsonPath("$[*].id", not(hasItem(draftHeads.toString()))))
        .andExpect(jsonPath("$[*].id", not(hasItem(courtTen.toString()))));
  }

  // ------------------------------------------------------------ ordering

  @Test
  void ordering_dateThenTimeNullsLastThenKindRankThenTitle() throws Exception {
    mockMvc
        .perform(
            diary(d(3), d(3))
                .param("matterId", matterA.toString())
                .param("includeOverdue", "false")
                .with(TestJwtFactory.adminJwt(ORG_ID, ADMIN)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$[*].id",
                contains(
                    courtTen.toString(),
                    courtNoTime.toString(),
                    prescription.toString(),
                    draftHeads.toString(),
                    fileNotice.toString())))
        .andExpect(jsonPath("$[*].id", not(hasItem(doneItem.toString()))));
  }

  // ------------------------------------------------------------ visibility

  @Test
  void member_seesOnlyAccessibleMatters() throws Exception {
    mockMvc
        .perform(diary(d(0), d(14)).with(TestJwtFactory.memberJwt(ORG_ID, LEAD)))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Diary-Types", "TASK_DUE,MATTER_DUE,COURT_DATE,PRESCRIPTION"))
        .andExpect(
            jsonPath(
                "$[*].id",
                hasItems(
                    courtTen.toString(),
                    prescription.toString(),
                    fileNotice.toString(),
                    matterA.toString())))
        .andExpect(jsonPath("$[*].id", not(hasItem(courtNoLead.toString()))))
        .andExpect(jsonPath("$[*].id", not(hasItem(matterNoLead.toString()))));

    mockMvc
        .perform(diary(d(-30), d(14)).with(TestJwtFactory.memberJwt(ORG_ID, OUTSIDER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", not(hasItem(courtTen.toString()))))
        .andExpect(jsonPath("$[*].id", not(hasItem(courtNoLead.toString()))))
        .andExpect(jsonPath("$[*].id", not(hasItem(prescription.toString()))))
        .andExpect(jsonPath("$[*].id", not(hasItem(fileNotice.toString()))))
        .andExpect(jsonPath("$[*].id", not(hasItem(overdueFiling.toString()))))
        .andExpect(jsonPath("$[*].id", not(hasItem(matterA.toString()))));
  }

  // ------------------------------------------------------------ shape

  @Test
  void shape_matterClientHrefAndOmittedNulls() throws Exception {
    mockMvc
        .perform(diary(d(0), d(14)).with(TestJwtFactory.adminJwt(ORG_ID, ADMIN)))
        .andExpect(status().isOk())
        // task row
        .andExpect(jsonPath(byId(fileNotice, "kind")).value("TASK_DUE"))
        .andExpect(
            jsonPath(byId(fileNotice, "href"))
                .value("/matters/" + matterA + "?taskId=" + fileNotice))
        .andExpect(jsonPath(byId(fileNotice, "matterId")).value(matterA.toString()))
        .andExpect(jsonPath(byId(fileNotice, "matterName")).value("Diary Matter A"))
        .andExpect(jsonPath(byId(fileNotice, "clientName")).value("Diary Client"))
        .andExpect(jsonPath(byId(fileNotice, "status")).value("OPEN"))
        .andExpect(jsonPath(byId(fileNotice, "time")).isEmpty())
        .andExpect(jsonPath(byId(fileNotice, "subtype")).isEmpty())
        // court-date row
        .andExpect(jsonPath(byId(courtTen, "kind")).value("COURT_DATE"))
        .andExpect(
            jsonPath(byId(courtTen, "title"))
                .value("Johannesburg High Court — Rule 43 application"))
        .andExpect(jsonPath(byId(courtTen, "subtype")).value("HEARING"))
        .andExpect(jsonPath(byId(courtTen, "status")).value("SCHEDULED"))
        .andExpect(jsonPath(byId(courtTen, "time")).value(hasItem(startsWith("10:00"))))
        .andExpect(
            jsonPath(byId(courtTen, "href"))
                .value("/matters/" + matterA + "?tab=court-dates&courtDateId=" + courtTen))
        // prescription row
        .andExpect(jsonPath(byId(prescription, "kind")).value("PRESCRIPTION"))
        .andExpect(jsonPath(byId(prescription, "title")).value("Prescription — General (3 years)"))
        .andExpect(jsonPath(byId(prescription, "subtype")).value("GENERAL_3Y"))
        .andExpect(
            jsonPath(byId(prescription, "href"))
                .value("/matters/" + matterA + "?tab=court-dates&prescriptionId=" + prescription))
        // matter-due row
        .andExpect(jsonPath(byId(matterA, "kind")).value("MATTER_DUE"))
        .andExpect(jsonPath(byId(matterA, "href")).value("/matters/" + matterA))
        .andExpect(jsonPath(byId(matterA, "status")).value("ACTIVE"))
        .andExpect(jsonPath(byId(matterA, "feeEarnerName")).value("Lead Attorney"))
        // NoLead matter has no reference number → field omitted (NON_NULL)
        .andExpect(jsonPath(byId(courtNoLead, "matterReference")).isEmpty())
        .andExpect(jsonPath(byId(courtNoLead, "matterName")).value("Diary Matter NoLead"));
  }
}
