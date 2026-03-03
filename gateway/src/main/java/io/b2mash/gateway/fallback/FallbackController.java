package io.b2mash.gateway.fallback;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Fallback handler invoked by the circuit breaker when the backend is unavailable. Returns a
 * structured 503 response instead of letting the client hang or receive an opaque error.
 */
@RestController
public class FallbackController {

  @RequestMapping("/fallback")
  public Mono<Map<String, Object>> fallback(ServerWebExchange exchange) {
    exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

    return Mono.just(
        Map.of(
            "type", "about:blank",
            "title", "Service Unavailable",
            "status", 503,
            "detail", "The backend service is temporarily unavailable. Please try again later."));
  }
}
