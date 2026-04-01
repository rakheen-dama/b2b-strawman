package io.b2mash.b2b.b2bstrawman.verticals.legal.tariff;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tariff_schedules")
public class TariffSchedule {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "category", nullable = false, length = 20)
  private String category;

  @Column(name = "court_level", nullable = false, length = 30)
  private String courtLevel;

  @Column(name = "effective_from", nullable = false)
  private LocalDate effectiveFrom;

  @Column(name = "effective_to")
  private LocalDate effectiveTo;

  @Column(name = "is_active", nullable = false)
  private boolean isActive;

  @Column(name = "is_system", nullable = false)
  private boolean isSystem;

  @Column(name = "source", length = 100)
  private String source;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<TariffItem> items = new ArrayList<>();

  protected TariffSchedule() {}

  public TariffSchedule(
      String name,
      String category,
      String courtLevel,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      String source) {
    this.name = name;
    this.category = category;
    this.courtLevel = courtLevel;
    this.effectiveFrom = effectiveFrom;
    this.effectiveTo = effectiveTo;
    this.isActive = true;
    this.isSystem = false;
    this.source = source;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getCourtLevel() {
    return courtLevel;
  }

  public void setCourtLevel(String courtLevel) {
    this.courtLevel = courtLevel;
  }

  public LocalDate getEffectiveFrom() {
    return effectiveFrom;
  }

  public void setEffectiveFrom(LocalDate effectiveFrom) {
    this.effectiveFrom = effectiveFrom;
  }

  public LocalDate getEffectiveTo() {
    return effectiveTo;
  }

  public void setEffectiveTo(LocalDate effectiveTo) {
    this.effectiveTo = effectiveTo;
  }

  public boolean isActive() {
    return isActive;
  }

  public void setActive(boolean active) {
    this.isActive = active;
  }

  public boolean isSystem() {
    return isSystem;
  }

  public void setSystem(boolean system) {
    this.isSystem = system;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public List<TariffItem> getItems() {
    return items;
  }
}
