package io.b2mash.b2b.b2bstrawman.billing;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.Organization;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Component
public class SubscriptionGuardFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(SubscriptionGuardFilter.class);

  private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
  private static final Set<Subscription.SubscriptionStatus> READ_ONLY_STATES =
      Set.of(
          Subscription.SubscriptionStatus.GRACE_PERIOD,
          Subscription.SubscriptionStatus.SUSPENDED,
          Subscription.SubscriptionStatus.EXPIRED);

  private final OrganizationRepository organizationRepository;
  private final SubscriptionStatusCache statusCache;
  private final ObjectMapper objectMapper;

  public SubscriptionGuardFilter(
      OrganizationRepository organizationRepository,
      SubscriptionStatusCache statusCache,
      ObjectMapper objectMapper) {
    this.organizationRepository = organizationRepository;
    this.statusCache = statusCache;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    // Skip guard if no org context is bound (unauthenticated or no org claim)
    if (!RequestScopes.ORG_ID.isBound()) {
      filterChain.doFilter(request, response);
      return;
    }

    String orgId = RequestScopes.ORG_ID.get();

    // Resolve internal org UUID from external Clerk org ID
    Optional<Organization> orgOpt = organizationRepository.findByExternalOrgId(orgId);
    if (orgOpt.isEmpty()) {
      // Unprovisioned org — skip guard (race condition during provisioning)
      filterChain.doFilter(request, response);
      return;
    }

    Subscription.SubscriptionStatus status = statusCache.getStatus(orgOpt.get().getId());
    String path = request.getRequestURI();
    String method = request.getMethod();

    // Billing paths always pass through regardless of subscription state
    if (path.startsWith("/api/billing/")) {
      filterChain.doFilter(request, response);
      return;
    }

    // LOCKED state: block all requests (GET and writes) except billing paths
    if (status == Subscription.SubscriptionStatus.LOCKED) {
      log.debug("Blocking {} {} — subscription LOCKED for org {}", method, path, orgId);
      writeLockedResponse(request, response);
      return;
    }

    // Read-only states: block mutating methods only
    if (READ_ONLY_STATES.contains(status) && MUTATING_METHODS.contains(method)) {
      log.debug("Blocking {} {} — subscription {} for org {}", method, path, status.name(), orgId);
      writeSubscriptionRequiredResponse(request, response);
      return;
    }

    // All other states (TRIALING, ACTIVE, PENDING_CANCELLATION, PAST_DUE): pass through
    filterChain.doFilter(request, response);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.startsWith("/internal/")
        || path.startsWith("/actuator/")
        || path.startsWith("/portal/");
  }

  private void writeSubscriptionRequiredResponse(
      HttpServletRequest request, HttpServletResponse response) throws IOException {
    var problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
    problem.setType(URI.create("subscription_required"));
    problem.setTitle("Subscription required");
    problem.setDetail("Your subscription has expired. Subscribe to regain full access.");
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("resubscribeUrl", "/settings/billing");
    writeProblemDetail(response, problem);
  }

  private void writeLockedResponse(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    var problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
    problem.setType(URI.create("subscription_locked"));
    problem.setTitle("Account locked");
    problem.setDetail("Your account has been locked. Resubscribe to access your data.");
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("resubscribeUrl", "/settings/billing");
    writeProblemDetail(response, problem);
  }

  private void writeProblemDetail(HttpServletResponse response, ProblemDetail problem)
      throws IOException {
    response.setStatus(problem.getStatus());
    response.setContentType("application/problem+json");
    response.getOutputStream().write(objectMapper.writeValueAsBytes(problem));
  }
}
