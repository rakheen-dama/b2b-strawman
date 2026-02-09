# Exception Handling Refactor — Implementation Plan

> **Branch**: `feat/exception-handling-refactor`
> **Base**: `main`
> **Scope**: Backend only — `backend/src/main/java/io/b2mash/b2b/b2bstrawman/`

## Problem

The backend has 3 inconsistent error handling patterns:

1. **Controllers manually build `ProblemDetail`** — `memberContextMissing()` helper duplicated identically in 3 controllers, `notFound()` duplicated in 2 controllers, inline `ProblemDetail` construction for 409/400 errors.
2. **Services throw `ErrorResponseException`** — `ProjectService` and `ProjectMemberService` have duplicated helper methods (`notFound()`, `conflict()`, `badRequest()`, `forbidden()`) that build `ErrorResponseException` with embedded `ProblemDetail`.
3. **Services return `Optional`** — `DocumentService` returns `Optional.empty()` for both "not found" and "access denied", forcing controllers to map errors. `ProjectService` also uses this for reads.

This couples HTTP error formatting to business logic, duplicates ~60 lines of helper methods, and makes controllers unnecessarily complex (e.g., `DocumentController.cancelUpload()` has a switch-case mapping `CancelResult` enum values to HTTP responses).

## Goal

One consistent pattern: **services throw semantic exceptions → Spring auto-handles → controllers become pure delegation**.

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Exception base class | Extend `ErrorResponseException` | Spring auto-renders as RFC 9457 ProblemDetail — no `@ControllerAdvice` needed for each exception type |
| Exception granularity | 5 shared exception classes | `ResourceNotFoundException`, `ResourceConflictException`, `ForbiddenException`, `InvalidStateException`, `MissingOrganizationContextException` — covers all current error cases |
| Exception package | `io.b2mash.b2b.b2bstrawman.exception` | Cross-cutting, used by multiple feature packages (like `config/`, `security/`) |
| "Not found" vs "access denied" | Both throw `ResourceNotFoundException` (404) | Security-by-obscurity — don't reveal resource existence to unauthorized users. Already the behavior in the codebase, just inconsistent |
| Member context extraction | Static `RequestScopes.requireMemberId()` / `getOrgRole()` | Eliminates 4-line block repeated 8+ times across 3 controllers |
| Member context missing error | Dedicated `MemberContextNotBoundException extends RuntimeException` in `multitenancy` package | Safer than raw `IllegalStateException` — `@ControllerAdvice` catches only this specific type |
| `ProvisioningController` | Leave as-is | Internal endpoint returns different response bodies on 201 vs 409 — no benefit from refactoring |
| `ProvisioningException` | Leave as-is | NOT dead code — used by `@Retryable` in `TenantProvisioningService` |

---

## Phase 1: Create Exception Infrastructure

All files are additive — no existing code breaks.

### 1.1 — Exception classes in `exception/` package

**File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/exception/ResourceNotFoundException.java`**

```java
package io.b2mash.b2b.b2bstrawman.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class ResourceNotFoundException extends ErrorResponseException {

  public ResourceNotFoundException(String resourceType, Object id) {
    super(HttpStatus.NOT_FOUND, createProblem(resourceType + " not found",
        "No " + resourceType.toLowerCase() + " found with id " + id), null);
  }

  public static ResourceNotFoundException withDetail(String title, String detail) {
    return new ResourceNotFoundException(title, detail, HttpStatus.NOT_FOUND);
  }

  private ResourceNotFoundException(String title, String detail, HttpStatus status) {
    super(status, createProblem(title, detail), null);
  }

  private static ProblemDetail createProblem(String title, String detail) {
    var problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
    problem.setTitle(title);
    problem.setDetail(detail);
    return problem;
  }
}
```

**File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/exception/ResourceConflictException.java`**

```java
package io.b2mash.b2b.b2bstrawman.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class ResourceConflictException extends ErrorResponseException {

  public ResourceConflictException(String title, String detail) {
    super(HttpStatus.CONFLICT, createProblem(title, detail), null);
  }

  private static ProblemDetail createProblem(String title, String detail) {
    var problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
    problem.setTitle(title);
    problem.setDetail(detail);
    return problem;
  }
}
```

