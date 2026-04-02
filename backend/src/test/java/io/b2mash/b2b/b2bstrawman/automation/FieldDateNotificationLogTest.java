package io.b2mash.b2b.b2bstrawman.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.event.FieldDateApproachingEvent;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FieldDateNotificationLogTest {

  private static final String ORG_ID = "org_field_date_notif_test";

  @Autowired private FieldDateNotificationLogRepository repository;
  @Autowired private TenantProvisioningService provisioningService;

  private String schemaName;

  @BeforeAll
  void provisionTenant() {
    schemaName =
        provisioningService
            .provisionTenant(ORG_ID, "Field Date Notification Test Org", null)
            .schemaName();
  }

  @Test
  void entitySavesAndLoadsCorrectly() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var entityId = UUID.randomUUID();
              var log =
                  new FieldDateNotificationLog(
                      "customer", entityId, "sars_submission_deadline", 14);

              var saved = repository.save(log);
              repository.flush();

              var found = repository.findById(saved.getId()).orElseThrow();
              assertThat(found.getEntityType()).isEqualTo("customer");
              assertThat(found.getEntityId()).isEqualTo(entityId);
              assertThat(found.getFieldName()).isEqualTo("sars_submission_deadline");
              assertThat(found.getDaysUntil()).isEqualTo(14);
              assertThat(found.getFiredAt()).isNotNull();
            });
  }

  @Test
  void uniqueConstraintPreventsDuplicate() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var entityId = UUID.randomUUID();
              var log1 = new FieldDateNotificationLog("project", entityId, "tax_deadline", 7);
              repository.save(log1);
              repository.flush();

              var log2 = new FieldDateNotificationLog("project", entityId, "tax_deadline", 7);

              assertThatThrownBy(
                      () -> {
                        repository.save(log2);
                        repository.flush();
                      })
                  .isInstanceOf(Exception.class);
            });
  }

  @Test
  void existsByDedupColumnsQuery() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var entityId = UUID.randomUUID();
              var log = new FieldDateNotificationLog("customer", entityId, "vat_return_date", 30);
              repository.save(log);
              repository.flush();

              assertThat(
                      repository.existsByEntityTypeAndEntityIdAndFieldNameAndDaysUntil(
                          "customer", entityId, "vat_return_date", 30))
                  .isTrue();

              // Different daysUntil should not match
              assertThat(
                      repository.existsByEntityTypeAndEntityIdAndFieldNameAndDaysUntil(
                          "customer", entityId, "vat_return_date", 7))
                  .isFalse();
            });
  }

  @Test
  void triggerTypeMapping_fieldDateApproachingEvent_returnsFIELD_DATE_APPROACHING() {
    var event =
        new FieldDateApproachingEvent(
            "field_date.approaching",
            "customer",
            UUID.randomUUID(),
            null,
            null,
            "system",
            "test",
            "org",
            Instant.now(),
            Map.of(
                "field_name", "sars_submission_deadline",
                "field_label", "SARS Submission Deadline",
                "field_value", "2026-03-30",
                "days_until", 14,
                "entity_name", "Acme Corp"));
    assertThat(TriggerTypeMapping.getTriggerType(event))
        .isEqualTo(TriggerType.FIELD_DATE_APPROACHING);
  }
}
