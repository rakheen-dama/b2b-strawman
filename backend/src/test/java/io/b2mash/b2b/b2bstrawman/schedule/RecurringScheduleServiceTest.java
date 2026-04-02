package io.b2mash.b2b.b2bstrawman.schedule;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.informationrequest.InformationRequestRepository;
import io.b2mash.b2b.b2bstrawman.informationrequest.RequestTemplate;
import io.b2mash.b2b.b2bstrawman.informationrequest.RequestTemplateRepository;
import io.b2mash.b2b.b2bstrawman.informationrequest.TemplateSource;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationRepository;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplate;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplateRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.schedule.dto.CreateScheduleRequest;
import io.b2mash.b2b.b2bstrawman.schedule.dto.UpdateScheduleRequest;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplate;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplateCategory;
import io.b2mash.b2b.b2bstrawman.template.TemplateEntityType;
import io.b2mash.b2b.b2bstrawman.template.TestDocumentBuilder;
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
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecurringScheduleServiceTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_schedule_svc_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private RecurringScheduleService scheduleService;
  @Autowired private RecurringScheduleRepository scheduleRepository;
  @Autowired private ScheduleExecutionRepository executionRepository;
  @Autowired private ProjectTemplateRepository templateRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private InformationRequestRepository informationRequestRepository;
  @Autowired private RequestTemplateRepository requestTemplateRepository;
  @Autowired private PortalContactRepository portalContactRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private GeneratedDocumentRepository generatedDocumentRepository;

  private String tenantSchema;
  private UUID memberId;
  private UUID templateId;
  private UUID customerId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Schedule Service Test Org", null);
    memberId =
        UUID.fromString(
            syncMember(ORG_ID, "user_sched_owner", "sched_owner@test.com", "Sched Owner", "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create shared test data: a template and a customer
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      templateRepository.saveAndFlush(
                          new ProjectTemplate(
                              "Monthly Bookkeeping",
                              "{customer} Bookkeeping",
                              "Monthly bookkeeping template",
                              true,
                              "MANUAL",
                              null,
                              memberId));
                  templateId = template.getId();

                  var customer =
                      customerRepository.saveAndFlush(
                          createActiveCustomer("Acme Corp", "acme@test.com", memberId));
                  customerId = customer.getId();
                }));
  }

  @Test
  void create_withValidInput_persistsAndCalculatesNextExecution() {
    runInTenant(
        () -> {
          var request =
              new CreateScheduleRequest(
                  templateId,
                  customerId,
                  "MONTHLY",
                  LocalDate.of(2026, 3, 1),
                  LocalDate.of(2027, 3, 1),
                  5,
                  memberId,
                  "Override Name",
                  null);

          var response = scheduleService.create(request, memberId);

          assertThat(response.id()).isNotNull();
          assertThat(response.templateId()).isEqualTo(templateId);
          assertThat(response.templateName()).isEqualTo("Monthly Bookkeeping");
          assertThat(response.customerId()).isEqualTo(customerId);
          assertThat(response.customerName()).isEqualTo("Acme Corp");
          assertThat(response.frequency()).isEqualTo("MONTHLY");
          assertThat(response.status()).isEqualTo("ACTIVE");
          assertThat(response.nameOverride()).isEqualTo("Override Name");
          assertThat(response.leadTimeDays()).isEqualTo(5);
          // nextExecutionDate = periodStart(2026-03-01) - leadTimeDays(5) = 2026-02-24
          assertThat(response.nextExecutionDate()).isEqualTo(LocalDate.of(2026, 2, 24));
          assertThat(response.executionCount()).isZero();
          assertThat(response.projectLeadMemberId()).isEqualTo(memberId);
          assertThat(response.projectLeadName()).isEqualTo("Sched Owner");

          // Clean up
          transactionTemplate.executeWithoutResult(
              tx -> scheduleRepository.deleteById(response.id()));
        });
  }

  @Test
  void create_withInactiveTemplate_throws400() {
    runInTenant(
        () -> {
          // Create an inactive template
          var inactiveTemplate =
              transactionTemplate.execute(
                  tx -> {
                    var t =
                        templateRepository.saveAndFlush(
                            new ProjectTemplate(
                                "Inactive Template",
                                "{customer}",
                                null,
                                false,
                                "MANUAL",
                                null,
                                memberId));
                    t.deactivate();
                    return templateRepository.saveAndFlush(t);
                  });

          var request =
              new CreateScheduleRequest(
                  inactiveTemplate.getId(),
                  customerId,
                  "MONTHLY",
                  LocalDate.of(2026, 4, 1),
                  null,
                  0,
                  null,
                  null,
                  null);

          assertThatThrownBy(() -> scheduleService.create(request, memberId))
              .isInstanceOf(InvalidStateException.class);
        });
  }

  @Test
  void create_withNonExistentCustomer_throws404() {
    runInTenant(
        () -> {
          var request =
              new CreateScheduleRequest(
                  templateId,
                  UUID.randomUUID(),
                  "MONTHLY",
                  LocalDate.of(2026, 4, 1),
                  null,
                  0,
                  null,
                  null,
                  null);

          assertThatThrownBy(() -> scheduleService.create(request, memberId))
              .isInstanceOf(ResourceNotFoundException.class);
        });
  }

  @Test
  void create_duplicateSchedule_throws409() {
    runInTenant(
        () -> {
          var request =
              new CreateScheduleRequest(
                  templateId,
                  customerId,
                  "WEEKLY",
                  LocalDate.of(2026, 4, 1),
                  null,
                  0,
                  null,
                  null,
                  null);

          var first = scheduleService.create(request, memberId);

          assertThatThrownBy(() -> scheduleService.create(request, memberId))
              .isInstanceOf(ResourceConflictException.class);

          // Clean up
          transactionTemplate.executeWithoutResult(
              tx -> {
                var s = scheduleRepository.findById(first.id()).orElseThrow();
                s.setStatus("PAUSED");
                scheduleRepository.saveAndFlush(s);
              });
          scheduleService.delete(first.id());
        });
  }

  @Test
  void update_mutableFields_succeeds() {
    runInTenant(
        () -> {
          var createRequest =
              new CreateScheduleRequest(
                  templateId,
                  customerId,
                  "QUARTERLY",
                  LocalDate.of(2026, 4, 1),
                  null,
                  0,
                  null,
                  null,
                  null);
          var created = scheduleService.create(createRequest, memberId);

          var updateRequest =
              new UpdateScheduleRequest(
                  "Updated Name", LocalDate.of(2027, 12, 31), 10, memberId, null);

          var updated = scheduleService.update(created.id(), updateRequest);

          assertThat(updated.nameOverride()).isEqualTo("Updated Name");
          assertThat(updated.endDate()).isEqualTo(LocalDate.of(2027, 12, 31));
          assertThat(updated.leadTimeDays()).isEqualTo(10);
          assertThat(updated.projectLeadMemberId()).isEqualTo(memberId);

          // Clean up
          transactionTemplate.executeWithoutResult(
              tx -> {
                var s = scheduleRepository.findById(created.id()).orElseThrow();
                s.setStatus("PAUSED");
                scheduleRepository.saveAndFlush(s);
              });
          scheduleService.delete(created.id());
        });
  }

  @Test
  void get_existingSchedule_returnsWithResolvedNames() {
    runInTenant(
        () -> {
          var request =
              new CreateScheduleRequest(
                  templateId,
                  customerId,
                  "ANNUALLY",
                  LocalDate.of(2026, 6, 1),
                  null,
                  3,
                  memberId,
                  null,
                  null);
          var created = scheduleService.create(request, memberId);

          var retrieved = scheduleService.get(created.id());

          assertThat(retrieved.id()).isEqualTo(created.id());
          assertThat(retrieved.templateName()).isEqualTo("Monthly Bookkeeping");
          assertThat(retrieved.customerName()).isEqualTo("Acme Corp");
          assertThat(retrieved.projectLeadName()).isEqualTo("Sched Owner");

          // Clean up
          transactionTemplate.executeWithoutResult(
              tx -> {
                var s = scheduleRepository.findById(created.id()).orElseThrow();
                s.setStatus("PAUSED");
                scheduleRepository.saveAndFlush(s);
              });
          scheduleService.delete(created.id());
        });
  }

  @Test
  void list_filterByStatus_returnsMatching() {
    runInTenant(
        () -> {
          var request1 =
              new CreateScheduleRequest(
                  templateId,
                  customerId,
                  "SEMI_ANNUALLY",
                  LocalDate.of(2026, 5, 1),
                  null,
                  0,
                  null,
                  null,
                  null);
          var created1 = scheduleService.create(request1, memberId);

          // Pause the first one
          scheduleService.pause(created1.id());

          // List only PAUSED
          var paused = scheduleService.list("PAUSED", null, null);
          assertThat(paused).anyMatch(s -> s.id().equals(created1.id()));

          // List only ACTIVE should not contain the paused one
          var active = scheduleService.list("ACTIVE", null, null);
          assertThat(active).noneMatch(s -> s.id().equals(created1.id()));

          // Clean up
          scheduleService.delete(created1.id());
        });
  }

  @Test
  void pause_activeSchedule_transitionsToPaused() {
    runInTenant(
        () -> {
          var request =
              new CreateScheduleRequest(
                  templateId,
                  customerId,
                  "FORTNIGHTLY",
                  LocalDate.of(2026, 7, 1),
                  null,
                  2,
                  null,
                  null,
                  null);
          var created = scheduleService.create(request, memberId);

          var paused = scheduleService.pause(created.id());

          assertThat(paused.status()).isEqualTo("PAUSED");
          // nextExecutionDate should be preserved
          assertThat(paused.nextExecutionDate()).isEqualTo(created.nextExecutionDate());

          // Clean up
          scheduleService.delete(paused.id());
        });
  }

  @Test
  void resume_pausedSchedule_recalculatesNextExecution() {
    runInTenant(
        () -> {
          var request =
              new CreateScheduleRequest(
                  templateId,
                  customerId,
                  "MONTHLY",
                  LocalDate.of(2025, 1, 1),
                  null,
                  0,
                  null,
                  "Resume Test",
                  null);
          var created = scheduleService.create(request, memberId);

          // Pause first
          scheduleService.pause(created.id());

          // Resume -- should recalculate to a future date
          var resumed = scheduleService.resume(created.id());

          assertThat(resumed.status()).isEqualTo("ACTIVE");
          // After resume, nextExecutionDate should be in the future (or today)
          assertThat(resumed.nextExecutionDate()).isAfterOrEqualTo(LocalDate.now());

          // Clean up
          transactionTemplate.executeWithoutResult(
              tx -> {
                var s = scheduleRepository.findById(created.id()).orElseThrow();
                s.setStatus("PAUSED");
                scheduleRepository.saveAndFlush(s);
              });
          scheduleService.delete(created.id());
        });
  }

  @Test
  void complete_activeSchedule_transitionsToCompleted() {
    runInTenant(
        () -> {
          var request =
              new CreateScheduleRequest(
                  templateId,
                  customerId,
                  "MONTHLY",
                  LocalDate.of(2026, 8, 1),
                  null,
                  0,
                  null,
                  "Complete Test",
                  null);
          var created = scheduleService.create(request, memberId);

          var completed = scheduleService.complete(created.id());

          assertThat(completed.status()).isEqualTo("COMPLETED");

          // Completing again should throw
          assertThatThrownBy(() -> scheduleService.complete(created.id()))
              .isInstanceOf(InvalidStateException.class);

          // Clean up -- COMPLETED can be deleted
          scheduleService.delete(completed.id());
        });
  }

  @Test
  void delete_pausedSchedule_succeeds() {
    runInTenant(
        () -> {
          var request =
              new CreateScheduleRequest(
                  templateId,
                  customerId,
                  "MONTHLY",
                  LocalDate.of(2026, 9, 1),
                  null,
                  0,
                  null,
                  "Delete Paused Test",
                  null);
          var created = scheduleService.create(request, memberId);

          scheduleService.pause(created.id());
          scheduleService.delete(created.id());

          assertThatThrownBy(() -> scheduleService.get(created.id()))
              .isInstanceOf(ResourceNotFoundException.class);
        });
  }

  @Test
  void delete_activeSchedule_throws409() {
    runInTenant(
        () -> {
          var request =
              new CreateScheduleRequest(
                  templateId,
                  customerId,
                  "MONTHLY",
                  LocalDate.of(2026, 10, 1),
                  null,
                  0,
                  null,
                  "Delete Active Test",
                  null);
          var created = scheduleService.create(request, memberId);

          assertThatThrownBy(() -> scheduleService.delete(created.id()))
              .isInstanceOf(ResourceConflictException.class);

          // Clean up
          scheduleService.pause(created.id());
          scheduleService.delete(created.id());
        });
  }

  // --- Post-Create Action Tests (Tasks 383.6, 383.7) ---

  /**
   * Creates a unique customer for post-create action tests to avoid unique constraint collisions on
   * (template_id, customer_id, frequency).
   */
  private UUID createUniquePostCreateCustomer(String suffix) {
    var result = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var c =
                      customerRepository.saveAndFlush(
                          createActiveCustomer(
                              "PostCreate " + suffix, "pc-" + suffix + "@test.com", memberId));
                  result[0] = c.getId();
                }));
    return result[0];
  }

  @Test
  void executeSingleSchedule_withNullPostCreateActions_createsProjectNormally() {
    var cid = createUniquePostCreateCustomer("null-actions");
    runInTenant(
        () -> {
          var schedule =
              transactionTemplate.execute(
                  tx -> {
                    var s =
                        new RecurringSchedule(
                            templateId,
                            cid,
                            null,
                            "MONTHLY",
                            LocalDate.now(),
                            null,
                            0,
                            memberId,
                            memberId);
                    s.setNextExecutionDate(LocalDate.now());
                    // postCreateActions is null by default — regression test
                    return scheduleRepository.saveAndFlush(s);
                  });

          boolean result = scheduleService.executeSingleSchedule(schedule);

          assertThat(result).isTrue();

          transactionTemplate.executeWithoutResult(
              tx -> {
                var executions =
                    executionRepository.findByScheduleIdOrderByPeriodStartDesc(
                        schedule.getId(), PageRequest.of(0, 10));
                assertThat(executions.getContent()).hasSize(1);

                var updated = scheduleRepository.findById(schedule.getId()).orElseThrow();
                assertThat(updated.getExecutionCount()).isEqualTo(1);
                assertThat(updated.getPostCreateActions()).isNull();
              });
        });
  }

  @Test
  void executeSingleSchedule_withDocGenerateAction_failsGracefullyForMissingTemplate() {
    var cid = createUniquePostCreateCustomer("doc-fail");
    runInTenant(
        () -> {
          var schedule =
              transactionTemplate.execute(
                  tx -> {
                    var s =
                        new RecurringSchedule(
                            templateId,
                            cid,
                            null,
                            "MONTHLY",
                            LocalDate.now(),
                            null,
                            0,
                            memberId,
                            memberId);
                    s.setNextExecutionDate(LocalDate.now());
                    s.setPostCreateActions(
                        Map.of(
                            "generateDocument", Map.of("templateSlug", "non-existent-slug-383a")));
                    return scheduleRepository.saveAndFlush(s);
                  });

          // Execution should succeed — project is still created despite action failure
          boolean result = scheduleService.executeSingleSchedule(schedule);
          assertThat(result).isTrue();

          transactionTemplate.executeWithoutResult(
              tx -> {
                // Verify project was created
                var executions =
                    executionRepository.findByScheduleIdOrderByPeriodStartDesc(
                        schedule.getId(), PageRequest.of(0, 10));
                assertThat(executions.getContent()).hasSize(1);

                var projectId = executions.getContent().getFirst().getProjectId();
                assertThat(projectId).isNotNull();
                var project = projectRepository.findById(projectId);
                assertThat(project).isPresent();
              });
        });
  }

  @Test
  void executeSingleSchedule_withDocGenerateActionFailure_sendsNotification() {
    var cid = createUniquePostCreateCustomer("doc-notif");
    Instant beforeTest = Instant.now();

    runInTenant(
        () -> {
          var schedule =
              transactionTemplate.execute(
                  tx -> {
                    var s =
                        new RecurringSchedule(
                            templateId,
                            cid,
                            null,
                            "MONTHLY",
                            LocalDate.now(),
                            null,
                            0,
                            memberId,
                            memberId);
                    s.setNextExecutionDate(LocalDate.now());
                    s.setPostCreateActions(
                        Map.of(
                            "generateDocument",
                            Map.of("templateSlug", "missing-template-383a-notif")));
                    return scheduleRepository.saveAndFlush(s);
                  });

          scheduleService.executeSingleSchedule(schedule);

          // Verify POST_CREATE_ACTION_FAILED notification was sent
          transactionTemplate.executeWithoutResult(
              tx -> {
                var notifications =
                    notificationRepository.findByRecipientMemberId(memberId, PageRequest.of(0, 50));
                var failureNotifications =
                    notifications.getContent().stream()
                        .filter(n -> "POST_CREATE_ACTION_FAILED".equals(n.getType()))
                        .filter(n -> n.getCreatedAt().isAfter(beforeTest))
                        .toList();
                assertThat(failureNotifications).isNotEmpty();
                assertThat(failureNotifications.getFirst().getTitle())
                    .contains("document generation");
              });
        });
  }

  @Test
  void executeSingleSchedule_withInfoRequestAction_failsGracefullyForMissingTemplate() {
    var cid = createUniquePostCreateCustomer("info-fail");
    runInTenant(
        () -> {
          var schedule =
              transactionTemplate.execute(
                  tx -> {
                    var s =
                        new RecurringSchedule(
                            templateId,
                            cid,
                            null,
                            "MONTHLY",
                            LocalDate.now(),
                            null,
                            0,
                            memberId,
                            memberId);
                    s.setNextExecutionDate(LocalDate.now());
                    s.setPostCreateActions(
                        Map.of(
                            "sendInfoRequest",
                            Map.of(
                                "requestTemplateSlug", "non-existent-pack-383a", "dueDays", 14)));
                    return scheduleRepository.saveAndFlush(s);
                  });

          // Execution should succeed — project is still created despite action failure
          boolean result = scheduleService.executeSingleSchedule(schedule);
          assertThat(result).isTrue();

          transactionTemplate.executeWithoutResult(
              tx -> {
                var executions =
                    executionRepository.findByScheduleIdOrderByPeriodStartDesc(
                        schedule.getId(), PageRequest.of(0, 10));
                assertThat(executions.getContent()).hasSize(1);

                var projectId = executions.getContent().getFirst().getProjectId();
                assertThat(projectId).isNotNull();
                var project = projectRepository.findById(projectId);
                assertThat(project).isPresent();
              });
        });
  }

  @Test
  void executeSingleSchedule_withBothActions_bothFailGracefully() {
    var cid = createUniquePostCreateCustomer("both-fail");
    runInTenant(
        () -> {
          var schedule =
              transactionTemplate.execute(
                  tx -> {
                    var s =
                        new RecurringSchedule(
                            templateId,
                            cid,
                            null,
                            "MONTHLY",
                            LocalDate.now(),
                            null,
                            0,
                            memberId,
                            memberId);
                    s.setNextExecutionDate(LocalDate.now());
                    s.setPostCreateActions(
                        Map.of(
                            "generateDocument",
                            Map.of("templateSlug", "missing-doc-383a"),
                            "sendInfoRequest",
                            Map.of("requestTemplateSlug", "missing-req-383a", "dueDays", 7)));
                    return scheduleRepository.saveAndFlush(s);
                  });

          boolean result = scheduleService.executeSingleSchedule(schedule);
          assertThat(result).isTrue();

          transactionTemplate.executeWithoutResult(
              tx -> {
                // Project was still created
                var executions =
                    executionRepository.findByScheduleIdOrderByPeriodStartDesc(
                        schedule.getId(), PageRequest.of(0, 10));
                assertThat(executions.getContent()).hasSize(1);

                var updated = scheduleRepository.findById(schedule.getId()).orElseThrow();
                assertThat(updated.getExecutionCount()).isEqualTo(1);
              });
        });
  }

  // --- Happy-Path Integration Tests (Review Fix: prove the feature works) ---

  @Test
  void executeSingleSchedule_withSendInfoRequestAction_createsInformationRequest() {
    var cid = createUniquePostCreateCustomer("info-happy");
    runInTenant(
        () -> {
          // Set up: create a RequestTemplate with a known packId and a PortalContact
          var reqTemplateId = new UUID[1];
          var portalContactId = new UUID[1];
          transactionTemplate.executeWithoutResult(
              tx -> {
                var rt =
                    new RequestTemplate(
                        "Happy Path Template", "For testing", TemplateSource.CUSTOM);
                rt.setPackId("happy-path-pack-383a");
                rt = requestTemplateRepository.saveAndFlush(rt);
                reqTemplateId[0] = rt.getId();

                var pc =
                    new PortalContact(
                        ORG_ID,
                        cid,
                        "happy-contact@test.com",
                        "Happy Contact",
                        PortalContact.ContactRole.PRIMARY);
                pc = portalContactRepository.saveAndFlush(pc);
                portalContactId[0] = pc.getId();
              });

          var schedule =
              transactionTemplate.execute(
                  tx -> {
                    var s =
                        new RecurringSchedule(
                            templateId,
                            cid,
                            null,
                            "MONTHLY",
                            LocalDate.now(),
                            null,
                            0,
                            memberId,
                            memberId);
                    s.setNextExecutionDate(LocalDate.now());
                    s.setPostCreateActions(
                        Map.of(
                            "sendInfoRequest",
                            Map.of("requestTemplateSlug", "happy-path-pack-383a", "dueDays", 14)));
                    return scheduleRepository.saveAndFlush(s);
                  });

          boolean result = scheduleService.executeSingleSchedule(schedule);
          assertThat(result).isTrue();

          transactionTemplate.executeWithoutResult(
              tx -> {
                // Verify project was created
                var executions =
                    executionRepository.findByScheduleIdOrderByPeriodStartDesc(
                        schedule.getId(), PageRequest.of(0, 10));
                assertThat(executions.getContent()).hasSize(1);
                var projectId = executions.getContent().getFirst().getProjectId();
                assertThat(projectId).isNotNull();

                // Verify information request was created for the customer
                var infoRequests = informationRequestRepository.findByCustomerId(cid);
                assertThat(infoRequests).isNotEmpty();
                var infoRequest = infoRequests.getFirst();
                assertThat(infoRequest.getCustomerId()).isEqualTo(cid);
                assertThat(infoRequest.getProjectId()).isEqualTo(projectId);
                assertThat(infoRequest.getRequestTemplateId()).isEqualTo(reqTemplateId[0]);
                assertThat(infoRequest.getPortalContactId()).isEqualTo(portalContactId[0]);
                // Verify reminderIntervalDays is the default (7), not dueDays (14)
                assertThat(infoRequest.getReminderIntervalDays()).isEqualTo(7);
              });
        });
  }

  @Test
  void executeSingleSchedule_withBothActions_infoRequestSucceedsDocGenerationFailsGracefully() {
    var cid = createUniquePostCreateCustomer("both-happy");
    runInTenant(
        () -> {
          // Set up: create a RequestTemplate + PortalContact for the info request action
          transactionTemplate.executeWithoutResult(
              tx -> {
                var rt =
                    new RequestTemplate(
                        "Both Actions Template", "For testing both actions", TemplateSource.CUSTOM);
                rt.setPackId("both-actions-pack-383a");
                requestTemplateRepository.saveAndFlush(rt);

                var pc =
                    new PortalContact(
                        ORG_ID,
                        cid,
                        "both-contact@test.com",
                        "Both Contact",
                        PortalContact.ContactRole.PRIMARY);
                portalContactRepository.saveAndFlush(pc);
              });

          var schedule =
              transactionTemplate.execute(
                  tx -> {
                    var s =
                        new RecurringSchedule(
                            templateId,
                            cid,
                            null,
                            "MONTHLY",
                            LocalDate.now(),
                            null,
                            0,
                            memberId,
                            memberId);
                    s.setNextExecutionDate(LocalDate.now());
                    s.setPostCreateActions(
                        Map.of(
                            "generateDocument",
                            Map.of("templateSlug", "non-existent-doc-both-383a"),
                            "sendInfoRequest",
                            Map.of("requestTemplateSlug", "both-actions-pack-383a", "dueDays", 7)));
                    return scheduleRepository.saveAndFlush(s);
                  });

          boolean result = scheduleService.executeSingleSchedule(schedule);
          assertThat(result).isTrue();

          transactionTemplate.executeWithoutResult(
              tx -> {
                // Project was created despite doc generation failure
                var executions =
                    executionRepository.findByScheduleIdOrderByPeriodStartDesc(
                        schedule.getId(), PageRequest.of(0, 10));
                assertThat(executions.getContent()).hasSize(1);
                var projectId = executions.getContent().getFirst().getProjectId();
                assertThat(projectId).isNotNull();

                // Info request was still created (independent of doc generation failure)
                var infoRequests = informationRequestRepository.findByCustomerId(cid);
                assertThat(infoRequests).isNotEmpty();
                assertThat(infoRequests.getFirst().getProjectId()).isEqualTo(projectId);
              });
        });
  }

  @Test
  void executeSingleSchedule_withInvalidJsonbConfig_skipsActionGracefully() {
    var cid = createUniquePostCreateCustomer("invalid-jsonb");
    runInTenant(
        () -> {
          var schedule =
              transactionTemplate.execute(
                  tx -> {
                    var s =
                        new RecurringSchedule(
                            templateId,
                            cid,
                            null,
                            "MONTHLY",
                            LocalDate.now(),
                            null,
                            0,
                            memberId,
                            memberId);
                    s.setNextExecutionDate(LocalDate.now());
                    // Missing required keys in the JSONB config
                    s.setPostCreateActions(
                        Map.of(
                            "generateDocument", Map.of("wrongKey", "value"),
                            "sendInfoRequest", Map.of("wrongKey", "value")));
                    return scheduleRepository.saveAndFlush(s);
                  });

          // Should still succeed — invalid config is skipped with a warning, not an exception
          boolean result = scheduleService.executeSingleSchedule(schedule);
          assertThat(result).isTrue();

          transactionTemplate.executeWithoutResult(
              tx -> {
                var executions =
                    executionRepository.findByScheduleIdOrderByPeriodStartDesc(
                        schedule.getId(), PageRequest.of(0, 10));
                assertThat(executions.getContent()).hasSize(1);

                var updated = scheduleRepository.findById(schedule.getId()).orElseThrow();
                assertThat(updated.getExecutionCount()).isEqualTo(1);
              });
        });
  }

  @Test
  void executeSingleSchedule_withDocGenerateAction_generatesDocumentSuccessfully() {
    var cid = createUniquePostCreateCustomer("doc-happy");
    runInTenant(
        () -> {
          // Seed a DocumentTemplate with a known slug and PROJECT entity type
          var docTemplateSlug = "sched-doc-happy-383a";
          transactionTemplate.executeWithoutResult(
              tx -> {
                var content =
                    TestDocumentBuilder.doc()
                        .heading(1, "Project Report")
                        .variable("project.name")
                        .paragraph("Auto-generated project report.")
                        .build();
                var dt =
                    new DocumentTemplate(
                        TemplateEntityType.PROJECT,
                        "Schedule Doc Happy Template",
                        docTemplateSlug,
                        TemplateCategory.ENGAGEMENT_LETTER,
                        content);
                documentTemplateRepository.saveAndFlush(dt);
              });

          var schedule =
              transactionTemplate.execute(
                  tx -> {
                    var s =
                        new RecurringSchedule(
                            templateId,
                            cid,
                            null,
                            "MONTHLY",
                            LocalDate.now(),
                            null,
                            0,
                            memberId,
                            memberId);
                    s.setNextExecutionDate(LocalDate.now());
                    s.setPostCreateActions(
                        Map.of("generateDocument", Map.of("templateSlug", docTemplateSlug)));
                    return scheduleRepository.saveAndFlush(s);
                  });

          boolean result = scheduleService.executeSingleSchedule(schedule);
          assertThat(result).isTrue();

          transactionTemplate.executeWithoutResult(
              tx -> {
                // Verify project was created
                var executions =
                    executionRepository.findByScheduleIdOrderByPeriodStartDesc(
                        schedule.getId(), PageRequest.of(0, 10));
                assertThat(executions.getContent()).hasSize(1);
                var projectId = executions.getContent().getFirst().getProjectId();
                assertThat(projectId).isNotNull();

                // Verify generated document was created for the project
                var generatedDocs =
                    generatedDocumentRepository
                        .findByPrimaryEntityTypeAndPrimaryEntityIdOrderByGeneratedAtDesc(
                            TemplateEntityType.PROJECT, projectId);
                assertThat(generatedDocs).isNotEmpty();
                var generatedDoc = generatedDocs.getFirst();
                assertThat(generatedDoc.getPrimaryEntityId()).isEqualTo(projectId);
                assertThat(generatedDoc.getFileName()).isNotBlank();
                assertThat(generatedDoc.getFileSize()).isGreaterThan(0);
              });
        });
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }

  private String syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                    {"clerkOrgId":"%s","clerkUserId":"%s","email":"%s","name":"%s","avatarUrl":null,"orgRole":"%s"}
                    """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return com.jayway.jsonpath.JsonPath.read(
        result.getResponse().getContentAsString(), "$.memberId");
  }
}
