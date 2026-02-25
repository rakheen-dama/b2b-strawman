package io.b2mash.b2b.b2bstrawman.integration.email;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EmailDeliveryLogServiceIntegrationTest {

  private static final String ORG_ID = "org_email_delivery_test";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private EmailDeliveryLogService deliveryLogService;
  @Autowired private EmailDeliveryLogRepository deliveryLogRepository;

  private String tenantSchema;

  @BeforeAll
  void provisionTenant() {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Email Delivery Test Org").schemaName();
    planSyncService.syncPlan(ORG_ID, "pro-plan");
  }

  @Test
  void record_createsEntry() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var referenceId = UUID.randomUUID();
              var result = new SendResult(true, "msg-001", null);

              var log =
                  deliveryLogService.record(
                      "NOTIFICATION",
                      referenceId,
                      "task-assigned",
                      "user@example.com",
                      "smtp",
                      result);

              assertThat(log.getId()).isNotNull();
              assertThat(log.getRecipientEmail()).isEqualTo("user@example.com");
              assertThat(log.getTemplateName()).isEqualTo("task-assigned");
              assertThat(log.getReferenceType()).isEqualTo("NOTIFICATION");
              assertThat(log.getReferenceId()).isEqualTo(referenceId);
              assertThat(log.getStatus()).isEqualTo("SENT");
              assertThat(log.getProviderMessageId()).isEqualTo("msg-001");
              assertThat(log.getProviderSlug()).isEqualTo("smtp");
              assertThat(log.getErrorMessage()).isNull();
              assertThat(log.getCreatedAt()).isNotNull();
            });
  }

  @Test
  void record_failedSendSetsFailedStatus() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var result = new SendResult(false, null, "Connection refused");

              var log =
                  deliveryLogService.record(
                      "INVOICE",
                      UUID.randomUUID(),
                      "invoice-sent",
                      "b@example.com",
                      "smtp",
                      result);

              assertThat(log.getStatus()).isEqualTo("FAILED");
              assertThat(log.getErrorMessage()).isEqualTo("Connection refused");
            });
  }

  @Test
  void updateStatus_updatesExistingEntry() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var result = new SendResult(true, "msg-update-001", null);
              deliveryLogService.record(
                  "NOTIFICATION", UUID.randomUUID(), "welcome", "c@example.com", "smtp", result);

              deliveryLogService.updateStatus(
                  "msg-update-001", EmailDeliveryStatus.DELIVERED, null);

              var updated = deliveryLogRepository.findByProviderMessageId("msg-update-001");
              assertThat(updated).isPresent();
              assertThat(updated.get().getStatus()).isEqualTo("DELIVERED");
            });
  }

  @Test
  void findByFilters_withStatus() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              deliveryLogService.record(
                  "TEST",
                  null,
                  "test-tmpl",
                  "d@example.com",
                  "smtp",
                  new SendResult(true, "msg-filter-1", null));
              deliveryLogService.record(
                  "TEST",
                  null,
                  "test-tmpl",
                  "e@example.com",
                  "smtp",
                  new SendResult(false, null, "err"));

              var from = Instant.now().minus(1, ChronoUnit.HOURS);
              var to = Instant.now().plus(1, ChronoUnit.HOURS);

              var sentPage =
                  deliveryLogService.findByFilters(
                      EmailDeliveryStatus.SENT, from, to, PageRequest.of(0, 50));
              assertThat(sentPage.getContent()).allMatch(l -> "SENT".equals(l.getStatus()));

              var failedPage =
                  deliveryLogService.findByFilters(
                      EmailDeliveryStatus.FAILED, from, to, PageRequest.of(0, 50));
              assertThat(failedPage.getContent()).allMatch(l -> "FAILED".equals(l.getStatus()));
            });
  }

  @Test
  void getStats_returnsCorrectCounts() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              // Record a mix of statuses
              deliveryLogService.record(
                  "TEST",
                  null,
                  "stats-tmpl",
                  "f@example.com",
                  "smtp",
                  new SendResult(true, "msg-stats-1", null));
              deliveryLogService.record(
                  "TEST",
                  null,
                  "stats-tmpl",
                  "g@example.com",
                  "smtp",
                  new SendResult(true, "msg-stats-2", null));
              deliveryLogService.record(
                  "TEST",
                  null,
                  "stats-tmpl",
                  "h@example.com",
                  "smtp",
                  new SendResult(false, null, "bounced"));

              var stats = deliveryLogService.getStats("smtp", 50);

              assertThat(stats.sent24h()).isGreaterThanOrEqualTo(2);
              assertThat(stats.hourlyLimit()).isEqualTo(50);
              assertThat(stats.providerSlug()).isEqualTo("smtp");
            });
  }
}
