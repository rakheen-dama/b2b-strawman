package io.b2mash.gateway.ratelimit;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Rate limiting filter with two tiers:
 *
 * <ul>
 *   <li><b>Per-IP</b> — protects against brute force and unauthenticated abuse (100 req/sec)
 *   <li><b>Per-tenant</b> — protects against noisy tenants consuming disproportionate resources
 *       (200 req/sec)
 * </ul>
 *
 * <p>Runs after security (so JWT is available for tenant extraction) but before routing.
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

  private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

  private final RateLimitProperties properties;
  private final InMemoryRateLimiter ipLimiter;
  private final InMemoryRateLimiter tenantLimiter;

  public RateLimitFilter(RateLimitProperties properties) {
    this.properties = properties;
    this.ipLimiter =
        new InMemoryRateLimiter(
            properties.getIpCapacity(),
            properties.getIpRefillTokens(),
            properties.getIpRefillInterval());
    this.tenantLimiter =
        new InMemoryRateLimiter(
            properties.getTenantCapacity(),
            properties.getTenantRefillTokens(),
            properties.getTenantRefillInterval());
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (!properties.isEnabled()) {
      return chain.filter(exchange);
    }

    // Per-IP rate limit
    String clientIp = resolveClientIp(exchange);
    if (!ipLimiter.tryAcquire("ip:" + clientIp)) {
      log.warn("Rate limit exceeded for IP: {}", clientIp);
      return rejectWithTooManyRequests(exchange);
    }

    // Per-tenant rate limit (only if authenticated with JWT containing org claim)
    return exchange
        .getPrincipal()
        .flatMap(
            principal -> {
              if (principal instanceof JwtAuthenticationToken jwtAuth) {
                String tenantId = extractTenantId(jwtAuth);
                if (tenantId != null && !tenantLimiter.tryAcquire("tenant:" + tenantId)) {
                  log.warn("Rate limit exceeded for tenant: {}", tenantId);
                  return rejectWithTooManyRequests(exchange);
                }
              }
              return chain.filter(exchange);
            })
        .switchIfEmpty(chain.filter(exchange));
  }

  @Override
  public int getOrder() {
    // After security filters (which are typically at order -100 to 0)
    return 10;
  }

  @SuppressWarnings("unchecked")
  private String extractTenantId(JwtAuthenticationToken jwtAuth) {
    var claims = jwtAuth.getToken().getClaims();
    Object orgClaim = claims.get("o");
    if (orgClaim instanceof Map<?, ?> orgMap) {
      Object id = orgMap.get("id");
      return id != null ? id.toString() : null;
    }
    return null;
  }

  private String resolveClientIp(ServerWebExchange exchange) {
    String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    var remoteAddress = exchange.getRequest().getRemoteAddress();
    return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
  }

  private Mono<Void> rejectWithTooManyRequests(ServerWebExchange exchange) {
    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
    exchange.getResponse().getHeaders().add("Retry-After", "1");
    return exchange.getResponse().setComplete();
  }
}
