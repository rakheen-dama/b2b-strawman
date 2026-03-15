package io.b2mash.b2b.b2bstrawman.security.keycloak;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "keycloak.admin")
public record KeycloakAdminConfig(
    String authServerUrl, String realm, String username, String password) {}
