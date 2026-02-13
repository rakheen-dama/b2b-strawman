package io.b2mash.b2b.b2bstrawman.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration
public class PortalDataSourceConfig {

  @Bean(name = "portalDataSource")
  @ConfigurationProperties("spring.datasource.portal")
  public HikariDataSource portalDataSource() {
    return new HikariDataSource();
  }

  @Bean(name = "portalJdbcClient")
  public JdbcClient portalJdbcClient(@Qualifier("portalDataSource") DataSource portalDataSource) {
    return JdbcClient.create(portalDataSource);
  }
}
