package io.b2mash.b2b.gateway;

import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Shared session DDL constants and helpers for tests that use H2 with Spring Session JDBC.
 *
 * <p>Centralizes the session table DDL so that {@link SessionStorageTest} and integration tests in
 * {@code io.b2mash.b2b.gateway.integration} can share the same schema definitions without
 * duplication.
 */
public final class SessionTestSupport {

  public static final String CREATE_SESSION_TABLE =
      """
      CREATE TABLE IF NOT EXISTS SPRING_SESSION (
        PRIMARY_ID CHAR(36) NOT NULL,
        SESSION_ID CHAR(36) NOT NULL,
        CREATION_TIME BIGINT NOT NULL,
        LAST_ACCESS_TIME BIGINT NOT NULL,
        MAX_INACTIVE_INTERVAL INT NOT NULL,
        EXPIRY_TIME BIGINT NOT NULL,
        PRINCIPAL_NAME VARCHAR(100),
        CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
      )
      """;

  public static final String CREATE_SESSION_IX1 =
      """
      CREATE UNIQUE INDEX IF NOT EXISTS SPRING_SESSION_IX1
        ON SPRING_SESSION (SESSION_ID)
      """;

  public static final String CREATE_SESSION_IX2 =
      """
      CREATE INDEX IF NOT EXISTS SPRING_SESSION_IX2
        ON SPRING_SESSION (EXPIRY_TIME)
      """;

  public static final String CREATE_SESSION_IX3 =
      """
      CREATE INDEX IF NOT EXISTS SPRING_SESSION_IX3
        ON SPRING_SESSION (PRINCIPAL_NAME)
      """;

  public static final String CREATE_ATTRIBUTES_TABLE =
      """
      CREATE TABLE IF NOT EXISTS SPRING_SESSION_ATTRIBUTES (
        SESSION_PRIMARY_ID CHAR(36) NOT NULL,
        ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
        ATTRIBUTE_BYTES BYTEA NOT NULL,
        CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
        CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK
          FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION(PRIMARY_ID)
          ON DELETE CASCADE
      )
      """;

  private SessionTestSupport() {}

  /** Initializes all Spring Session tables and indexes in the given datasource. */
  public static void initSessionSchema(DataSource dataSource) {
    var jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute(CREATE_SESSION_TABLE);
    jdbcTemplate.execute(CREATE_SESSION_IX1);
    jdbcTemplate.execute(CREATE_SESSION_IX2);
    jdbcTemplate.execute(CREATE_SESSION_IX3);
    jdbcTemplate.execute(CREATE_ATTRIBUTES_TABLE);
  }
}
