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
      "spring.cloud.gateway.routes[0].id=api",
      "spring.cloud.gateway.routes[0].uri=http://localhost:8080",
      "spring.cloud.gateway.routes[0].predicates[0]=Path=/api/**",
      "spring.cloud.gateway.routes[1].id=portal",
      "spring.cloud.gateway.routes[1].uri=http://localhost:8080",
      "spring.cloud.gateway.routes[1].predicates[0]=Path=/portal/**",
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
    // Returns 404 in test (no backend running) — the important thing is NOT 401.
    var status =
        webTestClient
            .post()
            .uri("/api/webhooks/email/inbound")
            .exchange()
            .expectStatus()
            .isNotFound()
            .returnResult(Void.class)
            .getStatus();
    // 404 (not 401) proves the security config permits webhook paths
  }

  @Test
  void portalAuthEndpointDoesNotRequireAuth() {
    // /portal/auth/** is permitted without JWT. Returns 404 in test (no backend).
    webTestClient.post().uri("/portal/auth/login").exchange().expectStatus().isNotFound();
  }
}
