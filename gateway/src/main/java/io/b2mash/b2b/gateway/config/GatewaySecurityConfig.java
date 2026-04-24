package io.b2mash.b2b.gateway.config;

import jakarta.servlet.http.Cookie;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class GatewaySecurityConfig {

  private final ClientRegistrationRepository clientRegistrationRepository;
  private final String frontendUrl;

  public GatewaySecurityConfig(
      ClientRegistrationRepository clientRegistrationRepository,
      @Value("${gateway.frontend-url:http://localhost:3000}") String frontendUrl) {
    this.clientRegistrationRepository = clientRegistrationRepository;
    this.frontendUrl = frontendUrl;
  }

  @Bean
  public CookieCsrfTokenRepository csrfTokenRepository() {
    return CookieCsrfTokenRepository.withHttpOnlyFalse();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/", "/error", "/actuator/health", "/bff/me", "/bff/csrf")
                    .permitAll()
                    .requestMatchers("/api/access-requests", "/api/access-requests/verify")
                    .permitAll()
                    .requestMatchers("/internal/**")
                    .denyAll()
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .authenticated())
        .oauth2Login(
            oauth2 ->
                oauth2
                    .defaultSuccessUrl(frontendUrl + "/dashboard", true)
                    .successHandler(oauth2LoginSuccessHandler()))
        .logout(
            logout ->
                logout
                    .logoutSuccessHandler(oidcLogoutSuccessHandler())
                    .invalidateHttpSession(true)
                    .deleteCookies("SESSION"))
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(csrfTokenRepository())
                    .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                    // All requests come server-to-server from Next.js, not from browser JS.
                    // SESSION cookie + SameSite=Lax + CORS provide sufficient CSRF protection.
                    .ignoringRequestMatchers("/bff/**", "/api/**"))
        .exceptionHandling(
            ex ->
                ex.defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    PathPatternRequestMatcher.pathPattern("/api/**")))
        .sessionManagement(
            session ->
                session
                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                    .sessionFixation(sessionFixation -> sessionFixation.changeSessionId()));
    return http.build();
  }

  private CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of(frontendUrl));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
    config.setAllowedHeaders(List.of("Content-Type", "Authorization", "X-XSRF-TOKEN", "Accept"));
    config.setExposedHeaders(List.of("X-XSRF-TOKEN"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  private LogoutSuccessHandler oidcLogoutSuccessHandler() {
    OidcClientInitiatedLogoutSuccessHandler handler =
        new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
    handler.setPostLogoutRedirectUri(frontendUrl);
    return handler;
  }

  private AuthenticationSuccessHandler oauth2LoginSuccessHandler() {
    return buildOauth2LoginSuccessHandler(frontendUrl + "/dashboard");
  }

  /**
   * OAuth2 login success handler that sets a short-lived, session-scoped cookie recording the
   * {@code sub} (subject) of the newly authenticated user. The Next.js middleware reads this cookie
   * and cross-checks it against {@code /bff/me} to detect stale SESSION handoff (GAP-L-22).
   *
   * <p>This handler does NOT invalidate sessions or force logouts — it only emits a passive signal.
   * All decision logic (and any actual session clearing) lives in the Next.js middleware, keeping
   * the arch-consultant's Option B+ invariant that returning users are never silently logged out by
   * a server-side hook.
   *
   * <p>Package-private for unit testing.
   */
  static AuthenticationSuccessHandler buildOauth2LoginSuccessHandler(String defaultTargetUrl) {
    SimpleUrlAuthenticationSuccessHandler delegate =
        new SimpleUrlAuthenticationSuccessHandler(defaultTargetUrl);
    delegate.setAlwaysUseDefaultTargetUrl(true);
    return (request, response, authentication) -> {
      if (authentication != null && authentication.getPrincipal() instanceof OidcUser oidcUser) {
        String sub = oidcUser.getSubject();
        if (sub != null && !sub.isBlank()) {
          Cookie cookie = new Cookie(KC_LAST_LOGIN_SUB_COOKIE, sub);
          cookie.setPath("/");
          // HttpOnly: prevents JS access; middleware still reads it server-side.
          cookie.setHttpOnly(true);
          // Short-lived: 120s is more than enough for the browser to follow the redirect and
          // hit the Next.js middleware once. After the first hit, middleware deletes it.
          cookie.setMaxAge(120);
          cookie.setSecure(request.isSecure());
          response.addCookie(cookie);
        }
      }
      delegate.onAuthenticationSuccess(request, response, authentication);
    };
  }

  /** Cookie name for the short-lived "just authenticated as" signal consumed by Next.js. */
  static final String KC_LAST_LOGIN_SUB_COOKIE = "KC_LAST_LOGIN_SUB";
}
