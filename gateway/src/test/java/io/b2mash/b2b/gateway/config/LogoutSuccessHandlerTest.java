package io.b2mash.b2b.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit test for the gateway post-logout redirect target (Epic 570B.1). Verifies the OIDC logout
 * success handler lands the browser on the branded first-party {@code /signed-out} route rather
 * than the bare frontend root.
 */
class LogoutSuccessHandlerTest {

  @Test
  void postLogoutRedirectTargetsBrandedSignedOutRoute() {
    String resolved = GatewaySecurityConfig.postLogoutRedirectUri("http://localhost:3000");
    assertThat(resolved).isEqualTo("http://localhost:3000/signed-out");
  }

  @Test
  void postLogoutRedirectAppendsSignedOutToProdOrigin() {
    String resolved = GatewaySecurityConfig.postLogoutRedirectUri("https://app-dev.heykazi.com");
    assertThat(resolved).isEqualTo("https://app-dev.heykazi.com/signed-out");
  }
}