**File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/exception/ForbiddenException.java`**

```java
package io.b2mash.b2b.b2bstrawman.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class ForbiddenException extends ErrorResponseException {

  public ForbiddenException(String title, String detail) {
    super(HttpStatus.FORBIDDEN, createProblem(title, detail), null);
  }

  private static ProblemDetail createProblem(String title, String detail) {
    var problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
    problem.setTitle(title);
    problem.setDetail(detail);
    return problem;
  }
}
```

**File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/exception/InvalidStateException.java`**

```java
package io.b2mash.b2b.b2bstrawman.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class InvalidStateException extends ErrorResponseException {

  public InvalidStateException(String title, String detail) {
    super(HttpStatus.BAD_REQUEST, createProblem(title, detail), null);
  }

  private static ProblemDetail createProblem(String title, String detail) {
    var problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    problem.setTitle(title);
    problem.setDetail(detail);
    return problem;
  }
}
```

**File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/exception/MissingOrganizationContextException.java`**

```java
package io.b2mash.b2b.b2bstrawman.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class MissingOrganizationContextException extends ErrorResponseException {

  public MissingOrganizationContextException() {
    super(HttpStatus.UNAUTHORIZED, createProblem(), null);
  }

  private static ProblemDetail createProblem() {
    var problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
    problem.setTitle("Missing organization context");
    problem.setDetail("JWT token does not contain organization claim");
    return problem;
  }
}
```

### 1.2 — `MemberContextNotBoundException` in `multitenancy/` package

**File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/MemberContextNotBoundException.java`**

```java
package io.b2mash.b2b.b2bstrawman.multitenancy;

public class MemberContextNotBoundException extends RuntimeException {

  public MemberContextNotBoundException() {
    super("Member context not available — MEMBER_ID not bound by filter chain");
  }
}
```

### 1.3 — `GlobalExceptionHandler` @ControllerAdvice

**File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/exception/GlobalExceptionHandler.java`**

```java
package io.b2mash.b2b.b2bstrawman.exception;

import io.b2mash.b2b.b2bstrawman.multitenancy.MemberContextNotBoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MemberContextNotBoundException.class)
  public ResponseEntity<ProblemDetail> handleMemberContextNotBound(
      MemberContextNotBoundException ex) {
    log.error("Member context invariant violation: {}", ex.getMessage());
    var problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    problem.setTitle("Member context not available");
    problem.setDetail("Unable to resolve member identity for request");
    return ResponseEntity.of(problem).build();
  }
}
```

### 1.4 — Add convenience methods to `RequestScopes`

**File: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java`**

Add these two methods to the existing class:

```java
/** Returns the current member's UUID. Throws if not bound by filter chain. */
public static UUID requireMemberId() {
  if (!MEMBER_ID.isBound()) {
    throw new MemberContextNotBoundException();
  }
  return MEMBER_ID.get();
}

/** Returns the current member's org role, or null if not bound. */
public static String getOrgRole() {
  return ORG_ROLE.isBound() ? ORG_ROLE.get() : null;
}
```

---

## Phase 2: Refactor Services

### 2.1 — `project/ProjectService.java`

**Changes:**
- Replace `import org.springframework.web.ErrorResponseException` with `import io.b2mash.b2b.b2bstrawman.exception.*`
- Remove `import org.springframework.http.HttpStatus` and `import org.springframework.http.ProblemDetail`

**`getProject` — change return type from `Optional<ProjectWithRole>` to `ProjectWithRole`:**

BEFORE:
```java
public Optional<ProjectWithRole> getProject(UUID id, UUID memberId, String orgRole) {
  var project = repository.findById(id);
  if (project.isEmpty()) {
    return Optional.empty();
  }
  var access = projectAccessService.checkAccess(id, memberId, orgRole);
  if (!access.canView()) {
    return Optional.empty();
  }
  return Optional.of(new ProjectWithRole(project.get(), access.projectRole()));
}
```

AFTER:
```java
public ProjectWithRole getProject(UUID id, UUID memberId, String orgRole) {
  var project = repository.findById(id)
      .orElseThrow(() -> new ResourceNotFoundException("Project", id));
  var access = projectAccessService.checkAccess(id, memberId, orgRole);
  if (!access.canView()) {
    throw new ResourceNotFoundException("Project", id);
  }
  return new ProjectWithRole(project, access.projectRole());
}
```

