package io.b2mash.b2b.b2bstrawman.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

  @Bean(initMethod = "migrate")
  public Flyway globalFlyway(@Qualifier("migrationDataSource") DataSource migrationDataSource) {
    return Flyway.configure()
        .dataSource(migrationDataSource)
        .locations("classpath:db/migration/global")
        .schemas("public")
        .baselineOnMigrate(true)
        .load();
  }
}
