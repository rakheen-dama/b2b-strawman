package io.b2mash.b2b.b2bstrawman.multitenancy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Shard metadata entity. The {@code shard_id} is an admin-assigned identifier (e.g. "primary",
 * "kazi_legal_1"). Credentials are resolved from environment variables at runtime, not stored in
 * this table.
 */
@Entity
@Table(name = "shard_config", schema = "public")
public class ShardConfig {

  @Id
  @Column(name = "shard_id", length = 50)
  private String shardId;

  @Column(name = "display_name", nullable = false, length = 100)
  private String displayName;

  @Column(name = "jdbc_url", length = 500)
  private String jdbcUrl;

  @Column(name = "username", length = 100)
  private String username;

  @Column(name = "pool_size", nullable = false)
  private int poolSize = 25;

  @Column(name = "read_only", nullable = false)
  private boolean readOnly = false;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** JPA requires a no-arg constructor. */
  protected ShardConfig() {}

  public ShardConfig(String shardId, String displayName) {
    this.shardId = shardId;
    this.displayName = displayName;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  // --- Getters ---

  public String getShardId() {
    return shardId;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getJdbcUrl() {
    return jdbcUrl;
  }

  public String getUsername() {
    return username;
  }

  public int getPoolSize() {
    return poolSize;
  }

  public boolean isReadOnly() {
    return readOnly;
  }

  public boolean isActive() {
    return active;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  // --- Setters ---

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public void setJdbcUrl(String jdbcUrl) {
    this.jdbcUrl = jdbcUrl;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public void setPoolSize(int poolSize) {
    this.poolSize = poolSize;
  }

  public void setReadOnly(boolean readOnly) {
    this.readOnly = readOnly;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
