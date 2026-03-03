package io.b2mash.b2b.b2bstrawman.keycloak;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Configures a RestClient for the Keycloak Admin REST API with OAuth2 client credentials auth. */
@Configuration
@ConditionalOnProperty(name = "keycloak.admin.enabled", havingValue = "true")
@EnableConfigurationProperties(KeycloakConfig.KeycloakAdminProperties.class)
public class KeycloakConfig {

  @ConfigurationProperties("keycloak.admin")
  public record KeycloakAdminProperties(
      boolean enabled, String serverUrl, String realm, String clientId, String clientSecret) {}

  @Bean
  RestClient keycloakAdminRestClient(KeycloakAdminProperties props) {
    return RestClient.builder()
        .baseUrl(props.serverUrl() + "/admin/realms/" + props.realm())
        .requestInterceptor(
            new KeycloakClientCredentialsInterceptor(
                props.serverUrl(), props.realm(), props.clientId(), props.clientSecret()))
        .build();
  }
}
