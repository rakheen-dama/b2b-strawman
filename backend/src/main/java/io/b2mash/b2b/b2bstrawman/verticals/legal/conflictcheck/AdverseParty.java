package io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "adverse_parties")
public class AdverseParty {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "name", nullable = false, length = 300)
  private String name;

  @Column(name = "id_number", length = 20)
  private String idNumber;

  @Column(name = "registration_number", length = 30)
  private String registrationNumber;

  @Column(name = "party_type", nullable = false, length = 20)
  private String partyType;

  @Column(name = "aliases", columnDefinition = "TEXT")
  private String aliases;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected AdverseParty() {}

  public AdverseParty(
      String name,
      String idNumber,
      String registrationNumber,
      String partyType,
      String aliases,
      String notes) {
    this.name = name;
    this.idNumber = idNumber;
    this.registrationNumber = registrationNumber;
    this.partyType = partyType;
    this.aliases = aliases;
    this.notes = notes;
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

  public String getIdNumber() {
    return idNumber;
  }

  public void setIdNumber(String idNumber) {
    this.idNumber = idNumber;
  }

  public String getRegistrationNumber() {
    return registrationNumber;
  }

  public void setRegistrationNumber(String registrationNumber) {
    this.registrationNumber = registrationNumber;
  }

  public String getPartyType() {
    return partyType;
  }

  public void setPartyType(String partyType) {
    this.partyType = partyType;
  }

  public String getAliases() {
    return aliases;
  }

  public void setAliases(String aliases) {
    this.aliases = aliases;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
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
}
