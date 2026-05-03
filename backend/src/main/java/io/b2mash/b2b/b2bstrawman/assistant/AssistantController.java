package io.b2mash.b2b.b2bstrawman.assistant;

import io.b2mash.b2b.b2bstrawman.assistant.specialist.SpecialistDtos;
import io.b2mash.b2b.b2bstrawman.assistant.specialist.SpecialistService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

  private final AssistantService assistantService;
  private final SpecialistService specialistService;
  private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

  public AssistantController(
      AssistantService assistantService, SpecialistService specialistService) {
    this.assistantService = assistantService;
    this.specialistService = specialistService;
  }

  /**
   * Streams an AI assistant response via SSE. The ScopedValue capture + re-bind is inherently
   * multi-step at the controller level because it bridges the servlet request thread to the virtual
   * thread that runs the LLM streaming loop (see ADR-204). This is a justified exception to the
   * thin-controller convention.
   */
  @PostMapping("/chat")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<SseEmitter> chat(@RequestBody ChatContext context) {
    // Capture ScopedValue bindings on the request thread before submitting to virtual thread
    var tenantId = RequestScopes.requireTenantId();
    var memberId = RequestScopes.requireMemberId();
    var orgId = RequestScopes.requireOrgId();
    var orgRole = RequestScopes.getOrgRole();
    var capabilities = RequestScopes.getCapabilities();

    var emitter = new SseEmitter(300_000L);
    // Register handlers before submitting to executor to avoid race condition
    emitter.onTimeout(emitter::complete);
    emitter.onError(e -> emitter.complete());

    // Build carrier conditionally — ScopedValue.where(key, null) throws NPE
    var carrier =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantId)
            .where(RequestScopes.MEMBER_ID, memberId)
            .where(RequestScopes.ORG_ID, orgId)
            .where(RequestScopes.CAPABILITIES, capabilities);
    if (orgRole != null) {
      carrier = carrier.where(RequestScopes.ORG_ROLE, orgRole);
    }
    final var boundCarrier = carrier;
    virtualThreadExecutor.submit(
        () -> boundCarrier.run(() -> assistantService.chat(context, emitter)));
    return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(emitter);
  }

  @PostMapping("/chat/confirm")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Map<String, Object>> confirm(@RequestBody ConfirmRequest request) {
    assistantService.confirm(request.toolCallId(), request.approved());
    return ResponseEntity.ok(Map.of("acknowledged", true));
  }

  /**
   * Phase 70: list specialists visible to the current member. Visibility = PRO subscription +
   * {@code AI_ASSISTANT_USE} capability + (optional) launcher surface filter. Returns an empty list
   * when the caller isn't entitled — does not throw.
   */
  @GetMapping("/specialists")
  @PreAuthorize("isAuthenticated()")
  @RequiresCapability("AI_ASSISTANT_USE")
  public ResponseEntity<List<SpecialistDtos.SpecialistSummary>> listSpecialists(
      @RequestParam(value = "surface", required = false) String surface) {
    return ResponseEntity.ok(specialistService.listVisible(surface));
  }

  /** Phase 70: fetch a single specialist by id. 404 when unknown or hidden from the caller. */
  @GetMapping("/specialists/{id}")
  @PreAuthorize("isAuthenticated()")
  @RequiresCapability("AI_ASSISTANT_USE")
  public ResponseEntity<SpecialistDtos.SpecialistSummary> getSpecialist(
      @PathVariable("id") String id) {
    return ResponseEntity.ok(specialistService.getOne(id));
  }

  /**
   * Phase 70: start an ephemeral specialist session. Returns the resolved system-prompt hash and
   * the capability-narrowed tool subset. PRO-tier gated: STARTER tenants receive 403.
   */
  @PostMapping("/specialists/{id}/sessions")
  @PreAuthorize("isAuthenticated()")
  @RequiresCapability("AI_ASSISTANT_USE")
  public ResponseEntity<SpecialistDtos.StartSessionResponse> startSpecialistSession(
      @PathVariable("id") String id,
      @RequestBody(required = false) SpecialistDtos.StartSessionRequest request) {
    return ResponseEntity.ok(specialistService.startSession(id, request));
  }

  /** Request body for the confirmation endpoint. Public for Jackson deserialization. */
  public record ConfirmRequest(String toolCallId, boolean approved) {}
}
