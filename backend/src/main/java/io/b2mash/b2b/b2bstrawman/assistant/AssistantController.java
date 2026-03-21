package io.b2mash.b2b.b2bstrawman.assistant;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

  private final AssistantService assistantService;
  private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

  public AssistantController(AssistantService assistantService) {
    this.assistantService = assistantService;
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

  /** Request body for the confirmation endpoint. Public for Jackson deserialization. */
  public record ConfirmRequest(String toolCallId, boolean approved) {}
}
