package io.b2mash.b2b.b2bstrawman.integration.ai;

import io.b2mash.b2b.b2bstrawman.integration.ConnectionTestResult;
import java.util.List;

/**
 * Port for AI text operations (generation, summarization, categorization). Tenant-scoped: each org
 * can configure their own AI provider.
 */
public interface AiProvider {

  /** Provider identifier (e.g., "openai", "anthropic", "noop"). */
  String providerId();

  /** Generate text from a prompt. */
  AiTextResult generateText(AiTextRequest request);

  /** Summarize content to a target length. */
  AiTextResult summarize(String content, int maxLength);

  /** Suggest categories for content based on existing category list. */
  List<String> suggestCategories(String content, List<String> existingCategories);

  /** Test connectivity with the configured credentials. */
  ConnectionTestResult testConnection();
}
