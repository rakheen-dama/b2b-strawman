package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Linter for the {@code billing-za.md} system prompt — Phase 70 Epic 512A.
 *
 * <p>Asserts the SA-context tokens required by ADR-269 / requirements §2.4 are present in the
 * markdown body so a careless edit cannot silently drop them. The general front-matter shape is
 * already covered by {@link SystemPromptLinterTest}; this class layers the SA-specific vocabulary
 * checks on top.
 */
class BillingSpecialistPromptLinterTest {

  @Test
  void billingZaContainsSaContextTokens() throws IOException {
    var resource = new ClassPathResource("assistant/specialists/billing-za.md");
    String raw;
    try (var in = resource.getInputStream()) {
      raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    var parsed = SystemPromptBuilder.parse(raw);

    assertThat(parsed.body())
        .contains("ZAR")
        .contains("SA English")
        .contains("LSSA")
        .contains("Perusal")
        .contains("Attendance");

    assertThat(parsed.frontMatter()).containsEntry("version", "1.0.0");
  }
}