**`updateProject` — change return type from `Optional<ProjectWithRole>` to `ProjectWithRole`:**

BEFORE:
```java
public Optional<ProjectWithRole> updateProject(
    UUID id, String name, String description, UUID memberId, String orgRole) {
  var project = repository.findById(id);
  if (project.isEmpty()) {
    return Optional.empty();
  }
  var access = projectAccessService.checkAccess(id, memberId, orgRole);
  if (!access.canView()) {
    return Optional.empty();
  }
  if (!access.canEdit()) {
    throw forbidden("Cannot edit project", "You do not have permission to edit project " + id);
  }
  var updated = project.get();
  updated.update(name, description);
  updated = repository.save(updated);
  return Optional.of(new ProjectWithRole(updated, access.projectRole()));
}
```

AFTER:
```java
public ProjectWithRole updateProject(
    UUID id, String name, String description, UUID memberId, String orgRole) {
  var project = repository.findById(id)
      .orElseThrow(() -> new ResourceNotFoundException("Project", id));
  var access = projectAccessService.checkAccess(id, memberId, orgRole);
  if (!access.canView()) {
    throw new ResourceNotFoundException("Project", id);
  }
  if (!access.canEdit()) {
    throw new ForbiddenException(
        "Cannot edit project", "You do not have permission to edit project " + id);
  }
  project.update(name, description);
  project = repository.save(project);
  return new ProjectWithRole(project, access.projectRole());
}
```

**`deleteProject` — change return type from `boolean` to `void`:**

BEFORE:
```java
public boolean deleteProject(UUID id) {
  return repository
      .findById(id)
      .map(
          project -> {
            repository.delete(project);
            return true;
          })
      .orElse(false);
}
```

AFTER:
```java
public void deleteProject(UUID id) {
  var project = repository.findById(id)
      .orElseThrow(() -> new ResourceNotFoundException("Project", id));
  repository.delete(project);
}
```

**Delete the `private forbidden()` helper method entirely.**

### 2.2 — `document/DocumentService.java`

This is the largest service change. Every method changes from `Optional<T>` to `T` (or `void`).

**`listDocuments` — `Optional<List<Document>>` → `List<Document>`:**

BEFORE:
```java
public Optional<List<Document>> listDocuments(UUID projectId, UUID memberId, String orgRole) {
  if (!projectRepository.existsById(projectId)) {
    return Optional.empty();
  }
  var access = projectAccessService.checkAccess(projectId, memberId, orgRole);
  if (!access.canView()) {
    return Optional.empty();
  }
  return Optional.of(documentRepository.findByProjectId(projectId));
}
```

AFTER:
```java
public List<Document> listDocuments(UUID projectId, UUID memberId, String orgRole) {
  if (!projectRepository.existsById(projectId)) {
    throw new ResourceNotFoundException("Project", projectId);
  }
  var access = projectAccessService.checkAccess(projectId, memberId, orgRole);
  if (!access.canView()) {
    throw new ResourceNotFoundException("Project", projectId);
  }
  return documentRepository.findByProjectId(projectId);
}
```

**`initiateUpload` — `Optional<UploadInitResult>` → `UploadInitResult`:**

BEFORE:
```java
public Optional<UploadInitResult> initiateUpload(
    UUID projectId, String fileName, String contentType, long size,
    String orgId, UUID memberId, String orgRole) {
  if (!projectRepository.existsById(projectId)) {
    return Optional.empty();
  }
  var access = projectAccessService.checkAccess(projectId, memberId, orgRole);
  if (!access.canView()) {
    return Optional.empty();
  }
  // ... document creation logic ...
  return Optional.of(new UploadInitResult(document.getId(), presigned.url(), presigned.expiresInSeconds()));
}
```

AFTER:
```java
public UploadInitResult initiateUpload(
    UUID projectId, String fileName, String contentType, long size,
    String orgId, UUID memberId, String orgRole) {
  if (!projectRepository.existsById(projectId)) {
    throw new ResourceNotFoundException("Project", projectId);
  }
  var access = projectAccessService.checkAccess(projectId, memberId, orgRole);
  if (!access.canView()) {
    throw new ResourceNotFoundException("Project", projectId);
  }
  // ... document creation logic unchanged ...
  return new UploadInitResult(document.getId(), presigned.url(), presigned.expiresInSeconds());
}
```

