package io.b2mash.b2b.b2bstrawman.multitenancy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Shard-aware Hibernate connection provider. Parses composite tenant identifiers of the form {@code
 * {shardId}:{schemaName}} and routes connections to the correct shard DataSource via {@link
 * ShardRegistry}. Active only when {@code kazi.sharding.enabled=true}.
 *
 * <p>The primary {@link DataSource} is injected directly for {@link #getAnyConnection()} —
 * Hibernate calls this during EntityManagerFactory bootstrap to detect the dialect, so it must not
 * depend on {@link ShardRegistry} (which requires JPA repositories that in turn need the EMF).
 *
 * <p>{@code @Lazy} on {@link ShardRegistry} breaks the remaining circular dependency:
 * EntityManagerFactory -> HibernateMultiTenancyConfig -> ShardAwareConnectionProvider ->
 * ShardRegistry -> ShardConfigRepository -> EntityManagerFactory. The registry proxy is resolved on
 * first tenant-specific connection request, by which time the EntityManagerFactory is fully
 * initialized.
 *
 * @see ShardAndSchema
 * @see SchemaMultiTenantConnectionProvider
 */
@Component
@ConditionalOnProperty(name = "kazi.sharding.enabled", havingValue = "true")
public class ShardAwareConnectionProvider implements MultiTenantConnectionProvider<String> {

  private static final Logger log = LoggerFactory.getLogger(ShardAwareConnectionProvider.class);

  private static final Pattern SCHEMA_PATTERN = Pattern.compile("^tenant_[0-9a-f]{12}$");

  private final DataSource primaryDataSource;
  private final ShardRegistry shardRegistry;

  public ShardAwareConnectionProvider(
      DataSource primaryDataSource, @Lazy ShardRegistry shardRegistry) {
    this.primaryDataSource = primaryDataSource;
    this.shardRegistry = shardRegistry;
  }

  @Override
  public Connection getAnyConnection() throws SQLException {
    return primaryDataSource.getConnection();
  }

  @Override
  public void releaseAnyConnection(Connection connection) throws SQLException {
    connection.close();
  }

  @Override
  public Connection getConnection(String tenantIdentifier) throws SQLException {
    ShardAndSchema parsed = ShardAndSchema.parse(tenantIdentifier);
    Connection connection = shardRegistry.getDataSource(parsed.shardId()).getConnection();
    try {
      setSearchPath(connection, parsed.schemaName());
    } catch (SQLException e) {
      // Release connection on setup failure to prevent pool leak
      connection.close();
      throw e;
    }
    return connection;
  }

  @Override
  public void releaseConnection(String tenantIdentifier, Connection connection)
      throws SQLException {
    try {
      resetSearchPath(connection);
    } catch (SQLException e) {
      log.warn(
          "Failed to reset search_path on connection release for tenant {}: {}",
          tenantIdentifier,
          e.getMessage());
    } finally {
      connection.close();
    }
  }

  @Override
  public Connection getReadOnlyConnection(String tenantIdentifier) throws SQLException {
    Connection connection = getConnection(tenantIdentifier);
    connection.setReadOnly(true);
    return connection;
  }

  @Override
  public void releaseReadOnlyConnection(String tenantIdentifier, Connection connection)
      throws SQLException {
    try {
      connection.setReadOnly(false);
    } catch (SQLException e) {
      log.warn(
          "Failed to clear read-only flag on connection release for tenant {}: {}",
          tenantIdentifier,
          e.getMessage());
    }
    releaseConnection(tenantIdentifier, connection);
  }

  @Override
  public boolean supportsAggressiveRelease() {
    return false;
  }

  @Override
  public boolean isUnwrappableAs(Class<?> unwrapType) {
    return MultiTenantConnectionProvider.class.isAssignableFrom(unwrapType);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T unwrap(Class<T> unwrapType) {
    if (isUnwrappableAs(unwrapType)) {
      return (T) this;
    }
    throw new IllegalArgumentException("Cannot unwrap to " + unwrapType);
  }

  private void setSearchPath(Connection connection, String schema) throws SQLException {
    try (var stmt = connection.createStatement()) {
      stmt.execute("SET search_path TO " + sanitizeSchema(schema));
    }
  }

  private void resetSearchPath(Connection connection) throws SQLException {
    try (var stmt = connection.createStatement()) {
      stmt.execute("SET search_path TO public");
    }
  }

  private String sanitizeSchema(String schema) {
    if ("public".equals(schema) || SCHEMA_PATTERN.matcher(schema).matches()) {
      return schema;
    }
    throw new IllegalArgumentException("Invalid schema name: " + schema);
  }
}
