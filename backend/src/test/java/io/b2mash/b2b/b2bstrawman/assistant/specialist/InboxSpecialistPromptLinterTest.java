package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** Prompt-linter tests for inbox-za.md per 514A.6 acceptance criteria. */
class InboxSpecialistPromptLinterTest {

  private static final String PROMPT_PATH = "assistant/specialists/inbox-za.md";

  @Test
  void inboxPromptContainsSaContextTokens() throws IOException {
    var raw = readClasspathResource(PROMPT_PATH);
    var parsed = SystemPromptBuilder.parse(raw);

    var body = parsed.body();
    assertThat(body).contains("SA English");
    assertThat(body).contains("factual");
    assertThat(body).contains("third-person");
    assertThat(body).contains("ZAR");
    assertThat(body).contains("terminology");
  }

  @Test
  void inboxPromptHasValidFrontMatter() throws IOException {
    var raw = readClasspathResource(PROMPT_PATH);
    var parsed = SystemPromptBuilder.parse(raw);

    assertThat(parsed.frontMatter()).containsKeys("specialist", "version", "createdAt");
    assertThat(parsed.frontMatter().get("specialist")).isEqualTo("INBOX");
    assertThat(parsed.frontMatter().get("version")).isEqualTo("1.0.0");
    assertThat(parsed.body()).isNotBlank();
  }

  @Test
  void inboxPromptContainsPrivilegeAwareness() throws IOException {
    var raw = readClasspathResource(PROMPT_PATH);
    var parsed = SystemPromptBuilder.parse(raw);

    var body = parsed.body();
    assertThat(body).contains("privilege");
    assertThat(body).contains("confidential");
    assertThat(body).containsIgnoringCase("never");
  }

  @Test
  void inboxPromptContainsMatterStageAwareness() throws IOException {
    var raw = readClasspathResource(PROMPT_PATH);
    var parsed = SystemPromptBuilder.parse(raw);

    var body = parsed.body();
    assertThat(body).contains("PROSPECT");
    assertThat(body).contains("ACTIVE");
    assertThat(body).contains("CLOSING");
  }

  @Test
  void inboxPromptContainsDirectModeInstructions() throws IOException {
    var raw = readClasspathResource(PROMPT_PATH);
    var parsed = SystemPromptBuilder.parse(raw);

    var body = parsed.body();
    assertThat(body).contains("DIRECT");
    assertThat(body).contains("ADR-267");
  }

  @Test
  void inboxPromptContainsVerticalConditionalTrustInstructions() throws IOException {
    var raw = readClasspathResource(PROMPT_PATH);
    var parsed = SystemPromptBuilder.parse(raw);

    var body = parsed.body();
    assertThat(body).contains("trust");
    assertThat(body).contains("legal-za");
    assertThat(body).contains("trustTransactionsIncluded");
  }

  @Test
  void inboxPromptContainsTerminologyKeyReference() throws IOException {
    var raw = readClasspathResource(PROMPT_PATH);
    var parsed = SystemPromptBuilder.parse(raw);

    var body = parsed.body();
    assertThat(body).contains("matter");
    assertThat(body).contains("project");
    assertThat(body).contains("client");
    assertThat(body).contains("customer");
    assertThat(body).contains("terminologyNamespace");
  }

  private static String readClasspathResource(String path) throws IOException {
    var resource = new ClassPathResource(path);
    try (InputStream in = resource.getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
