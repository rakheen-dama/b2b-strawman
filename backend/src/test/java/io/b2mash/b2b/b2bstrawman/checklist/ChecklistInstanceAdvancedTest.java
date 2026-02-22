package io.b2mash.b2b.b2bstrawman.checklist;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerType;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChecklistInstanceAdvancedTest {

  private static final String ORG_ID = "org_checklist_advanced_test";
  private static final String API_KEY = "test-api-key";

  @Autowired private MockMvc mockMvc;
  @Autowired private ChecklistInstanceService checklistInstanceService;
  @Autowired private ChecklistInstanceRepository instanceRepository;
  @Autowired private ChecklistInstanceItemRepository instanceItemRepository;
  @Autowired private ChecklistTemplateRepository templateRepository;
  @Autowired private ChecklistTemplateItemRepository templateItemRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private io.b2mash.b2b.b2bstrawman.document.DocumentRepository documentRepository;
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
    provisioningService.provisionTenant(ORG_ID, "Checklist Advanced Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    var syncResult =
        memberSyncService.syncMember(
            ORG_ID, "user_adv_owner", "adv_owner@test.com", "Adv Owner", null, "owner");
    memberId = syncResult.memberId();
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  // ---- 102.8: Dependency chain enforcement ----

  @Test
  void blockedItemCannotBeCompleted_returns409() throws Exception {
    var ids =
        runInTenant(
            () -> {
              var templateId = createTemplateWithDependency();
              var customerId = createCustomer();
              var instance = checklistInstanceService.createFromTemplate(templateId, customerId);
              var items = instanceItemRepository.findByInstanceIdOrderBySortOrder(instance.getId());
              // item 0 = PENDING prerequisite, item 1 = BLOCKED dependent
              return new UUID[] {items.get(0).getId(), items.get(1).getId()};
            });

    UUID blockedItemId = ids[1];

    mockMvc
        .perform(
            put("/api/checklist-items/" + blockedItemId + "/complete")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\": null, \"documentId\": null}"))
        .andExpect(status().isConflict());
  }

  @Test
  void completingPrerequisiteUnblocksDependent() throws Exception {
    var ids =
        runInTenant(
            () -> {
              var templateId = createTemplateWithDependency();
              var customerId = createCustomer();
              var instance = checklistInstanceService.createFromTemplate(templateId, customerId);
              var items = instanceItemRepository.findByInstanceIdOrderBySortOrder(instance.getId());
              return new UUID[] {items.get(0).getId(), items.get(1).getId()};
            });

    UUID prerequisiteId = ids[0];
    UUID dependentId = ids[1];

    // Complete prerequisite
    mockMvc
        .perform(
            put("/api/checklist-items/" + prerequisiteId + "/complete")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\": null, \"documentId\": null}"))
        .andExpect(status().isOk());

    // Dependent should now be PENDING
    runInTenant(
        () -> {
          var refreshed = instanceItemRepository.findById(dependentId).orElseThrow();
          assertThat(refreshed.getStatus()).isEqualTo("PENDING");
          return null;
        });
  }

  // ---- 102.9: Document requirement validation ----

  @Test
  void documentRequiredButMissing_returns400() throws Exception {
    var itemId =
        runInTenant(
            () -> {
              var template =
                  new ChecklistTemplate(
                      "Doc Test " + uuid8(),
                      "desc",
                      "doc-test-" + uuid8(),
                      "INDIVIDUAL",
                      "ORG_CUSTOM",
                      false);
              template = templateRepository.save(template);

              var templateItem = new ChecklistTemplateItem(template.getId(), "Upload ID", 1, true);
              templateItem.setRequiresDocument(true);
              templateItem.setRequiredDocumentLabel("Government ID");
              templateItemRepository.save(templateItem);

              var customerId = createCustomer();
              var instance =
                  checklistInstanceService.createFromTemplate(template.getId(), customerId);
              return instanceItemRepository
                  .findByInstanceIdOrderBySortOrder(instance.getId())
                  .getFirst()
                  .getId();
            });

    mockMvc
        .perform(
            put("/api/checklist-items/" + itemId + "/complete")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\": null, \"documentId\": null}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void documentProvidedAllowsCompletion() throws Exception {
    var ids =
        runInTenant(
            () -> {
              var template =
                  new ChecklistTemplate(
                      "Doc Allow " + uuid8(),
                      "desc",
                      "doc-allow-" + uuid8(),
                      "INDIVIDUAL",
                      "ORG_CUSTOM",
                      false);
              template = templateRepository.save(template);

              var templateItem =
                  new ChecklistTemplateItem(template.getId(), "Upload Contract", 1, true);
              templateItem.setRequiresDocument(true);
              templateItem.setRequiredDocumentLabel("Signed Contract");
              templateItemRepository.save(templateItem);

              var customerId = createCustomer();
              var instance =
                  checklistInstanceService.createFromTemplate(template.getId(), customerId);
              var itemId =
                  instanceItemRepository
                      .findByInstanceIdOrderBySortOrder(instance.getId())
                      .getFirst()
                      .getId();

              // Create a real document to satisfy FK constraint
              var doc =
                  new io.b2mash.b2b.b2bstrawman.document.Document(
                      "CUSTOMER",
                      null,
                      customerId,
                      "contract.pdf",
                      "application/pdf",
                      1024,
                      memberId,
                      "INTERNAL");
              doc = documentRepository.save(doc);
              return new UUID[] {itemId, doc.getId()};
            });

    UUID itemId = ids[0];
    UUID docId = ids[1];

    mockMvc
        .perform(
            put("/api/checklist-items/" + itemId + "/complete")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\": \"Verified\", \"documentId\": \"%s\"}".formatted(docId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.documentId").value(docId.toString()));
  }

  // ---- 102.10: Auto-cascade completion ----

  @Test
  void allRequiredItemsComplete_instanceAutoCompletes() {
    runInTenant(
        () -> {
          var template =
              new ChecklistTemplate(
                  "Auto Complete " + uuid8(),
                  "desc",
                  "auto-complete-" + uuid8(),
                  "INDIVIDUAL",
                  "ORG_CUSTOM",
                  false);
          template = templateRepository.save(template);
          templateItemRepository.save(
              new ChecklistTemplateItem(template.getId(), "Step 1", 1, true));

          var customerId = createCustomer();
          var instance = checklistInstanceService.createFromTemplate(template.getId(), customerId);
          var items = instanceItemRepository.findByInstanceIdOrderBySortOrder(instance.getId());

          checklistInstanceService.completeItem(items.get(0).getId(), null, null, memberId);

          var refreshed = instanceRepository.findById(instance.getId()).orElseThrow();
          assertThat(refreshed.getStatus()).isEqualTo("COMPLETED");
          assertThat(refreshed.getCompletedAt()).isNotNull();
          assertThat(refreshed.getCompletedBy()).isEqualTo(memberId);
          return null;
        });
  }

  @Test
  void allInstancesComplete_lifecycleAdvancesToActive() {
    runInTenant(
        () -> {
          // Create customer in ONBOARDING lifecycle status
          var customer =
              new Customer(
                  "Onboarding Customer " + uuid8(),
                  "onboarding" + emailCounter++ + "@test.com",
                  null,
                  null,
                  null,
                  memberId,
                  CustomerType.INDIVIDUAL,
                  LifecycleStatus.ONBOARDING);
          customer = customerRepository.save(customer);
          final UUID customerId = customer.getId();

          // Create a simple 1-item required template + instance
          var template =
              new ChecklistTemplate(
                  "Lifecycle Advance " + uuid8(),
                  "desc",
                  "lifecycle-advance-" + uuid8(),
                  "INDIVIDUAL",
                  "ORG_CUSTOM",
                  false);
          template = templateRepository.save(template);
          templateItemRepository.save(
              new ChecklistTemplateItem(template.getId(), "Final Step", 1, true));

          var instance = checklistInstanceService.createFromTemplate(template.getId(), customerId);
          var items = instanceItemRepository.findByInstanceIdOrderBySortOrder(instance.getId());

          // Complete the only required item
          checklistInstanceService.completeItem(items.get(0).getId(), null, null, memberId);

          // Customer should now be ACTIVE
          var refreshedCustomer = customerRepository.findById(customerId).orElseThrow();
          assertThat(refreshedCustomer.getLifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);
          return null;
        });
  }

  @Test
  void optionalItemsNotCompleted_doNotBlockInstanceCompletion() {
    runInTenant(
        () -> {
          var template =
              new ChecklistTemplate(
                  "Optional Test " + uuid8(),
                  "desc",
                  "optional-test-" + uuid8(),
                  "INDIVIDUAL",
                  "ORG_CUSTOM",
                  false);
          template = templateRepository.save(template);
          // 1 required, 1 optional
          templateItemRepository.save(
              new ChecklistTemplateItem(template.getId(), "Required Step", 1, true));
          templateItemRepository.save(
              new ChecklistTemplateItem(template.getId(), "Optional Step", 2, false));

          var customerId = createCustomer();
          var instance = checklistInstanceService.createFromTemplate(template.getId(), customerId);
          var items = instanceItemRepository.findByInstanceIdOrderBySortOrder(instance.getId());

          // Complete only the required item
          checklistInstanceService.completeItem(items.get(0).getId(), null, null, memberId);

          var refreshed = instanceRepository.findById(instance.getId()).orElseThrow();
          // Instance should complete even though optional item is still PENDING
          assertThat(refreshed.getStatus()).isEqualTo("COMPLETED");
          return null;
        });
  }

  // ---- 102.11: Reopen item ----

  @Test
  void reopenItem_revertsStatusToPending() throws Exception {
    var itemId =
        runInTenant(
            () -> {
              var templateId = createSimpleTemplate(1, false);
              var customerId = createCustomer();
              var instance = checklistInstanceService.createFromTemplate(templateId, customerId);
              var items = instanceItemRepository.findByInstanceIdOrderBySortOrder(instance.getId());
              var id = items.get(0).getId();
              checklistInstanceService.completeItem(id, "Done", null, memberId);
              return id;
            });

    mockMvc
        .perform(put("/api/checklist-items/" + itemId + "/reopen").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"));

    runInTenant(
        () -> {
          var refreshed = instanceItemRepository.findById(itemId).orElseThrow();
          assertThat(refreshed.getStatus()).isEqualTo("PENDING");
          assertThat(refreshed.getCompletedAt()).isNull();
          assertThat(refreshed.getCompletedBy()).isNull();
          assertThat(refreshed.getNotes()).isNull();
          return null;
        });
  }

  @Test
  void reopenItem_revertsCompletedInstanceToInProgress() {
    runInTenant(
        () -> {
          var template =
              new ChecklistTemplate(
                  "Reopen Test " + uuid8(),
                  "desc",
                  "reopen-test-" + uuid8(),
                  "INDIVIDUAL",
                  "ORG_CUSTOM",
                  false);
          template = templateRepository.save(template);
          templateItemRepository.save(
              new ChecklistTemplateItem(template.getId(), "Only Step", 1, true));

          var customerId = createCustomer();
          var instance = checklistInstanceService.createFromTemplate(template.getId(), customerId);
          var items = instanceItemRepository.findByInstanceIdOrderBySortOrder(instance.getId());
          var itemId = items.get(0).getId();

          // Complete the item (auto-completes instance)
          checklistInstanceService.completeItem(itemId, null, null, memberId);

          var completedInstance = instanceRepository.findById(instance.getId()).orElseThrow();
          assertThat(completedInstance.getStatus()).isEqualTo("COMPLETED");

          // Reopen the item
          checklistInstanceService.reopenItem(itemId, memberId);

          var revertedInstance = instanceRepository.findById(instance.getId()).orElseThrow();
          assertThat(revertedInstance.getStatus()).isEqualTo("IN_PROGRESS");
          assertThat(revertedInstance.getCompletedAt()).isNull();
          return null;
        });
  }

  // ---- 102.12: Controller endpoints ----

  @Test
  void getInstancesForCustomer_returnsListWithItems() throws Exception {
    var ids =
        runInTenant(
            () -> {
              var templateId = createSimpleTemplate(2, false);
              var customerId = createCustomer();
              var instance = checklistInstanceService.createFromTemplate(templateId, customerId);
              return new UUID[] {customerId, instance.getId()};
            });

    mockMvc
        .perform(get("/api/customers/" + ids[0] + "/checklists").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(ids[1].toString()))
        .andExpect(jsonPath("$[0].items.length()").value(2));
  }

  @Test
  void getInstanceById_returnsInstanceWithItems() throws Exception {
    var instanceId =
        runInTenant(
            () -> {
              var templateId = createSimpleTemplate(1, false);
              var customerId = createCustomer();
              return checklistInstanceService.createFromTemplate(templateId, customerId).getId();
            });

    mockMvc
        .perform(get("/api/checklist-instances/" + instanceId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(instanceId.toString()))
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.items.length()").value(1));
  }

  @Test
  void manualInstantiate_createsInstance() throws Exception {
    var ids =
        runInTenant(
            () -> {
              var templateId = createSimpleTemplate(1, false);
              var customerId = createCustomer();
              return new UUID[] {templateId, customerId};
            });

    mockMvc
        .perform(
            post("/api/customers/" + ids[1] + "/checklists")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"templateId\": \"%s\"}".formatted(ids[0])))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.customerId").value(ids[1].toString()))
        .andExpect(jsonPath("$.templateId").value(ids[0].toString()))
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.items.length()").value(1));
  }

  @Test
  void completeItemViaController_returnsCompletedItem() throws Exception {
    var itemId =
        runInTenant(
            () -> {
              var templateId = createSimpleTemplate(1, false);
              var customerId = createCustomer();
              var instance = checklistInstanceService.createFromTemplate(templateId, customerId);
              return instanceItemRepository
                  .findByInstanceIdOrderBySortOrder(instance.getId())
                  .getFirst()
                  .getId();
            });

    mockMvc
        .perform(
            put("/api/checklist-items/" + itemId + "/complete")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\": \"All good\", \"documentId\": null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.notes").value("All good"));
  }

  @Test
  void skipItemViaController_returnsSkippedItem() throws Exception {
    var itemId =
        runInTenant(
            () -> {
              var templateId = createSimpleTemplate(1, false);
              var customerId = createCustomer();
              var instance = checklistInstanceService.createFromTemplate(templateId, customerId);
              return instanceItemRepository
                  .findByInstanceIdOrderBySortOrder(instance.getId())
                  .getFirst()
                  .getId();
            });

    mockMvc
        .perform(
            put("/api/checklist-items/" + itemId + "/skip")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Not applicable\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SKIPPED"))
        .andExpect(jsonPath("$.notes").value("Not applicable"));
  }

  @Test
  void memberCannotCompleteItem_returns403() throws Exception {
    var itemId =
        runInTenant(
            () -> {
              var templateId = createSimpleTemplate(1, false);
              var customerId = createCustomer();
              var instance = checklistInstanceService.createFromTemplate(templateId, customerId);
              return instanceItemRepository
                  .findByInstanceIdOrderBySortOrder(instance.getId())
                  .getFirst()
                  .getId();
            });

    mockMvc
        .perform(
            put("/api/checklist-items/" + itemId + "/complete")
                .with(memberJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\": null, \"documentId\": null}"))
        .andExpect(status().isForbidden());
  }

  // ---- Helpers ----

  private UUID createCustomer() {
    emailCounter++;
    var customer =
        createActiveCustomer(
            "Test Customer " + emailCounter,
            "adv_test_customer_" + emailCounter + "@test.com",
            memberId);
    return customerRepository.save(customer).getId();
  }

  private UUID createSimpleTemplate(int itemCount, boolean withDependency) {
    var template =
        new ChecklistTemplate(
            "Test Template " + uuid8(),
            "Test description",
            "test-tpl-" + uuid8(),
            "INDIVIDUAL",
            "ORG_CUSTOM",
            false);
    template = templateRepository.save(template);

    UUID firstItemId = null;
    for (int i = 1; i <= itemCount; i++) {
      var item = new ChecklistTemplateItem(template.getId(), "Item " + i, i, i == 1);
      item = templateItemRepository.save(item);
      if (i == 1) firstItemId = item.getId();
      if (i == 2 && withDependency && firstItemId != null) {
        item.setDependsOnItemId(firstItemId);
        templateItemRepository.save(item);
      }
    }
    return template.getId();
  }

  private UUID createTemplateWithDependency() {
    var template =
        new ChecklistTemplate(
            "Dep Template " + uuid8(),
            "desc",
            "dep-tpl-" + uuid8(),
            "INDIVIDUAL",
            "ORG_CUSTOM",
            false);
    template = templateRepository.save(template);

    var item1 = new ChecklistTemplateItem(template.getId(), "Step 1 (prerequisite)", 1, true);
    item1 = templateItemRepository.save(item1);

    var item2 = new ChecklistTemplateItem(template.getId(), "Step 2 (depends on 1)", 2, true);
    item2.setDependsOnItemId(item1.getId());
    templateItemRepository.save(item2);

    return template.getId();
  }

  private String uuid8() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_adv_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }

  private JwtRequestPostProcessor memberJwt() {
    return jwt()
        .jwt(j -> j.subject("user_adv_member").claim("o", Map.of("id", ORG_ID, "rol", "member")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_MEMBER")));
  }

  private <T> T runInTenant(Callable<T> callable) {
    var auth =
        new TestingAuthenticationToken(
            "user_adv_owner", null, List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
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
}