**`confirmUpload` — `Optional<Document>` → `Document`:**

BEFORE:
```java
public Optional<Document> confirmUpload(UUID documentId, UUID memberId, String orgRole) {
  return documentRepository
      .findById(documentId)
      .flatMap(document -> {
        var access = projectAccessService.checkAccess(document.getProjectId(), memberId, orgRole);
        if (!access.canView()) {
          return Optional.empty();
        }
        if (document.getStatus() != Document.Status.UPLOADED) {
          document.confirmUpload();
          return Optional.of(documentRepository.save(document));
        }
        return Optional.of(document);
      });
}
```

AFTER:
```java
public Document confirmUpload(UUID documentId, UUID memberId, String orgRole) {
  var document = documentRepository.findById(documentId)
      .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
  var access = projectAccessService.checkAccess(document.getProjectId(), memberId, orgRole);
  if (!access.canView()) {
    throw new ResourceNotFoundException("Document", documentId);
  }
  if (document.getStatus() != Document.Status.UPLOADED) {
    document.confirmUpload();
    return documentRepository.save(document);
  }
  return document;
}
```

**`cancelUpload` — `Optional<CancelResult>` → `void`:**

BEFORE:
```java
public Optional<CancelResult> cancelUpload(UUID documentId, UUID memberId, String orgRole) {
  return documentRepository.findById(documentId)
      .flatMap(document -> {
        var access = projectAccessService.checkAccess(document.getProjectId(), memberId, orgRole);
        if (!access.canView()) {
          return Optional.empty();
        }
        if (document.getStatus() != Document.Status.PENDING) {
          return Optional.of(CancelResult.NOT_PENDING);
        }
        documentRepository.delete(document);
        return Optional.of(CancelResult.DELETED);
      });
}
```

AFTER:
```java
public void cancelUpload(UUID documentId, UUID memberId, String orgRole) {
  var document = documentRepository.findById(documentId)
      .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
  var access = projectAccessService.checkAccess(document.getProjectId(), memberId, orgRole);
  if (!access.canView()) {
    throw new ResourceNotFoundException("Document", documentId);
  }
  if (document.getStatus() != Document.Status.PENDING) {
    throw new ResourceConflictException(
        "Document not pending", "Only pending documents can be cancelled");
  }
  documentRepository.delete(document);
}
```

**`getPresignedDownloadUrl` — `Optional<PresignDownloadResult>` → `PresignDownloadResult`:**

BEFORE:
```java
public Optional<PresignDownloadResult> getPresignedDownloadUrl(
    UUID documentId, UUID memberId, String orgRole) {
  return documentRepository.findById(documentId)
      .flatMap(document -> {
        var access = projectAccessService.checkAccess(document.getProjectId(), memberId, orgRole);
        if (!access.canView()) {
          return Optional.empty();
        }
        if (document.getStatus() != Document.Status.UPLOADED) {
          return Optional.of(PresignDownloadResult.notUploaded());
        }
        var presigned = s3Service.generateDownloadUrl(document.getS3Key());
        return Optional.of(PresignDownloadResult.success(presigned.url(), presigned.expiresInSeconds()));
      });
}
```

AFTER:
```java
public PresignDownloadResult getPresignedDownloadUrl(
    UUID documentId, UUID memberId, String orgRole) {
  var document = documentRepository.findById(documentId)
      .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
  var access = projectAccessService.checkAccess(document.getProjectId(), memberId, orgRole);
  if (!access.canView()) {
    throw new ResourceNotFoundException("Document", documentId);
  }
  if (document.getStatus() != Document.Status.UPLOADED) {
    throw new InvalidStateException(
        "Document not uploaded", "Document has not been uploaded yet");
  }
  var presigned = s3Service.generateDownloadUrl(document.getS3Key());
  return new PresignDownloadResult(presigned.url(), presigned.expiresInSeconds());
}
```

**Delete `CancelResult` enum entirely.**

**Simplify `PresignDownloadResult`:**

