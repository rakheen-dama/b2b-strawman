package io.b2mash.b2b.b2bstrawman.assistant;

import io.b2mash.b2b.b2bstrawman.assistant.provider.ChatMessage;
import io.b2mash.b2b.b2bstrawman.assistant.provider.ChatRequest;
import io.b2mash.b2b.b2bstrawman.assistant.provider.LlmChatProviderRegistry;
import io.b2mash.b2b.b2bstrawman.assistant.provider.StreamEvent;
import io.b2mash.b2b.b2bstrawman.assistant.provider.ToolResult;
import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantToolRegistry;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDisabledException;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationGuardService;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegrationRepository;
import io.b2mash.b2b.b2bstrawman.integration.secret.SecretStore;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.Tier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

/**
 * Orchestration service for the AI assistant. Performs pre-flight gating, assembles the system
 * prompt, routes LLM streaming events, manages multi-turn tool execution, and handles the write
 * tool confirmation flow.
 */
@Service
public class AssistantService {

  private static final Logger LOG = LoggerFactory.getLogger(AssistantService.class);
  private static final long CONFIRMATION_TIMEOUT_SECONDS = 120;
  private static final String DEFAULT_MODEL = "claude-sonnet-4-6";

  private final LlmChatProviderRegistry providerRegistry;
  private final AssistantToolRegistry toolRegistry;
  private final SecretStore secretStore;
  private final IntegrationGuardService integrationGuardService;
  private final OrgIntegrationRepository orgIntegrationRepository;
  private final OrganizationRepository organizationRepository;
  private final ObjectMapper objectMapper;
  private final String systemGuide;
  private final ConcurrentHashMap<String, CompletableFuture<Boolean>> pendingConfirmations;

  public AssistantService(
      LlmChatProviderRegistry providerRegistry,
      AssistantToolRegistry toolRegistry,
      SecretStore secretStore,
      IntegrationGuardService integrationGuardService,
      OrgIntegrationRepository orgIntegrationRepository,
      OrganizationRepository organizationRepository,
      ObjectMapper objectMapper,
      @Value("classpath:assistant/system-guide.md") Resource systemGuideResource) {
    this.providerRegistry = providerRegistry;
    this.toolRegistry = toolRegistry;
    this.secretStore = secretStore;
    this.integrationGuardService = integrationGuardService;
    this.orgIntegrationRepository = orgIntegrationRepository;
    this.organizationRepository = organizationRepository;
    this.objectMapper = objectMapper;
    this.pendingConfirmations = new ConcurrentHashMap<>();
    try {
      this.systemGuide = systemGuideResource.getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load assistant system guide", e);
    }
  }

