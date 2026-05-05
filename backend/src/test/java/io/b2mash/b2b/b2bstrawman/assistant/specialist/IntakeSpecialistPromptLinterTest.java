package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** Validates the intake-za.md system prompt contains required SA-context tokens. */
class IntakeSpecialistPromptLinterTest {

  private static final String PROMPT_PATH = "assistant/specialists/intake-za.md";

  @Test
  void intakePromptContainsSaContextTokens() throws IOException {
    var raw = readClasspathResource(PROMPT_PATH);
    var parsed = SystemPromptBuilder.parse(raw);

    assertThat(parsed.frontMatter().get("version")).isEqualTo("1.0.0");
    assertThat(parsed.frontMatter().get("specialist")).isEqualTo("INTAKE");

    var body = parsed.body();
    assertThat(body).contains("ZAR");
    assertThat(body).contains("SA English");
    assertThat(body).contains("RSA ID");
    assertThat(body).contains("CIPC");
    assertThat(body).contains("POPIA");
    assertThat(body).contains("POSSIBLE_INJECTION_DETECTED");
  }

  @Test
  void intakePromptContainsEntityTypes() throws IOException {
    var raw = readClasspathResource(PROMPT_PATH);
    var parsed = SystemPromptBuilder.parse(raw);
    var body = parsed.body();

    assertThat(body).contains("(Pty) Ltd");
    assertThat(body).contains("Close corporation");
    assertThat(body).contains("Trust");
    assertThat(body).contains("NPC");
  }

  @Test
  void intakePromptContainsPopiaSection() throws IOException {
    var raw = readClasspathResource(PROMPT_PATH);
    var parsed = SystemPromptBuilder.parse(raw);
    var body = parsed.body();

    assertThat(body).containsIgnoringCase("special personal information");
    assertThat(body).containsIgnoringCase("health");
    assertThat(body).containsIgnoringCase("biometric");
    assertThat(body).containsIgnoringCase("popiaFlaggedFields");
  }

  @Test
  void intakePromptContainsPromptInjectionGuard() throws IOException {
    var raw = readClasspathResource(PROMPT_PATH);
    var parsed = SystemPromptBuilder.parse(raw);
    var body = parsed.body();

    assertThat(body).contains("POSSIBLE_INJECTION_DETECTED");
    assertThat(body).containsIgnoringCase("ignore your scope");
  }

  @Test
  void intakePromptContainsVatAndPostalCodeRules() throws IOException {
    var raw = readClasspathResource(PROMPT_PATH);
    var parsed = SystemPromptBuilder.parse(raw);
    var body = parsed.body();

    assertThat(body).contains("10 digits starting with");
    assertThat(body).contains("4 digits");
    assertThat(body).contains("Gauteng");
    assertThat(body).contains("Western Cape");
  }

  @Test
  void intakePromptHasValidFrontMatter() throws IOException {
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
