package io.b2mash.b2b.b2bstrawman.notification;

import io.b2mash.b2b.b2bstrawman.multitenancy.TenantAware;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantAwareEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

@Entity
@Table(name = "notification_preferences")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners(TenantAwareEntityListener.class)
public class NotificationPreference implements TenantAware {

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

  @Column(name = "tenant_id")
  private String tenantId;

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

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }
}
