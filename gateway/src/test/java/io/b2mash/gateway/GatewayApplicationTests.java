package io.b2mash.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestPropertySource(
    properties = {
      "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://example.com/.well-known/jwks.json",
      "spring.cloud.gateway.server.webflux.routes[0].id=api",
      "spring.cloud.gateway.server.webflux.routes[0].uri=http://localhost:8080",
      "spring.cloud.gateway.server.webflux.routes[0].predicates[0]=Path=/api/**",
      "spring.cloud.gateway.server.webflux.routes[1].id=portal",
      "spring.cloud.gateway.server.webflux.routes[1].uri=http://localhost:8080",
      "spring.cloud.gateway.server.webflux.routes[1].predicates[0]=Path=/portal/**",
      "gateway.rate-limit.enabled=false"
    })
class GatewayApplicationTests {

  @Autowired private WebTestClient webTestClient;

  @Test
  void contextLoads() {}

  @Test
  void actuatorHealthIsAccessible() {
    webTestClient.get().uri("/actuator/health").exchange().expectStatus().isOk();
  }

  @Test
  void apiEndpointRequiresAuthentication() {
    // /api/** requires valid JWT — unauthenticated request gets 401
    webTestClient.get().uri("/api/projects").exchange().expectStatus().isUnauthorized();
  }

  @Test
  void internalEndpointIsDenied() {
    // /internal/** has no gateway route AND is caught by denyAll() security rule.
    // The key point: it's NOT accessible through the gateway.
    webTestClient
        .get()
        .uri("/internal/orgs/provision")
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void webhookEndpointDoesNotRequireAuth() {
    // /api/webhooks/** is permitted without JWT (permitAll in security config).
    // The important thing is NOT 401 — any other status proves the path is accessible.
    // With routes configured, the circuit breaker may return 503 (no backend) or the
    // backend may return 400/404 depending on availability.
    var status =
        webTestClient
            .post()
            .uri("/api/webhooks/email/inbound")
            .exchange()
            .returnResult(Void.class)
            .getStatus();
    // Any status other than 401 proves the security config permits webhook paths
    assert status.value() != 401 : "Webhook endpoint should not require auth, got 401";
  }

  @Test
  void portalAuthEndpointDoesNotRequireAuth() {
    // /portal/auth/** is permitted without JWT. With routes, the backend may return
    // various status codes depending on availability.
    var status =
        webTestClient
            .post()
            .uri("/portal/auth/login")
            .exchange()
            .returnResult(Void.class)
            .getStatus();
    assert status.value() != 401 : "Portal auth endpoint should not require auth, got 401";
  }
}
