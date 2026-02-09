package io.b2mash.b2b.b2bstrawman.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class ApiKeyAuthFilterTest {

  private static final String EXPECTED_KEY = "test-secret-api-key";

  private ApiKeyAuthFilter filter;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;
  private boolean filterChainCalled;

  private final FilterChain filterChain =
      (req, res) -> {
        filterChainCalled = true;
      };

  @BeforeEach
  void setUp() {
    filter = new ApiKeyAuthFilter(EXPECTED_KEY);
    request = new MockHttpServletRequest();
    request.setRequestURI("/internal/orgs/provision");
    response = new MockHttpServletResponse();
    filterChainCalled = false;
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void validKey_continuesFilterChain() throws ServletException, IOException {
    request.addHeader("X-API-KEY", EXPECTED_KEY);

    filter.doFilterInternal(request, response, filterChain);

    assertThat(filterChainCalled).isTrue();
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void validKey_setsInternalServiceAuth() throws ServletException, IOException {
    request.addHeader("X-API-KEY", EXPECTED_KEY);

    filter.doFilterInternal(request, response, filterChain);

    var auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isNotNull();
    assertThat(auth.isAuthenticated()).isTrue();
    assertThat(auth.getPrincipal()).isEqualTo("internal-service");
    assertThat(auth.getAuthorities())
        .extracting("authority")
        .containsExactly(Roles.AUTHORITY_INTERNAL);
  }

  @Test
  void invalidKey_returns401() throws ServletException, IOException {
    request.addHeader("X-API-KEY", "wrong-key");

    filter.doFilterInternal(request, response, filterChain);

    assertThat(filterChainCalled).isFalse();
    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  void missingHeader_returns401() throws ServletException, IOException {
    // No X-API-KEY header set

    filter.doFilterInternal(request, response, filterChain);

    assertThat(filterChainCalled).isFalse();
    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  void emptyKey_returns401() throws ServletException, IOException {
    request.addHeader("X-API-KEY", "");

    filter.doFilterInternal(request, response, filterChain);

    assertThat(filterChainCalled).isFalse();
    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  void shouldNotFilter_nonInternalPath() {
    request.setRequestURI("/api/projects");

    assertThat(filter.shouldNotFilter(request)).isTrue();
  }

  @Test
  void shouldFilter_internalPath() {
    request.setRequestURI("/internal/orgs/provision");

    assertThat(filter.shouldNotFilter(request)).isFalse();
  }
}
