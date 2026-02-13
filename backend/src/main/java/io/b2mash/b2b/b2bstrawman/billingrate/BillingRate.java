package io.b2mash.b2b.b2bstrawman.billingrate;

import io.b2mash.b2b.b2bstrawman.multitenancy.TenantAware;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantAwareEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

@Entity
@Table(name = "billing_rates")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners(TenantAwareEntityListener.class)
public class BillingRate implements TenantAware {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "member_id", nullable = false)
  private UUID memberId;

  @Column(name = "project_id")
  private UUID projectId;

  @Column(name = "customer_id")
  private UUID customerId;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "hourly_rate", nullable = false, precision = 12, scale = 2)
  private BigDecimal hourlyRate;

  @Column(name = "effective_from", nullable = false)
  private LocalDate effectiveFrom;

  @Column(name = "effective_to")
  private LocalDate effectiveTo;

  @Column(name = "tenant_id")
  private String tenantId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected BillingRate() {}

  public BillingRate(
      UUID memberId,
      UUID projectId,
      UUID customerId,
      String currency,
      BigDecimal hourlyRate,
      LocalDate effectiveFrom,
      LocalDate effectiveTo) {
    this.memberId = memberId;
    this.projectId = projectId;
    this.customerId = customerId;
    this.currency = currency;
    this.hourlyRate = hourlyRate;
    this.effectiveFrom = effectiveFrom;
    this.effectiveTo = effectiveTo;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void update(
      BigDecimal hourlyRate, String currency, LocalDate effectiveFrom, LocalDate effectiveTo) {
    this.hourlyRate = hourlyRate;
    this.currency = currency;
    this.effectiveFrom = effectiveFrom;
    this.effectiveTo = effectiveTo;
    this.updatedAt = Instant.now();
  }

  /**
   * Returns the scope level of this billing rate based on which optional fields are set.
   *
   * @return "PROJECT_OVERRIDE" if projectId is set, "CUSTOMER_OVERRIDE" if customerId is set,
   *     "MEMBER_DEFAULT" otherwise
   */
  public String getScope() {
    if (projectId != null) {
      return "PROJECT_OVERRIDE";
    }
    if (customerId != null) {
      return "CUSTOMER_OVERRIDE";
    }
    return "MEMBER_DEFAULT";
  }

  public UUID getId() {
    return id;
  }

  public UUID getMemberId() {
    return memberId;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public String getCurrency() {
    return currency;
  }

  public BigDecimal getHourlyRate() {
    return hourlyRate;
  }

  public LocalDate getEffectiveFrom() {
    return effectiveFrom;
  }

  public LocalDate getEffectiveTo() {
    return effectiveTo;
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
}
