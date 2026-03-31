# The Reviewer is the Product: Why AI Code Review Matters More Than AI Code Writing

*Part 5 of "One Dev, 843 PRs" — a series about building a production B2B SaaS platform with AI coding agents.*

---

If I could only keep one agent in my pipeline, it wouldn't be the builder. It would be the reviewer.

This sounds backwards. The builder produces the code — the thing that ships. The reviewer just reads it and complains. Why would the complainer be more valuable than the creator?

Because code that ships with bugs is worse than code that doesn't ship yet.

Over 843 PRs, my Opus reviewer caught issues in roughly 1 out of every 5 reviews. Not nitpicks — real issues that would've affected users, leaked data, or violated the conventions that keep a 240K-line codebase navigable. Let me show you what I mean.

## The Bugs That Almost Shipped

### 1. The Backend URL Leak

Phase 12, Slice 95B. The builder implemented a document download endpoint. The generated PDF gets uploaded to S3, and the controller returns a download URL.

The builder's implementation:

```java
@GetMapping("/{id}/download")
public ResponseEntity<Resource> download(@PathVariable UUID id) {
    var doc = generatedDocumentService.findById(id);
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=" + doc.getFileName())
        .header("Location", doc.getStorageUrl())
        .body(/* ... */);
}
```

The reviewer flagged: **`doc.getStorageUrl()` contains the internal S3 URL with the bucket name, region, and access key embedded in the presigned URL.** This endpoint is called from the frontend. The full S3 infrastructure path would be visible in the browser's network tab.

The fix: return only the presigned URL (time-limited, no infrastructure details) or proxy the download through the backend.

This isn't a subtle architectural concern. It's a data leak. And the builder didn't catch it because the code *works* — the download succeeds. The reviewer caught it because it was asking a different question: not "does this work?" but "what does this expose?"

### 2. Server-Side Template Injection

Phase 12, Slice 94A. The document template system allows tenants to create Handlebars templates that get rendered with context data (customer name, project details, invoice line items). The builder implemented the rendering pipeline correctly — Handlebars compiles the template, injects context, produces HTML.

The reviewer flagged: **User-supplied template content is being compiled and executed server-side without sanitization.** A malicious tenant could craft a template with Handlebars helpers that access Java objects, read environment variables, or execute arbitrary code.

The fix: sandbox the Handlebars execution environment. Whitelist allowed helpers. Reject templates containing dangerous patterns. Add a test that attempts a known injection payload and verifies it's blocked.

This is the kind of issue that doesn't show up in functional testing. The template renders correctly with normal input. You only find it by thinking adversarially — and the reviewer does that by default.

### 3. Tenant Isolation Gap

Phase 5, Slice 46A. The "My Work" feature shows a developer their tasks and time entries across all projects. The builder wrote a JPQL query:

```java
@Query("SELECT t FROM Task t WHERE t.assignee.id = :memberId")
List<Task> findByAssignee(@Param("memberId") UUID memberId);
```

The reviewer flagged: **This query relies on the `WHERE memberId` clause for access control, but the schema isolation already scopes this to the correct tenant. However, the query doesn't verify that the member actually belongs to the current tenant.** If a member UUID from Tenant A is somehow known to Tenant B (through a data leak or API enumeration), this query would return Tenant A's tasks to someone querying from Tenant B's schema.

Wait — actually, the reviewer was wrong here. The query runs inside the tenant's schema, which is set by `SET search_path TO tenant_abc123`. The `tasks` table in Tenant B's schema doesn't contain Tenant A's data. The schema boundary *is* the isolation.

This is an important example: **the reviewer produces false positives.** About 1 in 4 findings are technically incorrect or don't apply given the architecture. This is why the fixer agent (also Opus) evaluates each finding critically instead of blindly applying fixes.

The false positive rate is the cost of thoroughness. I'd rather review 4 findings and dismiss 1 than miss the 3 real issues.

### 4. Frontend/Backend Permission Gap

Phase 5, Slice 48A. The backend grants project leads the ability to edit and delete time entries for their projects. The frontend only showed edit/delete buttons for org admins and owners.

The reviewer caught the gap: **Backend allows project leads to manage time entries, but the frontend `TimeEntryList` component only checks `role === 'admin' || role === 'owner'`. Project leads see time entries but can't edit them — inconsistent with the API contract.**

