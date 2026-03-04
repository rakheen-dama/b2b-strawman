package io.b2mash.b2b.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
public class GatewaySecurityConfig {

  private final ClientRegistrationRepository clientRegistrationRepository;

  public GatewaySecurityConfig(ClientRegistrationRepository clientRegistrationRepository) {
    this.clientRegistrationRepository = clientRegistrationRepository;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/", "/error", "/actuator/health")
                    .permitAll()
                    .requestMatchers("/internal/**")
                    .denyAll()
                    .requestMatchers("/bff/me")
                    .authenticated()
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .authenticated())
        .oauth2Login(oauth2 -> oauth2.defaultSuccessUrl("/"))
        .logout(
            logout ->
                logout
                    .logoutSuccessHandler(oidcLogoutSuccessHandler())
                    .invalidateHttpSession(true)
                    .deleteCookies("SESSION"))
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
        .sessionManagement(
            session ->
                session
                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                    .sessionFixation(sessionFixation -> sessionFixation.changeSessionId()));
    return http.build();
  }

  private LogoutSuccessHandler oidcLogoutSuccessHandler() {
    OidcClientInitiatedLogoutSuccessHandler handler =
        new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
    handler.setPostLogoutRedirectUri("{baseUrl}/");
    return handler;
  }
}
