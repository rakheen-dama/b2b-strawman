package io.b2mash.b2b.b2bstrawman.integration.accounting.xero;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Enables binding of {@link XeroProperties} from application configuration. */
@Configuration
@EnableConfigurationProperties(XeroProperties.class)
class XeroConfig {

  /**
   * RestClient used exclusively by {@link XeroOAuthService} for token endpoint calls
   * (application/x-www-form-urlencoded). Separate from the JSON-based {@link XeroApiClient}
   * RestClient.
   */
  @Bean
  RestClient xeroTokenClient() {
    return RestClient.create();
  }
}
