package io.b2mash.b2b.b2bstrawman.verticals.legal.tariff;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "tariff_items")
public class TariffItem {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "schedule_id")
  private TariffSchedule schedule;

  @Column(name = "item_number", nullable = false, length = 20)
  private String itemNumber;

  @Column(name = "section", nullable = false, length = 100)
  private String section;

  @Column(name = "description", nullable = false, columnDefinition = "TEXT")
  private String description;

  @Column(name = "amount", nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Column(name = "unit", nullable = false, length = 30)
  private String unit;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  protected TariffItem() {}

  public TariffItem(
      TariffSchedule schedule,
      String itemNumber,
      String section,
      String description,
      BigDecimal amount,
      String unit,
      String notes,
      int sortOrder) {
    this.schedule = schedule;
    this.itemNumber = itemNumber;
    this.section = section;
    this.description = description;
    this.amount = amount;
    this.unit = unit;
    this.notes = notes;
    this.sortOrder = sortOrder;
  }

  public UUID getId() {
    return id;
  }

  public TariffSchedule getSchedule() {
    return schedule;
  }

  public void setSchedule(TariffSchedule schedule) {
    this.schedule = schedule;
  }

  public String getItemNumber() {
    return itemNumber;
  }

  public void setItemNumber(String itemNumber) {
    this.itemNumber = itemNumber;
  }

  public String getSection() {
    return section;
  }

  public void setSection(String section) {
    this.section = section;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public String getUnit() {
    return unit;
  }

  public void setUnit(String unit) {
    this.unit = unit;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
  }
}
