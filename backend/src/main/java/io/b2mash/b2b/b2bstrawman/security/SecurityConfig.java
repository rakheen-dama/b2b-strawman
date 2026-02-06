package io.b2mash.b2b.b2bstrawman.security;

import io.b2mash.b2b.b2bstrawman.multitenancy.TenantFilter;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantLoggingFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("!local")
public class SecurityConfig {

  private final ClerkJwtAuthenticationConverter jwtAuthConverter;
  private final ApiKeyAuthFilter apiKeyAuthFilter;
  private final TenantFilter tenantFilter;
  private final TenantLoggingFilter tenantLoggingFilter;

  public SecurityConfig(
      ClerkJwtAuthenticationConverter jwtAuthConverter,
      ApiKeyAuthFilter apiKeyAuthFilter,
      TenantFilter tenantFilter,
      TenantLoggingFilter tenantLoggingFilter) {
    this.jwtAuthConverter = jwtAuthConverter;
    this.apiKeyAuthFilter = apiKeyAuthFilter;
    this.tenantFilter = tenantFilter;
    this.tenantLoggingFilter = tenantLoggingFilter;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/**")
                    .permitAll()
                    .requestMatchers("/internal/**")
                    .authenticated()
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .oauth2ResourceServer(
            oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter)))
        .addFilterBefore(apiKeyAuthFilter, BearerTokenAuthenticationFilter.class)
        .addFilterAfter(tenantFilter, BearerTokenAuthenticationFilter.class)
        .addFilterAfter(tenantLoggingFilter, TenantFilter.class);

    return http.build();
  }
}