  /**
   * Processes a chat request: validates pre-flight conditions, invokes the LLM provider with
   * streaming events, executes tool calls (with confirmation for write tools), and emits SSE events
   * to the client.
   */
  public void chat(ChatContext context, SseEmitter emitter) {
    // Pre-flight check 1: Organization lookup
    var org = organizationRepository.findByClerkOrgId(RequestScopes.requireOrgId()).orElse(null);
    if (org == null) {
      emitError(emitter, "Organization not found.");
      return;
    }

    // Pre-flight check 2: PRO tier required
    if (org.getTier() != Tier.PRO) {
      emitError(emitter, "AI assistant requires the PRO plan.");
      return;
    }

    // Pre-flight check 3: AI integration enabled
    try {
      integrationGuardService.requireEnabled(IntegrationDomain.AI);
    } catch (IntegrationDisabledException e) {
      emitError(emitter, "AI assistant is not enabled for this organization.");
      return;
    }

    // Pre-flight check 4: Provider configured (not noop)
    var integration = orgIntegrationRepository.findByDomain(IntegrationDomain.AI);
    var providerSlug = integration.map(i -> i.getProviderSlug()).orElse("noop");
    if ("noop".equals(providerSlug)) {
      emitError(emitter, "No AI provider configured. Ask your admin to set up the AI integration.");
      return;
    }

    // Pre-flight check 5: API key exists
    var secretKey = "ai:" + providerSlug + ":api_key";
    if (!secretStore.exists(secretKey)) {
      emitError(emitter, "No API key configured. Ask your admin to set up the AI integration.");
      return;
    }

    // All pre-flight checks passed — proceed with LLM invocation
    try {
      var apiKey = secretStore.retrieve(secretKey);
      var model = parseModel(integration.get().getConfigJson());
      var provider = providerRegistry.get(providerSlug);
      var toolDefs = toolRegistry.getToolDefinitions(RequestScopes.getCapabilities());
      var systemPrompt = assembleSystemPrompt(context.currentPage(), org.getName());

      // Build initial message list from history + new user message
      var initialMessages = new ArrayList<>(context.history());
      initialMessages.add(new ChatMessage("user", context.message(), null));

      // Multi-turn loop
      List<ChatMessage> runningMessages = new ArrayList<>(initialMessages);
      int totalInputTokens = 0;
      int totalOutputTokens = 0;
      boolean shouldContinue = true;

      while (shouldContinue) {
        var request =
            new ChatRequest(apiKey, model, systemPrompt, List.copyOf(runningMessages), toolDefs);
        List<String> assistantTextParts = new ArrayList<>();
        List<ToolResult> turnToolResults = new ArrayList<>();
        int[] turnUsage = {0, 0}; // [inputTokens, outputTokens]
        boolean[] hasError = {false};

        provider.chat(
            request,
            event -> {
              if (event instanceof StreamEvent.TextDelta td) {
                assistantTextParts.add(td.text());
                try {
                  emitter.send(
                      SseEmitter.event().name("text_delta").data(Map.of("text", td.text())));
                } catch (IOException e) {
                  LOG.debug("Client disconnected during text_delta", e);
                }
              } else if (event instanceof StreamEvent.ToolUse tu) {
                var tool = toolRegistry.getTool(tu.toolName(), RequestScopes.getCapabilities());
                if (tool.requiresConfirmation()) {
                  var result = handleWriteToolConfirmation(tu, emitter);
                  if (result != null) {
                    turnToolResults.add(result);
                  }
                } else {
                  // Read tool — execute inline
                  var result = tool.execute(tu.input(), TenantToolContext.fromRequestScopes());
                  var resultJson = serializeToolResult(result);
                  turnToolResults.add(new ToolResult(tu.toolCallId(), resultJson));
                  try {
                    emitter.send(
                        SseEmitter.event()
                            .name("tool_use")
                            .data(
                                Map.of(
                                    "toolCallId", tu.toolCallId(),
                                    "toolName", tu.toolName(),
                                    "input", tu.input(),
                                    "requiresConfirmation", false)));
                    emitter.send(
                        SseEmitter.event()
                            .name("tool_result")
                            .data(Map.of("toolCallId", tu.toolCallId(), "result", resultJson)));
                  } catch (IOException e) {
                    LOG.debug("Client disconnected during tool_use/result", e);
                  }
                }
              } else if (event instanceof StreamEvent.Usage u) {
                turnUsage[0] += u.inputTokens();
                turnUsage[1] += u.outputTokens();
              } else if (event instanceof StreamEvent.Error err) {
                emitError(emitter, err.message());
                hasError[0] = true;
              } else if (event instanceof StreamEvent.Done) {
                // Done signal from provider — handled after callback
              }
            });

        totalInputTokens += turnUsage[0];
        totalOutputTokens += turnUsage[1];

        if (hasError[0]) {
          return;
        }

        if (!turnToolResults.isEmpty()) {
          // Add assistant message with accumulated text
          var assistantText = String.join("", assistantTextParts);
          if (!assistantText.isEmpty()) {
            runningMessages.add(new ChatMessage("assistant", assistantText, null));
          }
          // Add tool results as user message
          runningMessages.add(new ChatMessage("user", null, List.copyOf(turnToolResults)));
        } else {
          shouldContinue = false;
        }
      }

      // Emit final usage and done events
      try {
        emitter.send(
            SseEmitter.event()
                .name("usage")
                .data(
                    Map.of(
                        "inputTokens", totalInputTokens,
                        "outputTokens", totalOutputTokens)));
        emitter.send(
            SseEmitter.event()
                .name("done")
                .data(
                    Map.of(
                        "totalInputTokens", totalInputTokens,
                        "totalOutputTokens", totalOutputTokens)));
        emitter.complete();
      } catch (IOException e) {
        LOG.debug("Client disconnected during done emission", e);
      }
    } catch (Exception e) {
      LOG.error("Unexpected error in assistant chat", e);
      emitError(emitter, "An unexpected error occurred. Please try again.");
    }
  }

