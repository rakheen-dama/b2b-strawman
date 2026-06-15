package io.b2mash.b2b.b2bstrawman.mcp;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RFC 9728 OAuth 2.0 Protected Resource Metadata for the Kazi MCP server (ADR-303).
 *
 * <p>PUBLIC (permitAll in {@code SecurityConfig}) — the Claude client fetches this BEFORE it has a
 * token, to discover the Keycloak authorization server and run the authorization-code/PKCE flow.
 *
 * <p>This is a degenerate config-reflection controller: it assembles two config-injected strings
 * into a record DTO and contains zero business logic. There is no service to delegate to.
 */
@RestController
public class McpProtectedResourceMetadataController {

  private final String resourceUrl;
  private final String issuerUri;

  public McpProtectedResourceMetadataController(
      @Value("${kazi.mcp.resource-url:http://localhost:8080/mcp}") String resourceUrl,
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri) {
    this.resourceUrl = resourceUrl;
    this.issuerUri = issuerUri;
  }

  @GetMapping("/.well-known/oauth-protected-resource")
  public ResponseEntity<ProtectedResourceMetadata> metadata() {
    return ResponseEntity.ok(new ProtectedResourceMetadata(resourceUrl, List.of(issuerUri)));
  }

  /** RFC 9728 fields: {@code resource} (this MCP server's URL), {@code authorization_servers}. */
  public record ProtectedResourceMetadata(String resource, List<String> authorization_servers) {}
}
