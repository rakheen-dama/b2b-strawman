package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Prompt-linter assertions for {@code billing-za.md}. Verifies SA-context tokens are present per
 * architecture §3.6.
 */
class BillingSpecialistPromptLinterTest {

  private static final String PROMPT_PATH = "assistant/specialists/billing-za.md";

  @Test
  void billingPromptContainsSaContextTokens() throws IOException {
    var raw = readClasspathResource(PROMPT_PATH);
    var parsed = SystemPromptBuilder.parse(raw);

    // Front-matter assertions
    assertThat(parsed.frontMatter().get("version")).isEqualTo("1.0.0");
    assertThat(parsed.frontMatter().get("specialist")).isEqualTo("BILLING");

    // Body must contain SA-context tokens
    var body = parsed.body();
    assertThat(body).contains("ZAR");
    assertThat(body).contains("SA English");
    assertThat(body).contains("LSSA");
    assertThat(body).contains("Perusal");
    assertThat(body).contains("Attendance");
  }

  @Test
  void billingPromptContainsNoHallucinationGuard() throws IOException {
    var raw = readClasspathResource(PROMPT_PATH);
    var parsed = SystemPromptBuilder.parse(raw);
    var body = parsed.body();

    // Must contain the explicit no-hallucination example
    assertThat(body).contains("Telephone attendance");
    assertThat(body).contains("Never infer details not present");
  }

  @Test
  void billingPromptContainsDisbursementAwareness() throws IOException {
    var raw = readClasspathResource(PROMPT_PATH);
    var parsed = SystemPromptBuilder.parse(raw);
    var body = parsed.body();

    assertThat(body).containsIgnoringCase("disbursement");
    assertThat(body).containsIgnoringCase("sheriff");
    assertThat(body).containsIgnoringCase("court fees");
  }

  @Test
  void billingPromptHasValidFrontMatter() throws IOException {
    var raw = readClasspathResource(PROMPT_PATH);
    var parsed = SystemPromptBuilder.parse(raw);

    assertThat(parsed.frontMatter()).containsKeys("specialist", "version", "createdAt");
    assertThat(parsed.body()).isNotBlank();
  }

  private static String readClasspathResource(String path) throws IOException {
    var resource = new ClassPathResource(path);
    try (InputStream in = resource.getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
