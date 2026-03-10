package io.b2mash.b2b.b2bstrawman.orgrole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.multitenancy.ScopedFilterChain;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Integration tests for the capability authorization infrastructure: {@link RequiresCapability}
 * annotation, {@link CapabilityAuthorizationManager}, and {@link CapabilityAuthorizationService}.
 *
 * <p>Uses a test-scoped filter to bind capabilities via a custom request header, avoiding the need
 * for full tenant/member provisioning while testing the authorization layer end-to-end.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, CapabilityAuthorizationTest.TestConfig.class})
@ActiveProfiles("test")
class CapabilityAuthorizationTest {

  private static final String INVOICING_ENDPOINT = "/api/capability-test/invoicing";
  private static final String OPEN_ENDPOINT = "/api/capability-test/open";
  private static final String PREAUTHORIZE_ENDPOINT = "/api/capability-test/preauthorize-and-cap";

  /**
   * Custom header to specify capabilities in tests. The test filter reads this header and binds the
   * capabilities to {@link RequestScopes#CAPABILITIES}.
   */
  private static final String CAPABILITIES_HEADER = "X-Test-Capabilities";

  @Autowired private MockMvc mockMvc;
  @Autowired private CapabilityAuthorizationService capabilityAuthorizationService;

  @TestConfiguration
  static class TestConfig {

    /**
     * Test filter that reads capabilities from the {@code X-Test-Capabilities} header and binds
     * them to {@link RequestScopes#CAPABILITIES}. This runs within the filter chain after
     * authentication, allowing us to test the authorization manager without provisioning a full
     * tenant + member + role setup.
     */
    @Component
    @Order(1)
    static class TestCapabilityFilter extends OncePerRequestFilter {

      @Override
      protected void doFilterInternal(
          HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
          throws ServletException, IOException {
        String capsHeader = request.getHeader(CAPABILITIES_HEADER);
        if (capsHeader != null && !capsHeader.isBlank()) {
          Set<String> capabilities = Set.of(capsHeader.split(","));
          var carrier = ScopedValue.where(RequestScopes.CAPABILITIES, capabilities);
          ScopedFilterChain.runScoped(carrier, filterChain, request, response);
        } else {
          // Bind empty capabilities explicitly so tests have a known state
          var carrier = ScopedValue.where(RequestScopes.CAPABILITIES, Collections.emptySet());
          ScopedFilterChain.runScoped(carrier, filterChain, request, response);
        }
      }

      @Override
      protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/capability-test/");
      }
    }

    @RestController
    @RequestMapping("/api/capability-test")
    static class CapabilityTestController {

      @RequiresCapability("INVOICING")
      @GetMapping("/invoicing")
      public ResponseEntity<String> invoicingOnly() {
        return ResponseEntity.ok("invoicing-access-granted");
      }

      @GetMapping("/open")
      public ResponseEntity<String> openEndpoint() {
        return ResponseEntity.ok("open-access-granted");
      }

      @PreAuthorize("isAuthenticated()")
      @RequiresCapability("TEAM_OVERSIGHT")
      @GetMapping("/preauthorize-and-cap")
      public ResponseEntity<String> preAuthorizeAndCapability() {
        return ResponseEntity.ok("preauthorize-and-cap-granted");
      }
    }
  }

  // --- @RequiresCapability annotation tests ---

  @Test
  void requiresCapability_withMatchingCapability_returns200() throws Exception {
    mockMvc
        .perform(
            get(INVOICING_ENDPOINT)
                .header(CAPABILITIES_HEADER, "INVOICING")
                .with(jwt().jwt(j -> j.subject("user_invoicer"))))
        .andExpect(status().isOk())
        .andExpect(content().string("invoicing-access-granted"));
  }

  @Test
  void requiresCapability_withoutMatchingCapability_returns403() throws Exception {
    mockMvc
        .perform(
            get(INVOICING_ENDPOINT)
                .header(CAPABILITIES_HEADER, "PROJECT_MANAGEMENT")
                .with(jwt().jwt(j -> j.subject("user_pm"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void requiresCapability_withEmptyCapabilities_returns403() throws Exception {
    mockMvc
        .perform(get(INVOICING_ENDPOINT).with(jwt().jwt(j -> j.subject("user_nocaps"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void requiresCapability_withAllIndividualCapabilities_returns200() throws Exception {
    String allCaps = String.join(",", Capability.ALL_NAMES);
    mockMvc
        .perform(
            get(INVOICING_ENDPOINT)
                .header(CAPABILITIES_HEADER, allCaps)
                .with(jwt().jwt(j -> j.subject("user_admin"))))
        .andExpect(status().isOk())
        .andExpect(content().string("invoicing-access-granted"));
  }

  @Test
  void requiresCapability_withMultipleCapabilities_returns200() throws Exception {
    mockMvc
        .perform(
            get(INVOICING_ENDPOINT)
                .header(CAPABILITIES_HEADER, "PROJECT_MANAGEMENT,INVOICING,TEAM_OVERSIGHT")
                .with(jwt().jwt(j -> j.subject("user_multi"))))
        .andExpect(status().isOk())
        .andExpect(content().string("invoicing-access-granted"));
  }

  @Test
  void requiresCapability_unauthenticated_returns401() throws Exception {
    mockMvc.perform(get(INVOICING_ENDPOINT)).andExpect(status().isUnauthorized());
  }

  // --- Endpoint without @RequiresCapability ---

  @Test
  void openEndpoint_withAuthentication_returns200() throws Exception {
    mockMvc
        .perform(get(OPEN_ENDPOINT).with(jwt().jwt(j -> j.subject("user_any"))))
        .andExpect(status().isOk())
        .andExpect(content().string("open-access-granted"));
  }

  // --- @PreAuthorize + @RequiresCapability coexistence ---

  @Test
  void preAuthorizeAndCapability_withBothSatisfied_returns200() throws Exception {
    mockMvc
        .perform(
            get(PREAUTHORIZE_ENDPOINT)
                .header(CAPABILITIES_HEADER, "TEAM_OVERSIGHT")
                .with(jwt().jwt(j -> j.subject("user_oversight"))))
        .andExpect(status().isOk())
        .andExpect(content().string("preauthorize-and-cap-granted"));
  }

  @Test
  void preAuthorizeAndCapability_withoutCapability_returns403() throws Exception {
    mockMvc
        .perform(
            get(PREAUTHORIZE_ENDPOINT)
                .header(CAPABILITIES_HEADER, "INVOICING")
                .with(jwt().jwt(j -> j.subject("user_wrong_cap"))))
        .andExpect(status().isForbidden());
  }

  // --- CapabilityAuthorizationService programmatic check ---

  @Test
  void capabilityAuthorizationService_isInjectable() {
    // Verify the service is available in the Spring context
    assertThat(capabilityAuthorizationService).isNotNull();
  }
}
