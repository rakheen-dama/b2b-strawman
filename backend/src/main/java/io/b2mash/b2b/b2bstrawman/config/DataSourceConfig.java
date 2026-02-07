package io.b2mash.b2b.b2bstrawman.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class DataSourceConfig {

  @Bean(name = "appDataSource")
  @Primary
  @ConfigurationProperties("spring.datasource.app")
  public HikariDataSource appDataSource() {
    return new HikariDataSource();
  }

  @Bean(name = "migrationDataSource")
  @ConfigurationProperties("spring.datasource.migration")
  public HikariDataSource migrationDataSource() {
    return new HikariDataSource();
  }
}
