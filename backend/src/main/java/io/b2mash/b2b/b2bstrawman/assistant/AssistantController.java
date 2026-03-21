package io.b2mash.b2b.b2bstrawman.assistant;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.util.Map;
import java.util.concurrent.Executors;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

  private final AssistantService assistantService;

  public AssistantController(AssistantService assistantService) {
    this.assistantService = assistantService;
  }

  @PostMapping("/chat")
  public ResponseEntity<SseEmitter> chat(@RequestBody ChatContext context) {
    // Capture ScopedValue bindings on the request thread before submitting to virtual thread
    var tenantId = RequestScopes.requireTenantId();
    var memberId = RequestScopes.requireMemberId();
    var orgId = RequestScopes.requireOrgId();
    var orgRole = RequestScopes.getOrgRole();
    var capabilities = RequestScopes.getCapabilities();

    var emitter = new SseEmitter(300_000L);
    var executor = Executors.newVirtualThreadPerTaskExecutor();
    executor.submit(
        () ->
            ScopedValue.where(RequestScopes.TENANT_ID, tenantId)
                .where(RequestScopes.MEMBER_ID, memberId)
                .where(RequestScopes.ORG_ID, orgId)
                .where(RequestScopes.ORG_ROLE, orgRole)
                .where(RequestScopes.CAPABILITIES, capabilities)
                .run(() -> assistantService.chat(context, emitter)));
    emitter.onTimeout(emitter::complete);
    emitter.onError(e -> emitter.complete());
    return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(emitter);
  }

  @PostMapping("/chat/confirm")
  public ResponseEntity<Map<String, Object>> confirm(@RequestBody ConfirmRequest request) {
    assistantService.confirm(request.toolCallId(), request.approved());
    return ResponseEntity.ok(Map.of("acknowledged", true));
  }

  public record ConfirmRequest(String toolCallId, boolean approved) {}
}
