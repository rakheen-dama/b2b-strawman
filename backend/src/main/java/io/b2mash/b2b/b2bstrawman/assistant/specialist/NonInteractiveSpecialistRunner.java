package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import io.b2mash.b2b.b2bstrawman.assistant.invocation.AiLlmCall;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.AiLlmCallRepository;
import io.b2mash.b2b.b2bstrawman.assistant.invocation.payload.OutputPayload;
import io.b2mash.b2b.b2bstrawman.assistant.provider.ChatMessage;
import io.b2mash.b2b.b2bstrawman.assistant.provider.ChatRequest;
import io.b2mash.b2b.b2bstrawman.assistant.provider.LlmChatProviderRegistry;
import io.b2mash.b2b.b2bstrawman.assistant.provider.StreamEvent;
import io.b2mash.b2b.b2bstrawman.assistant.provider.ToolDefinition;
import io.b2mash.b2b.b2bstrawman.assistant.provider.ToolResult;
import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantToolRegistry;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationGuardService;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationKeys;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegration;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegrationRepository;
import io.b2mash.b2b.b2bstrawman.integration.secret.SecretStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs a specialist non-interactively (no SSE streaming, no write-tool confirmation). Used by
 * automation executors to invoke specialists without human interaction. Binds a synthetic
 * ActorContext with "owner" role per ADR-T002.
 *
 * <p>Mirrors the interactive chat loop in {@link
 * io.b2mash.b2b.b2bstrawman.assistant.AssistantService} but collects output synchronously instead
 * of emitting SSE events.
 */
@Component
public class NonInteractiveSpecialistRunner {

  private static final Logger log = LoggerFactory.getLogger(NonInteractiveSpecialistRunner.class);
  private static final String DEFAULT_MODEL = "claude-sonnet-4-6";

  private final SpecialistRegistry specialistRegistry;
  private final SystemPromptBuilder systemPromptBuilder;
  private final AssistantToolRegistry toolRegistry;
  private final LlmChatProviderRegistry providerRegistry;
  private final OrgIntegrationRepository orgIntegrationRepository;
  private final SecretStore secretStore;
  private final IntegrationGuardService integrationGuardService;
  private final AiLlmCallRepository llmCallRepository;
  private final ObjectMapper objectMapper;

  public NonInteractiveSpecialistRunner(
      SpecialistRegistry specialistRegistry,
      SystemPromptBuilder systemPromptBuilder,
      AssistantToolRegistry toolRegistry,
      LlmChatProviderRegistry providerRegistry,
      OrgIntegrationRepository orgIntegrationRepository,
      SecretStore secretStore,
      IntegrationGuardService integrationGuardService,
      AiLlmCallRepository llmCallRepository,
      ObjectMapper objectMapper) {
    this.specialistRegistry = specialistRegistry;
    this.systemPromptBuilder = systemPromptBuilder;
    this.toolRegistry = toolRegistry;
    this.providerRegistry = providerRegistry;
    this.orgIntegrationRepository = orgIntegrationRepository;
    this.secretStore = secretStore;
    this.integrationGuardService = integrationGuardService;
    this.llmCallRepository = llmCallRepository;
    this.objectMapper = objectMapper;
  }

  /**
   * Runs the specialist synchronously, returning the proposed output payload.
   *
   * @param specialistId the specialist to invoke
   * @param contextEntityType the context entity type (e.g. "project")
   * @param contextEntityId the context entity UUID
   * @param actorId the synthetic actor for this automation run
   * @param context the automation context for variable resolution
   * @return the OutputPayload proposed by the specialist's tool calls
   * @throws IllegalStateException if the specialist produces no actionable output
   */
  public OutputPayload run(
      String specialistId,
      String contextEntityType,
      UUID contextEntityId,
      UUID actorId,
      Map<String, Map<String, Object>> context) {

    integrationGuardService.requireEnabled(IntegrationDomain.AI);

    var specialist = specialistRegistry.requireById(specialistId);

    // Resolve LLM provider + API key
    var integration = orgIntegrationRepository.findByDomain(IntegrationDomain.AI);
    var providerSlug = integration.map(OrgIntegration::getProviderSlug).orElse("noop");
    if ("noop".equals(providerSlug)) {
      throw new IllegalStateException("No AI provider configured for this organization");
    }
    var secretKey = IntegrationKeys.aiApiKey(providerSlug);
    String apiKey = secretStore.retrieve(secretKey);
    String model = parseModel(integration.get().getConfigJson());
    var provider = providerRegistry.get(providerSlug);

    // Build tool definitions — use all capabilities for automation
    Set<String> capabilities = Set.of("AI_ASSISTANT_USE", "TEAM_OVERSIGHT");
    var filteredTools = toolRegistry.filterBy(specialist.toolIds(), capabilities);
    List<ToolDefinition> toolDefs =
        filteredTools.stream()
            .map(t -> new ToolDefinition(t.name(), t.description(), t.inputSchema()))
            .toList();

    // Build system prompt
    var ref =
        new ContextRef(
            contextEntityType, contextEntityId != null ? contextEntityId.toString() : null, null);
    String systemPrompt = systemPromptBuilder.buildFor(specialist, ref);
    String promptVersion = systemPromptBuilder.promptVersion(specialistId);

    // Build initial user message
    String initialPrompt =
        "You are running in automation mode. Analyze the current context and produce your output"
            + " using the appropriate propose tool.";
    var messages = new ArrayList<ChatMessage>();
    messages.add(new ChatMessage("user", initialPrompt, null));

    // Multi-turn tool loop bounded by specialist.maxToolIterations
    int maxIterations = specialist.maxToolIterations();
    AtomicReference<OutputPayload> proposedOutput = new AtomicReference<>();
    UUID invocationId = UUID.randomUUID(); // Used for LLM call tracking

    for (int turn = 0; turn < maxIterations; turn++) {
      var request = new ChatRequest(apiKey, model, systemPrompt, List.copyOf(messages), toolDefs);

      List<String> textParts = new ArrayList<>();
      List<ToolResult> toolResults = new ArrayList<>();
      int[] usage = {0, 0};
      boolean[] hasError = {false};
      String[] errorMessage = {null};
      long startTime = System.currentTimeMillis();

      provider.chat(
          request,
          event -> {
            if (event instanceof StreamEvent.TextDelta td) {
              textParts.add(td.text());
            } else if (event instanceof StreamEvent.ToolUse tu) {
              var result = executeToolNonInteractive(tu, capabilities, actorId);
              toolResults.add(result);
              // Check if the tool produced a propose-type output
              tryExtractOutput(tu, result, proposedOutput);
            } else if (event instanceof StreamEvent.Usage u) {
              usage[0] += u.inputTokens();
              usage[1] += u.outputTokens();
            } else if (event instanceof StreamEvent.Error err) {
              hasError[0] = true;
              errorMessage[0] = err.message();
            }
          });

      long latencyMs = System.currentTimeMillis() - startTime;

      // Record LLM call telemetry
      recordLlmCall(invocationId, model, promptVersion, usage[0], usage[1], latencyMs);

      if (hasError[0]) {
        throw new IllegalStateException("LLM error during specialist run: " + errorMessage[0]);
      }

      if (toolResults.isEmpty()) {
        // No tool calls — LLM produced text only; end loop
        break;
      }

      // Append assistant text + tool results for next turn
      String assistantText = String.join("", textParts);
      if (!assistantText.isEmpty()) {
        messages.add(new ChatMessage("assistant", assistantText, null));
      }
      messages.add(new ChatMessage("user", null, List.copyOf(toolResults)));

      // If we got a proposed output, we're done
      if (proposedOutput.get() != null) {
        break;
      }
    }

    OutputPayload output = proposedOutput.get();
    if (output == null) {
      throw new IllegalStateException(
          "Specialist "
              + specialistId
              + " produced no actionable output after "
              + maxIterations
              + " iterations");
    }
    return output;
  }

