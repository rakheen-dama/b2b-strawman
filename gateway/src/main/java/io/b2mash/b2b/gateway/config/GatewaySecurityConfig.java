package io.b2mash.b2b.gateway.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
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
                    .authorizationEndpoint(
                        authorization ->
                            authorization.authorizationRequestResolver(
                                changePasswordAuthorizationRequestResolver(
                                    clientRegistrationRepository)))
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

  /**
   * Gateway-private sentinel query parameter the frontend appends to the standard authorization
   * endpoint ({@code /oauth2/authorization/keycloak?bff_action=change_password}) to initiate the
   * Keycloak change-password required action (Epic 571A). This name is intentionally DISTINCT from
   * the outbound Keycloak {@code kc_action} param so the inbound sentinel can never be confused
   * with — or duplicated alongside — the param the resolver injects on the outgoing authorization
   * request (review Finding 1). The resolver maps this sentinel to the real KC {@code
   * kc_action=UPDATE_PASSWORD} param and does NOT forward the sentinel itself onward.
   */
  static final String CHANGE_PASSWORD_SENTINEL_PARAM = "bff_action";

  static final String CHANGE_PASSWORD_SENTINEL_VALUE = "change_password";

  /** Keycloak authorization-endpoint param + value for the change-password required action. */
  static final String KC_ACTION_PARAM = "kc_action";

  static final String KC_ACTION_UPDATE_PASSWORD = "UPDATE_PASSWORD";

  /**
   * Authorization-request resolver that rides the existing {@code keycloak} OAuth client and, when
   * the standard authorization request carries the gateway-private change-password sentinel ({@code
   * ?bff_action=change_password}), appends {@code kc_action=UPDATE_PASSWORD} to the outgoing
   * authorization request. Keycloak then renders its {@code login-update-password} required-action
   * page under the already-branded login theme (Epic 572). The normal login path is left unchanged.
   *
   * <p>The sentinel name ({@code bff_action}) is deliberately distinct from the outbound KC param
   * ({@code kc_action}): {@link DefaultOAuth2AuthorizationRequestResolver} does not forward
   * arbitrary inbound query params into the authorization request's additional parameters (verified
   * against Spring Security 7.0.x — it only emits client_id/redirect_uri/scope/state/nonce/PKCE),
   * so the inbound sentinel never leaks onto the wire and the only {@code kc_action} present is the
   * single value this resolver injects (review Finding 1).
   *
   * <p>Package-private for unit testing.
   */
  static OAuth2AuthorizationRequestResolver changePasswordAuthorizationRequestResolver(
      ClientRegistrationRepository clientRegistrationRepository) {
    DefaultOAuth2AuthorizationRequestResolver delegate =
        new DefaultOAuth2AuthorizationRequestResolver(
            clientRegistrationRepository,
            OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
    return new OAuth2AuthorizationRequestResolver() {
      @Override
      public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return withChangePassword(delegate.resolve(request), request);
      }

      @Override
      public OAuth2AuthorizationRequest resolve(
          HttpServletRequest request, String clientRegistrationId) {
        return withChangePassword(delegate.resolve(request, clientRegistrationId), request);
      }
    };
  }

  /**
   * If the inbound request carries the change-password sentinel and the delegate produced an
   * authorization request, returns a copy with {@code kc_action=UPDATE_PASSWORD} added to the
   * authorization-endpoint parameters; otherwise returns the request unchanged.
   *
   * <p>Package-private for unit testing — exercised without a live Keycloak.
   */
  static OAuth2AuthorizationRequest withChangePassword(
      OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request) {
    if (authorizationRequest == null || !isChangePasswordRequest(request)) {
      return authorizationRequest;
    }
    return OAuth2AuthorizationRequest.from(authorizationRequest)
        .additionalParameters(params -> params.put(KC_ACTION_PARAM, KC_ACTION_UPDATE_PASSWORD))
        .build();
  }

  /** True when the request opts into the change-password flow via the sentinel query param. */
  static boolean isChangePasswordRequest(HttpServletRequest request) {
    return CHANGE_PASSWORD_SENTINEL_VALUE.equalsIgnoreCase(
        request.getParameter(CHANGE_PASSWORD_SENTINEL_PARAM));
  }

  /** Path segment of the branded post-logout confirmation route (Epic 570A). */
  static final String POST_LOGOUT_PATH = "/signed-out";

  /**
   * Resolves the post-logout redirect target: the branded first-party {@code /signed-out}
   * confirmation route under the frontend origin. Package-private for unit testing.
   */
  static String postLogoutRedirectUri(String frontendUrl) {
    String normalized =
        frontendUrl.endsWith("/")
            ? frontendUrl.substring(0, frontendUrl.length() - 1)
            : frontendUrl;
    return normalized + POST_LOGOUT_PATH;
  }

  private LogoutSuccessHandler oidcLogoutSuccessHandler() {
    OidcClientInitiatedLogoutSuccessHandler handler =
        new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
    // Epic 570B.1: land RP-initiated logout on the branded first-party
    // `/signed-out` confirmation page instead of the unstyled frontend root,
    // killing the post-logout whitelabel leak.
    handler.setPostLogoutRedirectUri(postLogoutRedirectUri(frontendUrl));
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
