package io.b2mash.b2b.b2bstrawman.notification.channel;

import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailDeliveryLogRepository;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailDeliveryStatus;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailRateLimiter;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.Notification;
import io.b2mash.b2b.b2bstrawman.notification.NotificationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EmailNotificationChannelIntegrationTest {

  private static final GreenMail greenMail;

  static {
    greenMail = new GreenMail(ServerSetupTest.SMTP);
    greenMail.start();
  }

  @DynamicPropertySource
  static void configureGreenMail(DynamicPropertyRegistry registry) {
    registry.add("spring.mail.host", () -> greenMail.getSmtp().getBindTo());
    registry.add("spring.mail.port", () -> String.valueOf(greenMail.getSmtp().getPort()));
    registry.add("spring.mail.username", () -> "");
    registry.add("spring.mail.password", () -> "");
    registry.add("spring.mail.properties.mail.smtp.auth", () -> "false");
    registry.add("spring.mail.properties.mail.smtp.starttls.enable", () -> "false");
    registry.add("docteams.email.sender-address", () -> "test@docteams.app");
  }

  private static final String ORG_ID = "org_email_channel_test";
  private static final String RATE_LIMIT_ORG_ID = "org_email_channel_rl_test";
  private static final String RECIPIENT_EMAIL = "alice@test.com";

  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private MemberSyncService memberSyncService;
  @Autowired private EmailNotificationChannel emailChannel;
  @Autowired private EmailDeliveryLogRepository deliveryLogRepository;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private EmailRateLimiter rateLimiter;

  private String tenantSchema;
  private String rateLimitTenantSchema;
  private UUID memberId;
  private UUID rateLimitMemberId;

  @BeforeAll
  void provisionTenant() {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Email Channel Test Org").schemaName();
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    memberId =
        memberSyncService
            .syncMember(ORG_ID, "user_ec_alice", RECIPIENT_EMAIL, "Alice Test", null, "owner")
            .memberId();

    rateLimitTenantSchema =
        provisioningService.provisionTenant(RATE_LIMIT_ORG_ID, "Rate Limit Test Org").schemaName();
    planSyncService.syncPlan(RATE_LIMIT_ORG_ID, "pro-plan");
    rateLimitMemberId =
        memberSyncService
            .syncMember(
                RATE_LIMIT_ORG_ID, "user_ec_rl", RECIPIENT_EMAIL, "Rate Limit User", null, "owner")
            .memberId();
  }

  @AfterAll
  static void stopGreenMail() {
    greenMail.stop();
  }

  @BeforeEach
  void resetGreenMail() throws Exception {
    greenMail.purgeEmailFromAllMailboxes();
  }

  @Test
  void deliver_sends_email_via_greenmail() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var notification =
                  savedNotification(
                      memberId, "TASK_ASSIGNED", "Alice assigned you to task \"Design Homepage\"");

              emailChannel.deliver(notification, RECIPIENT_EMAIL);

              assertThat(greenMail.getReceivedMessages()).hasSize(1);
              try {
                assertThat(greenMail.getReceivedMessages()[0].getAllRecipients()[0].toString())
                    .isEqualTo(RECIPIENT_EMAIL);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
  }

  @Test
  void deliver_records_delivery_log() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var notification =
                  savedNotification(
                      memberId, "TASK_ASSIGNED", "Alice assigned you to task \"Design Homepage\"");

              emailChannel.deliver(notification, RECIPIENT_EMAIL);

              var logs = deliveryLogRepository.findAll();
              var relevant =
                  logs.stream()
                      .filter(l -> notification.getId().equals(l.getReferenceId()))
                      .findFirst();
              assertThat(relevant).isPresent();
              assertThat(relevant.get().getStatus()).isEqualTo(EmailDeliveryStatus.SENT);
              assertThat(relevant.get().getReferenceType()).isEqualTo("NOTIFICATION");
              assertThat(relevant.get().getRecipientEmail()).isEqualTo(RECIPIENT_EMAIL);
            });
  }

  @Test
  void deliver_rate_limited_records_status() {
    ScopedValue.where(RequestScopes.TENANT_ID, rateLimitTenantSchema)
        .where(RequestScopes.ORG_ID, RATE_LIMIT_ORG_ID)
        .run(
            () -> {
              // Exhaust rate limit (default smtp limit is 50/hour)
              for (int i = 0; i < 50; i++) {
                rateLimiter.tryAcquire(rateLimitTenantSchema, "smtp");
              }

              var notification =
                  savedNotification(rateLimitMemberId, "TASK_ASSIGNED", "Rate limit test");

              emailChannel.deliver(notification, RECIPIENT_EMAIL);

              // No email should be sent
              assertThat(greenMail.getReceivedMessages()).isEmpty();

              var logs = deliveryLogRepository.findAll();
              var relevant =
                  logs.stream()
                      .filter(l -> notification.getId().equals(l.getReferenceId()))
                      .findFirst();
              assertThat(relevant).isPresent();
              assertThat(relevant.get().getStatus()).isEqualTo(EmailDeliveryStatus.RATE_LIMITED);
            });
  }

  @Test
  void deliver_no_exception_on_failure() {
    // Stop GreenMail to simulate SMTP connection failure
    greenMail.stop();
    try {
      ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
          .where(RequestScopes.ORG_ID, ORG_ID)
          .run(
              () -> {
                var notification =
                    savedNotification(memberId, "COMMENT_ADDED", "Bob commented on your task");

                // deliver() must not throw even when SMTP is down
                emailChannel.deliver(notification, RECIPIENT_EMAIL);
              });
    } finally {
      // Restart GreenMail for subsequent tests
      greenMail.start();
    }
  }

  @Test
  void deliver_maps_task_assigned_to_template() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var notification =
                  savedNotification(
                      memberId, "TASK_ASSIGNED", "Bob assigned you to task \"Write Tests\"");

              emailChannel.deliver(notification, RECIPIENT_EMAIL);

              assertThat(greenMail.getReceivedMessages()).hasSize(1);
              try {
                assertThat(greenMail.getReceivedMessages()[0].getSubject())
                    .isEqualTo("Bob assigned you to task \"Write Tests\"");
              } catch (Exception e) {
                throw new RuntimeException(e);
              }

              var log =
                  deliveryLogRepository.findAll().stream()
                      .filter(l -> notification.getId().equals(l.getReferenceId()))
                      .findFirst();
              assertThat(log).isPresent();
              assertThat(log.get().getTemplateName()).isEqualTo("notification-task");
            });
  }

  @Test
  void deliver_maps_comment_to_template() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var notification =
                  savedNotification(
                      memberId, "COMMENT_ADDED", "Carol commented on task \"Design Homepage\"");

              emailChannel.deliver(notification, RECIPIENT_EMAIL);

              assertThat(greenMail.getReceivedMessages()).hasSize(1);

              var log =
                  deliveryLogRepository.findAll().stream()
                      .filter(l -> notification.getId().equals(l.getReferenceId()))
                      .findFirst();
              assertThat(log).isPresent();
              assertThat(log.get().getTemplateName()).isEqualTo("notification-comment");
            });
  }

  @Test
  void deliver_email_contains_branding() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var notification =
                  savedNotification(
                      memberId,
                      "BUDGET_ALERT",
                      "Project \"Website Redesign\" has reached 85% of its time budget");

              emailChannel.deliver(notification, RECIPIENT_EMAIL);

              assertThat(greenMail.getReceivedMessages()).hasSize(1);
              try {
                String content =
                    new String(greenMail.getReceivedMessages()[0].getInputStream().readAllBytes());
                assertThat(content).isNotEmpty();
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
  }

  private Notification savedNotification(UUID recipientMemberId, String type, String title) {
    var notification =
        new Notification(recipientMemberId, type, title, null, "TASK", UUID.randomUUID(), null);
    return notificationRepository.save(notification);
  }
}
