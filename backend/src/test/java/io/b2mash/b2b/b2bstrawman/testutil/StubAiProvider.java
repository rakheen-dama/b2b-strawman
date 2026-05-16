package io.b2mash.b2b.b2bstrawman.testutil;

import io.b2mash.b2b.b2bstrawman.integration.ConnectionTestResult;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationAdapter;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiCompletionRequest;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiCompletionResponse;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiProvider;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiTextRequest;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiTextResult;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiVisionRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test stub for AiProvider that returns canned responses from test resources. Reads from:
 * test/resources/ai/stubs/{skill-id}/response.json
 *
 * <p>Registered via TestAiConfiguration as an IntegrationAdapter with slug "noop", replacing the
 * production NoOpAiProvider (which is excluded via @Profile("!test")). The IntegrationRegistry
 * resolves this as the default AI adapter in tests, providing canned responses for AI skill
 * execution tests.
 */
@IntegrationAdapter(domain = IntegrationDomain.AI, slug = "noop")
public class StubAiProvider implements AiProvider {

  private static final Logger log = LoggerFactory.getLogger(StubAiProvider.class);

  // Realistic token counts for cost calculation testing
  private static final int DEFAULT_INPUT_TOKENS = 2000;
  private static final int DEFAULT_OUTPUT_TOKENS = 800;
  private static final int DEFAULT_CACHE_READ_TOKENS = 1500;
  private static final int DEFAULT_CACHE_CREATION_TOKENS = 0;

  @Override
  public String providerId() {
    return "stub";
  }

  @Override
  public AiTextResult generateText(AiTextRequest request) {
    return new AiTextResult(true, "{\"stub\": true}", null, 100);
  }

  @Override
  public AiTextResult summarize(String content, int maxLength) {
    return new AiTextResult(true, "Stub summary", null, 50);
  }

  @Override
  public List<String> suggestCategories(String content, List<String> existingCategories) {
    return List.of("stub-category");
  }

  @Override
  public ConnectionTestResult testConnection() {
    return new ConnectionTestResult(true, "stub", null);
  }

  @Override
  public AiCompletionResponse complete(AiCompletionRequest request) {
    String skillId = request.metadata().getOrDefault("skill-id", "default");
    String content = loadCannedResponse(skillId);
    return buildResponse(content);
  }

  @Override
  public AiCompletionResponse completeWithVision(AiVisionRequest request) {
    String skillId = request.metadata().getOrDefault("skill-id", "default");
    String content = loadCannedResponse(skillId);
    return buildResponse(content);
  }

  private AiCompletionResponse buildResponse(String content) {
    return new AiCompletionResponse(
        content,
        "claude-sonnet-4-6",
        DEFAULT_INPUT_TOKENS,
        DEFAULT_OUTPUT_TOKENS,
        DEFAULT_CACHE_READ_TOKENS,
        DEFAULT_CACHE_CREATION_TOKENS,
        "end_turn",
        1500L);
  }

  private String loadCannedResponse(String skillId) {
    String resourcePath = "ai/stubs/" + skillId + "/response.json";
    try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
      if (is == null) {
        log.warn("No canned response found at {}, returning default stub", resourcePath);
        return "{\"stub\": true, \"skill_id\": \"" + skillId + "\"}";
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      log.warn("Failed to read canned response from {}: {}", resourcePath, e.getMessage());
      return "{\"stub\": true, \"error\": \"failed to load\"}";
    }
  }
}
