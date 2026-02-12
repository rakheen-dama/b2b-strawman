package io.b2mash.b2b.b2bstrawman.security;

import io.b2mash.b2b.b2bstrawman.audit.AuditAuthenticationEntryPoint;
import io.b2mash.b2b.b2bstrawman.member.MemberFilter;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantFilter;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantLoggingFilter;
import io.b2mash.b2b.b2bstrawman.portal.CustomerAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private final ClerkJwtAuthenticationConverter jwtAuthConverter;
  private final ApiKeyAuthFilter apiKeyAuthFilter;
  private final TenantFilter tenantFilter;
  private final MemberFilter memberFilter;
  private final TenantLoggingFilter tenantLoggingFilter;
  private final CustomerAuthFilter customerAuthFilter;
  private final AuditAuthenticationEntryPoint auditAuthEntryPoint;

  public SecurityConfig(
      ClerkJwtAuthenticationConverter jwtAuthConverter,
      ApiKeyAuthFilter apiKeyAuthFilter,
      TenantFilter tenantFilter,
      MemberFilter memberFilter,
      TenantLoggingFilter tenantLoggingFilter,
      CustomerAuthFilter customerAuthFilter,
      AuditAuthenticationEntryPoint auditAuthEntryPoint) {
    this.jwtAuthConverter = jwtAuthConverter;
    this.apiKeyAuthFilter = apiKeyAuthFilter;
    this.tenantFilter = tenantFilter;
    this.memberFilter = memberFilter;
    this.tenantLoggingFilter = tenantLoggingFilter;
    this.customerAuthFilter = customerAuthFilter;
    this.auditAuthEntryPoint = auditAuthEntryPoint;
  }

  /**
   * Portal filter chain for {@code /portal/**} endpoints. Uses CustomerAuthFilter instead of Clerk
   * JWT auth. Public auth endpoints ({@code /portal/auth/**}) are permitted without authentication.
   * Authenticated portal endpoints require a valid portal JWT. No MemberFilter — portal users are
   * customers, not org members.
   */
  @Bean
  @Order(1)
  public SecurityFilterChain portalFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/portal/**")
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/portal/auth/**")
                    .permitAll()
                    .requestMatchers("/portal/**")
                    .permitAll())
        // No oauth2ResourceServer — portal uses CustomerAuthFilter with portal JWTs
        .addFilterBefore(customerAuthFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  /**
   * Main filter chain for staff API ({@code /api/**}), internal endpoints ({@code /internal/**}),
   * and actuator. Uses Clerk JWT authentication + tenant/member resolution.
   */
  @Bean
  @Order(2)
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
            oauth2 ->
                oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter))
                    .authenticationEntryPoint(auditAuthEntryPoint))
        .addFilterBefore(apiKeyAuthFilter, BearerTokenAuthenticationFilter.class)
        .addFilterAfter(tenantFilter, BearerTokenAuthenticationFilter.class)
        .addFilterAfter(memberFilter, TenantFilter.class)
        .addFilterAfter(tenantLoggingFilter, MemberFilter.class);

    return http.build();
  }
}
