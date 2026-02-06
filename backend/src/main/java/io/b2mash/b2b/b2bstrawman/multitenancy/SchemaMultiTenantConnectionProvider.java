package io.b2mash.b2b.b2bstrawman.multitenancy;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;

@Component
public class SchemaMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

  private final DataSource dataSource;

  public SchemaMultiTenantConnectionProvider(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public Connection getAnyConnection() throws SQLException {
    return dataSource.getConnection();
  }

  @Override
  public void releaseAnyConnection(Connection connection) throws SQLException {
    connection.close();
  }

  @Override
  public Connection getConnection(String tenantIdentifier) throws SQLException {
    Connection connection = getAnyConnection();
    setSearchPath(connection, tenantIdentifier);
    return connection;
  }

  @Override
  public void releaseConnection(String tenantIdentifier, Connection connection)
      throws SQLException {
    resetSearchPath(connection);
    releaseAnyConnection(connection);
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
    connection.setReadOnly(false);
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
    if ("public".equals(schema) || schema.matches("^tenant_[0-9a-f]{12}$")) {
      return schema;
    }
    throw new IllegalArgumentException("Invalid schema name: " + schema);
  }
}
