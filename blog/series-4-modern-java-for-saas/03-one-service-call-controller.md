# The One-Service-Call Controller: A Convention That Scales

*Part 3 of "Modern Java for SaaS" — practical patterns from building production B2B software with Java 25, Spring Boot 4, and Hibernate 7.*

---

DocTeams has 104 REST controllers. Every single one follows the same rule: **each endpoint makes exactly one service call**.

No conditional logic. No repository injection. No data transformation. No error mapping. One line per method. The controller receives the request, calls a service method, wraps the result in a `ResponseEntity`, and returns it.

This sounds restrictive. It is. That's the point.

## The Pattern

```java
@RestController
@RequestMapping("/api/acceptance-requests")
public class AcceptanceController {

    private final AcceptanceService acceptanceService;

    public AcceptanceController(AcceptanceService acceptanceService) {
        this.acceptanceService = acceptanceService;
    }

    @PostMapping
    @RequiresCapability("CUSTOMER_MANAGEMENT")
    public ResponseEntity<AcceptanceRequestResponse> create(
            @Valid @RequestBody CreateAcceptanceRequest request) {
        var response = acceptanceService.createAndSendResponse(
            request.generatedDocumentId(),
            request.portalContactId(),
            request.expiryDays());
        return ResponseEntity.created(
            URI.create("/api/acceptance-requests/" + response.id()))
            .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AcceptanceRequestResponse> getDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(acceptanceService.getDetail(id));
    }

    @PostMapping("/{id}/remind")
    @RequiresCapability("CUSTOMER_MANAGEMENT")
    public ResponseEntity<AcceptanceRequestResponse> remind(@PathVariable UUID id) {
        return ResponseEntity.ok(acceptanceService.remindResponse(id));
    }

    @PostMapping("/{id}/revoke")
    @RequiresCapability("CUSTOMER_MANAGEMENT")
    public ResponseEntity<AcceptanceRequestResponse> revoke(@PathVariable UUID id) {
        return ResponseEntity.ok(acceptanceService.revokeResponse(id));
    }
}
```

Four endpoints. Four one-liners. Authorization is declarative (`@RequiresCapability`). Validation is declarative (`@Valid`). Error handling is elsewhere (`GlobalExceptionHandler`). The controller is a routing table with types.

## Why This Convention Exists

### It makes code review trivial

When an AI agent (or a junior developer) writes a new controller, reviewing it takes 10 seconds. Is each method one service call? Yes → approved. No → request changes. There's no judgment involved — the convention is binary.

Over 843 PRs, this saved hours of review time. The reviewer doesn't need to evaluate business logic in the controller because there isn't any.

### It forces clean service boundaries

If the controller can only make one service call, then the service must expose methods at the right granularity. "Create an invoice and send a notification and log an audit event" isn't three controller calls — it's one service method that orchestrates internally.

This prevents the common anti-pattern of "fat controllers" that sequence multiple service calls with error handling between them. The sequencing logic belongs in the service.

### It centralizes error handling

Controllers don't catch exceptions. Services throw semantic exceptions. The `GlobalExceptionHandler` catches them and renders RFC 9457 `ProblemDetail` responses:

```java
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ProblemDetail> handleForbidden(ForbiddenException ex) {
        auditService.log(/* security event */);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getBody());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(...) {
        var problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Concurrent modification");
        problem.setDetail("Resource was modified concurrently. Please retry.");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }
}
```

Every controller automatically gets consistent error responses. Adding a new exception type means adding one handler method — not updating 104 controllers.

The `GlobalExceptionHandler` must extend `ResponseEntityExceptionHandler`. Without this, Spring's `DefaultHandlerExceptionResolver` handles `ErrorResponseException` subclasses by calling `response.sendError()` — which produces the correct HTTP status but an **empty body**. Extending `ResponseEntityExceptionHandler` enables ProblemDetail JSON rendering. I learned this the hard way when error responses returned `404 {}` instead of `404 { "title": "Project not found", "detail": "..." }`.

### It simplifies testing

Controller tests are endpoint tests, not logic tests:

```java
@Test
void createProject_returnsCreated() throws Exception {
    mockMvc.perform(post("/api/projects")
            .with(ownerJwt())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"name": "Q1 Audit"}"""))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Q1 Audit"));
}

@Test
void createProject_withoutCapability_returnsForbidden() throws Exception {
    mockMvc.perform(post("/api/projects")
            .with(memberJwt()))  // member role lacks MANAGE_PROJECTS
        .andExpect(status().isForbidden());
}
```

Test the endpoint contract (request shape, response shape, status codes) and authorization (who can access what). Test business logic in the service tests, not here.

## The Capability System

Authorization deserves its own callout. DocTeams uses `@RequiresCapability` instead of Spring Security's `@PreAuthorize`:

```java
@PostMapping
@RequiresCapability("MANAGE_INVOICES")
public ResponseEntity<InvoiceResponse> create(...) {
    return ResponseEntity.created(...).body(invoiceService.create(...));
}
```

The `CapabilityAuthorizationManager` checks whether the current member has the named capability:

```java
@Component
public class CapabilityAuthorizationManager
        implements AuthorizationManager<MethodInvocation> {

    @Override
    public AuthorizationResult authorize(
            Supplier<? extends Authentication> authentication,
            MethodInvocation invocation) {
        RequiresCapability annotation =
            invocation.getMethod().getAnnotation(RequiresCapability.class);
        if (annotation == null) return null;
        return new AuthorizationDecision(
            RequestScopes.hasCapability(annotation.value()));
    }
}
```

Capabilities are resolved from the member's org role during `MemberFilter` and stored in `RequestScopes.CAPABILITIES`. They're fine-grained (`MANAGE_INVOICES`, `VIEW_PROFITABILITY`, `MANAGE_RATES`) instead of role-based (`ADMIN`, `MEMBER`). This means an org can create a "Bookkeeper" role that manages invoices but can't see profitability reports — without changing any controller code.

## The Anti-Patterns We Catch

The code review checklist for controllers is short:

1. **One service call per method?** If not, move the orchestration to the service.
2. **No repository injection?** Controllers depend on services, never on repositories directly.
3. **No conditional logic?** No `if (user.isAdmin())` branching. Use `@RequiresCapability`.
4. **No data transformation?** If the service returns a domain object and the controller converts it to a DTO, move the conversion to the service (or the DTO's static factory method).
5. **`@RequiresCapability` on write endpoints?** Every `POST`, `PUT`, `DELETE` needs explicit authorization.

When the review agent flags a convention violation, the fix is always the same: move logic to the service. The controller stays thin.

## When It Breaks Down

This convention has limits:

**File uploads.** Multipart file uploads require reading the file from the request, which is arguably "logic" in the controller. I handle this by passing the `MultipartFile` directly to the service method:

```java
@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<DocumentResponse> upload(
        @PathVariable UUID projectId,
        @RequestParam("file") MultipartFile file) {
    return ResponseEntity.ok(documentService.upload(projectId, file));
}
```

Still one service call. The file handling is the service's problem.

**Streaming responses.** PDF downloads and CSV exports need to write directly to the output stream. The controller sets headers and delegates to a service that writes to `OutputStream`. This technically violates "one service call" since there's header setup + service call, but the intent is preserved.

**Pagination.** Spring Data's `Pageable` parameter resolution happens in the controller. Passing `Pageable` to the service is fine — it's still one call.

None of these break the *intent* of the convention: controllers are routing tables, not logic containers.

---

*Next in this series: [Domain Events Without a Message Broker](04-domain-events-without-broker.md)*

*Previous: [Integration Testing with Testcontainers](02-testcontainers-no-mocks.md)*
