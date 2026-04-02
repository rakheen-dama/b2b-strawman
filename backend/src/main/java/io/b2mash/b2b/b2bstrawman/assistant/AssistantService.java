package io.b2mash.b2b.b2bstrawman.assistant;

import io.b2mash.b2b.b2bstrawman.assistant.provider.ChatMessage;
import io.b2mash.b2b.b2bstrawman.assistant.provider.ChatRequest;
import io.b2mash.b2b.b2bstrawman.assistant.provider.LlmChatProviderRegistry;
import io.b2mash.b2b.b2bstrawman.assistant.provider.StreamEvent;
import io.b2mash.b2b.b2bstrawman.assistant.provider.ToolResult;
import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantToolRegistry;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.billing.SubscriptionStatusCache;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDisabledException;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationGuardService;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationKeys;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegration;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegrationRepository;
import io.b2mash.b2b.b2bstrawman.integration.secret.SecretStore;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
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
  private static final int MAX_TOOL_TURNS = 10;

  private final LlmChatProviderRegistry providerRegistry;
  private final AssistantToolRegistry toolRegistry;
  private final SecretStore secretStore;
  private final IntegrationGuardService integrationGuardService;
  private final OrgIntegrationRepository orgIntegrationRepository;
  private final OrganizationRepository organizationRepository;
  private final SubscriptionStatusCache subscriptionStatusCache;
  private final ObjectMapper objectMapper;
  private final String systemGuide;
  private final ConcurrentHashMap<String, PendingConfirmation> pendingConfirmations;

  /** Holds the future and the member who initiated it, preventing cross-tenant confirmation. */
  private record PendingConfirmation(CompletableFuture<Boolean> future, UUID memberId) {}

  public AssistantService(
      LlmChatProviderRegistry providerRegistry,
      AssistantToolRegistry toolRegistry,
      SecretStore secretStore,
      IntegrationGuardService integrationGuardService,
      OrgIntegrationRepository orgIntegrationRepository,
      OrganizationRepository organizationRepository,
      SubscriptionStatusCache subscriptionStatusCache,
      ObjectMapper objectMapper,
      @Value("classpath:assistant/system-guide.md") Resource systemGuideResource) {
    this.providerRegistry = providerRegistry;
    this.toolRegistry = toolRegistry;
    this.secretStore = secretStore;
    this.integrationGuardService = integrationGuardService;
    this.orgIntegrationRepository = orgIntegrationRepository;
    this.organizationRepository = organizationRepository;
    this.subscriptionStatusCache = subscriptionStatusCache;
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
    var emitterCompleted = new AtomicBoolean(false);

    // Pre-flight checks
    try {
      validatePreflight(emitter, emitterCompleted);
    } catch (PreflightFailedException e) {
      return;
    }

    // Pre-flight check 4: Provider configured (not noop)
    var integration = orgIntegrationRepository.findByDomain(IntegrationDomain.AI);
    var providerSlug = integration.map(OrgIntegration::getProviderSlug).orElse("noop");
    if ("noop".equals(providerSlug)) {
      emitError(
          emitter,
          emitterCompleted,
          "No AI provider configured. Ask your admin to set up the AI integration.");
      return;
    }

    // Pre-flight check 5: API key exists (retrieve directly — avoids TOCTOU race)
    var secretKey = IntegrationKeys.aiApiKey(providerSlug);
    String apiKey;
    try {
      apiKey = secretStore.retrieve(secretKey);
    } catch (ResourceNotFoundException e) {
      emitError(
          emitter,
          emitterCompleted,
          "No API key configured. Ask your admin to set up the AI integration.");
      return;
    }

    // All pre-flight checks passed — proceed with LLM invocation
    try {
      var model = parseModel(integration.get().getConfigJson());
      var provider = providerRegistry.get(providerSlug);
      var toolDefs = toolRegistry.getToolDefinitions(RequestScopes.getCapabilities());
      var systemPrompt = assembleSystemPrompt(context.currentPage(), getOrgName());

      // Build initial message list from history + new user message
      var initialMessages = new ArrayList<>(context.history());
      initialMessages.add(new ChatMessage("user", context.message(), null));

      // Multi-turn loop with max-turns safety limit
      List<ChatMessage> runningMessages = new ArrayList<>(initialMessages);
      int totalInputTokens = 0;
      int totalOutputTokens = 0;
      boolean shouldContinue = true;
      int turnCount = 0;

      while (shouldContinue) {
        if (turnCount >= MAX_TOOL_TURNS) {
          LOG.warn("Max tool turns ({}) exceeded, stopping loop", MAX_TOOL_TURNS);
          emitError(
              emitter,
              emitterCompleted,
              "The assistant exceeded the maximum number of tool execution steps. Please try a"
                  + " simpler request.");
          return;
        }
        turnCount++;

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
                  var result = handleWriteToolConfirmation(tu, emitter, emitterCompleted);
                  if (result != null) {
                    turnToolResults.add(result);
                  }
                } else {
                  // Read tool — execute inline with error handling
                  var result = executeTool(tu, emitter);
                  turnToolResults.add(result);
                }
              } else if (event instanceof StreamEvent.Usage u) {
                turnUsage[0] += u.inputTokens();
                turnUsage[1] += u.outputTokens();
              } else if (event instanceof StreamEvent.Error err) {
                emitError(emitter, emitterCompleted, err.message());
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
        completeEmitter(emitter, emitterCompleted);
      } catch (IOException e) {
        LOG.debug("Client disconnected during done emission", e);
      }
    } catch (Exception e) {
      LOG.error("Unexpected error in assistant chat", e);
      emitError(emitter, emitterCompleted, "An unexpected error occurred. Please try again.");
    }
  }

  /**
   * Resolves a pending write-tool confirmation. Called by the controller when the user approves or
   * rejects a tool execution.
   *
   * @throws ResourceNotFoundException if no pending confirmation exists for the given toolCallId
   * @throws ForbiddenException if the caller is not the member who initiated the confirmation
   */
  public void confirm(String toolCallId, boolean approved) {
    var pending = pendingConfirmations.get(toolCallId);
    if (pending == null) {
      throw new ResourceNotFoundException("PendingConfirmation", toolCallId);
    }
    var currentMemberId = RequestScopes.requireMemberId();
    if (!pending.memberId().equals(currentMemberId)) {
      throw new ForbiddenException(
          "Confirmation denied", "You are not authorized to confirm this tool execution");
    }
    pending.future().complete(approved);
  }

  /** Package-private for testing only. Plants a pending confirmation for a given tool call ID. */
  CompletableFuture<Boolean> plantConfirmation(String toolCallId, UUID memberId) {
    var future = new CompletableFuture<Boolean>();
    pendingConfirmations.put(toolCallId, new PendingConfirmation(future, memberId));
    return future;
  }

  /**
   * Validates pre-flight conditions (org exists, subscription write-enabled, AI integration
   * enabled). Emits an error to the SSE emitter and throws {@link PreflightFailedException} if any
   * check fails. The subscription check uses {@code isWriteEnabled()}, which permits TRIALING,
   * ACTIVE, PENDING_CANCELLATION, and PAST_DUE statuses.
   */
  private void validatePreflight(SseEmitter emitter, AtomicBoolean emitterCompleted) {
    // Check 1: Organization lookup
    var org = organizationRepository.findByClerkOrgId(RequestScopes.requireOrgId()).orElse(null);
    if (org == null) {
      emitError(emitter, emitterCompleted, "Organization not found.");
      throw new PreflightFailedException();
    }

    // Check 2: Subscription status allows AI usage
    var status = subscriptionStatusCache.getStatus(org.getId());
    if (!status.isWriteEnabled()) {
      emitError(
          emitter,
          emitterCompleted,
          "Your subscription is inactive. Please renew to use the AI assistant.");
      throw new PreflightFailedException();
    }

    // Check 3: AI integration enabled
    try {
      integrationGuardService.requireEnabled(IntegrationDomain.AI);
    } catch (IntegrationDisabledException e) {
      emitError(emitter, emitterCompleted, "AI assistant is not enabled for this organization.");
      throw new PreflightFailedException();
    }
  }

  /**
   * Executes a read tool and returns the result. If execution throws, returns an error message
   * instead of crashing the SSE stream.
   */
  private ToolResult executeTool(StreamEvent.ToolUse tu, SseEmitter emitter) {
    String resultJson;
    try {
      var tool = toolRegistry.getTool(tu.toolName(), RequestScopes.getCapabilities());
      var result = tool.execute(tu.input(), TenantToolContext.fromRequestScopes());
      resultJson = serializeToolResult(result);
    } catch (Exception e) {
      LOG.warn("Tool execution failed for tool={}: {}", tu.toolName(), e.getMessage(), e);
      resultJson = "{\"error\": \"Tool execution failed: " + sanitize(e.getMessage()) + "\"}";
    }
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
    return new ToolResult(tu.toolCallId(), resultJson);
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

  private ToolResult handleWriteToolConfirmation(
      StreamEvent.ToolUse event, SseEmitter emitter, AtomicBoolean emitterCompleted) {
    var future = new CompletableFuture<Boolean>();
    var memberId = RequestScopes.requireMemberId();
    pendingConfirmations.put(event.toolCallId(), new PendingConfirmation(future, memberId));
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

      // Blocks virtual thread — acceptable since virtual threads don't pin on timed waits
      // (JDK 21+ / JEP 444). Async refactor deferred to Phase 53.
      boolean approved = future.get(CONFIRMATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

      if (approved) {
        var result = executeTool(event, emitter);
        return result;
      } else {
        var cancelMessage = "User cancelled this action";
        emitter.send(
            SseEmitter.event()
                .name("tool_result")
                .data(Map.of("toolCallId", event.toolCallId(), "result", cancelMessage)));
        return new ToolResult(event.toolCallId(), cancelMessage);
      }
    } catch (TimeoutException e) {
      emitError(emitter, emitterCompleted, "Confirmation timed out. The action was not performed.");
      return null;
    } catch (IOException e) {
      LOG.debug("Client disconnected during write tool confirmation", e);
      return null;
    } catch (Exception e) {
      LOG.error("Error during write tool confirmation", e);
      emitError(emitter, emitterCompleted, "An error occurred during tool confirmation.");
      return null;
    } finally {
      pendingConfirmations.remove(event.toolCallId());
    }
  }

  private void emitError(SseEmitter emitter, AtomicBoolean emitterCompleted, String message) {
    try {
      emitter.send(SseEmitter.event().name("error").data(Map.of("message", message)));
      completeEmitter(emitter, emitterCompleted);
    } catch (IOException e) {
      LOG.debug("Client disconnected during error emission", e);
      completeEmitterWithError(emitter, emitterCompleted, e);
    }
  }

  private void completeEmitter(SseEmitter emitter, AtomicBoolean emitterCompleted) {
    if (emitterCompleted.compareAndSet(false, true)) {
      emitter.complete();
    }
  }

  private void completeEmitterWithError(
      SseEmitter emitter, AtomicBoolean emitterCompleted, Throwable error) {
    if (emitterCompleted.compareAndSet(false, true)) {
      emitter.completeWithError(error);
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

  /** Parses the model name from the integration config JSON using ObjectMapper. */
  private String parseModel(String configJson) {
    if (configJson == null || configJson.isBlank()) return DEFAULT_MODEL;
    try {
      var node = objectMapper.readTree(configJson);
      var modelNode = node.get("model");
      if (modelNode == null || modelNode.isNull()) return DEFAULT_MODEL;
      return modelNode.asText(DEFAULT_MODEL);
    } catch (Exception e) {
      LOG.debug("Failed to parse model from config JSON, using default", e);
      return DEFAULT_MODEL;
    }
  }

  private String getOrgName() {
    return organizationRepository
        .findByClerkOrgId(RequestScopes.requireOrgId())
        .map(org -> org.getName())
        .orElse("Unknown");
  }

  /** Sanitizes a message for safe inclusion in a JSON string value. */
  private static String sanitize(String message) {
    if (message == null) return "unknown error";
    return message.replace("\"", "'").replace("\\", "\\\\");
  }

  /** Thrown internally when pre-flight validation fails (error already emitted to SSE). */
  private static class PreflightFailedException extends RuntimeException {
    PreflightFailedException() {
      super(null, null, true, false); // no stacktrace for flow-control exception
    }
  }
}
