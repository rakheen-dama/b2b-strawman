package io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ModuleNotEnabledException;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtCalendarService.CancelRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtCalendarService.CourtDateFilters;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtCalendarService.CreateCourtDateRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtCalendarService.OutcomeRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtCalendarService.PostponeRequest;
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
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CourtCalendarServiceTest {
  private static final String ORG_ID = "org_court_svc_test";
  private static final String DISABLED_ORG_ID = "org_court_svc_disabled";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private CourtCalendarService courtCalendarService;
  @Autowired private CourtDateRepository courtDateRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private AuditEventRepository auditEventRepository;

  private String tenantSchema;
  private String disabledTenantSchema;
  private UUID memberId;
  private UUID customerId;
  private UUID projectId;

  @BeforeAll
  void setup() throws Exception {
    // Provision enabled tenant
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Court Calendar Service Test Org", null)
            .schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_court_svc_owner",
                "court_svc@test.com",
                "Court Svc Owner",
                "owner"));

    // Enable the court_calendar module
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("court_calendar"));
                      orgSettingsRepository.save(settings);
                    }));

    // Create test customer and project
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.saveAndFlush(
                          createActiveCustomer("Court Test Corp", "court@test.com", memberId));
                  customerId = customer.getId();

                  var project = new Project("Smith v Jones", "Test matter", memberId);
                  project.setCustomerId(customerId);
                  project = projectRepository.saveAndFlush(project);
                  projectId = project.getId();
                }));

    // Provision disabled tenant (no modules enabled)
    disabledTenantSchema =
        provisioningService
            .provisionTenant(DISABLED_ORG_ID, "Court Disabled Org", null)
            .schemaName();
    TestMemberHelper.syncMember(
        mockMvc,
        DISABLED_ORG_ID,
        "user_court_svc_dis",
        "court_dis@test.com",
        "Court Disabled Owner",
        "owner");
  }

  @Test
  void createCourtDate_savesWithScheduledStatus() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var request =
                      new CreateCourtDateRequest(
                          projectId,
                          "HEARING",
                          LocalDate.of(2026, 5, 15),
                          LocalTime.of(10, 0),
                          "Johannesburg High Court",
                          "2026/12345",
                          "Judge Mogoeng",
                          "Application for summary judgment",
                          7);

                  var response = courtCalendarService.createCourtDate(request, memberId);

                  assertThat(response.status()).isEqualTo("SCHEDULED");
                  assertThat(response.dateType()).isEqualTo("HEARING");
                  assertThat(response.courtName()).isEqualTo("Johannesburg High Court");
                  assertThat(response.scheduledDate()).isEqualTo(LocalDate.of(2026, 5, 15));
                  assertThat(response.reminderDays()).isEqualTo(7);
                  assertThat(response.id()).isNotNull();
                }));
  }

  @Test
  void createCourtDate_resolvesCustomerIdFromProject() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var request =
                      new CreateCourtDateRequest(
                          projectId,
                          "TRIAL",
                          LocalDate.of(2026, 6, 1),
                          null,
                          "Cape Town High Court",
                          null,
                          null,
                          null,
                          null);

                  var response = courtCalendarService.createCourtDate(request, memberId);

                  assertThat(response.customerId()).isEqualTo(customerId);
                  assertThat(response.customerName()).isEqualTo("Court Test Corp");
                  assertThat(response.projectName()).isEqualTo("Smith v Jones");
                }));
  }

  @Test
  void postponeCourtDate_keepsOriginalAsPostponedAndCreatesNewScheduled() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var createRequest =
                      new CreateCourtDateRequest(
                          projectId,
                          "MOTION",
                          LocalDate.of(2026, 7, 10),
                          LocalTime.of(9, 30),
                          "Pretoria High Court",
                          null,
                          null,
                          "Original motion",
                          5);

                  var created = courtCalendarService.createCourtDate(createRequest, memberId);

                  var postponeRequest =
                      new PostponeRequest(LocalDate.of(2026, 8, 15), "Judge unavailable");
                  var newEntry =
                      courtCalendarService.postponeCourtDate(created.id(), postponeRequest);

                  // New entry should be SCHEDULED with the new date
                  assertThat(newEntry.status()).isEqualTo("SCHEDULED");
                  assertThat(newEntry.scheduledDate()).isEqualTo(LocalDate.of(2026, 8, 15));
                  assertThat(newEntry.id()).isNotEqualTo(created.id());
                  assertThat(newEntry.description()).isEqualTo("Rescheduled from 2026-07-10");
                  assertThat(newEntry.courtName()).isEqualTo("Pretoria High Court");

                  // Original should be POSTPONED with original date preserved
                  var original = courtCalendarService.getById(created.id());
                  assertThat(original.status()).isEqualTo("POSTPONED");
                  assertThat(original.scheduledDate()).isEqualTo(LocalDate.of(2026, 7, 10));
                  assertThat(original.outcome()).isEqualTo("Postponed: Judge unavailable");
                }));
  }

  @Test
  void postponeCourtDate_failsOnHeardStatus() {
    // Create and set to HEARD in one transaction
    final UUID[] courtDateId = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var createRequest =
                      new CreateCourtDateRequest(
                          projectId,
                          "HEARING",
                          LocalDate.of(2026, 7, 20),
                          null,
                          "Durban High Court",
                          null,
                          null,
                          null,
                          null);

                  var created = courtCalendarService.createCourtDate(createRequest, memberId);
                  courtCalendarService.recordOutcome(
                      created.id(), new OutcomeRequest("Judgment granted"));
                  courtDateId[0] = created.id();
                }));

    // Attempt postpone on HEARD court date in a separate scope (no transaction wrapping)
    runInTenant(
        () ->
            assertThatThrownBy(
                    () ->
                        courtCalendarService.postponeCourtDate(
                            courtDateId[0],
                            new PostponeRequest(LocalDate.of(2026, 9, 1), "Too late")))
                .isInstanceOf(InvalidStateException.class));
  }

  @Test
  void cancelCourtDate_setsCancelled() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var createRequest =
                      new CreateCourtDateRequest(
                          projectId,
                          "MEDIATION",
                          LocalDate.of(2026, 8, 5),
                          null,
                          "Bloemfontein High Court",
                          null,
                          null,
                          null,
                          null);

                  var created = courtCalendarService.createCourtDate(createRequest, memberId);
                  var cancelled =
                      courtCalendarService.cancelCourtDate(
                          created.id(), new CancelRequest("Settled out of court"));

                  assertThat(cancelled.status()).isEqualTo("CANCELLED");
                  assertThat(cancelled.outcome()).isEqualTo("Settled out of court");
                }));
  }

  @Test
  void recordOutcome_setsHeard() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var createRequest =
                      new CreateCourtDateRequest(
                          projectId,
                          "TRIAL",
                          LocalDate.of(2026, 9, 1),
                          LocalTime.of(9, 0),
                          "Pietermaritzburg High Court",
                          "2026/99999",
                          "Judge Smith",
                          "Criminal trial",
                          14);

                  var created = courtCalendarService.createCourtDate(createRequest, memberId);
                  var heard =
                      courtCalendarService.recordOutcome(
                          created.id(), new OutcomeRequest("Judgment in favor of plaintiff"));

                  assertThat(heard.status()).isEqualTo("HEARD");
                  assertThat(heard.outcome()).isEqualTo("Judgment in favor of plaintiff");
                }));
  }

  @Test
  void list_filtersByDateRange() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Create court dates in different date ranges
                  courtCalendarService.createCourtDate(
                      new CreateCourtDateRequest(
                          projectId,
                          "HEARING",
                          LocalDate.of(2026, 3, 1),
                          null,
                          "Court A",
                          null,
                          null,
                          null,
                          null),
                      memberId);
                  courtCalendarService.createCourtDate(
                      new CreateCourtDateRequest(
                          projectId,
                          "HEARING",
                          LocalDate.of(2026, 6, 15),
                          null,
                          "Court B",
                          null,
                          null,
                          null,
                          null),
                      memberId);
                  courtCalendarService.createCourtDate(
                      new CreateCourtDateRequest(
                          projectId,
                          "HEARING",
                          LocalDate.of(2026, 12, 20),
                          null,
                          "Court C",
                          null,
                          null,
                          null,
                          null),
                      memberId);

                  var filters =
                      new CourtDateFilters(
                          LocalDate.of(2026, 5, 1),
                          LocalDate.of(2026, 7, 31),
                          null,
                          null,
                          null,
                          null);
                  var page = courtCalendarService.list(filters, Pageable.ofSize(20));

                  assertThat(page.getContent()).hasSizeGreaterThanOrEqualTo(1);
                  assertThat(page.getContent())
                      .allSatisfy(
                          cd -> {
                            assertThat(cd.scheduledDate())
                                .isAfterOrEqualTo(LocalDate.of(2026, 5, 1));
                            assertThat(cd.scheduledDate())
                                .isBeforeOrEqualTo(LocalDate.of(2026, 7, 31));
                          });
                }));
  }

  @Test
  void createCourtDate_emitsAuditEvent() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var request =
                      new CreateCourtDateRequest(
                          projectId,
                          "CASE_MANAGEMENT",
                          LocalDate.of(2026, 10, 1),
                          null,
                          "Audit Test Court",
                          null,
                          null,
                          null,
                          null);

                  var response = courtCalendarService.createCourtDate(request, memberId);

                  var page =
                      auditEventRepository.findByFilter(
                          "court_date",
                          response.id(),
                          null,
                          "court_date.created",
                          null,
                          null,
                          Pageable.ofSize(10));
                  assertThat(page.getContent()).isNotEmpty();
                  assertThat(page.getContent().getFirst().getEventType())
                      .isEqualTo("court_date.created");
                }));
  }

  @Test
  void createCourtDate_throwsModuleNotEnabled_whenModuleDisabled() {
    ScopedValue.where(RequestScopes.TENANT_ID, disabledTenantSchema)
        .where(RequestScopes.ORG_ID, DISABLED_ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var request =
                  new CreateCourtDateRequest(
                      projectId,
                      "HEARING",
                      LocalDate.of(2026, 11, 1),
                      null,
                      "Disabled Court",
                      null,
                      null,
                      null,
                      null);

              assertThatThrownBy(() -> courtCalendarService.createCourtDate(request, memberId))
                  .isInstanceOf(ModuleNotEnabledException.class);
            });
  }

  // --- Helpers ---

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}
