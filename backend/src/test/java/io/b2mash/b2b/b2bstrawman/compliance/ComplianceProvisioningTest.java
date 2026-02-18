package io.b2mash.b2b.b2bstrawman.compliance;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceRepository;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateRepository;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerType;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
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
class ComplianceProvisioningTest {

  private static final String ORG_ID = "org_compliance_provisioning_test";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private MemberSyncService memberSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ChecklistTemplateRepository templateRepository;
  @Autowired private ChecklistInstanceRepository instanceRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerLifecycleService lifecycleService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private FieldDefinitionRepository fieldDefinitionRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;
  private int counter = 0;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Compliance Provisioning Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    var syncResult =
        memberSyncService.syncMember(
            ORG_ID, "user_cp_owner", "cp_owner@test.com", "CP Owner", null, "owner");
    memberId = syncResult.memberId();
  }

  @Test
  void newTenantHasGenericOnboardingTemplateSeeded() {
    runInTenant(
        () -> {
          var template = templateRepository.findBySlug("generic-client-onboarding");
          assertThat(template).isPresent();
          assertThat(template.get().isAutoInstantiate()).isTrue();
          assertThat(template.get().isActive()).isTrue();
          return null;
        });
  }

  @Test
  void ficaPacksSeededWithFieldDefinitions() {
    runInTenant(
        () -> {
          var saId =
              fieldDefinitionRepository.findByEntityTypeAndSlug(
                  EntityType.CUSTOMER, "sa_id_number");
          assertThat(saId).isPresent();
          return null;
        });
  }

  @Test
  void compliancePackStatusTracksAllThreePacks() {
    runInTenant(
        () -> {
          var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
          assertThat(settings.getCompliancePackStatus()).isNotNull();
          assertThat(settings.getCompliancePackStatus()).hasSize(3);
          return null;
        });
  }

  @Test
  void creatingCustomerAndStartingOnboardingInstantiatesGenericChecklist() {
    UUID customerId =
        runInTenant(
            () -> {
              counter++;
              var customer =
                  new Customer(
                      "Provisioning Customer " + counter,
                      "prov_" + counter + "@test.com",
                      null,
                      null,
                      null,
                      memberId,
                      CustomerType.INDIVIDUAL,
                      LifecycleStatus.PROSPECT);
              return customerRepository.save(customer).getId();
            });

    // Transition to ONBOARDING — should auto-instantiate generic-client-onboarding checklist
    runInTenant(() -> lifecycleService.transition(customerId, "ONBOARDING", null, memberId));

    runInTenant(
        () -> {
          var instances = instanceRepository.findByCustomerId(customerId);
          assertThat(instances).isNotEmpty();

          // Find the generic-onboarding template to confirm it's one of the instances
          var genericTemplate =
              templateRepository.findBySlug("generic-client-onboarding").orElseThrow();
          assertThat(instances).anyMatch(i -> i.getTemplateId().equals(genericTemplate.getId()));

          // All instances should be IN_PROGRESS
          assertThat(instances).allMatch(i -> "IN_PROGRESS".equals(i.getStatus()));
          return null;
        });
  }

  // --- Helpers ---

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
