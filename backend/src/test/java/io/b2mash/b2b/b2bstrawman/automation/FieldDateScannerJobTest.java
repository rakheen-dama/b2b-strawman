package io.b2mash.b2b.b2bstrawman.automation;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.event.FieldDateApproachingEvent;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinition;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldDefinitionRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldType;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRoleRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@RecordApplicationEvents
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FieldDateScannerJobTest {

  private static final String ORG_ID_1 = "org_field_date_scanner_test_1";
  private static final String ORG_ID_2 = "org_field_date_scanner_test_2";

  @Autowired private FieldDateScannerJob scannerJob;
  @Autowired private FieldDefinitionRepository fieldDefinitionRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private FieldDateNotificationLogRepository notificationLogRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private MemberRepository memberRepository;
  @Autowired private OrgRoleRepository orgRoleRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ApplicationEvents events;

  private String schemaName1;
  private String schemaName2;
  private UUID memberId1;
  private UUID memberId2;

  @BeforeAll
  void provisionTenants() {
    schemaName1 =
        provisioningService.provisionTenant(ORG_ID_1, "Scanner Test Org 1", null).schemaName();
    planSyncService.syncPlan(ORG_ID_1, "pro-plan");

    schemaName2 =
        provisioningService.provisionTenant(ORG_ID_2, "Scanner Test Org 2", null).schemaName();
    planSyncService.syncPlan(ORG_ID_2, "pro-plan");

    // Create a member in tenant 1
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName1)
        .where(RequestScopes.ORG_ID, ORG_ID_1)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var role = orgRoleRepository.findBySlug("owner").orElseThrow();
                      var member =
                          new Member(
                              "user_scanner_t1", "scanner_t1@test.com", "Scanner T1", null, role);
                      member = memberRepository.save(member);
                      memberId1 = member.getId();
                    }));

    // Create a member in tenant 2
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName2)
        .where(RequestScopes.ORG_ID, ORG_ID_2)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var role = orgRoleRepository.findBySlug("owner").orElseThrow();
                      var member =
                          new Member(
                              "user_scanner_t2", "scanner_t2@test.com", "Scanner T2", null, role);
                      member = memberRepository.save(member);
                      memberId2 = member.getId();
                    }));
  }

  @Test
  void scannerFiresEventForDateField14DaysOut() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName1)
        .where(RequestScopes.ORG_ID, ORG_ID_1)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      // Create a DATE field definition
                      var fieldDef =
                          new FieldDefinition(
                              EntityType.CUSTOMER,
                              "Tax Deadline",
                              "tax_deadline_14",
                              FieldType.DATE);
                      fieldDefinitionRepository.save(fieldDef);

                      // Create a customer with the date field 14 days from today
                      var customer =
                          TestCustomerFactory.createActiveCustomer(
                              "Scanner Test Client 14d", "scanner14@test.com", memberId1);
                      var customFields = new HashMap<String, Object>();
                      customFields.put("tax_deadline_14", LocalDate.now().plusDays(14).toString());
                      customer.setCustomFields(customFields);
                      var saved = customerRepository.save(customer);
                      customerRepository.flush();

                      // Run the scanner
                      scannerJob.execute();

                      // Verify dedup record was created
                      assertThat(
                              notificationLogRepository
                                  .existsByEntityTypeAndEntityIdAndFieldNameAndDaysUntil(
                                      "customer", saved.getId(), "tax_deadline_14", 14))
                          .isTrue();

                      // Verify event was published
                      long eventCount =
                          events.stream(FieldDateApproachingEvent.class)
                              .filter(e -> e.entityId().equals(saved.getId()))
                              .filter(e -> e.details().get("days_until").equals(14))
                              .count();
                      assertThat(eventCount).isEqualTo(1);
                    }));
  }

  @Test
  void scannerDeduplicatesOnSecondRun() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName1)
        .where(RequestScopes.ORG_ID, ORG_ID_1)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      // Create a DATE field definition
                      var fieldDef =
                          new FieldDefinition(
                              EntityType.CUSTOMER,
                              "Dedup Test Deadline",
                              "dedup_deadline",
                              FieldType.DATE);
                      fieldDefinitionRepository.save(fieldDef);

                      // Create a customer with the date field 7 days from today
                      var customer =
                          TestCustomerFactory.createActiveCustomer(
                              "Dedup Test Client", "dedup@test.com", memberId1);
                      var customFields = new HashMap<String, Object>();
                      customFields.put("dedup_deadline", LocalDate.now().plusDays(7).toString());
                      customer.setCustomFields(customFields);
                      var saved = customerRepository.save(customer);
                      customerRepository.flush();

                      // First run — should create dedup record
                      scannerJob.execute();

                      assertThat(
                              notificationLogRepository
                                  .existsByEntityTypeAndEntityIdAndFieldNameAndDaysUntil(
                                      "customer", saved.getId(), "dedup_deadline", 7))
                          .isTrue();

                      // Count log rows before second run
                      long countBefore =
                          notificationLogRepository.findAll().stream()
                              .filter(
                                  log ->
                                      log.getEntityId().equals(saved.getId())
                                          && log.getFieldName().equals("dedup_deadline")
                                          && log.getDaysUntil() == 7)
                              .count();

                      // Second run — should NOT create a duplicate
                      scannerJob.execute();

                      long countAfter =
                          notificationLogRepository.findAll().stream()
                              .filter(
                                  log ->
                                      log.getEntityId().equals(saved.getId())
                                          && log.getFieldName().equals("dedup_deadline")
                                          && log.getDaysUntil() == 7)
                              .count();
                      assertThat(countAfter).isEqualTo(countBefore);
                    }));
  }

  @Test
  void scannerDoesNotFireForDateOutsideThresholds() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName1)
        .where(RequestScopes.ORG_ID, ORG_ID_1)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      // Create a DATE field definition
                      var fieldDef =
                          new FieldDefinition(
                              EntityType.CUSTOMER,
                              "Far Away Deadline",
                              "far_away_deadline",
                              FieldType.DATE);
                      fieldDefinitionRepository.save(fieldDef);

                      // Create customer with date 30 days out (outside all thresholds)
                      var customer =
                          TestCustomerFactory.createActiveCustomer(
                              "Far Away Client", "faraway@test.com", memberId1);
                      var customFields = new HashMap<String, Object>();
                      customFields.put(
                          "far_away_deadline", LocalDate.now().plusDays(30).toString());
                      customer.setCustomFields(customFields);
                      var saved = customerRepository.save(customer);
                      customerRepository.flush();

                      // Run the scanner
                      scannerJob.execute();

                      // Verify NO dedup record was created for this entity/field
                      assertThat(
                              notificationLogRepository
                                  .existsByEntityTypeAndEntityIdAndFieldNameAndDaysUntil(
                                      "customer", saved.getId(), "far_away_deadline", 14))
                          .isFalse();
                      assertThat(
                              notificationLogRepository
                                  .existsByEntityTypeAndEntityIdAndFieldNameAndDaysUntil(
                                      "customer", saved.getId(), "far_away_deadline", 7))
                          .isFalse();
                      assertThat(
                              notificationLogRepository
                                  .existsByEntityTypeAndEntityIdAndFieldNameAndDaysUntil(
                                      "customer", saved.getId(), "far_away_deadline", 1))
                          .isFalse();

                      // Verify no events were published for this entity
                      long eventCount =
                          events.stream(FieldDateApproachingEvent.class)
                              .filter(e -> e.entityId().equals(saved.getId()))
                              .count();
                      assertThat(eventCount).isEqualTo(0);
                    }));
  }

  @Test
  void scannerMaintainsTenantIsolation() {
    // Create date field + customer in tenant 1
    UUID[] tenant1EntityId = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName1)
        .where(RequestScopes.ORG_ID, ORG_ID_1)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var fieldDef =
                          new FieldDefinition(
                              EntityType.CUSTOMER,
                              "Isolation Deadline T1",
                              "isolation_deadline_t1",
                              FieldType.DATE);
                      fieldDefinitionRepository.save(fieldDef);

                      var customer =
                          TestCustomerFactory.createActiveCustomer(
                              "Tenant1 Isolation Client", "t1iso@test.com", memberId1);
                      var customFields = new HashMap<String, Object>();
                      customFields.put(
                          "isolation_deadline_t1", LocalDate.now().plusDays(7).toString());
                      customer.setCustomFields(customFields);
                      var saved = customerRepository.save(customer);
                      customerRepository.flush();
                      tenant1EntityId[0] = saved.getId();
                    }));

    // Create date field + customer in tenant 2
    UUID[] tenant2EntityId = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName2)
        .where(RequestScopes.ORG_ID, ORG_ID_2)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var fieldDef =
                          new FieldDefinition(
                              EntityType.CUSTOMER,
                              "Isolation Deadline T2",
                              "isolation_deadline_t2",
                              FieldType.DATE);
                      fieldDefinitionRepository.save(fieldDef);

                      var customer =
                          TestCustomerFactory.createActiveCustomer(
                              "Tenant2 Isolation Client", "t2iso@test.com", memberId2);
                      var customFields = new HashMap<String, Object>();
                      customFields.put(
                          "isolation_deadline_t2", LocalDate.now().plusDays(7).toString());
                      customer.setCustomFields(customFields);
                      var saved = customerRepository.save(customer);
                      customerRepository.flush();
                      tenant2EntityId[0] = saved.getId();
                    }));

    // Run the scanner (it iterates all tenants internally)
    scannerJob.execute();

    // Verify tenant 1 has its own dedup record
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName1)
        .where(RequestScopes.ORG_ID, ORG_ID_1)
        .run(
            () -> {
              assertThat(
                      notificationLogRepository
                          .existsByEntityTypeAndEntityIdAndFieldNameAndDaysUntil(
                              "customer", tenant1EntityId[0], "isolation_deadline_t1", 7))
                  .isTrue();

              // Tenant 1 should NOT have tenant 2's entity
              assertThat(
                      notificationLogRepository
                          .existsByEntityTypeAndEntityIdAndFieldNameAndDaysUntil(
                              "customer", tenant2EntityId[0], "isolation_deadline_t2", 7))
                  .isFalse();
            });

    // Verify tenant 2 has its own dedup record
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName2)
        .where(RequestScopes.ORG_ID, ORG_ID_2)
        .run(
            () -> {
              assertThat(
                      notificationLogRepository
                          .existsByEntityTypeAndEntityIdAndFieldNameAndDaysUntil(
                              "customer", tenant2EntityId[0], "isolation_deadline_t2", 7))
                  .isTrue();

              // Tenant 2 should NOT have tenant 1's entity
              assertThat(
                      notificationLogRepository
                          .existsByEntityTypeAndEntityIdAndFieldNameAndDaysUntil(
                              "customer", tenant1EntityId[0], "isolation_deadline_t1", 7))
                  .isFalse();
            });
  }
}
