package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Static linter for {@code classpath:assistant/specialists/*.md}. Asserts each stub:
 *
 * <ul>
 *   <li>has YAML front-matter delimited by {@code ---},
 *   <li>declares the required keys ({@code specialist}, {@code version}, {@code createdAt}),
 *   <li>has a non-empty body.
 * </ul>
 *
 * <p>Full SA-context assertions (per arch §3.6) are deferred to slice 512A/513A/514A — at 511A the
 * bodies are intentional placeholders.
 */
class SystemPromptLinterTest {

  @Test
  void everySpecialistMarkdownHasFrontMatterAndBody() throws IOException {
    var resolver = new PathMatchingResourcePatternResolver();
    Resource[] resources = resolver.getResources("classpath:assistant/specialists/*.md");

    assertThat(resources)
        .as("specialist stub markdown files should be on the classpath")
        .isNotEmpty();

    for (Resource resource : resources) {
      var raw = readResource(resource);
      var parsed = SystemPromptBuilder.parse(raw);

      assertThat(parsed.frontMatter())
          .as("front-matter for %s", resource.getFilename())
          .containsKeys("specialist", "version", "createdAt");
      assertThat(parsed.body()).as("body for %s", resource.getFilename()).isNotBlank();
    }
  }

  @Test
  void parserRejectsMissingFrontMatter() {
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalStateException.class, () -> SystemPromptBuilder.parse("no front matter here"));
  }

  private static String readResource(Resource resource) throws IOException {
    try (InputStream in = resource.getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
