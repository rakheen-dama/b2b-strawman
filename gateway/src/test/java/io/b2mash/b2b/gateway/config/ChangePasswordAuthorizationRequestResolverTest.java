package io.b2mash.b2b.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Unit test for the gateway change-password initiation (Epic 571A.1). Verifies that the custom
 * {@link OAuth2AuthorizationRequestResolver} appends {@code kc_action=UPDATE_PASSWORD} to the
 * outgoing authorization request ONLY when the standard authorization endpoint carries the
 * change-password sentinel ({@code ?kc_action=update_password}), and that the normal login path is
 * left unchanged. Pure unit — no live Keycloak, no Spring context (mirrors {@code
 * LogoutSuccessHandlerTest}).
 */
class ChangePasswordAuthorizationRequestResolverTest {

  private static final String AUTHORIZATION_REQUEST_BASE_URI = "/oauth2/authorization";
  private static final String REGISTRATION_ID = "keycloak";

  private static ClientRegistrationRepository clientRegistrationRepository() {
    ClientRegistration registration =
        ClientRegistration.withRegistrationId(REGISTRATION_ID)
            .clientId("test")
            .clientSecret("test")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("openid", "profile", "email")
            .authorizationUri("https://example.com/auth")
            .tokenUri("https://example.com/token")
            .jwkSetUri("https://example.com/jwks")
            .userInfoUri("https://example.com/userinfo")
            .userNameAttributeName("sub")
            .build();
    return new InMemoryClientRegistrationRepository(registration);
  }

  /** Builds the authorization-endpoint request the gateway receives for {@code keycloak}. */
  private static MockHttpServletRequest authorizationRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI(AUTHORIZATION_REQUEST_BASE_URI + "/" + REGISTRATION_ID);
    request.setServletPath(AUTHORIZATION_REQUEST_BASE_URI + "/" + REGISTRATION_ID);
    return request;
  }

  @Test
  void changePasswordSentinel_appendsKcActionUpdatePassword() {
    OAuth2AuthorizationRequestResolver resolver =
        GatewaySecurityConfig.changePasswordAuthorizationRequestResolver(
            clientRegistrationRepository());

    MockHttpServletRequest request = authorizationRequest();
    request.setParameter(
        GatewaySecurityConfig.CHANGE_PASSWORD_SENTINEL_PARAM,
        GatewaySecurityConfig.CHANGE_PASSWORD_SENTINEL_VALUE);

    OAuth2AuthorizationRequest resolved = resolver.resolve(request);

    assertThat(resolved)
        .as("Resolver should produce an authorization request for the keycloak registration")
        .isNotNull();
    assertThat(resolved.getAdditionalParameters())
        .as("change-password initiation must carry kc_action=UPDATE_PASSWORD")
        .containsEntry(
            GatewaySecurityConfig.KC_ACTION_PARAM, GatewaySecurityConfig.KC_ACTION_UPDATE_PASSWORD);
  }

  @Test
  void standardLogin_doesNotCarryKcAction() {
    OAuth2AuthorizationRequestResolver resolver =
        GatewaySecurityConfig.changePasswordAuthorizationRequestResolver(
            clientRegistrationRepository());

    OAuth2AuthorizationRequest resolved = resolver.resolve(authorizationRequest());

    assertThat(resolved)
        .as("Resolver should produce an authorization request for the standard login path")
        .isNotNull();
    assertThat(resolved.getAdditionalParameters())
        .as("the normal login path must NOT carry kc_action")
        .doesNotContainKey(GatewaySecurityConfig.KC_ACTION_PARAM);
  }

  @Test
  void resolveByRegistrationId_appendsKcActionWhenSentinelPresent() {
    OAuth2AuthorizationRequestResolver resolver =
        GatewaySecurityConfig.changePasswordAuthorizationRequestResolver(
            clientRegistrationRepository());

    MockHttpServletRequest request = authorizationRequest();
    request.setParameter(
        GatewaySecurityConfig.CHANGE_PASSWORD_SENTINEL_PARAM,
        GatewaySecurityConfig.CHANGE_PASSWORD_SENTINEL_VALUE);

    OAuth2AuthorizationRequest resolved = resolver.resolve(request, REGISTRATION_ID);

    assertThat(resolved).isNotNull();
    assertThat(resolved.getAdditionalParameters())
        .containsEntry(
            GatewaySecurityConfig.KC_ACTION_PARAM, GatewaySecurityConfig.KC_ACTION_UPDATE_PASSWORD);
  }
}
