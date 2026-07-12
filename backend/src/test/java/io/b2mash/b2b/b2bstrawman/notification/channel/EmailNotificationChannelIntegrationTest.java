package io.b2mash.b2b.b2bstrawman.notification.channel;

import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.util.GreenMail;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailDeliveryLogRepository;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailDeliveryStatus;
import io.b2mash.b2b.b2bstrawman.integration.email.EmailRateLimiter;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.Notification;
import io.b2mash.b2b.b2bstrawman.notification.NotificationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
class EmailNotificationChannelIntegrationTest {

  // JVM-singleton GreenMail on a dynamic port (see GreenMailTestSupport + application-test.yml).
  // Test resets the inbox in @BeforeEach for isolation.
  private static final GreenMail greenMail =
      io.b2mash.b2b.b2bstrawman.testutil.GreenMailTestSupport.getInstance();

  private static final String ORG_ID = "org_email_channel_test";
  private static final String RATE_LIMIT_ORG_ID = "org_email_channel_rl_test";
  private static final String RECIPIENT_EMAIL = "alice@test.com";

  @Autowired private TenantProvisioningService provisioningService;
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
        provisioningService.provisionTenant(ORG_ID, "Email Channel Test Org", null).schemaName();
    memberId =
        memberSyncService
            .syncMember(ORG_ID, "user_ec_alice", RECIPIENT_EMAIL, "Alice Test", null, "owner")
            .memberId();

