package io.b2mash.b2b.b2bstrawman.checklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
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
class ChecklistEntityIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_checklist_test";
  private static final String ORG_ID_B = "org_checklist_test_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private ChecklistTemplateRepository templateRepository;
  @Autowired private ChecklistTemplateItemRepository templateItemRepository;
  @Autowired private ChecklistInstanceRepository instanceRepository;
  @Autowired private ChecklistInstanceItemRepository instanceItemRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private String tenantSchemaB;
  private UUID memberIdOwner;
  private UUID memberIdOwnerB;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Checklist Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    memberIdOwner =
        UUID.fromString(
            syncMember(ORG_ID, "user_cl_owner", "cl_owner@test.com", "Checklist Owner", "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    provisioningService.provisionTenant(ORG_ID_B, "Checklist Test Org B");
    planSyncService.syncPlan(ORG_ID_B, "pro-plan");
    memberIdOwnerB =
        UUID.fromString(
            syncMember(
                ORG_ID_B, "user_cl_owner_b", "cl_owner_b@test.com", "Checklist Owner B", "owner"));
    tenantSchemaB =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID_B).orElseThrow().getSchemaName();
  }

  @Test
  void shouldSaveAndRetrieveChecklistTemplateInDedicatedSchema() {
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      new ChecklistTemplate(
                          "Individual Onboarding",
                          "individual-onboarding",
                          "Onboarding checklist for individuals",
                          "INDIVIDUAL",
                          "ORG_CUSTOM");
                  template = templateRepository.save(template);

                  var found = templateRepository.findOneById(template.getId());
                  assertThat(found).isPresent();
                  assertThat(found.get().getName()).isEqualTo("Individual Onboarding");
                  assertThat(found.get().getSlug()).isEqualTo("individual-onboarding");
                  assertThat(found.get().getCustomerType()).isEqualTo("INDIVIDUAL");
                  assertThat(found.get().isActive()).isTrue();
                  assertThat(found.get().isAutoInstantiate()).isTrue();
                }));
  }

  @Test
  void shouldSaveTemplateItemLinkedToTemplate() {
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      new ChecklistTemplate(
                          "Item Test Template",
                          "item-test-template",
                          "Template for item test",
                          "ANY",
                          "ORG_CUSTOM");
                  template = templateRepository.save(template);

                  var item =
                      new ChecklistTemplateItem(
                          template.getId(), "Verify ID document", "Check identity", 1, true, true);
                  item.setRequiredDocumentLabel("Certified copy of ID");
                  item = templateItemRepository.save(item);

                  var found = templateItemRepository.findOneById(item.getId());
                  assertThat(found).isPresent();
                  assertThat(found.get().getTemplateId()).isEqualTo(template.getId());
                  assertThat(found.get().getName()).isEqualTo("Verify ID document");
                  assertThat(found.get().isRequired()).isTrue();
                  assertThat(found.get().isRequiresDocument()).isTrue();
                  assertThat(found.get().getRequiredDocumentLabel())
                      .isEqualTo("Certified copy of ID");
                }));
  }

  @Test
  void shouldSaveAndRetrieveChecklistInstanceWithItems() {
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Create template
                  var template =
                      new ChecklistTemplate(
                          "Instance Test",
                          "instance-test",
                          "Template for instance test",
                          "ANY",
                          "ORG_CUSTOM");
                  template = templateRepository.save(template);

                  var templateItem =
                      new ChecklistTemplateItem(
                          template.getId(), "Step 1", "First step", 0, true, false);
                  templateItem = templateItemRepository.save(templateItem);

                  // Create customer
                  var customer =
                      new Customer(
                          "Instance Test Customer",
                          "instance@test.com",
                          null,
                          null,
                          null,
                          memberIdOwner);
                  customer = customerRepository.save(customer);

                  // Create instance
                  var instance =
                      new ChecklistInstance(template.getId(), customer.getId(), "IN_PROGRESS");
                  instance = instanceRepository.save(instance);

                  // Create instance item
                  var instanceItem =
                      new ChecklistInstanceItem(
                          instance.getId(), templateItem.getId(), "Step 1", 0, true);
                  instanceItem = instanceItemRepository.save(instanceItem);

                  var foundInstance = instanceRepository.findOneById(instance.getId());
                  assertThat(foundInstance).isPresent();
                  assertThat(foundInstance.get().getStatus()).isEqualTo("IN_PROGRESS");
                  assertThat(foundInstance.get().getCustomerId()).isEqualTo(customer.getId());

                  var foundItem = instanceItemRepository.findOneById(instanceItem.getId());
                  assertThat(foundItem).isPresent();
                  assertThat(foundItem.get().getStatus()).isEqualTo("PENDING");
                  assertThat(foundItem.get().getInstanceId()).isEqualTo(instance.getId());
                }));
  }

  @Test
  void findOneByIdRespectsFilterForCrossTenantIsolation() {
    var idHolder = new UUID[1];
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      new ChecklistTemplate(
                          "Cross Tenant Test",
                          "cross-tenant-test",
                          "Testing isolation",
                          "ANY",
                          "ORG_CUSTOM");
                  template = templateRepository.save(template);
                  idHolder[0] = template.getId();
                }));

    runInTenant(
        tenantSchemaB,
        ORG_ID_B,
        memberIdOwnerB,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var found = templateRepository.findOneById(idHolder[0]);
                  assertThat(found).isEmpty();
                }));
  }

  @Test
  void instanceItemDomainMethodsWork() {
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      new ChecklistTemplate(
                          "Domain Methods Test",
                          "domain-methods-test",
                          "Testing domain methods",
                          "ANY",
                          "ORG_CUSTOM");
                  template = templateRepository.save(template);

                  var templateItem =
                      new ChecklistTemplateItem(
                          template.getId(), "Domain Step", "Step description", 0, true, false);
                  templateItem = templateItemRepository.save(templateItem);

                  var customer =
                      new Customer(
                          "Domain Test Customer",
                          "domain@test.com",
                          null,
                          null,
                          null,
                          memberIdOwner);
                  customer = customerRepository.save(customer);

                  var instance =
                      new ChecklistInstance(template.getId(), customer.getId(), "IN_PROGRESS");
                  instance = instanceRepository.save(instance);

                  var item =
                      new ChecklistInstanceItem(
                          instance.getId(), templateItem.getId(), "Domain Step", 0, true);
                  item = instanceItemRepository.save(item);

                  // Test complete
                  item.complete(memberIdOwner, java.time.Instant.now(), "Completed OK", null);
                  item = instanceItemRepository.save(item);

                  var found = instanceItemRepository.findOneById(item.getId());
                  assertThat(found).isPresent();
                  assertThat(found.get().getStatus()).isEqualTo("COMPLETED");
                  assertThat(found.get().getCompletedBy()).isEqualTo(memberIdOwner);
                  assertThat(found.get().getNotes()).isEqualTo("Completed OK");
                }));
  }

  // --- Helpers ---

  private void runInTenant(String schema, String orgId, UUID memberId, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
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
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "%s",
                          "email": "%s",
                          "name": "%s",
                          "avatarUrl": null,
                          "orgRole": "%s"
                        }
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }
}
