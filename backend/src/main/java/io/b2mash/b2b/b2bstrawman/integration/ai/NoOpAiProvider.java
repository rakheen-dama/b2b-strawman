package io.b2mash.b2b.b2bstrawman.integration.ai;

import io.b2mash.b2b.b2bstrawman.integration.ConnectionTestResult;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationAdapter;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@IntegrationAdapter(domain = IntegrationDomain.AI, slug = "noop")
public class NoOpAiProvider implements AiProvider {

  private static final Logger log = LoggerFactory.getLogger(NoOpAiProvider.class);

  private static final String NOT_CONFIGURED_ERROR =
      "{\"error\": \"AI not configured. Connect an Anthropic API key in Settings > AI.\"}";

  @Override
  public String providerId() {
    return "noop";
  }

  @Override
  public AiTextResult generateText(AiTextRequest request) {
    log.info(
        "NoOp AI: would generate text for prompt ({} chars, maxTokens={})",
        request.prompt().length(),
        request.maxTokens());
    return new AiTextResult(true, "", null, 0);
  }

  @Override
  public AiTextResult summarize(String content, int maxLength) {
    log.info("NoOp AI: would summarize {} chars to max {}", content.length(), maxLength);
    return new AiTextResult(true, "", null, 0);
  }

  @Override
  public List<String> suggestCategories(String content, List<String> existingCategories) {
    log.info("NoOp AI: would suggest categories from {} existing", existingCategories.size());
    return List.of();
  }

  @Override
  public ConnectionTestResult testConnection() {
    return new ConnectionTestResult(true, "noop", null);
  }

  @Override
  public AiCompletionResponse complete(AiCompletionRequest request) {
    int promptChars =
        (request == null || request.userPrompt() == null) ? 0 : request.userPrompt().length();
    log.info("NoOp AI: would complete prompt ({} chars)", promptChars);
    return noopResponse();
  }

  @Override
  public AiCompletionResponse completeWithVision(AiVisionRequest request) {
    int imageCount = (request == null || request.images() == null) ? 0 : request.images().size();
    log.info("NoOp AI: would complete vision prompt ({} images)", imageCount);
    return noopResponse();
  }

  private AiCompletionResponse noopResponse() {
    return new AiCompletionResponse(NOT_CONFIGURED_ERROR, "noop", 0, 0, 0, 0, "end_turn", 0L);
  }
}
