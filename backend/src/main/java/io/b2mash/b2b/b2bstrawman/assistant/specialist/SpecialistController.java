package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import io.b2mash.b2b.b2bstrawman.assistant.specialist.SpecialistService.SpecialistDetailDto;
import io.b2mash.b2b.b2bstrawman.assistant.specialist.SpecialistService.SpecialistSummaryDto;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Specialist registry + session HTTP adapter. All endpoints are gated by {@code AI_ASSISTANT_USE}.
 *
 * <p>Authorization is purely capability-based; this controller does NOT reference plan tiers (there
 * are no plan-tier subscriptions in this product — strategic decision 2026-04-11).
 */
@RestController
@RequestMapping("/api/assistant/specialists")
public class SpecialistController {

  private final SpecialistService specialistService;
  private final SpecialistSessionService specialistSessionService;

  public SpecialistController(
      SpecialistService specialistService, SpecialistSessionService specialistSessionService) {
    this.specialistService = specialistService;
    this.specialistSessionService = specialistSessionService;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  @RequiresCapability("AI_ASSISTANT_USE")
  public ResponseEntity<List<SpecialistSummaryDto>> list(
      @RequestParam(required = false) String route) {
    return ResponseEntity.ok(specialistService.listVisible(route));
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  @RequiresCapability("AI_ASSISTANT_USE")
  public ResponseEntity<SpecialistDetailDto> get(@PathVariable String id) {
    return ResponseEntity.ok(specialistService.detail(id));
  }

  @PostMapping("/{id}/sessions")
  @PreAuthorize("isAuthenticated()")
  @RequiresCapability("AI_ASSISTANT_USE")
  public ResponseEntity<SessionHandle> start(
      @PathVariable String id, @RequestBody StartSessionRequest req) {
    return ResponseEntity.ok(
        specialistSessionService.start(id, req.contextRef(), req.initialPrompt()));
  }

  public record StartSessionRequest(ContextRef contextRef, String initialPrompt) {}
}
