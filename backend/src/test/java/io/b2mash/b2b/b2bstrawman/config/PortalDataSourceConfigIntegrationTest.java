package io.b2mash.b2b.b2bstrawman.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PortalDataSourceConfigIntegrationTest {

  @Autowired
  @Qualifier("portalJdbcClient")
  private JdbcClient portalJdbcClient;

  @Autowired
  @Qualifier("portalDataSource")
  private DataSource portalDataSource;

  @Autowired
  @Qualifier("appDataSource")
  private DataSource appDataSource;

  @Test
  void portalJdbcClientIsInjectable() {
    assertThat(portalJdbcClient).isNotNull();
  }

  @Test
  void portalJdbcClientCanQueryPortalProjects() {
    var count =
        portalJdbcClient
            .sql("SELECT count(*) FROM portal.portal_projects")
            .query((rs, rowNum) -> rs.getLong(1))
            .single();
    assertThat(count).isGreaterThanOrEqualTo(0L);
  }

  @Test
  void portalJdbcClientCanInsertAndReadBack() {
    UUID projectId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    String orgId = "org_test_portal_ds";

    portalJdbcClient
        .sql(
            """
            INSERT INTO portal.portal_projects
                (id, customer_id, org_id, name, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, now())
            """)
        .params(
            projectId, customerId, orgId, "Test Project", "ACTIVE", Timestamp.from(Instant.now()))
        .update();

    var result =
        portalJdbcClient
            .sql(
                """
                SELECT name FROM portal.portal_projects
                WHERE id = ? AND customer_id = ?
                """)
            .params(projectId, customerId)
            .query((rs, rowNum) -> rs.getString("name"))
            .optional();

    assertThat(result).isPresent().hasValue("Test Project");
  }

  @Test
  void portalDataSourceIsDistinctFromAppDataSource() {
    assertThat(portalDataSource).isNotSameAs(appDataSource);
  }
}
