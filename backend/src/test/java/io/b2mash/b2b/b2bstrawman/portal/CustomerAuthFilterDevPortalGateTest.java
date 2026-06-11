package io.b2mash.b2b.b2bstrawman.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * TD-002 unit coverage for the profile gate on the {@code /portal/dev/**} skip in {@link
 * CustomerAuthFilter#shouldNotFilter}. Pure unit test (no Spring context) — drives the gate with a
 * {@link MockEnvironment} so we exercise both the dev-profile (skip allowed) and prod (skip
 * withheld) branches without paying for a second {@code @SpringBootTest} context.
 */
class CustomerAuthFilterDevPortalGateTest {

  private CustomerAuthFilter filterForProfiles(String... activeProfiles) {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles(activeProfiles);
    return new CustomerAuthFilter(
        mock(PortalJwtService.class),
        mock(OrgSchemaMappingRepository.class),
        mock(PortalContactRepository.class),
        env);
  }

  private boolean shouldNotFilter(CustomerAuthFilter filter, String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI(uri);
    // shouldNotFilter is protected; same-package test invokes it directly.
    return filter.shouldNotFilter((HttpServletRequest) request);
  }

  @Test
  void devPortalPath_isSkipped_underLocalProfile() {
    var filter = filterForProfiles("local");
    assertThat(shouldNotFilter(filter, "/portal/dev/generate-link")).isTrue();
  }

  @Test
  void devPortalPath_isSkipped_underKeycloakProfile() {
    // keycloak serves the MockPaymentController checkout page under /portal/dev/**
    var filter = filterForProfiles("keycloak");
    assertThat(shouldNotFilter(filter, "/portal/dev/mock-payment")).isTrue();
  }

  @Test
  void devPortalPath_isNotSkipped_underProdProfile() {
    // The harness does not exist in prod — the skip is withheld so the filter runs and the
    // missing Bearer token yields 401.
    var filter = filterForProfiles("prod");
    assertThat(shouldNotFilter(filter, "/portal/dev/generate-link")).isFalse();
  }

  @Test
  void authAndBrandingPaths_remainSkipped_regardlessOfProfile() {
    var filter = filterForProfiles("prod");
    assertThat(shouldNotFilter(filter, "/portal/auth/verify")).isTrue();
    assertThat(shouldNotFilter(filter, "/portal/branding")).isTrue();
  }
}
