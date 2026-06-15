package io.b2mash.b2b.b2bstrawman.mcp;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Emits {@code mcp.session.opened} audit events on the MCP {@code initialize} JSON-RPC handshake
 * (ADR-303 / ADR-305 attribution).
 *
 * <p>The Spring AI 2.0-M6 MCP sync server exposes no SDK-level session-opened lifecycle hook (the
 * {@code McpServer$SyncSpecification} builder offers only {@code rootsChangeHandler}, and the
 * {@code McpSyncServerExchange} surfaces client info only at tool-call time). So this is a servlet
 * filter fallback: the STREAMABLE transport delivers the {@code initialize} request as an HTTP POST
 * to {@code /mcp}, and by the time this filter runs — it is registered AFTER {@code
 * TenantLoggingFilter} on the authenticated {@code @Order(2)} chain — {@code
 * RequestScopes.MEMBER_ID} is bound, so the audit actor auto-resolves to the real member.
 *
 * <p>The body is read via {@link ContentCachingRequestWrapper}: {@code doFilter} runs FIRST so the
 * MCP transport consumes the stream (which is what populates the cache), then the cached bytes are
 * parsed afterwards. Parse/emit failures are swallowed at WARN — audit emission must never break
 * the handshake. The handshake succeeds regardless of {@code MCP_ACCESS} (this filter performs no
 * capability check).
 *
 * <p>Extension point: 567A extends this filter for {@code mcp.tool.invoked} / {@code
 * mcp.access.denied} by inspecting other JSON-RPC methods.
 */
@Component
public class McpSessionAuditListener extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(McpSessionAuditListener.class);
  private static final String MCP_PATH = "/mcp";

  /**
   * Cache only the first 64 KiB of the request body. The JSON-RPC {@code initialize} request is
   * tiny (a few hundred bytes); a bounded cache avoids buffering large/streamed MCP payloads in
   * memory.
   */
  private static final int MAX_CACHED_BODY_BYTES = 64 * 1024;

  private final AuditService auditService;
  private final ObjectMapper objectMapper;

  public McpSessionAuditListener(AuditService auditService, ObjectMapper objectMapper) {
    this.auditService = auditService;
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !HttpMethod.POST.matches(request.getMethod()) || !isMcpPath(request);
  }

  private boolean isMcpPath(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path != null && (path.equals(MCP_PATH) || path.startsWith(MCP_PATH + "/"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    ContentCachingRequestWrapper wrapped =
        new ContentCachingRequestWrapper(request, MAX_CACHED_BODY_BYTES);
    try {
      // Let the MCP transport consume the body first — this populates the cache.
      filterChain.doFilter(wrapped, response);
    } finally {
      emitSessionOpenedIfInitialize(wrapped);
    }
  }

  private void emitSessionOpenedIfInitialize(ContentCachingRequestWrapper wrapped) {
    try {
      byte[] body = wrapped.getContentAsByteArray();
      if (body.length == 0) {
        return;
      }
      JsonNode root = objectMapper.readTree(new String(body, StandardCharsets.UTF_8));
      JsonNode method = root.get("method");
      if (method == null || !McpSchema.METHOD_INITIALIZE.equals(method.asString())) {
        return;
      }
      if (!RequestScopes.MEMBER_ID.isBound()) {
        return;
      }
      JsonNode clientInfo = root.path("params").path("clientInfo");
      String clientName = textOrUnknown(clientInfo.get("name"));
      String clientVersion = textOrUnknown(clientInfo.get("version"));

      auditService.log(
          AuditEventBuilder.builder()
              .eventType("mcp.session.opened")
              .entityType("mcp_session")
              .entityId(RequestScopes.requireMemberId())
              .details(Map.of("clientName", clientName, "clientVersion", clientVersion))
              .build());
    } catch (RuntimeException e) {
      // Audit emission must never break the handshake.
      log.warn("Failed to emit mcp.session.opened audit event for /mcp initialize", e);
    }
  }

  private static String textOrUnknown(JsonNode node) {
    return node != null && !node.isNull() ? node.asString() : "unknown";
  }
}
