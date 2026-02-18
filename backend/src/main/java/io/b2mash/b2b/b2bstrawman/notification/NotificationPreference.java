package io.b2mash.b2b.b2bstrawman.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "member_id", nullable = false)
  private UUID memberId;

  @Column(name = "notification_type", nullable = false, length = 50)
  private String notificationType;

  @Column(name = "in_app_enabled", nullable = false)
  private boolean inAppEnabled;

  @Column(name = "email_enabled", nullable = false)
  private boolean emailEnabled;

  protected NotificationPreference() {}

  public NotificationPreference(
      UUID memberId, String notificationType, boolean inAppEnabled, boolean emailEnabled) {
    this.memberId = memberId;
    this.notificationType = notificationType;
    this.inAppEnabled = inAppEnabled;
    this.emailEnabled = emailEnabled;
  }

  public UUID getId() {
    return id;
  }

  public UUID getMemberId() {
    return memberId;
  }

  public String getNotificationType() {
    return notificationType;
  }

  public boolean isInAppEnabled() {
    return inAppEnabled;
  }

  public void setInAppEnabled(boolean inAppEnabled) {
    this.inAppEnabled = inAppEnabled;
  }

  public boolean isEmailEnabled() {
    return emailEnabled;
  }

  public void setEmailEnabled(boolean emailEnabled) {
    this.emailEnabled = emailEnabled;
  }
}
