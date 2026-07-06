package io.b2mash.b2b.b2bstrawman.packs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Tracks an installed content pack within a tenant schema. */
@Entity
@Table(name = "pack_install")
public class PackInstall {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "pack_id", nullable = false, length = 128)
  private String packId;

  @Enumerated(EnumType.STRING)
  @Column(name = "pack_type", nullable = false, length = 64)
  private PackType packType;

  @Column(name = "pack_version", nullable = false, length = 32)
  private String packVersion;

  @Column(name = "pack_name", nullable = false, length = 256)
  private String packName;

  @Column(name = "installed_at", nullable = false)
  private Instant installedAt;

  @Column(name = "installed_by_member_id")
  private UUID installedByMemberId;

  @Column(name = "item_count", nullable = false)
  private int itemCount;

  protected PackInstall() {}

  public PackInstall(
      String packId,
      PackType packType,
      String packVersion,
      String packName,
      Instant installedAt,
      UUID installedByMemberId,
      int itemCount) {
    this.packId = packId;
    this.packType = packType;
    this.packVersion = packVersion;
    this.packName = packName;
    this.installedAt = installedAt;
    this.installedByMemberId = installedByMemberId;
    this.itemCount = itemCount;
  }

  public UUID getId() {
    return id;
  }

  public String getPackId() {
    return packId;
  }

  public PackType getPackType() {
    return packType;
  }

  public String getPackVersion() {
    return packVersion;
  }

  public String getPackName() {
    return packName;
  }

  public Instant getInstalledAt() {
    return installedAt;
  }

  public UUID getInstalledByMemberId() {
    return installedByMemberId;
  }

  public int getItemCount() {
    return itemCount;
  }
}
