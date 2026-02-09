package io.b2mash.b2b.b2bstrawman.multitenancy;

import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import org.hibernate.cfg.MultiTenancySettings;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class HibernateMultiTenancyConfig {

  @Bean
  HibernatePropertiesCustomizer multiTenancyCustomizer(
      SchemaMultiTenantConnectionProvider connectionProvider,
      TenantIdentifierResolver tenantResolver) {
    return (Map<String, Object> hibernateProperties) -> {
      hibernateProperties.put(
          MultiTenancySettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
      hibernateProperties.put(
          MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantResolver);
    };
  }

  @Bean
  @Primary
  PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
    return new TenantFilterTransactionManager(emf);
  }
}
