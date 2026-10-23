package io.b2mash.b2b.b2bstrawman.project;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberService;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.Capability;
import io.b2mash.b2b.b2bstrawman.project.dto.MatterListRow;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.tag.EntityTagService;
import io.b2mash.b2b.b2bstrawman.task.TaskService;
import io.b2mash.b2b.b2bstrawman.testutil.TenantTestSupport;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestTrustBalanceFactory;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtDate;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtDateRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementCategory;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementPaymentSource;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.LegalDisbursement;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.VatTreatment;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code GET /api/matters} → {@code List<MatterListRow>} (Phase 87, Epic 615A): row shape,
 * capability redaction, the {@code mine} / {@code hasUnbilled} / {@code inactiveSince} params, and
 * the statement cap (≤ 7 for a ≥ 50-matter page, Hibernate statistics enabled in
 * application-test.yml). Assertions are id-scoped — the tenant is shared across methods.
 *
 * <p>Fixture (dedicated org, modules disbursements + trust_accounting + court_calendar):
 *
 * <ul>
 *   <li><b>Matter A</b> (client "MLR Client", owner = lead; member/fin/trust added): open task due
 *       today+3 with 120 billable minutes at 600/h (+ 30 non-billable), open task without due date,
 *       overdue open task (today−5), DONE task due today+1, one APPROVED+UNBILLED disbursement of
 *       300.00, a court date today+3, a past court date today−2, tag "MLR Urgent".
 *   <li><b>Matter B</b>: lead transferred from the owner to the plain member.
 *   <li><b>Orphan</b>: no client, no project_members rows.
 *   <li><b>Dormant</b>: one task with a non-billable entry dated today−200; matter and task {@code
 *       updated_at} pushed 200 days back.
 *   <li><b>Stale-but-billed</b>: matter and task {@code updated_at} pushed 200 days back, but a
 *       billable entry dated yesterday (work date counts as activity).
 *   <li>50 bulk matters on the same client (reach the 50-matter page).
 * </ul>
 *
 * The client holds 15,000.00 in trust (one ledger card).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MatterListRowIntegrationTest {
  private static final String ORG_ID = "org_matter_list_row";
  private static final String OWNER = "user_mlr_owner";
  private static final String MEMBER = "user_mlr_member";
  private static final String FIN = "user_mlr_fin";
  private static final String TRUST = "user_mlr_trust";
  private static final String BARE = "user_mlr_bare";
  private static final int MATTER_PAGE = 50;
  private static final int STATEMENT_CAP = 7;

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ProjectService projectService;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private ProjectMemberService projectMemberService;
  @Autowired private TaskService taskService;
  @Autowired private TimeEntryService timeEntryService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private DisbursementRepository disbursementRepository;
  @Autowired private CourtDateRepository courtDateRepository;
  @Autowired private TrustAccountRepository trustAccountRepository;
  @Autowired private ClientLedgerCardRepository clientLedgerCardRepository;
  @Autowired private EntityTagService entityTagService;
  @Autowired private EntityManager entityManager;
  @Autowired private EntityManagerFactory entityManagerFactory;

  private final LocalDate today = LocalDate.now();

  private String tenantSchema;
  private String currency;
  private UUID ownerId;
  private UUID memberId;
  private UUID finId;
  private UUID trustId;
  private UUID bareId;
  private UUID clientId;
  private UUID tagId;
  private UUID matterA;
  private UUID matterB;
  private UUID orphan;
  private UUID dormant;
  private UUID staleButBilled;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Matter List Row Org", null).schemaName();
    ownerId = sync(OWNER, "MLR Owner", "owner");
    memberId = sync(MEMBER, "MLR Member", "member");
    finId = sync(FIN, "MLR Finance", "member");
    trustId = sync(TRUST, "MLR Trust", "member");
    assignRole(finId, createCustomRole("MLR Finance Role", "FINANCIAL_VISIBILITY"));
    assignRole(trustId, createCustomRole("MLR Trust Role", "VIEW_TRUST"));
    // Holds neither FINANCIAL_VISIBILITY nor VIEW_TRUST nor VIEW_LEGAL.
    bareId = sync(BARE, "MLR Bare", "member");
    assignRole(bareId, createCustomRole("MLR Bare Role", "GENERATE_STATEMENT_OF_ACCOUNT"));

    asOwner(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsService.getOrCreateForCurrentTenant();
                  settings.setEnabledModules(
                      List.of("disbursements", "trust_accounting", "court_calendar"));
                  orgSettingsRepository.save(settings);
                }));
    asOwner(() -> currency = orgSettingsService.getDefaultCurrency());

    // Billing rate in the org currency BEFORE any time entry, so entries snapshot it.
    createBillingRate(ownerId, "600.00");
    tagId =
        UUID.fromString(
            TestEntityHelper.createTag(
                mockMvc, TestJwtFactory.ownerJwt(ORG_ID, OWNER), "MLR Urgent", "#EF4444"));

    asOwner(this::seed);
  }

  private void seed() {
    var owner = new ActorContext(ownerId, "owner");
    clientId =
        transactionTemplate.execute(
            tx ->
                customerRepository
                    .saveAndFlush(
                        createActiveCustomer("MLR Client", "mlr_client@test.com", ownerId))
                    .getId());

    // --- Matter A: the fully-populated row ---
    matterA =
        projectService
            .createProject(
                "MLR Matter A",
                "Matter A description",
                ownerId,
                null,
                null,
                clientId,
                today.plusDays(30))
            .getId();
    projectMemberService.addMemberAsSystem(matterA, memberId, ownerId);
    projectMemberService.addMemberAsSystem(matterA, finId, ownerId);
    projectMemberService.addMemberAsSystem(matterA, trustId, ownerId);
    projectMemberService.addMemberAsSystem(matterA, bareId, ownerId);
    var dueSameDayAsCourt =
        taskService.createTask(
            matterA, "Open due +3", "d", "MEDIUM", "GENERAL", today.plusDays(3), owner);
    taskService.createTask(matterA, "Open no due", "d", "MEDIUM", "GENERAL", null, owner);
    taskService.createTask(
        matterA, "Open overdue", "d", "MEDIUM", "GENERAL", today.minusDays(5), owner);
    var done =
        taskService.createTask(
            matterA, "Done +1", "d", "MEDIUM", "GENERAL", today.plusDays(1), owner);
    taskService.updateTask(
        done.getId(),
        "Done +1",
        "d",
        "MEDIUM",
        "IN_PROGRESS",
        "GENERAL",
        today.plusDays(1),
        null,
        owner);
    taskService.updateTask(
        done.getId(), "Done +1", "d", "MEDIUM", "DONE", "GENERAL", today.plusDays(1), null, owner);
    timeEntryService.createTimeEntry(
        dueSameDayAsCourt.getId(), today.minusDays(2), 120, true, null, "billable", owner);
    timeEntryService.createTimeEntry(
        dueSameDayAsCourt.getId(), today.minusDays(2), 30, false, null, "non-billable", owner);
    transactionTemplate.executeWithoutResult(
        tx -> {
          var disbursement =
              new LegalDisbursement(
                  matterA,
                  clientId,
                  DisbursementCategory.SHERIFF_FEES.name(),
                  "MLR sheriff fee",
                  new BigDecimal("300.00"),
                  VatTreatment.ZERO_RATED_PASS_THROUGH.name(),
                  new BigDecimal("0.00"),
                  DisbursementPaymentSource.OFFICE_ACCOUNT.name(),
                  null,
                  today.minusDays(1),
                  "Sheriff",
                  "REF-MLR",
                  null,
                  ownerId);
          disbursement.submitForApproval();
          disbursement.approve(ownerId, "ok");
          disbursementRepository.saveAndFlush(disbursement);
          courtDateRepository.saveAndFlush(courtDate(matterA, today.plusDays(3)));
          courtDateRepository.saveAndFlush(courtDate(matterA, today.minusDays(2))); // past
          TestTrustBalanceFactory.seedTrustBalance(
              trustAccountRepository,
              clientLedgerCardRepository,
              clientId,
              new BigDecimal("15000.00"));
        });
    // ADR-342: storage key, do not rename
    entityTagService.setEntityTags("PROJECT", matterA, List.of(tagId));

    // --- Matter B: lead transferred to the plain member ---
    matterB =
        projectService
            .createProject("MLR Matter B", "d", ownerId, null, null, clientId, null)
            .getId();
    projectMemberService.addMemberAsSystem(matterB, memberId, ownerId);
    projectMemberService.transferLead(matterB, owner, memberId);

    // --- Orphan: no client, no project_members rows → owner's matterRole is null ---
    orphan =
        transactionTemplate.execute(
            tx -> projectRepository.saveAndFlush(new Project("MLR Orphan", "d", ownerId)).getId());

    // --- Dormant: only old, non-billable activity ---
    dormant = projectService.createProject("MLR Dormant", "d", ownerId).getId();
    var dormantTask =
        taskService.createTask(dormant, "Old task", "d", "MEDIUM", "GENERAL", null, owner);
    timeEntryService.createTimeEntry(
        dormantTask.getId(), today.minusDays(200), 45, false, null, "old work", owner);
    pushUpdatedAtBack(dormant, dormantTask.getId());

    // --- Stale-but-billed: rows untouched for 200 days, but work dated yesterday ---
    staleButBilled = projectService.createProject("MLR Stale Billed", "d", ownerId).getId();
    var staleTask =
        taskService.createTask(staleButBilled, "Stale task", "d", "MEDIUM", "GENERAL", null, owner);
    timeEntryService.createTimeEntry(
        staleTask.getId(), today.minusDays(1), 60, true, null, "recent work", owner);
    pushUpdatedAtBack(staleButBilled, staleTask.getId());

    // --- Bulk: reach a 50-matter ACTIVE page ---
    for (int i = 0; i < MATTER_PAGE; i++) {
      projectService.createProject("MLR Bulk " + i, "d", ownerId, null, null, clientId, null);
    }
  }

  // --- Row shape ---

  @Test
  void ownerSeesFullyPopulatedRow() throws Exception {
    var row = row(list(TestJwtFactory.ownerJwt(ORG_ID, OWNER), ""), matterA);

    assertThat(row)
        .containsEntry("name", "MLR Matter A")
        .containsEntry("description", "Matter A description")
        .containsEntry("status", "ACTIVE")
        .containsEntry("dueDate", today.plusDays(30).toString())
        .containsEntry("clientId", clientId.toString())
        .containsEntry("clientName", "MLR Client")
        .containsEntry("feeEarnerId", ownerId.toString())
        .containsEntry("feeEarnerName", "MLR Owner")
        .containsEntry("matterRole", "lead")
        // 120 billable minutes; the 30 non-billable minutes are excluded
        .containsEntry("unbilledMinutes", 120)
        .containsEntry("currency", currency)
        // court date and open task share today+3 → COURT_DATE wins the DiaryKind.rank() tie-break;
        // the DONE task (+1), the overdue task (−5) and the past court date (−2) are never "next"
        .containsEntry("nextDiaryDate", today.plusDays(3).toString())
        .containsEntry("nextDiaryKind", "COURT_DATE")
        // three open tasks (+3, no due, overdue); the DONE task is excluded
        .containsEntry("openTaskCount", 3)
        .containsKeys("id", "referenceNumber", "workType", "priority", "createdAt", "customFields");
    // 120 min × 600/h = 1,200.00 time + 300.00 approved unbilled disbursement
    assertThat(money(row, "unbilledValue")).isEqualByComparingTo("1500.00");
    assertThat(money(row, "trustBalance")).isEqualByComparingTo("15000.00");
    @SuppressWarnings("unchecked")
    var tags = (List<Map<String, Object>>) row.get("tags");
    assertThat(tags).extracting(t -> t.get("id")).containsExactly(tagId.toString());
  }

  @Test
  void leadIsTheProjectLeadNotTheCreator() throws Exception {
    var asMember = row(list(TestJwtFactory.memberJwt(ORG_ID, MEMBER), ""), matterB);
    assertThat(asMember)
        .containsEntry("feeEarnerId", memberId.toString())
        .containsEntry("feeEarnerName", "MLR Member")
        .containsEntry("matterRole", "lead")
        .containsEntry("unbilledMinutes", 0)
        .containsEntry("openTaskCount", 0)
        .containsEntry("nextDiaryDate", null)
        .containsEntry("nextDiaryKind", null);
  }

  @Test
  void orphanRowHasNoClientLeadRoleOrTrust() throws Exception {
    var row = row(list(TestJwtFactory.ownerJwt(ORG_ID, OWNER), ""), orphan);
    assertThat(row)
        .containsEntry("clientId", null)
        .containsEntry("clientName", null)
        .containsEntry("feeEarnerId", null)
        .containsEntry("feeEarnerName", null)
        .containsEntry("matterRole", null)
        // no client → no client-level trust figure, even for a VIEW_TRUST caller
        .containsEntry("trustBalance", null)
        .containsEntry("unbilledMinutes", 0)
        .containsEntry("openTaskCount", 0);
    assertThat(money(row, "unbilledValue")).isEqualByComparingTo("0.00");
  }

  // --- Redaction by capability ---

  @Test
  void callerWithoutFinancialCapabilitiesGetsMinutesAndCountsButNoMoney() throws Exception {
    var row = row(list(TestJwtFactory.memberJwt(ORG_ID, BARE), ""), matterA);
    assertThat(row)
        .containsEntry("matterRole", "member")
        .containsEntry("unbilledMinutes", 120)
        .containsEntry("openTaskCount", 3)
        .containsEntry("currency", currency)
        .containsEntry("unbilledValue", null)
        .containsEntry("trustBalance", null);
  }

  @Test
  void systemMemberRoleSeesTrustButNotValue() throws Exception {
    // The system "member" role is seeded with VIEW_TRUST + VIEW_LEGAL but not
    // FINANCIAL_VISIBILITY (tenant V1__baseline.sql, "member: 5").
    var row = row(list(TestJwtFactory.memberJwt(ORG_ID, MEMBER), ""), matterA);
    assertThat(row)
        .containsEntry("matterRole", "member")
        .containsEntry("unbilledMinutes", 120)
        .containsEntry("unbilledValue", null)
        .containsEntry("nextDiaryKind", "COURT_DATE");
    assertThat(money(row, "trustBalance")).isEqualByComparingTo("15000.00");
  }

  @Test
  void financialVisibilityShowsValueButNotTrust() throws Exception {
    var row = row(list(TestJwtFactory.memberJwt(ORG_ID, FIN), ""), matterA);
    assertThat(money(row, "unbilledValue")).isEqualByComparingTo("1500.00");
    assertThat(row).containsEntry("trustBalance", null).containsEntry("unbilledMinutes", 120);
    // No VIEW_LEGAL → court dates are not served (One Diary gate): the open task due +3 is next.
    assertThat(row)
        .containsEntry("nextDiaryDate", today.plusDays(3).toString())
        .containsEntry("nextDiaryKind", "TASK_DUE");
  }

  @Test
  void viewTrustShowsTrustButNotValue() throws Exception {
    var row = row(list(TestJwtFactory.memberJwt(ORG_ID, TRUST), ""), matterA);
    assertThat(money(row, "trustBalance")).isEqualByComparingTo("15000.00");
    assertThat(row).containsEntry("unbilledValue", null).containsEntry("unbilledMinutes", 120);
  }

  // --- List params ---

  @Test
  void mineKeepsOnlyMattersTheCallerBelongsTo() throws Exception {
    var all = ids(list(TestJwtFactory.ownerJwt(ORG_ID, OWNER), ""));
    assertThat(all).contains(matterA.toString(), orphan.toString());

    var mine = ids(list(TestJwtFactory.ownerJwt(ORG_ID, OWNER), "?mine=true"));
    // owner is lead on A and a member on B (after the lead transfer); never on the orphan
    assertThat(mine)
        .contains(matterA.toString(), matterB.toString(), dormant.toString())
        .doesNotContain(orphan.toString());
  }

  @Test
  void hasUnbilledKeepsOnlyMattersWithUnbilledBillableMinutes() throws Exception {
    var rows = ids(list(TestJwtFactory.ownerJwt(ORG_ID, OWNER), "?hasUnbilled=true"));
    assertThat(rows)
        .contains(matterA.toString(), staleButBilled.toString())
        // dormant has only non-billable time; B and the orphan have none
        .doesNotContain(dormant.toString(), matterB.toString(), orphan.toString());
  }

  @Test
  void inactiveSinceKeepsOnlyMattersWithNoActivityOnOrAfterTheDate() throws Exception {
    var rows =
        ids(list(TestJwtFactory.ownerJwt(ORG_ID, OWNER), "?inactiveSince=" + today.minusDays(90)));
    assertThat(rows)
        .contains(dormant.toString())
        // A/B/orphan rows changed today; stale-but-billed has a time entry dated yesterday
        .doesNotContain(
            matterA.toString(), matterB.toString(), orphan.toString(), staleButBilled.toString());
  }

  @Test
  void paramsCombineWithTheStatusFilter() throws Exception {
    var rows =
        ids(
            list(
                TestJwtFactory.ownerJwt(ORG_ID, OWNER),
                "?status=ACTIVE&mine=true&hasUnbilled=true"));
    assertThat(rows)
        .contains(matterA.toString(), staleButBilled.toString())
        .doesNotContain(orphan.toString(), dormant.toString(), matterB.toString());
  }

  // --- Statement cap ---

  @Test
  void fiftyMatterPageCostsAtMostSevenStatements() throws Exception {
    Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    assertThat(stats.isStatisticsEnabled())
        .as("hibernate.generate_statistics must be on in application-test.yml")
        .isTrue();

    // Default path: no view, default status (ACTIVE), no inactiveSince.
    var page =
        listCounted(
            stats, new MatterListQuery(null, null, null, null, false, false, null, Map.of()));
    assertThat(page.rows()).hasSizeGreaterThanOrEqualTo(MATTER_PAGE);
    // the page really exercised every aggregate (tags, trust, diary, money)
    var a = page.rows().stream().filter(r -> r.id().equals(matterA)).findFirst().orElseThrow();
    assertThat(a.tags()).isNotEmpty();
    assertThat(a.trustBalance()).isEqualByComparingTo("15000.00");
    assertThat(a.nextDiaryKind()).isEqualTo("COURT_DATE");
    assertThat(a.unbilledValue()).isEqualByComparingTo("1500.00");
    assertThat(page.statements())
        .as("GET /api/matters statement cap (arch §12.10.1)")
        .isLessThanOrEqualTo(STATEMENT_CAP);

    // One-matter page (only matter A is due before today+31): same statement count → no N+1.
    var single =
        listCounted(
            stats,
            new MatterListQuery(
                null, null, today.plusDays(31), null, false, false, null, Map.of()));
    assertThat(single.rows()).extracting(MatterListRow::id).containsExactly(matterA);
    assertThat(page.statements())
        .as("statement count is independent of page size")
        .isEqualTo(single.statements());
  }

  // --- helpers ---

  private record CountedPage(List<MatterListRow> rows, long statements) {}

  /** Runs the list as the owner (all capabilities) and counts the JDBC statements it prepares. */
  private CountedPage listCounted(Statistics stats, MatterListQuery query) throws Exception {
    var actor = new ActorContext(ownerId, "owner");
    // warm-up (plan caches etc.) — not counted
    TenantTestSupport.callAsActor(
        tenantSchema,
        ORG_ID,
        ownerId,
        "owner",
        Capability.ALL_NAMES,
        () -> projectService.listMatterRows(query, actor));
    long[] statements = new long[1];
    List<MatterListRow> rows =
        TenantTestSupport.callAsActor(
            tenantSchema,
            ORG_ID,
            ownerId,
            "owner",
            Capability.ALL_NAMES,
            () -> {
              stats.clear();
              var result = projectService.listMatterRows(query, actor);
              statements[0] = stats.getPrepareStatementCount();
              return result;
            });
    System.out.printf(
        "%n[MatterListRowIntegrationTest] rows=%d prepared statements=%d%n",
        rows.size(), statements[0]);
    return new CountedPage(rows, statements[0]);
  }

  private String list(RequestPostProcessor jwt, String query) throws Exception {
    return mockMvc
        .perform(get("/api/matters" + query).with(jwt))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static Map<String, Object> row(String json, UUID id) {
    List<Map<String, Object>> rows = JsonPath.read(json, "$[?(@.id == '" + id + "')]");
    assertThat(rows).as("row %s present", id).hasSize(1);
    return rows.getFirst();
  }

  private static List<String> ids(String json) {
    return JsonPath.read(json, "$[*].id");
  }

  private static BigDecimal money(Map<String, Object> row, String field) {
    Object value = row.get(field);
    assertThat(value).as("%s present", field).isNotNull();
    return new BigDecimal(value.toString());
  }

  private CourtDate courtDate(UUID matterId, LocalDate date) {
    return new CourtDate(
        matterId,
        clientId,
        "HEARING",
        date,
        null,
        "Johannesburg High Court",
        null,
        null,
        null,
        7,
        ownerId);
  }

  private void pushUpdatedAtBack(UUID matterId, UUID taskId) {
    transactionTemplate.executeWithoutResult(
        tx -> {
          entityManager
              .createNativeQuery(
                  "UPDATE projects SET updated_at = now() - interval '200 days' WHERE id = :id")
              .setParameter("id", matterId)
              .executeUpdate();
          entityManager
              .createNativeQuery(
                  "UPDATE tasks SET updated_at = now() - interval '200 days' WHERE id = :id")
              .setParameter("id", taskId)
              .executeUpdate();
        });
  }

  private UUID sync(String subject, String name, String role) throws Exception {
    return UUID.fromString(
        TestMemberHelper.syncMember(mockMvc, ORG_ID, subject, subject + "@test.com", name, role));
  }

  private void asOwner(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }

  private void createBillingRate(UUID memberId, String hourlyRate) throws Exception {
    mockMvc
        .perform(
            post("/api/billing-rates")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"memberId": "%s", "currency": "%s", "hourlyRate": %s, "effectiveFrom": "2020-01-01"}
                    """
                        .formatted(memberId, currency, hourlyRate)))
        .andExpect(status().isCreated());
  }

  private String createCustomRole(String name, String capability) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/org-roles")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"%s","description":"Test role","capabilities":["%s"]}"""
                            .formatted(name, capability)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private void assignRole(UUID memberId, String roleId) throws Exception {
    mockMvc
        .perform(
            put("/api/members/" + memberId + "/role")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orgRoleId\":\"%s\",\"capabilityOverrides\":[]}".formatted(roleId)))
        .andExpect(status().isOk());
  }
}
