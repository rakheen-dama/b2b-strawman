package io.b2mash.b2b.b2bstrawman.informationrequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.SchemaNameGenerator;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
class RequestReminderSchedulerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_req_reminder_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private RequestReminderScheduler scheduler;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private InformationRequestRepository informationRequestRepository;
  @Autowired private RequestItemRepository requestItemRepository;
  @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private UUID portalContactId;
  private String schema;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Request Reminder Test Org", null);
    memberId =
        UUID.fromString(
            syncMember("user_reminder_owner", "req-reminder@test.com", "Reminder Owner", "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    schema = SchemaNameGenerator.generateSchemaName(ORG_ID);

    // Create customer and portal contact via JDBC
    customerId = UUID.randomUUID();
    portalContactId = UUID.randomUUID();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  jdbcTemplate.update(
                      "INSERT INTO \"%s\".customers (id, name, email, customer_type, lifecycle_status, created_by, created_at, updated_at) VALUES (?::uuid, ?, ?, 'INDIVIDUAL', 'ACTIVE', ?::uuid, now(), now())"
                          .formatted(schema),
                      customerId.toString(),
                      "Reminder Test Customer",
                      "reminder-cust@test.com",
                      memberId.toString());

                  jdbcTemplate.update(
                      "INSERT INTO \"%s\".portal_contacts (id, org_id, customer_id, email, display_name, role, status, created_at, updated_at) VALUES (?::uuid, ?, ?::uuid, ?, ?, 'PRIMARY', 'ACTIVE', now(), now())"
                          .formatted(schema),
                      portalContactId.toString(),
                      ORG_ID,
                      customerId.toString(),
                      "reminder-contact@test.com",
                      "Reminder Contact");
                }));
  }

  @BeforeEach
  void cleanTestData() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  requestItemRepository.deleteAll();
                  informationRequestRepository.deleteAll();
                }));
  }

  @Test
  void reminderSent_whenRequestSentAndIntervalElapsed() {
    var requestId = new AtomicReference<UUID>();

    // Create request and item in one transaction
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var request = createRequest(RequestStatus.SENT, 3);
                  requestId.set(request.getId());
                  createPendingItem(request.getId(), "Bank Statement");
                }));

    // Set sentAt to 4 days ago via JDBC (after JPA transaction commits)
    setSentAt(requestId.get(), Instant.now().minus(4, ChronoUnit.DAYS));

    scheduler.checkRequestReminders();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var requests = informationRequestRepository.findAll();
                  assertThat(requests).hasSize(1);
                  assertThat(requests.getFirst().getLastReminderSentAt()).isNotNull();
                }));
  }

  @Test
  void reminderNotSent_whenIntervalNotElapsed() {
    var requestId = new AtomicReference<UUID>();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var request = createRequest(RequestStatus.SENT, 5);
                  requestId.set(request.getId());
                  createPendingItem(request.getId(), "Tax Return");
                }));

    // Set sentAt to 2 days ago (does not exceed 5-day interval)
    setSentAt(requestId.get(), Instant.now().minus(2, ChronoUnit.DAYS));

    scheduler.checkRequestReminders();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var requests = informationRequestRepository.findAll();
                  assertThat(requests).hasSize(1);
                  assertThat(requests.getFirst().getLastReminderSentAt()).isNull();
                }));
  }

  @Test
  void reminderNotSent_whenRequestInDraft() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var request = createRequest(RequestStatus.DRAFT, null);
                  createPendingItem(request.getId(), "Draft Item");
                }));

    scheduler.checkRequestReminders();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var requests = informationRequestRepository.findAll();
                  assertThat(requests).hasSize(1);
                  assertThat(requests.getFirst().getLastReminderSentAt()).isNull();
                }));
  }

  @Test
  void reminderNotSent_whenRequestCompleted() {
    var requestId = new AtomicReference<UUID>();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var request = createRequest(RequestStatus.SENT, null);
                  request.complete();
                  informationRequestRepository.save(request);
                  requestId.set(request.getId());
                  createPendingItem(request.getId(), "Completed Item");
                }));

    setSentAt(requestId.get(), Instant.now().minus(10, ChronoUnit.DAYS));

    scheduler.checkRequestReminders();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var requests = informationRequestRepository.findAll();
                  assertThat(requests).hasSize(1);
                  assertThat(requests.getFirst().getLastReminderSentAt()).isNull();
                }));
  }

  @Test
  void reminderNotSent_whenRequestCancelled() {
    var requestId = new AtomicReference<UUID>();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var request = createRequest(RequestStatus.SENT, null);
                  request.cancel();
                  informationRequestRepository.save(request);
                  requestId.set(request.getId());
                  createPendingItem(request.getId(), "Cancelled Item");
                }));

    setSentAt(requestId.get(), Instant.now().minus(10, ChronoUnit.DAYS));

    scheduler.checkRequestReminders();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var requests = informationRequestRepository.findAll();
                  assertThat(requests).hasSize(1);
                  assertThat(requests.getFirst().getLastReminderSentAt()).isNull();
                }));
  }

  @Test
  void reminderSent_usesRequestLevelOverride() {
    var requestId = new AtomicReference<UUID>();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var request = createRequest(RequestStatus.SENT, 2);
                  requestId.set(request.getId());
                  createPendingItem(request.getId(), "Override Item");
                }));

    // Sent 3 days ago, override is 2 days -> should trigger
    setSentAt(requestId.get(), Instant.now().minus(3, ChronoUnit.DAYS));

    scheduler.checkRequestReminders();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var requests = informationRequestRepository.findAll();
                  assertThat(requests).hasSize(1);
                  assertThat(requests.getFirst().getLastReminderSentAt()).isNotNull();
                }));
  }

  @Test
  void reminderSent_usesOrgDefault_whenNoOverride() {
    // Set org default to 3 days
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var orgSettings =
                      orgSettingsRepository
                          .findForCurrentTenant()
                          .orElseGet(() -> new OrgSettings("USD"));
                  orgSettings.setDefaultRequestReminderDays(3);
                  orgSettingsRepository.save(orgSettings);
                }));

    var requestId = new AtomicReference<UUID>();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var request = createRequest(RequestStatus.SENT, null);
                  requestId.set(request.getId());
                  createPendingItem(request.getId(), "Org Default Item");
                }));

    // Sent 4 days ago, org default is 3 -> should trigger
    setSentAt(requestId.get(), Instant.now().minus(4, ChronoUnit.DAYS));

    scheduler.checkRequestReminders();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var requests = informationRequestRepository.findAll();
                  assertThat(requests).hasSize(1);
                  assertThat(requests.getFirst().getLastReminderSentAt()).isNotNull();
                }));
  }

  @Test
  void deduplication_updatesLastReminderSentAt() {
    Instant firstReminderTime = Instant.now().minus(10, ChronoUnit.DAYS);
    var requestId = new AtomicReference<UUID>();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var request = createRequest(RequestStatus.IN_PROGRESS, 3);
                  request.setLastReminderSentAt(firstReminderTime);
                  informationRequestRepository.save(request);
                  requestId.set(request.getId());
                  createPendingItem(request.getId(), "Dedup Item");
                }));

    // Sent 15 days ago, last reminder 10 days ago, interval 3 -> should trigger
    setSentAt(requestId.get(), Instant.now().minus(15, ChronoUnit.DAYS));

    scheduler.checkRequestReminders();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var requests = informationRequestRepository.findAll();
                  assertThat(requests).hasSize(1);
                  // lastReminderSentAt should be updated to a time after firstReminderTime
                  assertThat(requests.getFirst().getLastReminderSentAt())
                      .isAfter(firstReminderTime);
                }));
  }

  // ========== Helpers ==========

  private InformationRequest createRequest(RequestStatus targetStatus, Integer reminderDays) {
    var request =
        new InformationRequest(
            "REQ-%04d".formatted((int) (Math.random() * 9999)),
            customerId,
            portalContactId,
            memberId);

    if (reminderDays != null) {
      request.setReminderIntervalDays(reminderDays);
    }

    request = informationRequestRepository.save(request);

    if (targetStatus == RequestStatus.SENT || targetStatus == RequestStatus.IN_PROGRESS) {
      request.send();
      request = informationRequestRepository.save(request);
    }

    if (targetStatus == RequestStatus.IN_PROGRESS) {
      request.markInProgress();
      request = informationRequestRepository.save(request);
    }

    return request;
  }

  private void setSentAt(UUID requestId, Instant sentAt) {
    // Use JDBC to set sentAt to a specific time (bypassing entity lifecycle)
    jdbcTemplate.update(
        "UPDATE \"%s\".information_requests SET sent_at = ? WHERE id = ?::uuid".formatted(schema),
        java.sql.Timestamp.from(sentAt),
        requestId.toString());
  }

  private void createPendingItem(UUID requestId, String name) {
    var item =
        new RequestItem(requestId, name, "Description", ResponseType.FILE_UPLOAD, true, null, 0);
    requestItemRepository.save(item);
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }

  private String syncMember(String clerkUserId, String email, String name, String orgRole)
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
                            .formatted(ORG_ID, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return com.jayway.jsonpath.JsonPath.read(
        result.getResponse().getContentAsString(), "$.memberId");
  }
}
