package io.b2mash.b2b.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * Unit tests for {@link GatewaySecurityConfig#buildOauth2LoginSuccessHandler(String)}.
 *
 * <p>Covers the GAP-L-22 signalling cookie: after a successful OAuth2 login the handler must set a
 * short-lived, HttpOnly {@code KC_LAST_LOGIN_SUB} cookie containing the new user's {@code sub}, so
 * the Next.js middleware can detect stale SESSION handoff on the next request.
 */
class Oauth2LoginSuccessHandlerTest {

  private static final String TARGET_URL = "http://localhost:3000/dashboard";

  @Test
  void setsKcLastLoginSubCookieAfterOidcLogin() throws Exception {
    AuthenticationSuccessHandler handler =
        GatewaySecurityConfig.buildOauth2LoginSuccessHandler(TARGET_URL);
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    Authentication auth = oidcAuthentication("thandi-sub-uuid");

    handler.onAuthenticationSuccess(request, response, auth);

    Cookie cookie = response.getCookie(GatewaySecurityConfig.KC_LAST_LOGIN_SUB_COOKIE);
    assertThat(cookie).as("KC_LAST_LOGIN_SUB cookie must be set").isNotNull();
    assertThat(cookie.getValue()).isEqualTo("thandi-sub-uuid");
    assertThat(cookie.getPath()).isEqualTo("/");
    assertThat(cookie.isHttpOnly()).as("cookie must be HttpOnly").isTrue();
    // 2 minutes is plenty for the redirect → middleware hand-off.
    assertThat(cookie.getMaxAge()).isLessThanOrEqualTo(120);
    assertThat(cookie.getMaxAge()).isGreaterThan(0);
  }

  @Test
  void redirectsToConfiguredTargetUrl() throws Exception {
    AuthenticationSuccessHandler handler =
        GatewaySecurityConfig.buildOauth2LoginSuccessHandler(TARGET_URL);
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    Authentication auth = oidcAuthentication("thandi-sub-uuid");

    handler.onAuthenticationSuccess(request, response, auth);

    assertThat(response.getRedirectedUrl()).isEqualTo(TARGET_URL);
  }

  @Test
  void doesNotSetCookieWhenPrincipalIsNotOidcUser() throws Exception {
    AuthenticationSuccessHandler handler =
        GatewaySecurityConfig.buildOauth2LoginSuccessHandler(TARGET_URL);
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    // A non-OIDC authentication (e.g. form login) should NOT trigger the signalling cookie —
    // only real OAuth2 / OIDC flows emit it.
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            "alice", "n/a", AuthorityUtils.createAuthorityList("ROLE_USER"));

    handler.onAuthenticationSuccess(request, response, auth);

    assertThat(response.getCookie(GatewaySecurityConfig.KC_LAST_LOGIN_SUB_COOKIE)).isNull();
  }

  @Test
  void doesNotSetCookieWhenSubIsBlank() throws Exception {
    // Ill-formed OidcUser with a blank sub (defensive). Use Mockito to bypass constructor
    // validation which otherwise requires a non-blank sub.
    OidcUser principal = mock(OidcUser.class);
    when(principal.getSubject()).thenReturn("   ");
    Authentication auth = mock(Authentication.class);
    when(auth.getPrincipal()).thenReturn(principal);

    AuthenticationSuccessHandler handler =
        GatewaySecurityConfig.buildOauth2LoginSuccessHandler(TARGET_URL);
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    handler.onAuthenticationSuccess(request, response, auth);

    assertThat(response.getCookie(GatewaySecurityConfig.KC_LAST_LOGIN_SUB_COOKIE)).isNull();
    // Even with a blank sub, the redirect still fires (the handler's primary contract).
    verify(auth).getPrincipal();
    // Sanity: default target URL is honoured.
    assertThat(response.getRedirectedUrl()).isEqualTo(TARGET_URL);
  }

  @Test
  void cookieSecureFlagMirrorsRequestTlsContext() throws Exception {
    AuthenticationSuccessHandler handler =
        GatewaySecurityConfig.buildOauth2LoginSuccessHandler(TARGET_URL);
    MockHttpServletRequest httpsRequest = new MockHttpServletRequest();
    httpsRequest.setSecure(true);
    MockHttpServletResponse httpsResponse = new MockHttpServletResponse();
    Authentication auth = oidcAuthentication("thandi-sub-uuid");

    handler.onAuthenticationSuccess(httpsRequest, httpsResponse, auth);

    Cookie secureCookie = httpsResponse.getCookie(GatewaySecurityConfig.KC_LAST_LOGIN_SUB_COOKIE);
    assertThat(secureCookie).isNotNull();
    assertThat(secureCookie.getSecure()).isTrue();

    // And: over plaintext HTTP (local dev), Secure must be false so the browser actually
    // attaches the cookie on the follow-up redirect.
    MockHttpServletRequest httpRequest = new MockHttpServletRequest();
    httpRequest.setSecure(false);
    MockHttpServletResponse httpResponse = new MockHttpServletResponse();
    handler.onAuthenticationSuccess(httpRequest, httpResponse, auth);
    Cookie plainCookie = httpResponse.getCookie(GatewaySecurityConfig.KC_LAST_LOGIN_SUB_COOKIE);
    assertThat(plainCookie).isNotNull();
    assertThat(plainCookie.getSecure()).isFalse();
  }

  private static Authentication oidcAuthentication(String sub) {
    OidcIdToken idToken =
        new OidcIdToken(
            "token",
            java.time.Instant.now(),
            java.time.Instant.now().plusSeconds(60),
            Map.of(IdTokenClaimNames.SUB, sub, IdTokenClaimNames.ISS, "https://example.com"));
    OidcUser user =
        new DefaultOidcUser(AuthorityUtils.createAuthorityList("ROLE_USER"), idToken, "sub");
    Authentication auth = mock(Authentication.class);
    when(auth.getPrincipal()).thenReturn(user);
    return auth;
  }
}
