package io.b2mash.b2b.b2bstrawman.audit;

import io.b2mash.b2b.b2bstrawman.multitenancy.TenantAware;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantAwareEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.ParamDef;
import org.hibernate.type.SqlTypes;

/**
 * Audit event entity representing a single auditable action on a tenant-scoped entity. Events are
 * append-only -- once written, rows are never updated.
 *
 * <p>Key differences from standard entities: no {@code updatedAt}, no {@code @Version}, no setters
 * (except {@code setTenantId} for {@link TenantAware}). The entity is a pure data container.
 */
@Entity
@Table(name = "audit_events")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners(TenantAwareEntityListener.class)
public class AuditEvent implements TenantAware {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "event_type", nullable = false, length = 100)
  private String eventType;

  @Column(name = "entity_type", nullable = false, length = 50)
  private String entityType;

  @Column(name = "entity_id", nullable = false)
  private UUID entityId;

  @Column(name = "actor_id")
  private UUID actorId;

  @Column(name = "actor_type", nullable = false, length = 20)
  private String actorType;

  @Column(name = "source", nullable = false, length = 30)
  private String source;

  @Column(name = "ip_address", length = 45)
  private String ipAddress;

  @Column(name = "user_agent", length = 500)
  private String userAgent;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "details", columnDefinition = "jsonb")
  private Map<String, Object> details;

  @Column(name = "tenant_id")
  private String tenantId;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  protected AuditEvent() {}

  public AuditEvent(AuditEventRecord record) {
    this.eventType = record.eventType();
    this.entityType = record.entityType();
    this.entityId = record.entityId();
    this.actorId = record.actorId();
    this.actorType = record.actorType();
    this.source = record.source();
    this.ipAddress = record.ipAddress();
    this.userAgent = record.userAgent();
    this.details = record.details();
    this.occurredAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getEventType() {
    return eventType;
  }

  public String getEntityType() {
    return entityType;
  }

  public UUID getEntityId() {
    return entityId;
  }

  public UUID getActorId() {
    return actorId;
  }

  public String getActorType() {
    return actorType;
  }

  public String getSource() {
    return source;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public Map<String, Object> getDetails() {
    return details;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }
}
