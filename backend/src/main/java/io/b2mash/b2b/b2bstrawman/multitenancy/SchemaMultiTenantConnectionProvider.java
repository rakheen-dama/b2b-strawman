package io.b2mash.b2b.b2bstrawman.multitenancy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SchemaMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

  private static final Logger log =
      LoggerFactory.getLogger(SchemaMultiTenantConnectionProvider.class);

  private static final String SHARED_SCHEMA = "tenant_shared";
  private static final Pattern SCHEMA_PATTERN = Pattern.compile("^tenant_[0-9a-f]{12}$");
  private static final Pattern ORG_ID_PATTERN = Pattern.compile("^org_[a-zA-Z0-9_]+$");

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
    try {
      setSearchPath(connection, tenantIdentifier);
      setCurrentTenant(connection, tenantIdentifier);
    } catch (SQLException e) {
      // Release connection on setup failure to prevent pool leak
      releaseAnyConnection(connection);
      throw e;
    }
    return connection;
  }

  @Override
  public void releaseConnection(String tenantIdentifier, Connection connection)
      throws SQLException {
    resetCurrentTenant(connection, tenantIdentifier);
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

  /**
   * For shared schema, sets the Postgres session variable used by RLS policies. This is
   * defense-in-depth alongside the Hibernate @Filter — catches native SQL and direct DB access.
   */
  private void setCurrentTenant(Connection connection, String schema) throws SQLException {
    if (SHARED_SCHEMA.equals(schema) && RequestScopes.ORG_ID.isBound()) {
      String orgId = RequestScopes.ORG_ID.get();
      validateOrgId(orgId);
      // Use set_config() with parameterized query to prevent SQL injection.
      // SET does not support placeholders, but set_config() does.
      try (var stmt =
          connection.prepareStatement("SELECT set_config('app.current_tenant', ?, false)")) {
        stmt.setString(1, orgId);
        stmt.execute();
      }
    }
  }

  private void resetCurrentTenant(Connection connection, String schema) throws SQLException {
    if (SHARED_SCHEMA.equals(schema)) {
      try (var stmt = connection.createStatement()) {
        stmt.execute("RESET app.current_tenant");
      }
    }
  }

  private String sanitizeSchema(String schema) {
    if ("public".equals(schema)
        || SHARED_SCHEMA.equals(schema)
        || SCHEMA_PATTERN.matcher(schema).matches()) {
      return schema;
    }
    throw new IllegalArgumentException("Invalid schema name: " + schema);
  }

  private void validateOrgId(String orgId) {
    if (!ORG_ID_PATTERN.matcher(orgId).matches()) {
      throw new IllegalArgumentException("Invalid org ID: " + orgId);
    }
  }
}