BEFORE:
```java
public record PresignDownloadResult(boolean uploaded, String url, long expiresInSeconds) {
  static PresignDownloadResult success(String url, long expiresInSeconds) {
    return new PresignDownloadResult(true, url, expiresInSeconds);
  }
  static PresignDownloadResult notUploaded() {
    return new PresignDownloadResult(false, null, 0);
  }
}
```

AFTER:
```java
public record PresignDownloadResult(String url, long expiresInSeconds) {}
```

**Add imports:** `import io.b2mash.b2b.b2bstrawman.exception.*`
**Remove imports:** `java.util.Optional` (if no longer used)

### 2.3 — `member/ProjectMemberService.java`

Replace `ErrorResponseException` helper methods with custom exception constructors.

**In `addMember()`:**

BEFORE:
```java
if (!memberRepository.existsById(memberId)) {
  throw notFound("Member not found", "No member found with id " + memberId);
}
if (projectMemberRepository.existsByProjectIdAndMemberId(projectId, memberId)) {
  throw conflict(
      "Member already on project",
      "Member " + memberId + " is already a member of project " + projectId);
}
```

AFTER:
```java
if (!memberRepository.existsById(memberId)) {
  throw new ResourceNotFoundException("Member", memberId);
}
if (projectMemberRepository.existsByProjectIdAndMemberId(projectId, memberId)) {
  throw new ResourceConflictException(
      "Member already on project",
      "Member " + memberId + " is already a member of project " + projectId);
}
```

**In `removeMember()`:**

BEFORE:
```java
.orElseThrow(() -> notFound(
    "Project member not found",
    "Member " + memberId + " is not a member of project " + projectId));
// ...
throw badRequest(
    "Cannot remove project lead",
    "Transfer lead role to another member before removing the current lead");
```

AFTER:
```java
.orElseThrow(() -> ResourceNotFoundException.withDetail(
    "Project member not found",
    "Member " + memberId + " is not a member of project " + projectId));
// ...
throw new InvalidStateException(
    "Cannot remove project lead",
    "Transfer lead role to another member before removing the current lead");
```

**In `transferLead()` — similarly replace all `notFound(...)` and `badRequest(...)` calls:**
- `notFound("Current lead not found", ...)` → `ResourceNotFoundException.withDetail("Current lead not found", ...)`
- `badRequest("Not the project lead", ...)` → `new InvalidStateException("Not the project lead", ...)`
- `notFound("New lead not found", ...)` → `ResourceNotFoundException.withDetail("New lead not found", ...)`

**Delete all 3 helper methods:** `private notFound()`, `private conflict()`, `private badRequest()`.

**Replace imports:** `ErrorResponseException`, `HttpStatus`, `ProblemDetail` → `io.b2mash.b2b.b2bstrawman.exception.*`

### 2.4 — `member/MemberSyncService.java`

**`deleteMember()` — change return from `boolean` to `void`:**

BEFORE:
```java
public boolean deleteMember(String clerkOrgId, String clerkUserId) {
  String schemaName = resolveSchema(clerkOrgId);
  return ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
      .call(() -> {
        boolean deleted = Boolean.TRUE.equals(
            txTemplate.execute(status -> {
              if (!memberRepository.existsByClerkUserId(clerkUserId)) {
                log.info("Member {} not found in tenant {}", clerkUserId, schemaName);
                return false;
              }
              memberRepository.deleteByClerkUserId(clerkUserId);
              log.info("Deleted member {} from tenant {}", clerkUserId, schemaName);
              return true;
            }));
        if (deleted) {
          memberFilter.evictFromCache(schemaName, clerkUserId);
        }
        return deleted;
      });
}
```

AFTER:
```java
public void deleteMember(String clerkOrgId, String clerkUserId) {
  String schemaName = resolveSchema(clerkOrgId);
  ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
      .call(() -> {
        txTemplate.executeWithoutResult(status -> {
          if (!memberRepository.existsByClerkUserId(clerkUserId)) {
            throw ResourceNotFoundException.withDetail(
                "Member not found",
                "No member found with clerkUserId: " + clerkUserId);
          }
          memberRepository.deleteByClerkUserId(clerkUserId);
          log.info("Deleted member {} from tenant {}", clerkUserId, schemaName);
        });
        memberFilter.evictFromCache(schemaName, clerkUserId);
        return null;
      });
}
```