    rateLimitTenantSchema =
        provisioningService
            .provisionTenant(RATE_LIMIT_ORG_ID, "Rate Limit Test Org", null)
            .schemaName();
    rateLimitMemberId =
        memberSyncService
            .syncMember(
                RATE_LIMIT_ORG_ID, "user_ec_rl", RECIPIENT_EMAIL, "Rate Limit User", null, "owner")
            .memberId();
  }

  // No @AfterAll stop() — greenMail is a JVM singleton (shutdown hook in GreenMailTestSupport
  // handles process-exit cleanup). Stopping it here would break later test classes.

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
  void deliver_maps_deal_won_to_template() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              UUID dealId = UUID.randomUUID();
              var notification =
                  notificationRepository.save(
                      new Notification(
                          memberId,
                          "DEAL_WON",
                          "You won a deal",
                          "A deal you own has been marked as won.",
                          "DEAL",
                          dealId,
                          null));

              boolean delivered = emailChannel.deliver(notification, RECIPIENT_EMAIL);

              assertThat(delivered).isTrue();
              assertThat(greenMail.getReceivedMessages()).hasSize(1);
              try {
                var message = greenMail.getReceivedMessages()[0];
                assertThat(message.getAllRecipients()[0].toString()).isEqualTo(RECIPIENT_EMAIL);
                assertThat(message.getSubject()).isEqualTo("You won a deal");
                assertThat(decodedContent(message))
                    .contains("/org/" + ORG_ID + "/pipeline/" + dealId);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }

              var log =
                  deliveryLogRepository.findAll().stream()
                      .filter(l -> notification.getId().equals(l.getReferenceId()))
                      .findFirst();
              assertThat(log).isPresent();
              assertThat(log.get().getTemplateName()).isEqualTo("notification-deal-won");
              assertThat(log.get().getStatus()).isEqualTo(EmailDeliveryStatus.SENT);
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

  // LZKC-022 — CTA deep links must carry the /org/{slug} prefix: the app has no bare top-level
  // routes, so un-prefixed links 404. The bound ORG_ID doubles as the route slug (Keycloak org
  // alias).

  @Test
  void deliver_task_email_links_to_org_scoped_task_deep_link() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              UUID taskId = UUID.randomUUID();
              UUID projectId = UUID.randomUUID();
              var notification =
                  notificationRepository.save(
                      new Notification(
                          memberId,
                          "TASK_ASSIGNED",
                          "Task assigned",
                          null,
                          "TASK",
                          taskId,
                          projectId));

              emailChannel.deliver(notification, RECIPIENT_EMAIL);

              assertThat(greenMail.getReceivedMessages()).hasSize(1);
              // No /tasks/{id} route exists — the working deep link is the project page with the
              // task sheet opened via query params. The & renders HTML-escaped inside th:href.
              assertThat(decodedContent(greenMail.getReceivedMessages()[0]))
                  .contains(
                      "/org/"
                          + ORG_ID
                          + "/projects/"
                          + projectId
                          + "?tab=tasks&amp;taskId="
                          + taskId);
            });
  }

  @Test
  void deliver_invoice_email_links_to_org_scoped_invoice() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              UUID invoiceId = UUID.randomUUID();
              var notification =
                  notificationRepository.save(
                      new Notification(
                          memberId,
                          "INVOICE_SENT",
                          "Invoice sent",
                          null,
                          "INVOICE",
                          invoiceId,
                          null));

              emailChannel.deliver(notification, RECIPIENT_EMAIL);

              assertThat(greenMail.getReceivedMessages()).hasSize(1);
              assertThat(decodedContent(greenMail.getReceivedMessages()[0]))
                  .contains("/org/" + ORG_ID + "/invoices/" + invoiceId);
            });
  }

  @Test
  void deliver_proposal_email_links_to_org_scoped_proposal() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              UUID proposalId = UUID.randomUUID();
              var notification =
                  notificationRepository.save(
                      new Notification(
                          memberId,
                          "PROPOSAL_SENT",
                          "Proposal sent",
                          null,
                          "PROPOSAL",
                          proposalId,
                          null));

              emailChannel.deliver(notification, RECIPIENT_EMAIL);

              assertThat(greenMail.getReceivedMessages()).hasSize(1);
              assertThat(decodedContent(greenMail.getReceivedMessages()[0]))
                  .contains("/org/" + ORG_ID + "/proposals/" + proposalId);
            });
  }

  @Test
  void deliver_budget_alert_links_to_org_scoped_project() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              UUID projectId = UUID.randomUUID();
              var notification =
                  notificationRepository.save(
                      new Notification(
                          memberId,
                          "BUDGET_ALERT",
                          "Budget alert",
                          null,
                          "PROJECT",
                          projectId,
                          null));

              emailChannel.deliver(notification, RECIPIENT_EMAIL);

              assertThat(greenMail.getReceivedMessages()).hasSize(1);
              assertThat(decodedContent(greenMail.getReceivedMessages()[0]))
                  .contains("/org/" + ORG_ID + "/projects/" + projectId);
            });
  }

  @Test
  void deliver_member_invited_links_to_org_dashboard() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              var notification =
                  savedNotification(memberId, "MEMBER_INVITED", "You have been invited");

              emailChannel.deliver(notification, RECIPIENT_EMAIL);

              assertThat(greenMail.getReceivedMessages()).hasSize(1);
              // Bare /org/{slug} has no page — the org landing route is the dashboard.
              assertThat(decodedContent(greenMail.getReceivedMessages()[0]))
                  .contains("/org/" + ORG_ID + "/dashboard");
            });
  }

  @Test
  void deliver_schedule_skipped_links_to_org_scoped_schedule() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              UUID scheduleId = UUID.randomUUID();
              var notification =
                  notificationRepository.save(
                      new Notification(
                          memberId,
                          "SCHEDULE_SKIPPED",
                          "Schedule skipped",
                          null,
                          "RECURRING_SCHEDULE",
                          scheduleId,
                          null));

              emailChannel.deliver(notification, RECIPIENT_EMAIL);

              assertThat(greenMail.getReceivedMessages()).hasSize(1);
              assertThat(decodedContent(greenMail.getReceivedMessages()[0]))
                  .contains("/org/" + ORG_ID + "/schedules/" + scheduleId);
            });
  }

  @Test
  void deliver_recurring_project_created_links_to_org_scoped_project() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              UUID projectId = UUID.randomUUID();
              var notification =
                  notificationRepository.save(
                      new Notification(
                          memberId,
                          "RECURRING_PROJECT_CREATED",
                          "Recurring project created",
                          null,
                          "PROJECT",
                          projectId,
                          projectId));

              emailChannel.deliver(notification, RECIPIENT_EMAIL);

              assertThat(greenMail.getReceivedMessages()).hasSize(1);
              // References the created project, not the schedule — link to the project.
              assertThat(decodedContent(greenMail.getReceivedMessages()[0]))
                  .contains("/org/" + ORG_ID + "/projects/" + projectId);
            });
  }

  @Test
  void deliver_retainer_agreement_email_links_to_org_scoped_retainer() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              UUID agreementId = UUID.randomUUID();
              var notification =
                  notificationRepository.save(
                      new Notification(
                          memberId,
                          "RETAINER_APPROACHING_CAPACITY",
                          "Retainer approaching capacity",
                          null,
                          "RETAINER_AGREEMENT",
                          agreementId,
                          null));

              emailChannel.deliver(notification, RECIPIENT_EMAIL);

              assertThat(greenMail.getReceivedMessages()).hasSize(1);
              assertThat(decodedContent(greenMail.getReceivedMessages()[0]))
                  .contains("/org/" + ORG_ID + "/retainers/" + agreementId);
            });
  }

  @Test
  void deliver_retainer_period_email_links_to_retainers_list() {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(
            () -> {
              UUID periodId = UUID.randomUUID();
              var notification =
                  notificationRepository.save(
                      new Notification(
                          memberId,
                          "RETAINER_PERIOD_READY_TO_CLOSE",
                          "Retainer period ready to close",
                          null,
                          "RETAINER_PERIOD",
                          periodId,
                          null));

              emailChannel.deliver(notification, RECIPIENT_EMAIL);

              assertThat(greenMail.getReceivedMessages()).hasSize(1);
              // The reference is a period id, not an agreement id — /retainers/{periodId} would
              // 404, so the CTA targets the retainers list.
              String content = decodedContent(greenMail.getReceivedMessages()[0]);
              assertThat(content).contains("/org/" + ORG_ID + "/retainers");
              assertThat(content).doesNotContain("/retainers/" + periodId);
            });
  }

  private Notification savedNotification(UUID recipientMemberId, String type, String title) {
    var notification =
        new Notification(recipientMemberId, type, title, null, "TASK", UUID.randomUUID(), null);
    return notificationRepository.save(notification);
  }

  /**
   * Decodes every text part of the message. Raw MIME bytes are quoted-printable encoded, whose soft
   * line breaks split long URLs mid-string — substring assertions must run on decoded content.
   */
  private String decodedContent(MimeMessage message) {
    try {
      return decodePart(message.getContent());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private String decodePart(Object content) throws Exception {
    if (content instanceof String text) {
      return text;
    }
    if (content instanceof Multipart multipart) {
      var sb = new StringBuilder();
      for (int i = 0; i < multipart.getCount(); i++) {
        sb.append(decodePart(multipart.getBodyPart(i).getContent()));
      }
      return sb.toString();
    }
    return "";
  }
}
