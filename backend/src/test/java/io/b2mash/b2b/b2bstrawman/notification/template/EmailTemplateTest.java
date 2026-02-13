package io.b2mash.b2b.b2bstrawman.notification.template;

import static org.assertj.core.api.Assertions.*;

import io.b2mash.b2b.b2bstrawman.notification.Notification;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class EmailTemplateTest {

  private Notification createNotification(String type, String title) {
    return new Notification(
        UUID.randomUUID(), type, title, null, "TASK", UUID.randomUUID(), UUID.randomUUID());
  }

  @ParameterizedTest
  @EnumSource(
      value = EmailTemplate.class,
      names = {"DEFAULT"},
      mode = EnumSource.Mode.EXCLUDE)
  void fromNotificationTypeReturnsCorrectTemplateForKnownTypes(EmailTemplate expected) {
    EmailTemplate result = EmailTemplate.fromNotificationType(expected.name());
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void fromNotificationTypeReturnsDefaultForUnknownType() {
    EmailTemplate result = EmailTemplate.fromNotificationType("UNKNOWN_TYPE");
    assertThat(result).isEqualTo(EmailTemplate.DEFAULT);
  }

  @Test
  void renderSubjectReturnsExpectedString() {
    var notification = createNotification("TASK_ASSIGNED", "Some task title");
    EmailTemplate template = EmailTemplate.TASK_ASSIGNED;

    String subject = template.renderSubject(notification);

    assertThat(subject).isEqualTo("You've been assigned to a task");
  }

  @Test
  void renderBodyIncludesNotificationTitle() {
    String title = "Alice assigned you to task \"Write docs\"";
    var notification = createNotification("TASK_ASSIGNED", title);
    EmailTemplate template = EmailTemplate.TASK_ASSIGNED;

    String body = template.renderBody(notification);

    assertThat(body).contains(title);
    assertThat(body).startsWith("Hi,");
    assertThat(body).endsWith("Best,\nDocTeams");
  }
}
