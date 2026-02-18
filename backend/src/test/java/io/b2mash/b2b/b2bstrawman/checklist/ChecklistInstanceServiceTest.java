package io.b2mash.b2b.b2bstrawman.checklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
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
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChecklistInstanceServiceTest {

  private static final String ORG_ID = "org_checklist_instance_svc_test";

  @Autowired private ChecklistInstanceService checklistInstanceService;
  @Autowired private ChecklistInstanceRepository instanceRepository;
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

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Checklist Instance Service Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    var syncResult =
        memberSyncService.syncMember(
            ORG_ID, "user_ci_svc_test", "ci_svc_test@test.com", "CI SVC Tester", null, "owner");
    memberId = syncResult.memberId();
  }

  @Test
  void createFromTemplateCreatesInstanceWithStatusInProgress() {
    runInTenant(
        () -> {
          var templateId = createTemplateWithItems(2, false);
          var customerId = createCustomer();

          var instance = checklistInstanceService.createFromTemplate(templateId, customerId);

          assertThat(instance.getStatus()).isEqualTo("IN_PROGRESS");
          assertThat(instance.getTemplateId()).isEqualTo(templateId);
          assertThat(instance.getCustomerId()).isEqualTo(customerId);
          assertThat(instance.getStartedAt()).isNotNull();
          assertThat(instance.getCompletedAt()).isNull();
          assertThat(instance.getCompletedBy()).isNull();
          return null;
        });
  }

  @Test
  void createFromTemplateCreatesCorrectNumberOfItems() {
    runInTenant(
        () -> {
          var templateId = createTemplateWithItems(3, false);
          var customerId = createCustomer();

          var instance = checklistInstanceService.createFromTemplate(templateId, customerId);
          var items = instanceItemRepository.findByInstanceIdOrderBySortOrder(instance.getId());

          assertThat(items).hasSize(3);
          return null;
        });
  }

  @Test
  void createFromTemplateItemsHavePendingStatusWhenNoDependencies() {
    runInTenant(
        () -> {
          var templateId = createTemplateWithItems(2, false);
          var customerId = createCustomer();

          var instance = checklistInstanceService.createFromTemplate(templateId, customerId);
          var items = instanceItemRepository.findByInstanceIdOrderBySortOrder(instance.getId());

          assertThat(items).allMatch(item -> "PENDING".equals(item.getStatus()));
          return null;
        });
  }

  @Test
  void createFromTemplateSnapshotsFieldValues() {
    runInTenant(
        () -> {
          var template =
              new ChecklistTemplate(
                  "Snapshot Test", "desc", "snapshot-test", "INDIVIDUAL", "ORG_CUSTOM", false);
          template = templateRepository.save(template);

          var templateItem =
              new ChecklistTemplateItem(template.getId(), "Collect ID Document", 1, true);
          templateItem.setDescription("Photo ID required");
          templateItem.setRequiresDocument(true);
          templateItem.setRequiredDocumentLabel("ID Document");
          templateItem = templateItemRepository.save(templateItem);

          var customerId = createCustomer();
          var instance = checklistInstanceService.createFromTemplate(template.getId(), customerId);
          var items = instanceItemRepository.findByInstanceIdOrderBySortOrder(instance.getId());

          assertThat(items).hasSize(1);
          var item = items.getFirst();
          assertThat(item.getName()).isEqualTo("Collect ID Document");
          assertThat(item.getDescription()).isEqualTo("Photo ID required");
          assertThat(item.getSortOrder()).isEqualTo(1);
          assertThat(item.isRequired()).isTrue();
          assertThat(item.isRequiresDocument()).isTrue();
          assertThat(item.getRequiredDocumentLabel()).isEqualTo("ID Document");
          assertThat(item.getTemplateItemId()).isEqualTo(templateItem.getId());
          return null;
        });
  }

  @Test
  void createFromTemplateItemsAreOrderedBySortOrder() {
    runInTenant(
        () -> {
          var template =
              new ChecklistTemplate(
                  "Order Test", "desc", "order-test", "INDIVIDUAL", "ORG_CUSTOM", false);
          template = templateRepository.save(template);

          templateItemRepository.save(
              new ChecklistTemplateItem(template.getId(), "Third", 3, false));
          templateItemRepository.save(
              new ChecklistTemplateItem(template.getId(), "First", 1, true));
          templateItemRepository.save(
              new ChecklistTemplateItem(template.getId(), "Second", 2, false));

          var customerId = createCustomer();
          var instance = checklistInstanceService.createFromTemplate(template.getId(), customerId);
          var items = instanceItemRepository.findByInstanceIdOrderBySortOrder(instance.getId());

          assertThat(items)
              .extracting(ChecklistInstanceItem::getName)
              .containsExactly("First", "Second", "Third");
          assertThat(items)
              .extracting(ChecklistInstanceItem::getSortOrder)
              .containsExactly(1, 2, 3);
          return null;
        });
  }

  @Test
  void completeItemSetsStatusCompletedAndActorInfo() {
    runInTenant(
        () -> {
          var templateId = createTemplateWithItems(1, false);
          var customerId = createCustomer();

          var instance = checklistInstanceService.createFromTemplate(templateId, customerId);
          var items = instanceItemRepository.findByInstanceIdOrderBySortOrder(instance.getId());
          var itemId = items.getFirst().getId();
          var actorId = UUID.randomUUID();

          var completed = checklistInstanceService.completeItem(itemId, null, null, actorId);

          assertThat(completed.getStatus()).isEqualTo("COMPLETED");
          assertThat(completed.getCompletedAt()).isNotNull();
          assertThat(completed.getCompletedBy()).isEqualTo(actorId);
          return null;
        });
  }

  @Test
  void completeItemSetsNotesAndDocumentId() {
    runInTenant(
        () -> {
          var templateId = createTemplateWithItems(1, false);
          var customerId = createCustomer();

          var instance = checklistInstanceService.createFromTemplate(templateId, customerId);
          var items = instanceItemRepository.findByInstanceIdOrderBySortOrder(instance.getId());
          var itemId = items.getFirst().getId();
          var actorId = UUID.randomUUID();

          var completed =
              checklistInstanceService.completeItem(itemId, "All verified", null, actorId);

          assertThat(completed.getNotes()).isEqualTo("All verified");
          return null;
        });
  }

  @Test
  void skipItemSetsStatusSkippedAndNotes() {
    runInTenant(
        () -> {
          var templateId = createTemplateWithItems(1, false);
          var customerId = createCustomer();

          var instance = checklistInstanceService.createFromTemplate(templateId, customerId);
          var items = instanceItemRepository.findByInstanceIdOrderBySortOrder(instance.getId());
          var itemId = items.getFirst().getId();
          var actorId = UUID.randomUUID();

          var skipped =
              checklistInstanceService.skipItem(
                  itemId, "Not applicable for this customer", actorId);

          assertThat(skipped.getStatus()).isEqualTo("SKIPPED");
          assertThat(skipped.getNotes()).isEqualTo("Not applicable for this customer");
          return null;
        });
  }

  @Test
  void getProgressReturnsCorrectCounts() {
    runInTenant(
        () -> {
          var templateId = createTemplateWithItems(3, false);
          var customerId = createCustomer();

          var instance = checklistInstanceService.createFromTemplate(templateId, customerId);
          var items = instanceItemRepository.findByInstanceIdOrderBySortOrder(instance.getId());

          // Complete first item
          checklistInstanceService.completeItem(
              items.get(0).getId(), null, null, UUID.randomUUID());

          var progress = checklistInstanceService.getProgress(instance.getId());

          assertThat(progress.completed()).isEqualTo(1);
          assertThat(progress.total()).isEqualTo(3);
          return null;
        });
  }

  @Test
  void getProgressTracksRequiredItemsSeparately() {
    runInTenant(
        () -> {
          var template =
              new ChecklistTemplate(
                  "Progress Required Test",
                  "desc",
                  "progress-required-test",
                  "INDIVIDUAL",
                  "ORG_CUSTOM",
                  false);
          template = templateRepository.save(template);

          // 2 required, 1 optional
          templateItemRepository.save(
              new ChecklistTemplateItem(template.getId(), "Required 1", 1, true));
          templateItemRepository.save(
              new ChecklistTemplateItem(template.getId(), "Required 2", 2, true));
          templateItemRepository.save(
              new ChecklistTemplateItem(template.getId(), "Optional 1", 3, false));

          var customerId = createCustomer();
          var instance = checklistInstanceService.createFromTemplate(template.getId(), customerId);
          var items = instanceItemRepository.findByInstanceIdOrderBySortOrder(instance.getId());

          // Complete both required items
          checklistInstanceService.completeItem(
              items.get(0).getId(), null, null, UUID.randomUUID());
          checklistInstanceService.completeItem(
              items.get(1).getId(), null, null, UUID.randomUUID());

          var progress = checklistInstanceService.getProgress(instance.getId());

          assertThat(progress.requiredCompleted()).isEqualTo(2);
          assertThat(progress.requiredTotal()).isEqualTo(2);
          assertThat(progress.completed()).isEqualTo(2);
          assertThat(progress.total()).isEqualTo(3);
          return null;
        });
  }

  @Test
  void createFromTemplateThrowsWhenTemplateNotFound() {
    assertThatThrownBy(
            () ->
                runInTenant(
                    () ->
                        checklistInstanceService.createFromTemplate(
                            UUID.randomUUID(), UUID.randomUUID())))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void createFromTemplateDependencyResolutionMapsTemplateItemIdsToInstanceItemIds() {
    runInTenant(
        () -> {
          var template =
              new ChecklistTemplate(
                  "Dep Resolution Test",
                  "desc",
                  "dep-resolution-test",
                  "INDIVIDUAL",
                  "ORG_CUSTOM",
                  false);
          template = templateRepository.save(template);

          var item1 = new ChecklistTemplateItem(template.getId(), "Step 1", 1, true);
          item1 = templateItemRepository.save(item1);

          var item2 = new ChecklistTemplateItem(template.getId(), "Step 2 (depends on 1)", 2, true);
          item2.setDependsOnItemId(item1.getId());
          item2 = templateItemRepository.save(item2);

          var customerId = createCustomer();
          var instance = checklistInstanceService.createFromTemplate(template.getId(), customerId);
          var instanceItems =
              instanceItemRepository.findByInstanceIdOrderBySortOrder(instance.getId());

          assertThat(instanceItems).hasSize(2);

          var instanceItem1 = instanceItems.get(0);
          var instanceItem2 = instanceItems.get(1);

          // Instance item 2 should depend on instance item 1 (not template item 1)
          assertThat(instanceItem2.getDependsOnItemId()).isEqualTo(instanceItem1.getId());
          // Instance item 1 should have no dependency
          assertThat(instanceItem1.getDependsOnItemId()).isNull();
          return null;
        });
  }

  @Test
  void createFromTemplateItemsWithDependenciesStartAsBlocked() {
    runInTenant(
        () -> {
          var template =
              new ChecklistTemplate(
                  "Blocked Test", "desc", "blocked-test", "INDIVIDUAL", "ORG_CUSTOM", false);
          template = templateRepository.save(template);

          var item1 = new ChecklistTemplateItem(template.getId(), "Step 1", 1, true);
          item1 = templateItemRepository.save(item1);

          var item2 = new ChecklistTemplateItem(template.getId(), "Step 2 (blocked)", 2, true);
          item2.setDependsOnItemId(item1.getId());
          item2 = templateItemRepository.save(item2);

          var customerId = createCustomer();
          var instance = checklistInstanceService.createFromTemplate(template.getId(), customerId);
          var instanceItems =
              instanceItemRepository.findByInstanceIdOrderBySortOrder(instance.getId());

          assertThat(instanceItems.get(0).getStatus()).isEqualTo("PENDING");
          assertThat(instanceItems.get(1).getStatus()).isEqualTo("BLOCKED");
          return null;
        });
  }

  @Test
  void createFromTemplateItemsWithoutDependenciesStartAsPending() {
    runInTenant(
        () -> {
          var templateId = createTemplateWithItems(2, false);
          var customerId = createCustomer();

          var instance = checklistInstanceService.createFromTemplate(templateId, customerId);
          var items = instanceItemRepository.findByInstanceIdOrderBySortOrder(instance.getId());

          assertThat(items).allMatch(item -> "PENDING".equals(item.getStatus()));
          assertThat(items).allMatch(item -> item.getDependsOnItemId() == null);
          return null;
        });
  }

  @Test
  void createFromTemplateTwoPassResolutionCorrectlyMapsOldIdsToNewIds() {
    runInTenant(
        () -> {
          var template =
              new ChecklistTemplate(
                  "Two Pass Test", "desc", "two-pass-test", "INDIVIDUAL", "ORG_CUSTOM", false);
          template = templateRepository.save(template);

          // Create 3 items: item3 depends on item1
          var item1 = new ChecklistTemplateItem(template.getId(), "Step A", 1, true);
          item1 = templateItemRepository.save(item1);

          var item2 = new ChecklistTemplateItem(template.getId(), "Step B", 2, false);
          item2 = templateItemRepository.save(item2);

          var item3 = new ChecklistTemplateItem(template.getId(), "Step C (depends on A)", 3, true);
          item3.setDependsOnItemId(item1.getId());
          item3 = templateItemRepository.save(item3);

          var customerId = createCustomer();
          var instance = checklistInstanceService.createFromTemplate(template.getId(), customerId);
          var instanceItems =
              instanceItemRepository.findByInstanceIdOrderBySortOrder(instance.getId());

          assertThat(instanceItems).hasSize(3);

          var instanceItemA = instanceItems.get(0);
          var instanceItemB = instanceItems.get(1);
          var instanceItemC = instanceItems.get(2);

          // Item C's dependsOnItemId should point to the INSTANCE item A, not the template item A
          assertThat(instanceItemC.getDependsOnItemId()).isEqualTo(instanceItemA.getId());
          // The dependency ID should NOT be the template item ID
          assertThat(instanceItemC.getDependsOnItemId()).isNotEqualTo(item1.getId());
          // Item B has no dependency
          assertThat(instanceItemB.getDependsOnItemId()).isNull();
          // Item A has no dependency
          assertThat(instanceItemA.getDependsOnItemId()).isNull();
          return null;
        });
  }

  // ---- Helpers ----

  private int emailCounter = 0;

  private UUID createCustomer() {
    emailCounter++;
    var customer =
        new Customer(
            "Test Customer " + emailCounter,
            "ci_svc_customer_" + emailCounter + "@test.com",
            null,
            null,
            null,
            memberId);
    return customerRepository.save(customer).getId();
  }

  /**
   * Creates a template with the given number of items. If withDependency is true, item 2 depends on
   * item 1.
   */
  private UUID createTemplateWithItems(int itemCount, boolean withDependency) {
    var template =
        new ChecklistTemplate(
            "Test Template " + UUID.randomUUID().toString().substring(0, 8),
            "Test description",
            "test-template-" + UUID.randomUUID().toString().substring(0, 8),
            "INDIVIDUAL",
            "ORG_CUSTOM",
            false);
    template = templateRepository.save(template);

    UUID firstItemId = null;
    for (int i = 1; i <= itemCount; i++) {
      var item = new ChecklistTemplateItem(template.getId(), "Item " + i, i, i == 1);
      item = templateItemRepository.save(item);
      if (i == 1) {
        firstItemId = item.getId();
      }
      if (i == 2 && withDependency && firstItemId != null) {
        item.setDependsOnItemId(firstItemId);
        templateItemRepository.save(item);
      }
    }
    return template.getId();
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  private <T> T runInTenant(Callable<T> callable) {
    var auth =
        new TestingAuthenticationToken(
            "user_ci_svc_test", null, List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
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
