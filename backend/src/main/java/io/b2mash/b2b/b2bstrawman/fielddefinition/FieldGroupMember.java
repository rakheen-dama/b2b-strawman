package io.b2mash.b2b.b2bstrawman.fielddefinition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "field_group_members")
public class FieldGroupMember {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

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