The fix: thread a `canManage` prop from the server component (which has access to the member's role and project lead status) down to the `TimeEntryList` client component.

This is a class of bug that's invisible in isolation. The backend tests pass (project leads can edit via API). The frontend tests pass (the component renders correctly for admins). The gap only appears when you compare the two — which is exactly what a cross-cutting reviewer does.

## Why the Builder Misses These

It's not that the builder is bad. It's that the builder has a different job.

The builder's context is: "Here's a brief. Implement these files. Follow these patterns. Run the build." Its success metric is: *does it compile and pass tests?*

The reviewer's context is: "Here's a diff. Here are the conventions. Here's what this system is supposed to protect against." Its success metric is: *should this merge?*

These are fundamentally different questions. "Does it work?" is about functionality. "Should it merge?" is about safety, consistency, and maintainability. You can answer "yes" to the first and "no" to the second.

Some concrete ways the reviewer thinks differently:

**Adversarial thinking.** The builder assumes inputs are well-formed (because in tests, they are). The reviewer asks: "What if the input is malicious? What if this UUID belongs to another tenant? What if this template contains JavaScript?"

**Cross-cutting analysis.** The builder focuses on the files it's creating. The reviewer reads the diff in context — does this controller match the pattern of other controllers? Does this migration conflict with the naming scheme? Does this frontend component match the backend's permission model?

**Convention enforcement.** The builder follows conventions from the brief (which includes them). But it occasionally drifts — a method named `getRate` instead of `resolveRate`, a missing `@RequiresCapability`, a test using `new Customer()` instead of `TestCustomerFactory.createActiveCustomer()`. The reviewer catches drift.

**Absence detection.** The hardest bugs to find are things that *aren't there*. Missing validation. Missing error handling for a specific state. Missing test for an edge case. The builder produces what's specified. The reviewer notices what's missing.

## The Review Process

The reviewer gets exactly two inputs:

1. **The PR diff** (typically 200-800 lines)
2. **The CLAUDE.md convention files** (backend and/or frontend, depending on the slice)

It does NOT get the epic brief. This is deliberate. If the reviewer reads the brief, it evaluates the implementation against the *spec*. I want it to evaluate the implementation against *the codebase conventions and security model*. Different lens, different findings.

The reviewer's output is structured:

```markdown
## Review: PR #141 — BillingRate Service + Rate Resolution

### CRITICAL
(None found)

### HIGH
1. **RateResolutionService.resolve() returns null for missing rates instead of
   throwing RateNotFoundException.**
   File: RateResolutionService.java:42
   Impact: Callers must null-check, violating the convention of using
   semantic exceptions. Will cause NPE in TimeEntryService.
   Fix: Throw RateNotFoundException with customer/project context.

### MEDIUM
2. **BillingRateController.update() doesn't validate that the rate belongs
   to the current request context (customer/project).**
   File: BillingRateController.java:58
   Impact: Low — schema isolation prevents cross-tenant access. But the
   endpoint allows updating any rate within the tenant, not just the one
   scoped to the path parameters.
   Fix: Add findById + verify parent matches path.

### LOW
(None)

### VERDICT: REQUEST_CHANGES (1 high, 1 medium)
```

The fixer agent reads these findings, evaluates each one (is it real? does the fix make sense?), and applies targeted patches. After fixing, the PR is re-reviewed.

## The Cost of Not Reviewing

Let me quantify what would've happened without the reviewer.

Across 843 PRs, the reviewer flagged issues in roughly 170 reviews (~20%). Of those:
- **~15 were security issues** (data leaks, injection risks, missing auth checks)
- **~40 were correctness bugs** (null returns, missing validation, wrong error codes)
- **~50 were convention violations** (naming, patterns, missing annotations)
- **~25 were false positives** (valid concern but not applicable given the architecture)
- **~40 were minor improvements** (better error messages, missing test cases, documentation gaps)

The 15 security issues alone justify the entire review pipeline. One data leak in a multi-tenant SaaS product and you lose every customer's trust.

But the convention violations matter too. Not individually — one misnamed method doesn't matter. In aggregate — 50 convention violations across 843 PRs would've created a codebase where every file is slightly different from the others. New agents (or new humans) would have inconsistent reference patterns to learn from, and the inconsistency would compound.

## Building Your Own Review Pipeline

You don't need a full scout-builder-reviewer pipeline to get value from AI code review. Here's the minimum viable version:

**1. Write your conventions down.** If it's not in a file, the reviewer can't check it. Start with 10-20 rules that matter most.

**2. Review every PR diff.** Even if you wrote the code yourself. Even if it's "just a small change." The review should take 30 seconds for trivial PRs and 5 minutes for complex ones.

**3. Use the most capable model for review.** This is the one place where model quality directly translates to value. A mid-tier model will miss the subtle issues — the tenant isolation gaps, the injection risks, the permission parity problems.

**4. Tolerate false positives.** A 20-25% false positive rate is acceptable. A fixer agent can evaluate findings and skip the wrong ones. The cost of a false positive (wasted 2 minutes) is nothing compared to the cost of a missed security issue.

**5. Don't let the reviewer see the spec.** Give it the diff and the conventions. If it can only approve code that matches conventions (not code that matches the spec), it catches a different class of issues.

The builder produces what you asked for. The reviewer catches what you forgot to ask for. That's why it's the product.

---

*Next in this series: [Skills, Hooks, and Memory: Teaching Your AI Agent to Get Better Over Time](06-skills-hooks-and-memory.md)*

*Previous: [Phase Execution](04-phase-execution.md)*
