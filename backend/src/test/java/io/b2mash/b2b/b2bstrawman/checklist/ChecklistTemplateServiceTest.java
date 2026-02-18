package io.b2mash.b2b.b2bstrawman.checklist;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateDtos.ChecklistTemplateItemRequest;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateDtos.CreateChecklistTemplateRequest;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateDtos.UpdateChecklistTemplateRequest;
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
class ChecklistTemplateServiceTest {

  private static final String ORG_ID = "org_checklist_svc_test";

  @Autowired private ChecklistTemplateService checklistTemplateService;
  @Autowired private ChecklistTemplateRepository templateRepository;
  @Autowired private ChecklistTemplateItemRepository templateItemRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private MemberSyncService memberSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Checklist Service Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    var syncResult =
        memberSyncService.syncMember(
            ORG_ID, "user_cl_svc_test", "cl_svc_test@test.com", "CL SVC Tester", null, "owner");
    memberId = syncResult.memberId();
  }

  @Test
  void createTemplateWithItemsSavesAllInTransaction() {
    var result =
        runInTenant(
            () ->
                checklistTemplateService.create(
                    new CreateChecklistTemplateRequest(
                        "KYC Onboarding",
                        "KYC checklist for new clients",
                        "INDIVIDUAL",
                        true,
                        null,
                        null,
                        List.of(
                            new ChecklistTemplateItemRequest(
                                "Collect ID",
                                "Collect identity document",
                                1,
                                true,
                                true,
                                "ID Document",
                                null),
                            new ChecklistTemplateItemRequest(
                                "Verify Address",
                                "Proof of address",
                                2,
                                true,
                                false,
                                null,
                                null)))));

    assertThat(result.name()).isEqualTo("KYC Onboarding");
    assertThat(result.slug()).isEqualTo("kyc-onboarding");
    assertThat(result.customerType()).isEqualTo("INDIVIDUAL");
    assertThat(result.source()).isEqualTo("ORG_CUSTOM");
    assertThat(result.active()).isTrue();
    assertThat(result.items()).hasSize(2);
    assertThat(result.items().get(0).name()).isEqualTo("Collect ID");
    assertThat(result.items().get(1).name()).isEqualTo("Verify Address");
  }

  @Test
  void listActiveReturnsOnlyActiveTemplates() {
    var template1 =
        runInTenant(
            () ->
                checklistTemplateService.create(
                    new CreateChecklistTemplateRequest(
                        "Active Template", null, "ANY", true, null, null, List.of())));
    var template2 =
        runInTenant(
            () ->
                checklistTemplateService.create(
                    new CreateChecklistTemplateRequest(
                        "Deactivated Template", null, "ANY", true, null, null, List.of())));

    runInTenant(() -> checklistTemplateService.deactivate(template2.id()));

    var activeList = runInTenant(() -> checklistTemplateService.listActive(null));

    assertThat(activeList).noneMatch(t -> t.id().equals(template2.id()));
    assertThat(activeList).anyMatch(t -> t.id().equals(template1.id()));
  }

  @Test
  void getByIdIncludesItems() {
    var created =
        runInTenant(
            () ->
                checklistTemplateService.create(
                    new CreateChecklistTemplateRequest(
                        "With Items",
                        null,
                        "COMPANY",
                        true,
                        null,
                        null,
                        List.of(
                            new ChecklistTemplateItemRequest(
                                "Step 1", null, 1, true, false, null, null),
                            new ChecklistTemplateItemRequest(
                                "Step 2", null, 2, true, false, null, null),
                            new ChecklistTemplateItemRequest(
                                "Step 3", null, 3, false, false, null, null)))));

    var fetched = runInTenant(() -> checklistTemplateService.getById(created.id()));

    assertThat(fetched.items()).hasSize(3);
    assertThat(fetched.items().get(0).name()).isEqualTo("Step 1");
    assertThat(fetched.items().get(1).name()).isEqualTo("Step 2");
    assertThat(fetched.items().get(2).name()).isEqualTo("Step 3");
  }

  @Test
  void updateReplacesItems() {
    var created =
        runInTenant(
            () ->
                checklistTemplateService.create(
                    new CreateChecklistTemplateRequest(
                        "Replace Test",
                        null,
                        "ANY",
                        true,
                        null,
                        null,
                        List.of(
                            new ChecklistTemplateItemRequest(
                                "Old Item 1", null, 1, true, false, null, null),
                            new ChecklistTemplateItemRequest(
                                "Old Item 2", null, 2, true, false, null, null)))));

    var updated =
        runInTenant(
            () ->
                checklistTemplateService.update(
                    created.id(),
                    new UpdateChecklistTemplateRequest(
                        "Replace Test Updated",
                        "Now with description",
                        false,
                        null,
                        List.of(
                            new ChecklistTemplateItemRequest(
                                "New Item A", null, 1, true, false, null, null),
                            new ChecklistTemplateItemRequest(
                                "New Item B", null, 2, false, false, null, null),
                            new ChecklistTemplateItemRequest(
                                "New Item C", null, 3, true, true, "Upload proof", null)))));

    assertThat(updated.name()).isEqualTo("Replace Test Updated");
    assertThat(updated.description()).isEqualTo("Now with description");
    assertThat(updated.autoInstantiate()).isFalse();
    assertThat(updated.items()).hasSize(3);
    assertThat(updated.items().stream().map(i -> i.name()).toList())
        .containsExactly("New Item A", "New Item B", "New Item C");
  }

  @Test
  void deactivateHidesFromList() {
    var created =
        runInTenant(
            () ->
                checklistTemplateService.create(
                    new CreateChecklistTemplateRequest(
                        "To Deactivate", null, "ANY", true, null, null, List.of())));

    runInTenant(() -> checklistTemplateService.deactivate(created.id()));

    var activeList = runInTenant(() -> checklistTemplateService.listActive(null));
    assertThat(activeList).noneMatch(t -> t.id().equals(created.id()));

    var fetched = runInTenant(() -> checklistTemplateService.getById(created.id()));
    assertThat(fetched.active()).isFalse();
  }

  @Test
  void slugUniquenessSuffixAppended() {
    var first =
        runInTenant(
            () ->
                checklistTemplateService.create(
                    new CreateChecklistTemplateRequest(
                        "Duplicate Name", null, "ANY", true, null, null, List.of())));

    var second =
        runInTenant(
            () ->
                checklistTemplateService.create(
                    new CreateChecklistTemplateRequest(
                        "Duplicate Name", null, "ANY", true, null, null, List.of())));

    assertThat(first.slug()).isEqualTo("duplicate-name");
    assertThat(second.slug()).isEqualTo("duplicate-name-2");
  }

  @Test
  void customerTypeFilterWorksInRepository() {
    runInTenant(
        () ->
            checklistTemplateService.create(
                new CreateChecklistTemplateRequest(
                    "Individual Only", null, "INDIVIDUAL", true, null, null, List.of())));
    runInTenant(
        () ->
            checklistTemplateService.create(
                new CreateChecklistTemplateRequest(
                    "Company Only", null, "COMPANY", true, null, null, List.of())));
    runInTenant(
        () ->
            checklistTemplateService.create(
                new CreateChecklistTemplateRequest(
                    "Any Type", null, "ANY", true, null, null, List.of())));

    var filtered =
        runInTenant(
            () ->
                transactionTemplate.execute(
                    tx ->
                        templateRepository.findByActiveAndAutoInstantiateAndCustomerTypeIn(
                            true, true, List.of("INDIVIDUAL", "ANY"))));

    assertThat(filtered).allMatch(t -> List.of("INDIVIDUAL", "ANY").contains(t.getCustomerType()));
    assertThat(filtered).noneMatch(t -> t.getCustomerType().equals("COMPANY"));
  }

  @Test
  void adminOnlyMutationBusinessLogicWorks() {
    // This test verifies service business logic independently of RBAC
    // @PreAuthorize is tested at controller level via MockMvc
    var result =
        runInTenant(
            () ->
                checklistTemplateService.create(
                    new CreateChecklistTemplateRequest(
                        "Business Logic Test", null, "ANY", false, null, null, List.of())));

    assertThat(result.autoInstantiate()).isFalse();
    assertThat(result.active()).isTrue();
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  private <T> T runInTenant(Callable<T> callable) {
    var auth =
        new TestingAuthenticationToken(
            "user_cl_svc_test", null, List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
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
