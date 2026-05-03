package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads specialist system prompts from the classpath. Each prompt file is a markdown document with
 * YAML front-matter:
 *
 * <pre>
 * ---
 * version: "1.0.0"
 * createdAt: "2026-05-03"
 * specialist: "BILLING"
 * ---
 *
 * # Billing Assistant — System Prompt
 * ...
 * </pre>
 *
 * <p>Results are cached via Caffeine keyed by resource path. The classpath is immutable in
 * production, so there is no expiry; the dev-only reload endpoint at {@code POST
 * /internal/assistant/specialists/reload} invalidates the cache.
 */
@Service
public class SpecialistSystemPromptLoader {

  private final Cache<String, LoadedPrompt> cache = Caffeine.newBuilder().maximumSize(16).build();

  /**
   * Load the prompt at {@code resourcePath} (e.g. {@code "assistant/specialists/billing-za.md"}).
   *
   * @return the parsed front-matter version + body text (front-matter stripped)
   * @throws IllegalStateException if the resource is missing or front-matter is malformed
   */
  public LoadedPrompt loadPrompt(String resourcePath) {
    return cache.get(resourcePath, this::parse);
  }

  /** Invalidate the entire cache. Called by the dev-only reload endpoint. */
  public void invalidateAll() {
    cache.invalidateAll();
  }

  private LoadedPrompt parse(String resourcePath) {
    try (var in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
      if (in == null) {
        throw new IllegalStateException("Prompt resource not found on classpath: " + resourcePath);
      }
      var raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (!raw.startsWith("---")) {
        throw new IllegalStateException("Missing YAML front-matter in " + resourcePath);
      }
      int end = raw.indexOf("\n---", 3);
      if (end < 0) {
        throw new IllegalStateException("Unterminated YAML front-matter in " + resourcePath);
      }
      String yaml = raw.substring(3, end).trim();
      String body = raw.substring(end + 4).stripLeading();
      Map<String, Object> meta = new Yaml().load(yaml);
      if (meta == null || meta.get("version") == null) {
        throw new IllegalStateException("Missing 'version' in front-matter of " + resourcePath);
      }
      var version = String.valueOf(meta.get("version"));
      return new LoadedPrompt(version, body);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Result of parsing a specialist prompt resource. */
  public record LoadedPrompt(String version, String body) {}

  /**
   * Dev-only endpoint that invalidates the prompt cache so authors can edit a {@code .md} file and
   * see the change without restarting the JVM. Profile-gated per ADR-033 — never exposed in
   * production.
   */
  @RestController
  @Profile({"local", "dev"})
  @RequestMapping("/internal/assistant/specialists")
  public static class ReloadController {

    private final SpecialistSystemPromptLoader loader;

    public ReloadController(SpecialistSystemPromptLoader loader) {
      this.loader = loader;
    }

    @PostMapping("/reload")
    public ResponseEntity<Void> reload() {
      loader.invalidateAll();
      return ResponseEntity.noContent().build();
    }
  }
}
