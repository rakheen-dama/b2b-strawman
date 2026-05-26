package io.b2mash.b2b.b2bstrawman.config;

import java.util.Optional;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider.Configuration;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@org.springframework.context.annotation.Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "1h")
class ShedLockConfig {

  @Bean
  @Profile("!test")
  LockProvider lockProvider(@Qualifier("appDataSource") DataSource dataSource) {
    return new JdbcTemplateLockProvider(
        Configuration.builder()
            .withJdbcTemplate(new org.springframework.jdbc.core.JdbcTemplate(dataSource))
            .withTableName("public.shedlock")
            .usingDbTime()
            .build());
  }

  @Bean
  @Profile("test")
  LockProvider noOpLockProvider() {
    return lockConfiguration -> Optional.of((SimpleLock) () -> {});
  }
}
