package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalDeadlineViewRepository;
import io.b2mash.b2b.b2bstrawman.event.FieldDateApproachingEvent;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldType;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for {@link DeadlinePortalSyncService} (Epic 497A). Exercises the full
 * event-listener + read-model stack on embedded Postgres (no mocks), covering the 5 scenarios in
 * the Epic 497A test plan:
 *
 * <ol>
 *   <li>Filing-schedule synth source → FILING deadline row (drives the shared write seam).
 *   <li>Re-syncing the same (source_entity, id) is idempotent — only one row, last_synced_at
 *       progresses.
 *   <li>{@code FieldDateApproachingEvent} with {@code portalVisibleDeadline=true} inserts a row;
 *       flag=false is a noop.
 *   <li>Court-date synth source → COURT_DATE row (same write seam, different source tag).
 *   <li>Status auto-derives from due-date proximity (OVERDUE / DUE_SOON / UPCOMING).
 * </ol>
 *
 * <p>Scenarios 1 &amp; 4 exercise {@link DeadlinePortalSyncService#upsertFromDeadline} directly
 * because the event classes for filing schedules, court dates and prescriptions are not yet wired
 * (see the class javadoc on the SUT). Scenario 3 drives the event path, gating on the {@code
 * FieldDefinition.portalVisibleDeadline} flag introduced by this epic (ADR-257).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeadlinePortalSyncServiceIntegrationTest {

  private static final String ORG_ID = "org_portal_deadline_sync_test";
  private static final String ORG_NAME = "Portal Deadline Sync Test Org";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private FieldDefinitionRepository fieldDefinitionRepository;
  @Autowired private PortalDeadlineViewRepository deadlineRepo;
  @Autowired private DeadlinePortalSyncService syncService;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private PlatformTransactionManager transactionManager;

  private String tenantSchema;
  private UUID memberId;
  private TransactionTemplate tenantTxTemplate;

  private UUID customerId;
  private UUID projectId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, ORG_NAME, "legal-za");
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_deadline_sync_owner",
                "deadline_sync_owner@test.com",
                "Dee Owner",
                "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    tenantTxTemplate = new TransactionTemplate(transactionManager);

    // Create a customer + a project linked to that customer once for the class.
    runInTenant(
        () ->
            tenantTxTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Deadline Customer", "deadline@test.com", memberId));
                  customerId = customer.getId();

                  var project =
                      projectRepository.save(
                          new Project("Deadline Project", "Matter 001", memberId));
                  projectId = project.getId();

                  customerProjectRepository.save(
                      new CustomerProject(customerId, projectId, memberId));
                }));
  }

  // ==========================================================================
  // Scenario 1 — Filing-schedule synth source upserts a FILING row
  // ==========================================================================

  @Test
  void filingSchedule_upsertsRowWithFilingTypeAndFilingScheduleSource() {
    UUID filingId = UUID.randomUUID();
    LocalDate dueDate = LocalDate.now().plusDays(30);

    runInTenant(
        () ->
            syncService.upsertFromDeadline(
                new DeadlinePortalSyncService.DeadlineSource(
                    "FILING_SCHEDULE",
                    "FILING",
                    filingId,
                    customerId,
                    projectId,
                    "VAT 201 — 2026-Q1",
                    dueDate,
                    null,
                    "Submit VAT 201 return",
                    "VAT-2026-Q1")));

    var row =
        deadlineRepo
            .findByCustomerIdAndSourceEntityAndId(customerId, "FILING_SCHEDULE", filingId)
            .orElseThrow();
    assertThat(row.id()).isEqualTo(filingId);
    assertThat(row.sourceEntity()).isEqualTo("FILING_SCHEDULE");
    assertThat(row.deadlineType()).isEqualTo("FILING");
    assertThat(row.customerId()).isEqualTo(customerId);
    assertThat(row.matterId()).isEqualTo(projectId);
    assertThat(row.dueDate()).isEqualTo(dueDate);
    assertThat(row.label()).isEqualTo("VAT 201 — 2026-Q1");
    // 30 days out → UPCOMING.
    assertThat(row.status()).isEqualTo("UPCOMING");
  }

  // ==========================================================================
  // Scenario 2 — Re-upsert on the same (source_entity, id) is idempotent
  // ==========================================================================

  @Test
  void filingSchedule_reSyncUpdatesSameRowIdempotently() {
    UUID filingId = UUID.randomUUID();
    LocalDate originalDue = LocalDate.now().plusDays(30);
    LocalDate revisedDue = LocalDate.now().plusDays(45);

    runInTenant(
        () ->
            syncService.upsertFromDeadline(
                new DeadlinePortalSyncService.DeadlineSource(
                    "FILING_SCHEDULE",
                    "FILING",
                    filingId,
                    customerId,
                    projectId,
                    "CIPC annual return",
                    originalDue,
                    null,
                    "Draft",
                    "CIPC-2026")));

    var first =
        deadlineRepo
            .findByCustomerIdAndSourceEntityAndId(customerId, "FILING_SCHEDULE", filingId)
            .orElseThrow();

    runInTenant(
        () ->
            syncService.upsertFromDeadline(
                new DeadlinePortalSyncService.DeadlineSource(
                    "FILING_SCHEDULE",
                    "FILING",
                    filingId,
                    customerId,
                    projectId,
                    "CIPC annual return (revised)",
                    revisedDue,
                    "COMPLETED",
                    "Submitted",
                    "CIPC-2026")));

    // Still exactly one row for this (source_entity, id) pair.
    var rows =
        deadlineRepo.findByCustomer(customerId, LocalDate.now().minusDays(1), revisedDue, null);
    long matchCount = rows.stream().filter(r -> r.id().equals(filingId)).count();
    assertThat(matchCount).isEqualTo(1);

    var second =
        deadlineRepo
            .findByCustomerIdAndSourceEntityAndId(customerId, "FILING_SCHEDULE", filingId)
            .orElseThrow();
    assertThat(second.label()).isEqualTo("CIPC annual return (revised)");
    assertThat(second.dueDate()).isEqualTo(revisedDue);
    assertThat(second.status()).isEqualTo("COMPLETED");
    // last_synced_at advanced (monotonic) on re-upsert.
    assertThat(second.lastSyncedAt()).isAfterOrEqualTo(first.lastSyncedAt());
  }

  // ==========================================================================
  // Scenario 3 — FieldDateApproachingEvent with portalVisibleDeadline=true
  //              inserts a row; flag=false publishes but produces NO row.
  // ==========================================================================

  @Test
  void fieldDateApproaching_insertsOnlyWhenPortalVisibleDeadlineIsTrue() {
    // Opted-in field.
    FieldDefinition visibleField =
        runInTenantReturning(
            () ->
                tenantTxTemplate.execute(
                    tx -> {
                      var fd =
                          new FieldDefinition(
                              EntityType.CUSTOMER,
                              "Contract renewal",
                              "contract_renewal_" + UUID.randomUUID().toString().substring(0, 6),
                              FieldType.DATE);
                      fd.setPortalVisibleDeadline(true);
                      return fieldDefinitionRepository.saveAndFlush(fd);
                    }));

    // NOT opted-in field.
    FieldDefinition hiddenField =
        runInTenantReturning(
            () ->
                tenantTxTemplate.execute(
                    tx -> {
                      var fd =
                          new FieldDefinition(
                              EntityType.CUSTOMER,
                              "Internal review",
                              "internal_review_" + UUID.randomUUID().toString().substring(0, 6),
                              FieldType.DATE);
                      fd.setPortalVisibleDeadline(false);
                      return fieldDefinitionRepository.saveAndFlush(fd);
                    }));

    LocalDate dueDate = LocalDate.now().plusDays(14);

    // Publish event for the opted-in field — expect a row.
    publishFieldDateApproaching(
        customerId, visibleField.getSlug(), visibleField.getName(), dueDate, "Deadline Customer");

    var visibleRow =
        deadlineRepo.findByCustomerIdAndSourceEntityAndId(
            customerId, "CUSTOM_FIELD_DATE", customerId);
    assertThat(visibleRow).isPresent();
    assertThat(visibleRow.get().deadlineType()).isEqualTo("CUSTOM_DATE");
    assertThat(visibleRow.get().dueDate()).isEqualTo(dueDate);
    // 14 days out → UPCOMING (boundary is 7 days, exclusive of 14).
    assertThat(visibleRow.get().status()).isEqualTo("UPCOMING");

    // Use a DIFFERENT customer so the hidden-field case can't collide with the previous row.
    // We do this by removing the visible-field row first, then asserting the hidden-field event
    // produced no row at all.
    runInTenant(() -> deadlineRepo.deleteBySourceEntityAndId("CUSTOM_FIELD_DATE", customerId));

    publishFieldDateApproaching(
        customerId, hiddenField.getSlug(), hiddenField.getName(), dueDate, "Deadline Customer");

    var hiddenRow =
        deadlineRepo.findByCustomerIdAndSourceEntityAndId(
            customerId, "CUSTOM_FIELD_DATE", customerId);
    assertThat(hiddenRow).isEmpty();
  }

  // ==========================================================================
  // Scenario 4 — Court-date synth source → COURT_DATE row
  // ==========================================================================

  @Test
  void courtDate_upsertsRowWithCourtDateTypeAndCourtDateSource() {
    UUID courtDateId = UUID.randomUUID();
    LocalDate hearing = LocalDate.now().plusDays(20);

    runInTenant(
        () ->
            syncService.upsertFromDeadline(
                new DeadlinePortalSyncService.DeadlineSource(
                    "COURT_DATE",
                    "COURT_DATE",
                    courtDateId,
                    customerId,
                    projectId,
                    "Trial — Johannesburg High Court",
                    hearing,
                    null,
                    "Hearing on matter 001",
                    "MATTER-001")));

    var row =
        deadlineRepo
            .findByCustomerIdAndSourceEntityAndId(customerId, "COURT_DATE", courtDateId)
            .orElseThrow();
    assertThat(row.sourceEntity()).isEqualTo("COURT_DATE");
    assertThat(row.deadlineType()).isEqualTo("COURT_DATE");
    assertThat(row.label()).isEqualTo("Trial — Johannesburg High Court");
    assertThat(row.status()).isEqualTo("UPCOMING");
  }

  // ==========================================================================
  // Scenario 5 — Status auto-derives from due-date proximity
  // ==========================================================================

  @Test
  void deriveStatus_producesDueSoonForProximateDatesAndOverdueForPast() {
    UUID dueSoonId = UUID.randomUUID();
    UUID overdueId = UUID.randomUUID();

    LocalDate fiveDaysOut = LocalDate.now().plusDays(5);
    LocalDate tenDaysAgo = LocalDate.now().minusDays(10);

    runInTenant(
        () -> {
          syncService.upsertFromDeadline(
              new DeadlinePortalSyncService.DeadlineSource(
                  "FILING_SCHEDULE",
                  "FILING",
                  dueSoonId,
                  customerId,
                  projectId,
                  "Close to deadline",
                  fiveDaysOut,
                  null,
                  null,
                  "REF-1"));
          syncService.upsertFromDeadline(
              new DeadlinePortalSyncService.DeadlineSource(
                  "FILING_SCHEDULE",
                  "FILING",
                  overdueId,
                  customerId,
                  projectId,
                  "Missed deadline",
                  tenDaysAgo,
                  null,
                  null,
                  "REF-2"));
        });

    var dueSoonRow =
        deadlineRepo
            .findByCustomerIdAndSourceEntityAndId(customerId, "FILING_SCHEDULE", dueSoonId)
            .orElseThrow();
    var overdueRow =
        deadlineRepo
            .findByCustomerIdAndSourceEntityAndId(customerId, "FILING_SCHEDULE", overdueId)
            .orElseThrow();

    assertThat(dueSoonRow.status()).isEqualTo("DUE_SOON");
    assertThat(overdueRow.status()).isEqualTo("OVERDUE");
  }

  // ==========================================================================
  // Helpers
  // ==========================================================================

  private void publishFieldDateApproaching(
      UUID entityId, String slug, String label, LocalDate dueDate, String entityName) {
    // Wrap in a tenant scope + transaction so the AFTER_COMMIT listener fires once the outer
    // transaction commits. The listener rebinds its own tenant scope from event.tenantId().
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () ->
                tenantTxTemplate.executeWithoutResult(
                    tx ->
                        eventPublisher.publishEvent(
                            new FieldDateApproachingEvent(
                                "field_date.approaching",
                                "customer",
                                entityId,
                                null,
                                null,
                                "system",
                                tenantSchema,
                                ORG_ID,
                                Instant.now(),
                                Map.of(
                                    "field_name", slug,
                                    "field_label", label,
                                    "field_value", dueDate.toString(),
                                    "days_until", 14,
                                    "entity_name", entityName)))));
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }

  private <T> T runInTenantReturning(java.util.function.Supplier<T> supplier) {
    final Object[] holder = new Object[1];
    runInTenant(() -> holder[0] = supplier.get());
    @SuppressWarnings("unchecked")
    T result = (T) holder[0];
    return result;
  }
}