  /**
   * Resolves a pending write-tool confirmation. Called by the controller when the user approves or
   * rejects a tool execution.
   *
   * @throws ResourceNotFoundException if no pending confirmation exists for the given toolCallId
   */
  public void confirm(String toolCallId, boolean approved) {
    var future = pendingConfirmations.get(toolCallId);
    if (future == null) {
      throw new ResourceNotFoundException("PendingConfirmation", toolCallId);
    }
    future.complete(approved);
  }

  private String assembleSystemPrompt(String currentPage, String orgName) {
    var orgRole = RequestScopes.getOrgRole();
    return systemGuide
        + "\n\n## Current Context\n"
        + "- Organization: "
        + orgName
        + "\n"
        + "- Your role: "
        + orgRole
        + "\n"
        + "- Current page: "
        + currentPage
        + "\n"
        + "\nYou are the DocTeams assistant. Always use tools to look up data rather than guessing. "
        + "For write actions, clearly describe what will be created or changed before invoking the tool. "
        + "Never claim to have performed an action that requires confirmation unless the user confirmed it.";
  }

  private ToolResult handleWriteToolConfirmation(StreamEvent.ToolUse event, SseEmitter emitter) {
    var future = new CompletableFuture<Boolean>();
    pendingConfirmations.put(event.toolCallId(), future);
    try {
      // Emit confirmation request to client
      emitter.send(
          SseEmitter.event()
              .name("tool_use")
              .data(
                  Map.of(
                      "toolCallId", event.toolCallId(),
                      "toolName", event.toolName(),
                      "input", event.input(),
                      "requiresConfirmation", true)));

      // Block until user confirms or timeout
      boolean approved = future.get(CONFIRMATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

      if (approved) {
        var tool = toolRegistry.getTool(event.toolName(), RequestScopes.getCapabilities());
        var result = tool.execute(event.input(), TenantToolContext.fromRequestScopes());
        var resultJson = serializeToolResult(result);
        emitter.send(
            SseEmitter.event()
                .name("tool_result")
                .data(Map.of("toolCallId", event.toolCallId(), "result", resultJson)));
        return new ToolResult(event.toolCallId(), resultJson);
      } else {
        var cancelMessage = "User cancelled this action";
        emitter.send(
            SseEmitter.event()
                .name("tool_result")
                .data(Map.of("toolCallId", event.toolCallId(), "result", cancelMessage)));
        return new ToolResult(event.toolCallId(), cancelMessage);
      }
    } catch (TimeoutException e) {
      emitError(emitter, "Confirmation timed out. The action was not performed.");
      return null;
    } catch (IOException e) {
      LOG.debug("Client disconnected during write tool confirmation", e);
      return null;
    } catch (Exception e) {
      LOG.error("Error during write tool confirmation", e);
      emitError(emitter, "An error occurred during tool confirmation.");
      return null;
    } finally {
      pendingConfirmations.remove(event.toolCallId());
    }
  }

  private void emitError(SseEmitter emitter, String message) {
    try {
      emitter.send(SseEmitter.event().name("error").data(Map.of("message", message)));
      emitter.complete();
    } catch (IOException e) {
      LOG.debug("Client disconnected during error emission", e);
      emitter.completeWithError(e);
    }
  }

  private String serializeToolResult(Object result) {
    try {
      return objectMapper.writeValueAsString(result);
    } catch (tools.jackson.core.JacksonException e) {
      LOG.warn("Failed to serialize tool result", e);
      return "{\"error\": \"Failed to serialize tool result\"}";
    }
  }

  static String parseModel(String configJson) {
    if (configJson == null || configJson.isBlank()) return DEFAULT_MODEL;
    try {
      var idx = configJson.indexOf("\"model\"");
      if (idx < 0) return DEFAULT_MODEL;
      var afterColon = configJson.indexOf("\"", idx + 8);
      var end = configJson.indexOf("\"", afterColon + 1);
      return configJson.substring(afterColon + 1, end);
    } catch (Exception e) {
      return DEFAULT_MODEL;
    }
  }
}
