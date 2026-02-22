package io.b2mash.b2b.b2bstrawman.datarequest;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataSubjectRequestServiceTest {

  private static final String ORG_ID = "org_dsr_svc_test";

  @Autowired private DataSubjectRequestService dataSubjectRequestService;
  @Autowired private DataSubjectRequestRepository requestRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private MemberSyncService memberSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "DSR Service Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    var syncResult =
        memberSyncService.syncMember(
            ORG_ID, "user_dsr_svc_test", "dsr_svc@test.com", "DSR SVC Tester", null, "owner");
    memberId = syncResult.memberId();

    // Create a customer for tests
    customerId =
        runInTenant(
            () -> {
              var customer =
                  createActiveCustomer("DSR Test Customer", "dsr-customer@test.com", memberId);
              customer = customerRepository.save(customer);
              return customer.getId();
            });
  }

  @Test
  void createRequest_setsDeadlineFromOrgSettings() {
    // Set up OrgSettings with 14-day deadline
    runInTenant(
        () -> {
          var settings =
              orgSettingsRepository.findForCurrentTenant().orElseGet(() -> new OrgSettings("USD"));
          settings.setDataRequestDeadlineDays(14);
          orgSettingsRepository.save(settings);
        });

    var request =
        runInTenant(
            () ->
                dataSubjectRequestService.createRequest(
                    customerId, "ACCESS", "Need my data", memberId));

    assertThat(request.getDeadline()).isEqualTo(LocalDate.now().plusDays(14));
    assertThat(request.getStatus()).isEqualTo("RECEIVED");
    assertThat(request.getRequestType()).isEqualTo("ACCESS");
  }

  @Test
  void createRequest_usesDefault30DaysWhenOrgSettingsMissing() {
    // Delete any OrgSettings to test default
    runInTenant(
        () -> {
          orgSettingsRepository.findForCurrentTenant().ifPresent(orgSettingsRepository::delete);
        });

    var request =
        runInTenant(
            () ->
                dataSubjectRequestService.createRequest(
                    customerId, "DELETION", "Delete my data", memberId));

    assertThat(request.getDeadline()).isEqualTo(LocalDate.now().plusDays(30));
  }

  @Test
  void statusTransition_receivedToInProgress_succeeds() {
    var request =
        runInTenant(
            () ->
                dataSubjectRequestService.createRequest(
                    customerId, "ACCESS", "Access request", memberId));

    var updated =
        runInTenant(() -> dataSubjectRequestService.startProcessing(request.getId(), memberId));

    assertThat(updated.getStatus()).isEqualTo("IN_PROGRESS");
  }

  @Test
  void statusTransition_inProgressToCompleted_succeeds() {
    var request =
        runInTenant(
            () ->
                dataSubjectRequestService.createRequest(
                    customerId, "CORRECTION", "Correct my data", memberId));

    runInTenant(() -> dataSubjectRequestService.startProcessing(request.getId(), memberId));
    var completed =
        runInTenant(() -> dataSubjectRequestService.completeRequest(request.getId(), memberId));

    assertThat(completed.getStatus()).isEqualTo("COMPLETED");
    assertThat(completed.getCompletedAt()).isNotNull();
    assertThat(completed.getCompletedBy()).isEqualTo(memberId);
  }

  @Test
  void statusTransition_inProgressToRejected_setsReason() {
    var request =
        runInTenant(
            () ->
                dataSubjectRequestService.createRequest(
                    customerId, "OBJECTION", "Object to processing", memberId));

    runInTenant(() -> dataSubjectRequestService.startProcessing(request.getId(), memberId));
    var rejected =
        runInTenant(
            () ->
                dataSubjectRequestService.rejectRequest(
                    request.getId(), "Insufficient grounds", memberId));

    assertThat(rejected.getStatus()).isEqualTo("REJECTED");
    assertThat(rejected.getRejectionReason()).isEqualTo("Insufficient grounds");
    assertThat(rejected.getCompletedAt()).isNotNull();
    assertThat(rejected.getCompletedBy()).isEqualTo(memberId);
  }

  @Test
  void statusTransition_invalidTransition_throwsInvalidState() {
    var request =
        runInTenant(
            () ->
                dataSubjectRequestService.createRequest(
                    customerId, "ACCESS", "Test invalid", memberId));

    // Try to complete a RECEIVED request (should fail — needs IN_PROGRESS)
    assertThatThrownBy(
            () ->
                runInTenant(
                    () -> dataSubjectRequestService.completeRequest(request.getId(), memberId)))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void listByStatus_returnsFilteredRequests() {
    // Create two requests with different statuses
    var req1 =
        runInTenant(
            () ->
                dataSubjectRequestService.createRequest(
                    customerId, "ACCESS", "Request A", memberId));
    var req2 =
        runInTenant(
            () ->
                dataSubjectRequestService.createRequest(
                    customerId, "DELETION", "Request B", memberId));
    runInTenant(() -> dataSubjectRequestService.startProcessing(req2.getId(), memberId));

    var receivedList = runInTenant(() -> dataSubjectRequestService.listByStatus("RECEIVED"));
    var inProgressList = runInTenant(() -> dataSubjectRequestService.listByStatus("IN_PROGRESS"));

    assertThat(receivedList.stream().map(DataSubjectRequest::getId)).contains(req1.getId());
    assertThat(inProgressList.stream().map(DataSubjectRequest::getId)).contains(req2.getId());
  }

  @Test
  void auditEventPublished_onCreateRequest() {
    var request =
        runInTenant(
            () ->
                dataSubjectRequestService.createRequest(
                    customerId, "ACCESS", "Audit test", memberId));

    runInTenant(
        () -> {
          var page =
              auditEventRepository.findByFilter(
                  "data_subject_request",
                  request.getId(),
                  null,
                  "data.request.created",
                  null,
                  null,
                  Pageable.ofSize(10));
          assertThat(page.getContent()).isNotEmpty();
          assertThat(page.getContent().getFirst().getEventType()).isEqualTo("data.request.created");
        });
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  private <T> T runInTenant(Callable<T> callable) {
    var auth =
        new TestingAuthenticationToken(
            "user_dsr_svc_test", null, List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
    SecurityContextHolder.getContext().setAuthentication(auth);

    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .call(
            () ->
                transactionTemplate.execute(
                    tx -> {
                      try {
                        return callable.call();
                      } catch (RuntimeException e) {
                        throw e;
                      } catch (Exception e) {
                        throw new RuntimeException(e);
                      }
                    }));
  }

  private void runInTenant(Runnable runnable) {
    runInTenant(
        () -> {
          runnable.run();
          return null;
        });
  }
}
