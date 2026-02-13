package io.b2mash.b2b.b2bstrawman;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  @SuppressWarnings("resource")
  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
  }

  @Bean
  DynamicPropertyRegistrar datasourceProperties(PostgreSQLContainer container) {
    return registry -> {
      registry.add("spring.datasource.app.jdbc-url", container::getJdbcUrl);
      registry.add("spring.datasource.app.username", container::getUsername);
      registry.add("spring.datasource.app.password", container::getPassword);
      registry.add("spring.datasource.migration.jdbc-url", container::getJdbcUrl);
      registry.add("spring.datasource.migration.username", container::getUsername);
      registry.add("spring.datasource.migration.password", container::getPassword);
      // Portal DataSource: same database, search_path set via connection-init-sql
      registry.add("spring.datasource.portal.jdbc-url", container::getJdbcUrl);
      registry.add("spring.datasource.portal.username", container::getUsername);
      registry.add("spring.datasource.portal.password", container::getPassword);
      registry.add(
          "spring.datasource.portal.connection-init-sql",
          () -> "SET search_path TO portal, public");
    };
  }
}
