package io.b2mash.b2b.b2bstrawman.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceItemRepository;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceRepository;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceService;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplate;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateItem;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateItemRepository;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.PrerequisiteNotMetException;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldType;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for lifecycle prerequisite enforcement (Epic 242B). Tests both manual
 * transition (via CustomerLifecycleService) and auto-transition (via checklist completion) paths.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomerLifecyclePrerequisiteTest {

  private static final String ORG_ID = "org_prereq_test_242b";

  @Autowired private CustomerLifecycleService lifecycleService;
  @Autowired private ChecklistInstantiationService instantiationService;
  @Autowired private ChecklistInstanceService instanceService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private io.b2mash.b2b.b2bstrawman.customer.CustomerService customerService;
  @Autowired private FieldDefinitionRepository fieldDefinitionRepository;
  @Autowired private ChecklistTemplateRepository templateRepository;
  @Autowired private ChecklistTemplateItemRepository templateItemRepository;
  @Autowired private ChecklistInstanceRepository instanceRepository;
  @Autowired private ChecklistInstanceItemRepository instanceItemRepository;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private MemberSyncService memberSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private MockMvc mockMvc;

  private String tenantSchema;
  private UUID memberId;
  private int counter = 0;

  @BeforeAll
  void setup() {
    provisioningService.provisionTenant(ORG_ID, "Prerequisite Test Org", null);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    var syncResult =
        memberSyncService.syncMember(
            ORG_ID, "user_prereq_242b", "prereq_242b@test.com", "Prereq Tester", null, "owner");
    memberId = syncResult.memberId();
  }

  // ---- 242.7: Manual transition prerequisite tests ----

  @Test
  void transitionToActive_allFieldsFilled_succeeds() {
    var fieldDefs = createRequiredFieldDefinitions("filled_ok");

    try {
      UUID customerId =
          runInTenant(
              () -> {
                var customer =
                    TestCustomerFactory.createOnboardingCustomerWithPrerequisites(
                        "Filled Corp " + (++counter),
                        "filled_ok_" + counter + "@test.com",
                        memberId,
                        fieldDefs);
                // Fill structural fields so LIFECYCLE_ACTIVATION structural check passes
                customer.setAddressLine1("123 Test Street");
                customer.setCity("Test City");
                customer.setCountry("ZA");
                customer.setTaxNumber("VAT123456");
                return customerRepository.save(customer).getId();
              });

      var result =
          runInTenant(
              () -> lifecycleService.transition(customerId, "ACTIVE", "fields filled", memberId));
      assertThat(result.getLifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);
    } finally {
      cleanupFieldDefinitions(fieldDefs);
    }
  }

  @Test
  void transitionToActive_missingFields_returns422WithViolations() {
    var fieldDefs = createRequiredFieldDefinitions("missing_fields");

    try {
      // Create ONBOARDING customer WITHOUT filling required custom fields but WITH structural
      // fields (address/city/country/tax) so that only the 2 custom-field violations fire.
      UUID customerId =
          runInTenant(
              () -> {
                var customer =
                    TestCustomerFactory.createCustomerWithStatus(
                        "Missing Fields Corp " + (++counter),
                        "missing_" + counter + "@test.com",
                        memberId,
                        LifecycleStatus.ONBOARDING);
                customer.setAddressLine1("123 Test Street");
                customer.setCity("Test City");
                customer.setCountry("ZA");
                customer.setTaxNumber("VAT123456");
                return customerRepository.save(customer).getId();
              });

      assertThatThrownBy(
              () ->
                  runInTenant(
                      () ->
                          lifecycleService.transition(
                              customerId, "ACTIVE", "should fail", memberId)))
          .isInstanceOf(PrerequisiteNotMetException.class)
          .satisfies(
              ex -> {
                var prereqEx = (PrerequisiteNotMetException) ex;
                var check = prereqEx.getPrerequisiteCheck();
                assertThat(check.passed()).isFalse();
                assertThat(check.violations()).hasSize(2);
              });
    } finally {
      cleanupFieldDefinitions(fieldDefs);
    }
  }

  @Test
  void transitionToActive_noFieldsRequired_succeeds() {
    // No field definitions with LIFECYCLE_ACTIVATION context — transition should succeed.
    // Structural fields (address/city/country/tax) must be filled so the built-in structural
    // prerequisite check for LIFECYCLE_ACTIVATION does not block the transition.
    UUID customerId =
        runInTenant(
            () -> {
              var customer =
                  TestCustomerFactory.createCustomerWithStatus(
                      "No Prereqs Corp " + (++counter),
                      "no_prereqs_" + counter + "@test.com",
                      memberId,
                      LifecycleStatus.ONBOARDING);
              customer.setAddressLine1("123 Test Street");
              customer.setCity("Test City");
              customer.setCountry("ZA");
              customer.setTaxNumber("VAT123456");
              return customerRepository.save(customer).getId();
            });

    var result =
        runInTenant(
            () -> lifecycleService.transition(customerId, "ACTIVE", "no prereqs", memberId));
    assertThat(result.getLifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);
  }

  @Test
  void transitionToActive_fromProspect_throwsInvalidStateException() {
    // PROSPECT -> ACTIVE is not a valid transition — should throw InvalidStateException
    // before reaching prerequisite check
    UUID customerId =
        runInTenant(
            () -> {
              var customer =
                  TestCustomerFactory.createCustomerWithStatus(
                      "Prospect Skip Corp " + (++counter),
                      "prospect_skip_" + counter + "@test.com",
                      memberId,
                      LifecycleStatus.PROSPECT);
              return customerRepository.save(customer).getId();
            });

    assertThatThrownBy(
            () ->
                runInTenant(
                    () ->
                        lifecycleService.transition(customerId, "ACTIVE", "should fail", memberId)))
        .isInstanceOf(InvalidStateException.class);
  }

  // ---- 242.8: Auto-transition prerequisite tests ----

  @Test
  void autoTransition_fieldsComplete_transitionsToActive() {
    var fieldDefs = createRequiredFieldDefinitions("auto_ok");

    try {
      UUID customerId =
          runInTenant(
              () -> {
                var customer =
                    TestCustomerFactory.createOnboardingCustomerWithPrerequisites(
                        "Auto OK Corp " + (++counter),
                        "auto_ok_" + counter + "@test.com",
                        memberId,
                        fieldDefs);
                // Fill structural fields so LIFECYCLE_ACTIVATION structural check passes
                customer.setAddressLine1("123 Test Street");
                customer.setCity("Test City");
                customer.setCountry("ZA");
                customer.setTaxNumber("VAT123456");
                return customerRepository.save(customer).getId();
              });

      // Create a checklist template, instantiate it, and complete all items
      completeAllChecklistItems(customerId);

      // Verify customer auto-transitioned to ACTIVE
      var customer = runInTenant(() -> customerRepository.findById(customerId).orElseThrow());
      assertThat(customer.getLifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);
    } finally {
      cleanupFieldDefinitions(fieldDefs);
    }
  }

  @Test
  void autoTransition_fieldsMissing_blocksAndSendsNotification() {
    var fieldDefs = createRequiredFieldDefinitions("auto_blocked");

    try {
      UUID customerId =
          runInTenant(
              () -> {
                var customer =
                    TestCustomerFactory.createCustomerWithStatus(
                        "Auto Blocked Corp " + (++counter),
                        "auto_blocked_" + counter + "@test.com",
                        memberId,
                        LifecycleStatus.ONBOARDING);
                return customerRepository.save(customer).getId();
              });

      // Create and complete all checklist items — auto-transition should be blocked
      completeAllChecklistItems(customerId);

      // Verify customer stays in ONBOARDING
      var customer = runInTenant(() -> customerRepository.findById(customerId).orElseThrow());
      assertThat(customer.getLifecycleStatus()).isEqualTo(LifecycleStatus.ONBOARDING);

      // Verify notification was sent
      var notifications =
          runInTenant(
              () ->
                  notificationRepository
                      .findByRecipientMemberId(memberId, PageRequest.of(0, 50))
                      .getContent());

      var blocked =
          notifications.stream()
              .filter(n -> "PREREQUISITE_BLOCKED_ACTIVATION".equals(n.getType()))
              .filter(n -> n.getTitle().contains("Auto Blocked Corp"))
              .toList();
      assertThat(blocked).isNotEmpty();
    } finally {
      cleanupFieldDefinitions(fieldDefs);
    }
  }

  // ---- GAP-D2-02: Field update triggers auto-transition ----

  @Test
  void updateCustomer_fillsMissingPrerequisite_triggersAutoTransitionToActive() {
    // Scenario: checklists are 100% complete but tax_number was missing at checklist completion
    // time. Updating the customer to add tax_number should now auto-transition to ACTIVE.
    UUID customerId =
        runInTenant(
            () -> {
              var customer =
                  TestCustomerFactory.createCustomerWithStatus(
                      "Late Tax Corp " + (++counter),
                      "late_tax_" + counter + "@test.com",
                      memberId,
                      LifecycleStatus.ONBOARDING);
              // Fill all structural fields EXCEPT tax_number
              customer.setAddressLine1("123 Test Street");
              customer.setCity("Test City");
              customer.setCountry("ZA");
              // tax_number intentionally omitted
              return customerRepository.save(customer).getId();
            });

    // Complete all checklist items - auto-transition should be BLOCKED (missing tax_number)
    completeAllChecklistItems(customerId);

    // Verify customer is still ONBOARDING
    var beforeUpdate = runInTenant(() -> customerRepository.findById(customerId).orElseThrow());
    assertThat(beforeUpdate.getLifecycleStatus()).isEqualTo(LifecycleStatus.ONBOARDING);

    // Now update customer to fill the missing tax_number via CustomerService.updateCustomer()
    runInTenant(
        () ->
            customerService.updateCustomer(
                customerId,
                beforeUpdate.getName(),
                beforeUpdate.getEmail(),
                beforeUpdate.getPhone(),
                beforeUpdate.getIdNumber(),
                beforeUpdate.getNotes(),
                beforeUpdate.getCustomFields(),
                beforeUpdate.getAppliedFieldGroups(),
                beforeUpdate.getRegistrationNumber(),
                beforeUpdate.getAddressLine1(),
                beforeUpdate.getAddressLine2(),
                beforeUpdate.getCity(),
                beforeUpdate.getStateProvince(),
                beforeUpdate.getPostalCode(),
                beforeUpdate.getCountry(),
                "VAT999888", // <-- filling the missing tax_number
                beforeUpdate.getContactName(),
                beforeUpdate.getContactEmail(),
                beforeUpdate.getContactPhone(),
                beforeUpdate.getEntityType(),
                beforeUpdate.getFinancialYearEnd()));

    // Verify customer auto-transitioned to ACTIVE
    var afterUpdate = runInTenant(() -> customerRepository.findById(customerId).orElseThrow());
    assertThat(afterUpdate.getLifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);
  }

  @Test
  void autoTransition_notificationContainsCustomerNameAndFieldCount() {
    var fieldDefs = createRequiredFieldDefinitions("auto_notify");

    try {
      UUID customerId =
          runInTenant(
              () -> {
                var customer =
                    TestCustomerFactory.createCustomerWithStatus(
                        "Notify Check Corp " + (++counter),
                        "auto_notify_" + counter + "@test.com",
                        memberId,
                        LifecycleStatus.ONBOARDING);
                // Fill structural fields so that only the 2 custom-field violations fire.
                // Without this, structural checks add 4 more violations and the notification
                // would say "6 incomplete required fields" rather than "2".
                customer.setAddressLine1("123 Test Street");
                customer.setCity("Test City");
                customer.setCountry("ZA");
                customer.setTaxNumber("VAT123456");
                return customerRepository.save(customer).getId();
              });

      completeAllChecklistItems(customerId);

      // Verify notification title format contains customer name and violation count
      var notifications =
          runInTenant(
              () ->
                  notificationRepository
                      .findByRecipientMemberId(memberId, PageRequest.of(0, 50))
                      .getContent());

      var blocked =
          notifications.stream()
              .filter(n -> "PREREQUISITE_BLOCKED_ACTIVATION".equals(n.getType()))
              .filter(n -> n.getTitle().contains("Notify Check Corp"))
              .findFirst()
              .orElseThrow(() -> new AssertionError("Expected notification not found"));

      assertThat(blocked.getTitle()).contains("Notify Check Corp");
      assertThat(blocked.getTitle()).contains("2 incomplete required fields");
    } finally {
      cleanupFieldDefinitions(fieldDefs);
    }
  }

  // ---- 242.10: PrerequisiteNotMetException handler test (MockMvc) ----

  @Test
  void prerequisiteNotMet_returns422WithViolationsJson() throws Exception {
    var fieldDefs = createRequiredFieldDefinitions("handler_test");

    try {
      // Create ONBOARDING customer without filling required custom fields but WITH structural
      // fields so only the 2 custom-field violations are returned in the 422 response.
      UUID customerId =
          runInTenant(
              () -> {
                var customer =
                    TestCustomerFactory.createCustomerWithStatus(
                        "Handler Test Corp " + (++counter),
                        "handler_" + counter + "@test.com",
                        memberId,
                        LifecycleStatus.ONBOARDING);
                customer.setAddressLine1("123 Test Street");
                customer.setCity("Test City");
                customer.setCountry("ZA");
                customer.setTaxNumber("VAT123456");
                return customerRepository.save(customer).getId();
              });

      // Attempt manual transition via MockMvc — should return 422
      mockMvc
          .perform(
              post("/api/customers/" + customerId + "/transition")
                  .with(TestJwtFactory.adminJwt(ORG_ID, "user_prereq_242b"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"targetStatus": "ACTIVE", "notes": "should fail"}
                      """))
          .andExpect(status().isUnprocessableEntity())
          .andExpect(jsonPath("$.title").value("Prerequisites not met"))
          .andExpect(
              jsonPath("$.detail")
                  .value(org.hamcrest.Matchers.containsString("required field(s) missing")))
          .andExpect(jsonPath("$.violations").isArray())
          .andExpect(jsonPath("$.violations.length()").value(2))
          .andExpect(jsonPath("$.violations[0].code").value("MISSING_FIELD"))
          .andExpect(jsonPath("$.violations[0].fieldSlug").isNotEmpty());
    } finally {
      cleanupFieldDefinitions(fieldDefs);
    }
  }

  // ---- Helpers ----

  /**
   * Creates two required FieldDefinitions with LIFECYCLE_ACTIVATION context: one TEXT and one
   * DROPDOWN. Returns the list of saved FieldDefinitions (for use with
   * TestCustomerFactory.withRequiredFields and cleanup).
   */
  private List<FieldDefinition> createRequiredFieldDefinitions(String prefix) {
    return runInTenant(
        () -> {
          var fd1 =
              new FieldDefinition(
                  EntityType.CUSTOMER,
                  prefix + " Tax Number",
                  prefix + "_tax_number",
                  FieldType.TEXT);
          fd1.setRequiredForContexts(List.of("LIFECYCLE_ACTIVATION"));

          var fd2 =
              new FieldDefinition(
                  EntityType.CUSTOMER,
                  prefix + " Industry",
                  prefix + "_industry",
                  FieldType.DROPDOWN);
          fd2.setRequiredForContexts(List.of("LIFECYCLE_ACTIVATION"));

          return List.of(fieldDefinitionRepository.save(fd1), fieldDefinitionRepository.save(fd2));
        });
  }

  private void cleanupFieldDefinitions(List<FieldDefinition> fieldDefs) {
    runInTenant(
        () -> {
          for (var fd : fieldDefs) {
            fieldDefinitionRepository.deleteById(fd.getId());
          }
        });
  }

  /**
   * Creates a checklist template with a single required item, instantiates it for the customer, and
   * completes the item — triggering the auto-transition path.
   */
  private void completeAllChecklistItems(UUID customerId) {
    runInTenant(
        () -> {
          // Create template
          var template =
              new ChecklistTemplate(
                  "Prereq Test Template " + (++counter),
                  "test template for prerequisite tests",
                  "prereq-tpl-" + counter + "-" + uuid8(),
                  "ANY",
                  "ORG_CUSTOM",
                  true);
          template = templateRepository.save(template);

          // Create template item
          templateItemRepository.save(
              new ChecklistTemplateItem(template.getId(), "Step 1", 1, true));

          // Instantiate for customer
          var customer = customerRepository.findById(customerId).orElseThrow();
          instantiationService.instantiateForCustomer(customer);

          // Find and complete/skip all instance items
          var instances = instanceRepository.findByCustomerId(customerId);
          for (var instance : instances) {
            var items = instanceItemRepository.findByInstanceIdOrderBySortOrder(instance.getId());
            for (var item : items) {
              if (item.isRequiresDocument()) {
                // Skip document-required items (same as TestChecklistHelper pattern)
                instanceService.skipItem(item.getId(), "skipped for test", memberId);
              } else {
                instanceService.completeItem(item.getId(), "auto-test", null, memberId);
              }
            }
          }
        });
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
