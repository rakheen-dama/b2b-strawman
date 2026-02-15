package io.b2mash.b2b.b2bstrawman.settings;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.ParamDef;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "org_settings")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners(TenantAwareEntityListener.class)
public class OrgSettings implements TenantAware {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "default_currency", nullable = false, length = 3)
  private String defaultCurrency;

  @Column(name = "tenant_id")
  private String tenantId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "field_pack_status", columnDefinition = "jsonb")
  private List<Map<String, Object>> fieldPackStatus;

  protected OrgSettings() {}

  public OrgSettings(String defaultCurrency) {
    this.defaultCurrency = defaultCurrency;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void updateCurrency(String currency) {
    this.defaultCurrency = currency;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getDefaultCurrency() {
    return defaultCurrency;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public List<Map<String, Object>> getFieldPackStatus() {
    return fieldPackStatus;
  }

  public void setFieldPackStatus(List<Map<String, Object>> fieldPackStatus) {
    this.fieldPackStatus = fieldPackStatus;
  }

  /** Records a field pack application in the status list. */
  public void recordPackApplication(String packId, int version) {
    if (this.fieldPackStatus == null) {
      this.fieldPackStatus = new ArrayList<>();
    }
    var entry = new HashMap<String, Object>();
    entry.put("packId", packId);
    entry.put("version", version);
    entry.put("appliedAt", Instant.now().toString());
    this.fieldPackStatus.add(entry);
    this.updatedAt = Instant.now();
  }
}
