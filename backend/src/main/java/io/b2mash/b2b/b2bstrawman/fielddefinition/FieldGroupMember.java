package io.b2mash.b2b.b2bstrawman.fielddefinition;

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
@Table(name = "field_group_members")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners(TenantAwareEntityListener.class)
public class FieldGroupMember implements TenantAware {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id")
  private String tenantId;

  @Column(name = "field_group_id", nullable = false)
  private UUID fieldGroupId;

  @Column(name = "field_definition_id", nullable = false)
  private UUID fieldDefinitionId;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  protected FieldGroupMember() {}

  public FieldGroupMember(UUID fieldGroupId, UUID fieldDefinitionId, int sortOrder) {
    this.fieldGroupId = fieldGroupId;
    this.fieldDefinitionId = fieldDefinitionId;
    this.sortOrder = sortOrder;
  }

  // --- TenantAware ---

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  // --- Getters ---

  public UUID getId() {
    return id;
  }

  public UUID getFieldGroupId() {
    return fieldGroupId;
  }

  public UUID getFieldDefinitionId() {
    return fieldDefinitionId;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  // --- Setters ---

  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
  }
}
