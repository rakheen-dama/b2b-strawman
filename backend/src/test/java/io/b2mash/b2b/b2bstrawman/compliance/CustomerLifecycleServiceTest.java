package io.b2mash.b2b.b2bstrawman.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventFilter;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomerLifecycleServiceTest {

  private static final String ORG_ID = "org_lifecycle_svc_test";

  @Autowired private CustomerLifecycleService lifecycleService;
  @Autowired private CustomerService customerService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private AuditService auditService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private MemberSyncService memberSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;
  private int emailCounter = 0;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Lifecycle Service Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Sync a member so we have a valid member ID for FK constraints
    var syncResult =
        memberSyncService.syncMember(
            ORG_ID, "user_lc_svc_test", "lc_svc_test@test.com", "LC SVC Tester", null, "owner");
    memberId = syncResult.memberId();
  }

  @Test
  void prospectToOnboardingSucceeds() {
    UUID customerId = createCustomer();
    var customer =
        runInTenant(() -> lifecycleService.transition(customerId, "ONBOARDING", "test", memberId));
    assertThat(customer.getLifecycleStatus()).isEqualTo(LifecycleStatus.ONBOARDING);
    assertThat(customer.getLifecycleStatusChangedAt()).isNotNull();
  }

  @Test
  void onboardingToActiveSucceeds() {
    // Create customer directly in ONBOARDING status (bypasses PROSPECT->ONBOARDING
    // auto-instantiation, so no checklist instances block the ONBOARDING->ACTIVE guard)
    UUID customerId = createCustomerWithStatus(LifecycleStatus.ONBOARDING);
    var customer =
        runInTenant(() -> lifecycleService.transition(customerId, "ACTIVE", "activated", memberId));
    assertThat(customer.getLifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);
  }

  @Test
  void activeToDormantSucceeds() {
    UUID customerId = createActiveCustomer();
    var customer =
        runInTenant(() -> lifecycleService.transition(customerId, "DORMANT", null, memberId));
    assertThat(customer.getLifecycleStatus()).isEqualTo(LifecycleStatus.DORMANT);
  }

  @Test
  void dormantToActiveSucceeds() {
    UUID customerId = createActiveCustomer();
    runInTenant(() -> lifecycleService.transition(customerId, "DORMANT", null, memberId));
    var customer =
        runInTenant(
            () -> lifecycleService.transition(customerId, "ACTIVE", "reactivated", memberId));
    assertThat(customer.getLifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);
  }

  @Test
  void activeToOffboardingSetsOffboardedAtOnOffboarded() {
    UUID customerId = createActiveCustomer();
    runInTenant(() -> lifecycleService.transition(customerId, "OFFBOARDING", null, memberId));
    var customer =
        runInTenant(() -> lifecycleService.transition(customerId, "OFFBOARDED", null, memberId));
    assertThat(customer.getLifecycleStatus()).isEqualTo(LifecycleStatus.OFFBOARDED);
    assertThat(customer.getOffboardedAt()).isNotNull();
  }

  @Test
  void offboardedCanReactivateToActive() {
    UUID customerId = createActiveCustomer();
    runInTenant(() -> lifecycleService.transition(customerId, "OFFBOARDING", null, memberId));
    runInTenant(() -> lifecycleService.transition(customerId, "OFFBOARDED", null, memberId));

    var customer =
        runInTenant(
            () -> lifecycleService.transition(customerId, "ACTIVE", "reactivate", memberId));
    assertThat(customer.getLifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);
    assertThat(customer.getOffboardedAt()).isNull();
  }

  @Test
  void offboardedCannotTransitionToProspect() {
    UUID customerId = createActiveCustomer();
    runInTenant(() -> lifecycleService.transition(customerId, "OFFBOARDING", null, memberId));
    runInTenant(() -> lifecycleService.transition(customerId, "OFFBOARDED", null, memberId));

    assertThatThrownBy(
            () ->
                runInTenant(
                    () ->
                        lifecycleService.transition(
                            customerId, "PROSPECT", "reactivate", memberId)))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void prospectToDormantThrowsConflict() {
    UUID customerId = createCustomer();
    assertThatThrownBy(
            () ->
                runInTenant(
                    () -> lifecycleService.transition(customerId, "DORMANT", null, memberId)))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void onboardingToDormantThrowsConflict() {
    UUID customerId = createCustomer();
    runInTenant(() -> lifecycleService.transition(customerId, "ONBOARDING", null, memberId));
    assertThatThrownBy(
            () ->
                runInTenant(
                    () -> lifecycleService.transition(customerId, "DORMANT", null, memberId)))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void invalidStatusStringThrows() {
    UUID customerId = createCustomer();
    assertThatThrownBy(
            () ->
                runInTenant(
                    () -> lifecycleService.transition(customerId, "BADVALUE", null, memberId)))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void auditEventPublishedOnTransition() {
    UUID customerId = createCustomer();
    runInTenant(
        () -> lifecycleService.transition(customerId, "ONBOARDING", "audit test", memberId));

    var events =
        runInTenant(
            () ->
                auditService
                    .findEvents(
                        new AuditEventFilter(
                            "customer", customerId, null, "customer.lifecycle.", null, null),
                        PageRequest.of(0, 10))
                    .getContent());

    assertThat(events).isNotEmpty();
    assertThat(events.getFirst().getEventType()).isEqualTo("customer.lifecycle.transitioned");
  }

  @Test
  void prospectToActiveDirectlyThrows() {
    UUID customerId = createCustomer();
    assertThatThrownBy(
            () ->
                runInTenant(
                    () -> lifecycleService.transition(customerId, "ACTIVE", null, memberId)))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void archiveSetsLifecycleToOffboarded() {
    UUID customerId = createActiveCustomer();
    var customer = runInTenant(() -> customerService.archiveCustomer(customerId));

    assertThat(customer.getStatus()).isEqualTo("ARCHIVED");
    assertThat(customer.getLifecycleStatus()).isEqualTo(LifecycleStatus.OFFBOARDED);
    assertThat(customer.getOffboardedAt()).isNotNull();
  }

  @Test
  void archivePreservesTerminalLifecycleStatus() {
    // Customer already in OFFBOARDING — archive should not change lifecycle to OFFBOARDED
    UUID customerId = createActiveCustomer();
    runInTenant(() -> lifecycleService.transition(customerId, "OFFBOARDING", null, memberId));

    var customer = runInTenant(() -> customerService.archiveCustomer(customerId));
    assertThat(customer.getStatus()).isEqualTo("ARCHIVED");
    assertThat(customer.getLifecycleStatus()).isEqualTo(LifecycleStatus.OFFBOARDING);
  }

  @Test
  void unarchiveSetsLifecycleToDormant() {
    UUID customerId = createActiveCustomer();
    runInTenant(() -> customerService.archiveCustomer(customerId));

    var customer = runInTenant(() -> customerService.unarchiveCustomer(customerId));
    assertThat(customer.getStatus()).isEqualTo("ACTIVE");
    assertThat(customer.getLifecycleStatus()).isEqualTo(LifecycleStatus.DORMANT);
    assertThat(customer.getOffboardedAt()).isNull();
  }

  @Test
  void unarchiveNonArchivedThrows() {
    UUID customerId = createActiveCustomer();
    assertThatThrownBy(() -> runInTenant(() -> customerService.unarchiveCustomer(customerId)))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void dormancyCheckReturnsResult() {
    // Create an active customer with no activity
    createActiveCustomer();

    var result = runInTenant(() -> lifecycleService.runDormancyCheck());
    assertThat(result.thresholdDays()).isGreaterThan(0);
    assertThat(result.candidates()).isNotNull();
    // The newly created ACTIVE customer should appear as dormant (no activity records)
    assertThat(result.candidates()).isNotEmpty();
  }

  // --- Helpers ---

  /** Creates a customer with PROSPECT lifecycle status (for lifecycle transition tests). */
  private UUID createCustomer() {
    return createCustomerWithStatus(LifecycleStatus.PROSPECT);
  }

  private UUID createCustomerWithStatus(LifecycleStatus status) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .call(
            () ->
                transactionTemplate.execute(
                    tx -> {
                      var customer =
                          new Customer(
                              "Test Corp " + (++emailCounter),
                              "lifecycle_svc_" + emailCounter + "@test.com",
                              null,
                              null,
                              null,
                              memberId,
                              null,
                              status);
                      return customerRepository.save(customer).getId();
                    }));
  }

  /** Creates a customer directly with ACTIVE lifecycle status. */
  private UUID createActiveCustomer() {
    return createCustomerWithStatus(LifecycleStatus.ACTIVE);
  }

  private <T> T runInTenant(java.util.concurrent.Callable<T> callable) {
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