Note: `ResourceNotFoundException` extends `RuntimeException`, so it propagates through `executeWithoutResult()` and `call()` naturally. If the member is not found, the exception propagates and we never reach `evictFromCache()` — correct behavior.

**Add import:** `import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException`

---

## Phase 3: Refactor Controllers

### 3.1 — `project/ProjectController.java`

**Full AFTER state (complete replacement):**

```java
package io.b2mash.b2b.b2bstrawman.project;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

  private final ProjectService projectService;

  public ProjectController(ProjectService projectService) {
    this.projectService = projectService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<ProjectResponse>> listProjects() {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();
    var projects =
        projectService.listProjects(memberId, orgRole).stream()
            .map(pwr -> ProjectResponse.from(pwr.project(), pwr.projectRole()))
            .toList();
    return ResponseEntity.ok(projects);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ProjectResponse> getProject(@PathVariable UUID id) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();
    var pwr = projectService.getProject(id, memberId, orgRole);
    return ResponseEntity.ok(ProjectResponse.from(pwr.project(), pwr.projectRole()));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ProjectResponse> createProject(
      @Valid @RequestBody CreateProjectRequest request) {
    UUID createdBy = RequestScopes.requireMemberId();
    var project = projectService.createProject(request.name(), request.description(), createdBy);
    return ResponseEntity.created(URI.create("/api/projects/" + project.getId()))
        .body(ProjectResponse.from(project, "lead"));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ProjectResponse> updateProject(
      @PathVariable UUID id, @Valid @RequestBody UpdateProjectRequest request) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();
    var pwr =
        projectService.updateProject(id, request.name(), request.description(), memberId, orgRole);
    return ResponseEntity.ok(ProjectResponse.from(pwr.project(), pwr.projectRole()));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ORG_OWNER')")
  public ResponseEntity<Void> deleteProject(@PathVariable UUID id) {
    projectService.deleteProject(id);
    return ResponseEntity.noContent().build();
  }

  // Records unchanged — keep CreateProjectRequest, UpdateProjectRequest, ProjectResponse as-is
  // DELETE: private notFound() helper
  // DELETE: private memberContextMissing() helper
}
```

**Key changes:**
- Remove `import org.springframework.http.ProblemDetail`
- All `ResponseEntity<?>` → specific types (`ResponseEntity<ProjectResponse>`, etc.)
- No more `if (!RequestScopes.MEMBER_ID.isBound())` guards
- No more `.orElseGet()` chains
- Delete both helper methods

### 3.2 — `document/DocumentController.java`

**Key changes per method:**

**`initiateUpload`:**
- Replace `if (orgClaim == null || orgClaim.get("id") == null) { var problem = ... }` with `throw new MissingOrganizationContextException()`
- Replace member context check with `RequestScopes.requireMemberId()` / `getOrgRole()`
- Remove `.map()` / `.orElseGet()` chain — service returns `UploadInitResult` directly

**`confirmUpload`:**
- Replace member context check → `RequestScopes.requireMemberId()` / `getOrgRole()`
- Service returns `Document` directly → `ResponseEntity.ok(DocumentResponse.from(doc))`

**`cancelUpload`:**
- Replace member context check → `RequestScopes.requireMemberId()` / `getOrgRole()`
- Service returns `void` → `ResponseEntity.noContent().build()`
- DELETE the entire switch-case block

**`listDocuments`:**
- Replace member context check → `RequestScopes.requireMemberId()` / `getOrgRole()`
- Service returns `List<Document>` directly

**`presignDownload`:**
- Replace member context check → `RequestScopes.requireMemberId()` / `getOrgRole()`
- Service returns `PresignDownloadResult` directly — no `uploaded()` check needed
- `new PresignDownloadResponse(result.url(), result.expiresInSeconds())`

**DELETE:** `projectNotFound()`, `documentNotFound()`, `memberContextMissing()` helpers

**Add import:** `io.b2mash.b2b.b2bstrawman.exception.MissingOrganizationContextException`
**Remove imports:** `ProblemDetail`, `Map` (if no longer needed for other things — check, Map is still used for org claim)

### 3.3 — `member/ProjectMemberController.java`

