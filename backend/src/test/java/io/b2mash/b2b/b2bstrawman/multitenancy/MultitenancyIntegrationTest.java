package io.b2mash.b2b.b2bstrawman.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("local")
class MultitenancyIntegrationTest {

  @Autowired private DataSource dataSource;

  @Autowired private OrgSchemaMappingRepository mappingRepository;

  private JdbcTemplate jdbc;

  private static final String TENANT_A = "tenant_aaaaaaaaaaaa";
  private static final String TENANT_B = "tenant_bbbbbbbbbbbb";

  @BeforeEach
  void setUp() {
    jdbc = new JdbcTemplate(dataSource);

    // Reset search_path in case a previous test left it on a tenant schema
    jdbc.execute("SET search_path TO public");

    jdbc.execute("CREATE SCHEMA IF NOT EXISTS " + TENANT_A);
    jdbc.execute("CREATE SCHEMA IF NOT EXISTS " + TENANT_B);
    jdbc.execute(
        "CREATE TABLE IF NOT EXISTS "
            + TENANT_A
            + ".test_data (id SERIAL PRIMARY KEY, value TEXT)");
    jdbc.execute(
        "CREATE TABLE IF NOT EXISTS "
            + TENANT_B
            + ".test_data (id SERIAL PRIMARY KEY, value TEXT)");

    jdbc.execute("DELETE FROM " + TENANT_A + ".test_data");
    jdbc.execute("DELETE FROM " + TENANT_B + ".test_data");

    jdbc.execute(
        "INSERT INTO org_schema_mapping (id, clerk_org_id, schema_name, created_at) "
            + "VALUES (gen_random_uuid(), 'org_aaa', '"
            + TENANT_A
            + "', now()) "
            + "ON CONFLICT (clerk_org_id) DO NOTHING");
    jdbc.execute(
        "INSERT INTO org_schema_mapping (id, clerk_org_id, schema_name, created_at) "
            + "VALUES (gen_random_uuid(), 'org_bbb', '"
            + TENANT_B
            + "', now()) "
            + "ON CONFLICT (clerk_org_id) DO NOTHING");
  }

  @Test
  void tenantIsolation_dataInSchemaA_notVisibleInSchemaB() {
    jdbc.execute("SET search_path TO " + TENANT_A);
    jdbc.execute("INSERT INTO test_data (value) VALUES ('tenant-a-data')");

    jdbc.execute("SET search_path TO " + TENANT_B);
    jdbc.execute("INSERT INTO test_data (value) VALUES ('tenant-b-data')");

    jdbc.execute("SET search_path TO " + TENANT_A);
    var resultA = jdbc.queryForList("SELECT value FROM test_data", String.class);
    assertThat(resultA).containsExactly("tenant-a-data");

    jdbc.execute("SET search_path TO " + TENANT_B);
    var resultB = jdbc.queryForList("SELECT value FROM test_data", String.class);
    assertThat(resultB).containsExactly("tenant-b-data");

    jdbc.execute("SET search_path TO public");
  }

  @Test
  void schemaMapping_lookupByClerkOrgId() {
    var mapping = mappingRepository.findByClerkOrgId("org_aaa");
    assertThat(mapping).isPresent();
    assertThat(mapping.get().getSchemaName()).isEqualTo(TENANT_A);
  }

  @Test
  void schemaMapping_returnsEmptyForUnknownOrg() {
    var mapping = mappingRepository.findByClerkOrgId("org_unknown");
    assertThat(mapping).isEmpty();
  }

  @Test
  void connectionProvider_setsSearchPath() throws Exception {
    var provider = new SchemaMultiTenantConnectionProvider(dataSource);

    var conn = provider.getConnection(TENANT_A);
    try {
      try (var stmt = conn.createStatement();
          var rs = stmt.executeQuery("SHOW search_path")) {
        rs.next();
        assertThat(rs.getString(1)).contains(TENANT_A);
      }
    } finally {
      provider.releaseConnection(TENANT_A, conn);
    }
  }

  @Test
  void connectionProvider_resetsSearchPathOnRelease() throws Exception {
    var provider = new SchemaMultiTenantConnectionProvider(dataSource);

    var conn = provider.getConnection(TENANT_A);
    provider.releaseConnection(TENANT_A, conn);

    // Get a new connection and verify search_path is reset
    try (var freshConn = provider.getAnyConnection()) {
      try (var stmt = freshConn.createStatement();
          var rs = stmt.executeQuery("SHOW search_path")) {
        rs.next();
        // After release, the connection had search_path reset to public
        // A fresh connection from pool should have public search_path
        assertThat(rs.getString(1)).contains("public");
      }
    }
  }
}
