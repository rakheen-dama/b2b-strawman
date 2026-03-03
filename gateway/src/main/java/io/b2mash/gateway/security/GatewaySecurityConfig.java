package io.b2mash.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Gateway security configuration — validates JWT signatures at the edge. Invalid or expired tokens
 * are rejected here before reaching the backend. The backend still performs full JWT validation
 * (defense in depth) plus tenant/member resolution from claims.
 *
 * <p>Public endpoints (webhooks, portal auth, actuator) are permitted without authentication. The
 * gateway does NOT expose /internal/** routes — those are only accessible via direct backend access.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

  @Bean
  public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
    http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .authorizeExchange(
            exchanges ->
                exchanges
                    // Public endpoints — no JWT required
                    .pathMatchers("/actuator/**")
                    .permitAll()
                    .pathMatchers("/backend/actuator/**")
                    .permitAll()
                    .pathMatchers("/api/webhooks/**")
                    .permitAll()
                    .pathMatchers("/api/email/unsubscribe")
                    .permitAll()
                    .pathMatchers("/api/portal/acceptance/**")
                    .permitAll()
                    .pathMatchers("/portal/auth/**")
                    .permitAll()
                    .pathMatchers("/portal/dev/**")
                    .permitAll()
                    // Portal endpoints — permitted at gateway level, CustomerAuthFilter handles
                    // auth in backend
                    .pathMatchers("/portal/**")
                    .permitAll()
                    // API endpoints — require valid JWT signature
                    .pathMatchers("/api/**")
                    .authenticated()
                    // Everything else — deny
                    .anyExchange()
                    .denyAll())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

    return http.build();
  }
}
