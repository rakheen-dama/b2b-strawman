package io.b2mash.b2b.b2bstrawman.assistant;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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

  private static final long SSE_TIMEOUT_MS = 150_000L;

  private final AssistantService assistantService;
  private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
  private final Semaphore concurrencyLimit;

  public AssistantController(
      AssistantService assistantService,
      @Value("${kazi.ai.max-concurrent-sessions:20}") int maxConcurrentSessions) {
    this.assistantService = assistantService;
    this.concurrencyLimit = new Semaphore(maxConcurrentSessions);
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
    if (!concurrencyLimit.tryAcquire()) {
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
    }

    var tenantId = RequestScopes.requireTenantId();
    var memberId = RequestScopes.requireMemberId();
    var orgId = RequestScopes.requireOrgId();
    var orgRole = RequestScopes.getOrgRole();
    var capabilities = RequestScopes.getCapabilities();

    var emitter = new SseEmitter(SSE_TIMEOUT_MS);
    var released = new AtomicBoolean(false);
    Runnable releaseOnce =
        () -> {
          if (released.compareAndSet(false, true)) concurrencyLimit.release();
        };
    emitter.onCompletion(releaseOnce::run);
    emitter.onTimeout(
        () -> {
          releaseOnce.run();
          emitter.complete();
        });
    emitter.onError(
        e -> {
          releaseOnce.run();
          emitter.complete();
        });

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
