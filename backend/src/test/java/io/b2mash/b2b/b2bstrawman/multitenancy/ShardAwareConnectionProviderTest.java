package io.b2mash.b2b.b2bstrawman.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for {@link ShardAwareConnectionProvider}. Verifies connection routing, search
 * path setting, release reset, and invalid identifier rejection. Uses primary shard only (single
 * embedded Postgres). Multi-shard routing is tested in Epic 555B.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
// @TestPropertySource is required here: application-test.yml disables sharding globally, but this
// test class needs the ShardAwareConnectionProvider and DefaultShardRegistry beans active. This
// genuinely varies per test class per the anti-pattern policy in backend/CLAUDE.md.
@TestPropertySource(properties = "kazi.sharding.enabled=true")
class ShardAwareConnectionProviderTest {

  private static final String TENANT_SCHEMA = "tenant_aaaaaaaaaaaa";

  private final ShardAwareConnectionProvider provider;
  private final DataSource dataSource;

  @Autowired
  ShardAwareConnectionProviderTest(ShardAwareConnectionProvider provider, DataSource dataSource) {
    this.provider = provider;
    this.dataSource = dataSource;
  }

  @BeforeEach
  void setUp() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("CREATE SCHEMA IF NOT EXISTS " + TENANT_SCHEMA);
  }

  @Test
  void getConnection_withPrimaryAndTenantSchema_setsCorrectSearchPath() throws Exception {
    String composite = ShardAndSchema.format("primary", TENANT_SCHEMA);

    Connection conn = provider.getConnection(composite);
    try {
      try (var stmt = conn.createStatement();
          var rs = stmt.executeQuery("SHOW search_path")) {
        rs.next();
        assertThat(rs.getString(1)).contains(TENANT_SCHEMA);
      }
    } finally {
      provider.releaseConnection(composite, conn);
    }
  }

  @Test
  void getConnection_withPrimaryAndPublicSchema_setsSearchPathToPublic() throws Exception {
    String composite = ShardAndSchema.format("primary", "public");

    Connection conn = provider.getConnection(composite);
    try {
      try (var stmt = conn.createStatement();
          var rs = stmt.executeQuery("SHOW search_path")) {
        rs.next();
        assertThat(rs.getString(1)).contains("public");
      }
    } finally {
      provider.releaseConnection(composite, conn);
    }
  }

  @Test
  void getConnection_withInvalidCompositeIdentifier_throwsIllegalArgumentException() {
    assertThatThrownBy(() -> provider.getConnection("not-a-valid-identifier"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void releaseConnection_resetsSearchPathToPublic() throws Exception {
    String composite = ShardAndSchema.format("primary", TENANT_SCHEMA);

    Connection conn = provider.getConnection(composite);
    provider.releaseConnection(composite, conn);

    // Get a fresh connection and verify search_path is back to public
    try (Connection freshConn = provider.getAnyConnection()) {
      try (var stmt = freshConn.createStatement();
          var rs = stmt.executeQuery("SHOW search_path")) {
        rs.next();
        assertThat(rs.getString(1)).contains("public");
      }
    }
  }
}
