package io.b2mash.b2b.b2bstrawman.security;

import io.b2mash.b2b.b2bstrawman.audit.AuditAuthenticationEntryPoint;
import io.b2mash.b2b.b2bstrawman.member.MemberFilter;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantFilter;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantLoggingFilter;
import io.b2mash.b2b.b2bstrawman.portal.CustomerAuthFilter;
import java.util.List;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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
  private final Environment environment;

  public SecurityConfig(
      ClerkJwtAuthenticationConverter jwtAuthConverter,
      ApiKeyAuthFilter apiKeyAuthFilter,
      TenantFilter tenantFilter,
      MemberFilter memberFilter,
      TenantLoggingFilter tenantLoggingFilter,
      CustomerAuthFilter customerAuthFilter,
      AuditAuthenticationEntryPoint auditAuthEntryPoint,
      Environment environment) {
    this.jwtAuthConverter = jwtAuthConverter;
    this.apiKeyAuthFilter = apiKeyAuthFilter;
    this.tenantFilter = tenantFilter;
    this.memberFilter = memberFilter;
    this.tenantLoggingFilter = tenantLoggingFilter;
    this.customerAuthFilter = customerAuthFilter;
    this.auditAuthEntryPoint = auditAuthEntryPoint;
    this.environment = environment;
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
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/portal/auth/**")
                    .permitAll()
                    .requestMatchers("/portal/dev/**")
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
    http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/**")
                    .permitAll()
                    .requestMatchers("/api/webhooks/email/**")
                    .permitAll()
                    .requestMatchers("/api/webhooks/payment/**")
                    .permitAll()
                    .requestMatchers("/api/email/unsubscribe")
                    .permitAll()
                    .requestMatchers("/api/portal/acceptance/**")
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

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    List<String> origins =
        Binder.get(environment)
            .bind("cors.allowed-origins", Bindable.listOf(String.class))
            .orElse(List.of());

    var config = new CorsConfiguration();
    if (!origins.isEmpty()) {
      config.setAllowedOrigins(origins);
    }
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);

    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
