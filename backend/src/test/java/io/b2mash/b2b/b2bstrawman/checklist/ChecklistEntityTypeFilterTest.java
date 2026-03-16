package io.b2mash.b2b.b2bstrawman.checklist;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChecklistEntityTypeFilterTest {

  private static final String ORG_ID = "org_entity_type_filter_test";

  @Autowired private ChecklistInstanceService checklistInstanceService;
  @Autowired private ChecklistInstanceItemRepository instanceItemRepository;
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
    provisioningService.provisionTenant(ORG_ID, "Entity Type Filter Test Org", null);
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    var syncResult =
        memberSyncService.syncMember(
            ORG_ID, "user_etf_test", "etf_test@test.com", "ETF Tester", null, "owner");
    memberId = syncResult.memberId();
  }

  @Test
  void trustCustomerExcludesCompanyRegistrationAndIncludesTrustItems() {
    runInTenant(
        () -> {
          var template = createTemplateWithEntityTypeItems();
          var customer = createCustomerWithEntityType("TRUST");

          var instance =
              checklistInstanceService.createFromTemplate(
                  template.getId(), customer.getId(), customer);
          var items = instanceItemRepository.findByInstanceIdOrderBySortOrder(instance.getId());

          // TRUST should get: Universal Item, Trust Only Item, Company + Trust Item
          // TRUST should NOT get: Company Only Item
          var itemNames = items.stream().map(ChecklistInstanceItem::getName).toList();
          assertThat(itemNames).contains("Universal Item");
          assertThat(itemNames).contains("Trust Only Item");
          assertThat(itemNames).contains("Company + Trust Item");
          assertThat(itemNames).doesNotContain("Company Only Item");
          assertThat(items).hasSize(3);
          return null;
        });
  }

  @Test
  void soleProprietorExcludesCompanyAndTrustItems() {
    runInTenant(
        () -> {
          var template = createTemplateWithEntityTypeItems();
          var customer = createCustomerWithEntityType("SOLE_PROPRIETOR");

          var instance =
              checklistInstanceService.createFromTemplate(
                  template.getId(), customer.getId(), customer);
          var items = instanceItemRepository.findByInstanceIdOrderBySortOrder(instance.getId());

          // SOLE_PROPRIETOR should only get: Universal Item
          var itemNames = items.stream().map(ChecklistInstanceItem::getName).toList();
          assertThat(itemNames).contains("Universal Item");
          assertThat(itemNames).doesNotContain("Company Only Item");
          assertThat(itemNames).doesNotContain("Trust Only Item");
          assertThat(itemNames).doesNotContain("Company + Trust Item");
          assertThat(items).hasSize(1);
          return null;
        });
  }

  @Test
  void customerWithNoEntityTypeIncludesAllItems() {
    runInTenant(
        () -> {
          var template = createTemplateWithEntityTypeItems();
          var customer = createCustomerNoEntityType();

          var instance =
              checklistInstanceService.createFromTemplate(
                  template.getId(), customer.getId(), customer);
          var items = instanceItemRepository.findByInstanceIdOrderBySortOrder(instance.getId());

          // No entity type set = all items included (backwards compatible)
          assertThat(items).hasSize(4);
          return null;
        });
  }

  @Test
  void ptyLtdCustomerGetsCompanyAndUniversalItems() {
    runInTenant(
        () -> {
          var template = createTemplateWithEntityTypeItems();
          var customer = createCustomerWithEntityType("PTY_LTD");

          var instance =
              checklistInstanceService.createFromTemplate(
                  template.getId(), customer.getId(), customer);
          var items = instanceItemRepository.findByInstanceIdOrderBySortOrder(instance.getId());

          var itemNames = items.stream().map(ChecklistInstanceItem::getName).toList();
          assertThat(itemNames).contains("Universal Item");
          assertThat(itemNames).contains("Company Only Item");
          assertThat(itemNames).contains("Company + Trust Item");
          assertThat(itemNames).doesNotContain("Trust Only Item");
          assertThat(items).hasSize(3);
          return null;
        });
  }

  @Test
  void applicableEntityTypesSnapshotOnInstanceItem() {
    runInTenant(
        () -> {
          var template = createTemplateWithEntityTypeItems();
          var customer = createCustomerWithEntityType("TRUST");

          var instance =
              checklistInstanceService.createFromTemplate(
                  template.getId(), customer.getId(), customer);
          var items = instanceItemRepository.findByInstanceIdOrderBySortOrder(instance.getId());

          // Verify that the Trust Only Item has the snapshot of applicableEntityTypes
          var trustItem =
              items.stream()
                  .filter(i -> "Trust Only Item".equals(i.getName()))
                  .findFirst()
                  .orElseThrow();
          assertThat(trustItem.getApplicableEntityTypes()).containsExactly("TRUST");

          // Universal Item should have null applicableEntityTypes
          var universalItem =
              items.stream()
                  .filter(i -> "Universal Item".equals(i.getName()))
                  .findFirst()
                  .orElseThrow();
          assertThat(universalItem.getApplicableEntityTypes()).isNull();
          return null;
        });
  }

  // --- Helpers ---

  private ChecklistTemplate createTemplateWithEntityTypeItems() {
    counter++;
    var template =
        new ChecklistTemplate(
            "Entity Type Test " + counter,
            "test",
            "entity-type-test-" + counter + "-" + uuid8(),
            "INDIVIDUAL",
            "ORG_CUSTOM",
            false);
    template = templateRepository.save(template);

    // Item 1: Universal (null applicableEntityTypes)
    var item1 = new ChecklistTemplateItem(template.getId(), "Universal Item", 1, true);
    templateItemRepository.save(item1);

    // Item 2: Company only (PTY_LTD, CC, NPC)
    var item2 = new ChecklistTemplateItem(template.getId(), "Company Only Item", 2, true);
    item2.setApplicableEntityTypes(List.of("PTY_LTD", "CC", "NPC"));
    templateItemRepository.save(item2);

    // Item 3: Trust only
    var item3 = new ChecklistTemplateItem(template.getId(), "Trust Only Item", 3, true);
    item3.setApplicableEntityTypes(List.of("TRUST"));
    templateItemRepository.save(item3);

    // Item 4: Company + Trust
    var item4 = new ChecklistTemplateItem(template.getId(), "Company + Trust Item", 4, false);
    item4.setApplicableEntityTypes(List.of("PTY_LTD", "CC", "NPC", "TRUST"));
    templateItemRepository.save(item4);

    return template;
  }

  private Customer createCustomerWithEntityType(String entityType) {
    counter++;
    var customer =
        createActiveCustomer(
            "Test Customer " + counter,
            "etf_customer_" + counter + "_" + uuid8() + "@test.com",
            memberId);
    Map<String, Object> customFields = new HashMap<>();
    customFields.put("acct_entity_type", entityType);
    customer.setCustomFields(customFields);
    return customerRepository.save(customer);
  }

  private Customer createCustomerNoEntityType() {
    counter++;
    var customer =
        createActiveCustomer(
            "Test Customer " + counter,
            "etf_customer_" + counter + "_" + uuid8() + "@test.com",
            memberId);
    return customerRepository.save(customer);
  }

  private String uuid8() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  private <T> T runInTenant(Callable<T> callable) {
    var auth = new TestingAuthenticationToken("user_etf_test", null, List.of());
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