  /** Returns the prompt version for a specialist. Used by the executor to record it. */
  public String promptVersion(String specialistId) {
    return systemPromptBuilder.promptVersion(specialistId);
  }

  private ToolResult executeToolNonInteractive(
      StreamEvent.ToolUse tu, Set<String> capabilities, UUID actorId) {
    String resultJson;
    try {
      var tool = toolRegistry.getTool(tu.toolName(), capabilities);
      // Build a synthetic TenantToolContext from current RequestScopes
      var toolContext = TenantToolContext.fromRequestScopes();
      var result = tool.execute(tu.input(), toolContext);
      resultJson = objectMapper.writeValueAsString(result);
    } catch (Exception e) {
      log.warn(
          "Non-interactive tool execution failed for tool={}: {}", tu.toolName(), e.getMessage());
      resultJson = "{\"error\": \"Tool execution failed: " + sanitize(e.getMessage()) + "\"}";
    }
    return new ToolResult(tu.toolCallId(), resultJson);
  }

  private void tryExtractOutput(
      StreamEvent.ToolUse tu, ToolResult result, AtomicReference<OutputPayload> proposedOutput) {
    // Propose tools follow naming convention: propose_* (e.g. propose_billing_polish)
    if (tu.toolName() != null && tu.toolName().startsWith("propose_")) {
      try {
        // The tool result JSON may contain the proposed output
        var node = objectMapper.readTree(result.content());
        if (node != null && node.has("payload")) {
          var payload = objectMapper.treeToValue(node.get("payload"), OutputPayload.class);
          proposedOutput.compareAndSet(null, payload);
        }
      } catch (Exception e) {
        log.debug("Could not extract output payload from propose tool result: {}", e.getMessage());
      }
    }
    // Also check post_* tools for DIRECT mode (e.g. post_inbox_summary)
    if (tu.toolName() != null && tu.toolName().startsWith("post_")) {
      try {
        var node = objectMapper.readTree(result.content());
        if (node != null && node.has("payload")) {
          var payload = objectMapper.treeToValue(node.get("payload"), OutputPayload.class);
          proposedOutput.compareAndSet(null, payload);
        }
      } catch (Exception e) {
        log.debug("Could not extract output payload from post tool result: {}", e.getMessage());
      }
    }
  }

  private void recordLlmCall(
      UUID invocationId,
      String model,
      String promptVersion,
      int inputTokens,
      int outputTokens,
      long latencyMs) {
    try {
      var call =
          new AiLlmCall(
              invocationId,
              model,
              promptVersion,
              inputTokens,
              outputTokens,
              0, // cacheReadInputTokens
              0, // cacheCreationInputTokens
              null, // requestId
              null, // stopReason
              (int) latencyMs,
              false); // wasVision
      llmCallRepository.save(call);
    } catch (Exception e) {
      log.warn("Failed to record LLM call telemetry: {}", e.getMessage());
    }
  }

  private String parseModel(String configJson) {
    if (configJson == null || configJson.isBlank()) return DEFAULT_MODEL;
    try {
      var node = objectMapper.readTree(configJson);
      var modelNode = node.get("model");
      if (modelNode == null || modelNode.isNull()) return DEFAULT_MODEL;
      return modelNode.asText(DEFAULT_MODEL);
    } catch (Exception e) {
      return DEFAULT_MODEL;
    }
  }

  private static String sanitize(String message) {
    if (message == null) return "unknown error";
    return message.replace("\"", "'").replace("\\", "\\\\");
  }
}