**Key changes:**
- Replace 3 member context checks → `RequestScopes.requireMemberId()`
- Replace `ResponseEntity<?>` → `ResponseEntity<Void>` where applicable
- DELETE `memberContextMissing()` helper
- Remove `import ProblemDetail`

### 3.4 — `member/MemberSyncController.java`

**`deleteMember` simplifies to:**

```java
@DeleteMapping("/{clerkUserId}")
public ResponseEntity<Void> deleteMember(
    @PathVariable String clerkUserId, @RequestParam String clerkOrgId) {
  log.info("Received member delete: clerkOrgId={}, clerkUserId={}", clerkOrgId, clerkUserId);
  syncService.deleteMember(clerkOrgId, clerkUserId);
  return ResponseEntity.noContent().build();
}
```

DELETE the inline `ProblemDetail` construction.
Remove `import ProblemDetail`.

---

## Phase 4: Update Unit Tests

### 4.1 — `project/ProjectServiceTest.java`

Tests that asserted `Optional.isEmpty()` now assert `assertThatThrownBy(...).isInstanceOf(ResourceNotFoundException.class)`.
Tests that asserted `Optional.isPresent()` / `.get()` now use the direct return value.

| Test method | Change |
|-------------|--------|
| `getProject_returnsProjectWhenAccessible` | Remove `.isPresent()` / `.get()` — use direct return |
| `getProject_returnsEmptyWhenNotFound` | Rename to `getProject_throwsWhenNotFound`, assert `ResourceNotFoundException` |
| `getProject_returnsEmptyWhenAccessDenied` | Rename to `getProject_throwsWhenAccessDenied`, assert `ResourceNotFoundException` |
| `updateProject_updatesWhenCanEdit` | Remove `.isPresent()` / `.get()` — use direct return |
| `updateProject_returnsEmptyWhenNotFound` | Rename to `updateProject_throwsWhenNotFound`, assert `ResourceNotFoundException` |
| `updateProject_returnsEmptyWhenAccessDenied` | Rename to `updateProject_throwsWhenAccessDenied`, assert `ResourceNotFoundException` |
| `updateProject_throwsForbiddenWhenCannotEdit` | Change `.isInstanceOf(ErrorResponseException.class)` → `.isInstanceOf(ForbiddenException.class)` |
| `deleteProject_returnsTrueWhenDeleted` | Remove boolean assertion, just verify `repository.delete()` was called |
| `deleteProject_returnsFalseWhenNotFound` | Rename to `deleteProject_throwsWhenNotFound`, assert `ResourceNotFoundException` |

**Replace imports:** `ErrorResponseException` → `ResourceNotFoundException`, `ForbiddenException`

### 4.2 — `document/DocumentServiceTest.java`

| Test method | Change |
|-------------|--------|
| `listDocuments_returnsDocumentsForExistingProject` | Remove `.isPresent()` / `.get()` — use direct `List<Document>` return |
| `listDocuments_returnsEmptyOptionalForMissingProject` | Rename `*_throwsForMissingProject`, assert `ResourceNotFoundException` |
| `listDocuments_returnsEmptyOptionalWhenAccessDenied` | Rename `*_throwsWhenAccessDenied`, assert `ResourceNotFoundException` |
| `initiateUpload_createsDocumentAndGeneratesPresignedUrl` | Remove `.isPresent()` / `.get()` — use direct return |
| `initiateUpload_returnsEmptyForMissingProject` | Rename `*_throwsForMissingProject`, assert `ResourceNotFoundException` |
| `initiateUpload_returnsEmptyWhenAccessDenied` | Rename `*_throwsWhenAccessDenied`, assert `ResourceNotFoundException` |
| `confirmUpload_transitionsPendingToUploaded` | Remove `.isPresent()` / `.get()` — use direct return |
| `confirmUpload_isIdempotentForAlreadyUploadedDocument` | Remove `.isPresent()` / `.get()` |
| `confirmUpload_returnsEmptyForUnknownDocument` | Rename `*_throwsForUnknownDocument`, assert `ResourceNotFoundException` |
| `confirmUpload_returnsEmptyWhenAccessDenied` | Rename `*_throwsWhenAccessDenied`, assert `ResourceNotFoundException` |
| `cancelUpload_deletesDocumentWhenPending` | Remove `.isPresent()` / CancelResult assertion — method is void, verify `delete()` |
| `cancelUpload_returnsNotPendingForUploadedDocument` | Rename `*_throwsConflictForUploadedDocument`, assert `ResourceConflictException` |
| `cancelUpload_returnsEmptyWhenAccessDenied` | Rename `*_throwsWhenAccessDenied`, assert `ResourceNotFoundException` |
| `cancelUpload_returnsEmptyForUnknownDocument` | Rename `*_throwsForUnknownDocument`, assert `ResourceNotFoundException` |
| `getPresignedDownloadUrl_returnsUrlForUploadedDocument` | Remove `.isPresent()` / `.get()` / `.uploaded()` — use direct return |
| `getPresignedDownloadUrl_returnsNotUploadedForPendingDocument` | Rename `*_throwsForPendingDocument`, assert `InvalidStateException` |
| `getPresignedDownloadUrl_returnsEmptyForUnknownDocument` | Rename `*_throwsForUnknownDocument`, assert `ResourceNotFoundException` |
| `getPresignedDownloadUrl_returnsEmptyWhenAccessDenied` | Rename `*_throwsWhenAccessDenied`, assert `ResourceNotFoundException` |

