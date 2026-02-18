package io.b2mash.b2b.b2bstrawman.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstance;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceRepository;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplate;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateItem;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateItemRepository;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateRepository;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerType;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChecklistInstantiationServiceTest {

  private static final String ORG_ID = "org_checklist_instantiation_test";

  @Autowired private ChecklistInstantiationService instantiationService;
  @Autowired private CustomerLifecycleService lifecycleService;
  @Autowired private ChecklistInstanceRepository instanceRepository;
  @Autowired private ChecklistTemplateRepository templateRepository;
  @Autowired private ChecklistTemplateItemRepository templateItemRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private MemberSyncService memberSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;
  private int counter = 0;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Checklist Instantiation Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    var syncResult =
        memberSyncService.syncMember(
            ORG_ID, "user_ci_owner", "ci_owner@test.com", "CI Owner", null, "owner");
    memberId = syncResult.memberId();
  }

  @Test
  void onboardingTransitionCreatesInstancesFromMatchingTemplates() {
    UUID customerId =
        runInTenant(
            () -> {
              var tpl = createAutoInstantiateTemplate("INDIVIDUAL");
              createTemplateItem(tpl.getId());
              return createCustomer(CustomerType.INDIVIDUAL, LifecycleStatus.PROSPECT).getId();
            });

    runInTenant(() -> lifecycleService.transition(customerId, "ONBOARDING", null, memberId));

    runInTenant(
        () -> {
          var instances = instanceRepository.findByCustomerId(customerId);
          // At least 1 created (from this test's template + possibly generic-onboarding from
          // compliance pack seeder)
          assertThat(instances).isNotEmpty();
          return null;
        });
  }

  @Test
  void anyTemplateMatchesAllCustomerTypes() {
    runInTenant(
        () -> {
          var anyTemplate = createAutoInstantiateTemplate("ANY");
          createTemplateItem(anyTemplate.getId());
          var customer = createCustomer(CustomerType.INDIVIDUAL, LifecycleStatus.PROSPECT);

          var created = instantiationService.instantiateForCustomer(customer);

          // Should include the ANY template (plus any others from pack seeder)
          assertThat(created).anyMatch(i -> i.getTemplateId().equals(anyTemplate.getId()));
          return null;
        });
  }

  @Test
  void individualTemplateDoesNotMatchCompanyCustomer() {
    runInTenant(
        () -> {
          var individualTemplate = createAutoInstantiateTemplate("INDIVIDUAL");
          createTemplateItem(individualTemplate.getId());
          var companyCustomer = createCustomer(CustomerType.COMPANY, LifecycleStatus.PROSPECT);

          var created = instantiationService.instantiateForCustomer(companyCustomer);

          // The INDIVIDUAL template should NOT be in the created list
          assertThat(created).noneMatch(i -> i.getTemplateId().equals(individualTemplate.getId()));
          return null;
        });
  }

  @Test
  void idempotentRetransitionDoesNotDuplicate() {
    runInTenant(
        () -> {
          var tpl = createAutoInstantiateTemplate("ANY");
          createTemplateItem(tpl.getId());
          var customer = createCustomer(CustomerType.INDIVIDUAL, LifecycleStatus.PROSPECT);

          // First call — creates instance
          var first = instantiationService.instantiateForCustomer(customer);
          // Second call — should skip, returns empty list for this template
          var second = instantiationService.instantiateForCustomer(customer);

          assertThat(first).anyMatch(i -> i.getTemplateId().equals(tpl.getId()));
          assertThat(second).noneMatch(i -> i.getTemplateId().equals(tpl.getId()));

          // Total instances for this customer for this template: exactly 1
          var all = instanceRepository.findByCustomerId(customer.getId());
          long countForTpl =
              all.stream().filter(i -> i.getTemplateId().equals(tpl.getId())).count();
          assertThat(countForTpl).isEqualTo(1);
          return null;
        });
  }

  @Test
  void cancelSetsInstancesToCancelled() {
    runInTenant(
        () -> {
          var tpl = createAutoInstantiateTemplate("ANY");
          createTemplateItem(tpl.getId());
          var customer = createCustomer(CustomerType.INDIVIDUAL, LifecycleStatus.PROSPECT);

          instantiationService.instantiateForCustomer(customer);

          int count = instantiationService.cancelActiveInstances(customer.getId());
          assertThat(count).isGreaterThan(0);

          var all = instanceRepository.findByCustomerId(customer.getId());
          assertThat(all)
              .filteredOn(i -> i.getTemplateId().equals(tpl.getId()))
              .allMatch(i -> "CANCELLED".equals(i.getStatus()));
          return null;
        });
  }

  @Test
  void onboardingToActiveBlockedWhenInstancesInProgress() {
    UUID customerId =
        runInTenant(
            () -> {
              var tpl = createAutoInstantiateTemplate("ANY");
              createTemplateItem(tpl.getId());
              var customer = createCustomer(CustomerType.INDIVIDUAL, LifecycleStatus.ONBOARDING);
              // Create instance manually (customer is already in ONBOARDING)
              instantiationService.instantiateForCustomer(customer);
              return customer.getId();
            });

    assertThatThrownBy(
            () ->
                runInTenant(
                    () -> lifecycleService.transition(customerId, "ACTIVE", null, memberId)))
        .isInstanceOf(InvalidStateException.class)
        .hasMessageContaining("Onboarding incomplete");
  }

  @Test
  void onboardingToActiveAllowedWhenAllCompleted() {
    runInTenant(
        () -> {
          // Create a customer with ONBOARDING status and no instances
          var customer = createCustomer(CustomerType.INDIVIDUAL, LifecycleStatus.ONBOARDING);
          // No instances — existsByCustomerIdAndStatusNot returns false — guard passes
          var result = lifecycleService.transition(customer.getId(), "ACTIVE", null, memberId);
          assertThat(result.getLifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);
          return null;
        });
  }

  @Test
  void cancelReturnsCountOfCancelledInstances() {
    runInTenant(
        () -> {
          // Create 2 templates + manually instantiate both
          var tpl1 = createAutoInstantiateTemplate("ANY");
          createTemplateItem(tpl1.getId());
          var tpl2 = createAutoInstantiateTemplate("ANY");
          createTemplateItem(tpl2.getId());
          var customer = createCustomer(CustomerType.INDIVIDUAL, LifecycleStatus.PROSPECT);

          instanceRepository.save(
              new ChecklistInstance(tpl1.getId(), customer.getId(), Instant.now()));
          instanceRepository.save(
              new ChecklistInstance(tpl2.getId(), customer.getId(), Instant.now()));

          int count = instantiationService.cancelActiveInstances(customer.getId());
          assertThat(count).isGreaterThanOrEqualTo(2);
          return null;
        });
  }

  // --- Helpers ---

  private ChecklistTemplate createAutoInstantiateTemplate(String customerType) {
    counter++;
    var tpl =
        new ChecklistTemplate(
            "Auto Tpl " + counter,
            "auto-instantiate template",
            "auto-tpl-" + counter + "-" + uuid8(),
            customerType,
            "ORG_CUSTOM",
            true);
    return templateRepository.save(tpl);
  }

  private void createTemplateItem(UUID templateId) {
    templateItemRepository.save(new ChecklistTemplateItem(templateId, "Step 1", 1, true));
  }

  private Customer createCustomer(CustomerType type, LifecycleStatus status) {
    counter++;
    var customer =
        new Customer(
            "Test Customer " + counter,
            "ci_test_" + counter + "_" + uuid8() + "@test.com",
            null,
            null,
            null,
            memberId,
            type,
            status);
    return customerRepository.save(customer);
  }

  private String uuid8() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  private <T> T runInTenant(Callable<T> callable) {
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
