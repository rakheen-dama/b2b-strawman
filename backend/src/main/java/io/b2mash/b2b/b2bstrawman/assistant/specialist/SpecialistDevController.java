package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev-only specialist prompt reload endpoint. Profile-gated per ADR-033 — never exposed in
 * production. The {@code /internal/*} prefix is auth-gated by {@code ApiKeyAuthFilter}.
 */
@RestController
@Profile({"local", "dev"})
@RequestMapping("/internal/assistant/specialists")
public class SpecialistDevController {

  private final SystemPromptBuilder systemPromptBuilder;

  public SpecialistDevController(SystemPromptBuilder systemPromptBuilder) {
    this.systemPromptBuilder = systemPromptBuilder;
  }

  @PostMapping("/reload")
  public ResponseEntity<Map<String, Object>> reload() {
    systemPromptBuilder.reload();
    return ResponseEntity.ok(Map.of("reloaded", true));
  }
}
