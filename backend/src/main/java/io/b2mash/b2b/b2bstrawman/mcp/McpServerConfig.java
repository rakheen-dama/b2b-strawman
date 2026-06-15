package io.b2mash.b2b.b2bstrawman.mcp;

import org.springframework.context.annotation.Configuration;

/**
 * Configuration home for the Kazi MCP (Model Context Protocol) server layer.
 *
 * <p>Phase 78, Epic 562 stands up an in-process MCP server at {@code /mcp} (Spring AI 2.0
 * streamable-http WebMVC starter). Server identity and capability advertisement are driven
 * declaratively by {@code spring.ai.mcp.server.*} properties (see {@code application.yml}):
 *
 * <ul>
 *   <li>{@code protocol=STREAMABLE}, {@code type=SYNC} — blocking servlet transport (NOT WebFlux),
 *       so the request traverses the standard authenticated Spring Security filter chain and binds
 *       {@code RequestScopes} (TENANT_ID → MEMBER_ID → ORG_ROLE → CAPABILITIES) before any tool
 *       runs.
 *   <li>{@code capabilities.tool=true}, {@code capabilities.resource=true} — the only two
 *       capabilities this server advertises.
 *   <li>{@code capabilities.prompt=false}, {@code capabilities.completion=false} — Kazi's MCP
 *       surface is strictly READ-ONLY: no prompts, no sampling/completion. This posture is
 *       deliberate (ADR-303).
 * </ul>
 *
 * <p>This class is the package anchor and read-only documentation point for the {@code mcp} layer.
 * It intentionally declares no beans in slice 562A: the Spring AI autoconfiguration already builds
 * the MCP server from the properties above, and hand-building a {@code McpServer}/capabilities bean
 * here would fight that autoconfiguration. Capability/handshake/registry beans land in 562B/562C.
 */
@Configuration
public class McpServerConfig {}
