package io.b2mash.gateway.filter;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global filter that logs every request passing through the gateway. Captures method, path, status
 * code, latency, and client IP. Runs at the lowest order to ensure all requests are logged —
 * including those rejected by security or rate limiting.
 */
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

  private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    long startTime = System.nanoTime();
    ServerHttpRequest request = exchange.getRequest();

    String requestId = UUID.randomUUID().toString();
    String method = request.getMethod().name();
    String path = request.getURI().getPath();
    String clientIp = resolveClientIp(request);

    // Propagate request ID to downstream services
    ServerWebExchange mutatedExchange =
        exchange
            .mutate()
            .request(request.mutate().header("X-Request-Id", requestId).build())
            .build();

    // NOTE: MDC is ThreadLocal-based, which is unreliable in reactive (WebFlux) context
    // since execution may hop between threads. The pattern below — set MDC, log, clear
    // immediately — minimizes risk. The log message uses inline parameters (not MDC) for
    // correctness. MDC fields are supplementary structured data in ECS output.
    // TODO: Migrate to Micrometer context-propagation for proper reactive MDC support.
    return chain
        .filter(mutatedExchange)
        .then(
            Mono.fromRunnable(
                () -> {
                  ServerHttpResponse response = mutatedExchange.getResponse();
                  long durationMs = (System.nanoTime() - startTime) / 1_000_000;
                  int status =
                      response.getStatusCode() != null
                          ? response.getStatusCode().value()
                          : 0;

                  try {
                    MDC.put("requestId", requestId);
                    MDC.put("clientIp", clientIp);
                    MDC.put("httpMethod", method);
                    MDC.put("httpPath", path);
                    MDC.put("httpStatus", String.valueOf(status));
                    MDC.put("durationMs", String.valueOf(durationMs));

                    if (status >= 500) {
                      log.error(
                          "gateway {} {} {} {}ms ip={}",
                          method,
                          path,
                          status,
                          durationMs,
                          clientIp);
                    } else if (status >= 400) {
                      log.warn(
                          "gateway {} {} {} {}ms ip={}",
                          method,
                          path,
                          status,
                          durationMs,
                          clientIp);
                    } else {
                      log.info(
                          "gateway {} {} {} {}ms ip={}",
                          method,
                          path,
                          status,
                          durationMs,
                          clientIp);
                    }
                  } finally {
                    MDC.clear();
                  }
                }));
  }

  @Override
  public int getOrder() {
    // Run first — before security, rate limiting, and routing
    return Ordered.HIGHEST_PRECEDENCE;
  }

  private String resolveClientIp(ServerHttpRequest request) {
    // Check X-Forwarded-For for proxied requests (ALB, CloudFront, etc.)
    String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      // Take the first IP (original client)
      return forwarded.split(",")[0].trim();
    }
    var remoteAddress = request.getRemoteAddress();
    return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
  }
}
