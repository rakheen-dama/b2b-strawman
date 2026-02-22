package io.b2mash.b2b.b2bstrawman.integration.ai;

import io.b2mash.b2b.b2bstrawman.integration.ConnectionTestResult;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NoOpAiProvider implements AiProvider {

  private static final Logger log = LoggerFactory.getLogger(NoOpAiProvider.class);

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
}
