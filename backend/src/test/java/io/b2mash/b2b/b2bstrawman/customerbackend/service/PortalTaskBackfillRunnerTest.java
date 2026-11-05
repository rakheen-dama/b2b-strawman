package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalTaskView;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMapping;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplateRepository;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplateService;
import io.b2mash.b2b.b2bstrawman.projecttemplate.dto.InstantiateTemplateRequest;
import io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.task.PortalTaskEventPublisher;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * G88-F19 existing tenants (Phase 89 633A, §14.3.13, §14.8.4, R7.2, B3): a matter instantiated
 * before the live path published task events has no {@code portal.portal_tasks} rows; {@link
 * PortalTaskBackfillRunner} projects them once per tenant (insert-if-absent at {@code sort_order
 * 0}) and records a {@code portal_task_backfill} marker. The live publisher is mocked so the
 * instantiation reproduces a pre-633A tenant. Assertions go through the package-private {@code
 * backfillTenant} seam on this class's own tenant (never global counts — the shared test database
 * accumulates tenants); one {@code run(null)} smoke check covers the runner's wiring.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalTaskBackfillRunnerTest {

  private static final String ORG_ID = "org_portal_task_backfill";
  private static final String REPROVISION_ORG_ID = "org_portal_task_backfill_reprovision";
  private static final String TEMPLATE_NAME = "Litigation (Personal Injury / General)";
  private static final String LEAD_NAME = "Task Backfill Lead";

  @MockitoBean private PortalTaskEventPublisher portalTaskEventPublisher;

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ProjectTemplateService templateService;
  @Autowired private ProjectTemplateRepository templateRepository;
  @Autowired private CustomerService customerService;
  @Autowired private TaskRepository taskRepository;
  @Autowired private PortalReadModelRepository readModelRepo;
  @Autowired private PortalTaskBackfillRunner runner;
  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired
  @Qualifier("portalJdbcClient")
  private JdbcClient portalJdbc;

  private OrgSchemaMapping mapping;
  private UUID memberId;
  private UUID projectId;
  private List<UUID> taskIds;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Portal Task Backfill Firm", "legal-za");
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_portal_task_backfill_owner",
                "portal_task_backfill_owner@test.com",
                LEAD_NAME,
                "owner"));
    mapping = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow();
    var tenantTx = new TransactionTemplate(transactionManager);
    projectId =
        inTenant(
            () ->
                tenantTx.execute(
                    s -> {
                      UUID templateId =
                          templateRepository.findAll().stream()
                              .filter(t -> TEMPLATE_NAME.equals(t.getName()))
                              .findFirst()
                              .orElseThrow()
                              .getId();
                      var customer =
                          customerService.createCustomer(
                              "Backfill Client",
                              "portal-task-backfill-client@test.com",
                              null,
                              null,
                              null,
                              memberId);
                      return templateService
                          .instantiateTemplate(
                              templateId,
                              new InstantiateTemplateRequest(
                                  "Backfill Matter",
                                  customer.getId(),
                                  memberId,
                                  null,
                                  null,
                                  null,
                                  null),
                              memberId)
                          .getId();
                    }));
    taskIds =
        inTenant(
            () -> taskRepository.findByProjectId(projectId).stream().map(t -> t.getId()).toList());
    assertThat(taskIds).hasSize(9);
  }

  /** Simulates a tenant that predates 633A: no projected task rows, no marker. */
  @BeforeEach
  void resetTenant() {
    portalJdbc
        .sql("DELETE FROM portal.portal_tasks WHERE portal_project_id = ? AND org_id = ?")
        .params(projectId, ORG_ID)
        .update();
    portalJdbc
        .sql("DELETE FROM portal.portal_backfill_markers WHERE tenant_schema = ?")
        .params(mapping.getSchemaName())
        .update();
  }

  @Test
  void instantiationWithTheLivePathDisabledProjectsNothing() {
    assertThat(rows()).isEmpty();
  }

  @Test
  void backfillProjectsEveryLinkedTaskOnceAndIsSkippedOnTheSecondRun() {
    int first = runner.backfillTenant(mapping);

    assertThat(first).isEqualTo(9);
    assertThat(rows()).extracting(PortalTaskView::id).containsExactlyInAnyOrderElementsOf(taskIds);
    assertThat(rows()).extracting(PortalTaskView::sortOrder).containsOnly(0);
    assertThat(rows()).extracting(PortalTaskView::status).containsOnly("OPEN");
    assertThat(rows()).extracting(PortalTaskView::assigneeName).contains(LEAD_NAME);
    Map<String, Object> markerAfterFirst = marker(mapping.getSchemaName());
    assertThat(((Number) markerAfterFirst.get("row_count")).intValue()).isEqualTo(9);

    int second = runner.backfillTenant(mapping);

    assertThat(second).isEqualTo(-1); // marker present → skipped
    assertThat(marker(mapping.getSchemaName())).isEqualTo(markerAfterFirst);
    assertThat(rows()).hasSize(9);
  }

  @Test
  void aRowTheLiveListenerWroteIsNotOverwritten() {
    UUID liveTaskId = taskIds.getFirst();
    readModelRepo.upsertPortalTask(
        liveTaskId, ORG_ID, projectId, "Live listener name", "IN_PROGRESS", "Live Assignee", 7);

    int inserted = runner.backfillTenant(mapping);

    assertThat(inserted).isEqualTo(8);
    assertThat(((Number) marker(mapping.getSchemaName()).get("row_count")).intValue()).isEqualTo(8);
    var live = rows().stream().filter(r -> r.id().equals(liveTaskId)).findFirst().orElseThrow();
    assertThat(live.name()).isEqualTo("Live listener name");
    assertThat(live.status()).isEqualTo("IN_PROGRESS");
    assertThat(live.assigneeName()).isEqualTo("Live Assignee");
    assertThat(live.sortOrder()).isEqualTo(7);
    assertThat(rows()).hasSize(9);
  }

  @Test
  void reProvisioningAnOrgClearsItsStaleMarkersSoTheBackfillRunsAgain() {
    String schema = SchemaNameGenerator.generateSchemaName(REPROVISION_ORG_ID);
    // A stale marker left behind by a previous incarnation of this (deterministic) schema name.
    portalJdbc
        .sql(
            "INSERT INTO portal.portal_backfill_markers (backfill_name, tenant_schema, row_count)"
                + " VALUES (?, ?, 5) ON CONFLICT DO NOTHING")
        .params(PortalTaskBackfillRunner.NAME, schema)
        .update();

    provisioningService.provisionTenant(REPROVISION_ORG_ID, "Re-provisioned Firm", "legal-za");

    assertThat(markerCount(schema)).isZero();
    var reprovisioned =
        orgSchemaMappingRepository.findByClerkOrgId(REPROVISION_ORG_ID).orElseThrow();
    assertThat(runner.backfillTenant(reprovisioned)).isZero(); // backfilled, nothing linked yet
    assertThat(((Number) marker(schema).get("row_count")).intValue()).isZero();
  }

  @Test
  void runSmokeCheckBackfillsAnUnmarkedTenant() {
    runner.run(null);

    assertThat(rows()).extracting(PortalTaskView::id).containsExactlyInAnyOrderElementsOf(taskIds);
    assertThat(((Number) marker(mapping.getSchemaName()).get("row_count")).intValue()).isEqualTo(9);
  }

  // ── helpers ──

  private List<PortalTaskView> rows() {
    return readModelRepo.findTasksByProject(projectId, ORG_ID);
  }

  private Map<String, Object> marker(String schema) {
    return portalJdbc
        .sql(
            "SELECT row_count, backfilled_at FROM portal.portal_backfill_markers"
                + " WHERE backfill_name = ? AND tenant_schema = ?")
        .params(PortalTaskBackfillRunner.NAME, schema)
        .query()
        .singleRow();
  }

  private int markerCount(String schema) {
    return portalJdbc
        .sql("SELECT count(*) FROM portal.portal_backfill_markers WHERE tenant_schema = ?")
        .params(schema)
        .query(Integer.class)
        .single();
  }

  private <T> T inTenant(Callable<T> action) {
    try {
      return ScopedValue.where(RequestScopes.TENANT_ID, mapping.getSchemaName())
          .where(RequestScopes.ORG_ID, ORG_ID)
          .where(RequestScopes.MEMBER_ID, memberId)
          .call(action::call);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