**Add imports:** All custom exception classes from `io.b2mash.b2b.b2bstrawman.exception`

### 4.3 — New test: `multitenancy/RequestScopesTest.java` (or add to existing if it exists)

Add 4 tests:
- `requireMemberId_throwsWhenNotBound` — assert `MemberContextNotBoundException`
- `requireMemberId_returnsValueWhenBound` — wrap in `ScopedValue.where(MEMBER_ID, uuid).run()`
- `getOrgRole_returnsNullWhenNotBound` — assert returns null
- `getOrgRole_returnsValueWhenBound` — assert returns the role string

---

## Phase 5: Verify

- [ ] `./mvnw test` — all tests pass (unit + integration)
- [ ] `./mvnw spotless:check` — formatting passes
- [ ] Integration tests should need **zero changes** — they assert HTTP status codes, not ProblemDetail body content. The same status codes are produced since:
  - `ResourceNotFoundException` → 404 (same as before)
  - `ResourceConflictException` → 409 (same as before)
  - `InvalidStateException` → 400 (same as before)
  - `ForbiddenException` → 403 (same as before)

---

## Files Summary

| File | Action |
|------|--------|
| `exception/ResourceNotFoundException.java` | **Create** |
| `exception/ResourceConflictException.java` | **Create** |
| `exception/ForbiddenException.java` | **Create** |
| `exception/InvalidStateException.java` | **Create** |
| `exception/MissingOrganizationContextException.java` | **Create** |
| `exception/GlobalExceptionHandler.java` | **Create** |
| `multitenancy/MemberContextNotBoundException.java` | **Create** |
| `multitenancy/RequestScopes.java` | **Modify** — add 2 static methods |
| `project/ProjectService.java` | **Modify** — throw exceptions, remove helper |
| `document/DocumentService.java` | **Modify** — throw exceptions, simplify records, delete enum |
| `member/ProjectMemberService.java` | **Modify** — use custom exceptions, remove 3 helpers |
| `member/MemberSyncService.java` | **Modify** — throw instead of return boolean |
| `project/ProjectController.java` | **Modify** — remove boilerplate, simplify |
| `document/DocumentController.java` | **Modify** — remove boilerplate, simplify |
| `member/ProjectMemberController.java` | **Modify** — remove boilerplate |
| `member/MemberSyncController.java` | **Modify** — remove inline ProblemDetail |
| `project/ProjectServiceTest.java` | **Modify** — assert exceptions |
| `document/DocumentServiceTest.java` | **Modify** — assert exceptions |
| `multitenancy/RequestScopesTest.java` | **Create or modify** — test new methods |
| `provisioning/ProvisioningController.java` | **No change** |
| All integration tests | **No change expected** |

## Implementation Order

Execute phases sequentially: Phase 1 → 2 → 3 → 4 → 5. Within each phase, the order of files doesn't matter much, but services (Phase 2) must complete before controllers (Phase 3) since controller code depends on service return types.

After all code changes, run `./mvnw spotless:apply` to fix formatting, then `./mvnw test` to verify.
