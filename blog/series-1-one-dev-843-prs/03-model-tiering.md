# Model Tiering: When to Use the Expensive Model and When Not To

*Part 3 of "One Dev, 843 PRs" — a series about building a production B2B SaaS platform with AI coding agents.*

---

Sometime around Week 3, I realized I was spending too much money.

Every agent in my pipeline — scouts, builders, reviewers, orchestrators — was running on Opus, Anthropic's most capable (and most expensive) model. It felt like the safe choice. More capable model = better code, right?

Wrong. Or at least, wrong in a way that matters when you're running 594 agent sessions across 8 weeks.

The epiphany came from a simple question: *what decisions does each agent actually need to make?*

## The Decision Spectrum

Not all tasks require the same kind of intelligence. Here's what each agent in my pipeline actually does:

**Scout** — Reads files. Extracts relevant sections. Formats them into a brief. The "decisions" are: which files to include, which sections are relevant, how to organize the brief. These are *retrieval and formatting* decisions. Important, but bounded.

**Builder** — Reads a brief. Follows the patterns. Writes code that looks like the reference examples. Runs the build. Creates a PR. The "decisions" are: how to adapt reference patterns to the new feature, how to handle edge cases not covered in the brief, how to name things. These are *pattern-following* decisions.

**Reviewer** — Reads a diff. Evaluates whether the code follows conventions. Spots security issues, isolation leaks, missing tests. Decides whether to approve or request changes. These are *judgment* decisions.

**Fixer** — Reads review findings. Decides which are real issues and which are false positives. Applies targeted fixes without breaking other things. These are also *judgment* decisions.

See the split? Scouts and builders work within constraints defined by briefs and conventions. They're executing, not deciding. Reviewers and fixers are making qualitative judgments about code that might be subtly wrong.

## The Tiering

Here's what I settled on:

| Agent | Model | Why |
|-------|-------|-----|
| Scout | Sonnet | Retrieval + formatting. Constrained by conventions. |
| Builder | Sonnet | Pattern-following. Constrained by the brief. |
| Reviewer | **Opus** | Judgment. Must spot what *isn't* there. |
| Fixer | **Opus** | Judgment. Must evaluate findings critically. |
| Orchestrator | **Opus** | Coordination. Merge decisions. Status tracking. |

The reasoning is simple: **if the task is constrained by a document (brief, convention file, task spec), a mid-tier model is sufficient. If the task requires evaluating something that might be subtly wrong, pay for the best model.**

## What Sonnet Builders Get Right

Let me be concrete. Here's a Sonnet builder implementing a new service method:

```java
@Transactional
public Invoice create(UUID customerId, CreateInvoiceRequest request) {
    var customer = customerRepository.findById(customerId)
        .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

    var invoice = new Invoice();
    invoice.setCustomer(customer);
    invoice.setInvoiceNumber(invoiceNumberService.nextNumber());
    invoice.setStatus(InvoiceStatus.DRAFT);
    invoice.setIssueDate(request.issueDate());
    invoice.setDueDate(request.dueDate());
    invoice.setNotes(request.notes());

    var saved = invoiceRepository.save(invoice);

    auditService.log(AuditEventBuilder.create("INVOICE_CREATED")
        .entity("Invoice", saved.getId())
        .detail("customer_id", customerId)
        .detail("invoice_number", saved.getInvoiceNumber())
        .build());

    return saved;
}
```

This is fine. It follows the exact pattern from the brief: lookup related entity, create instance, set fields, save, audit. The same structure as `BudgetService.create()`, `TimeEntryService.create()`, `CustomerService.create()`. Sonnet can do this all day.

It also gets the controller pattern right:

```java
@PostMapping
@RequiresCapability("MANAGE_INVOICES")
public ResponseEntity<InvoiceResponse> create(
        @PathVariable UUID customerId,
        @Valid @RequestBody CreateInvoiceRequest request) {
    var invoice = invoiceService.create(customerId, request);
    return ResponseEntity.created(
        URI.create("/api/invoices/" + invoice.getId()))
        .body(InvoiceResponse.from(invoice));
}
```

One service call. `@RequiresCapability`. No logic. Exactly what the conventions require. Sonnet follows instructions. That's the point.

## What Sonnet Builders Get Wrong

But — and this is important — Sonnet builders do produce more issues that the reviewer catches. Some examples:

**Missing validation edge cases.** The brief says "validate that due date is after issue date." Sonnet implements the check. But it doesn't consider: what if issue date is null? What if both are the same day? An Opus builder might think through these edges unprompted. Sonnet needs them spelled out.

**Subtler security considerations.** When building the document template system, the Sonnet builder implemented Handlebars rendering without considering that user-supplied templates could include server-side template injection payloads. The reviewer (Opus) caught this. Would an Opus builder have caught it during implementation? Maybe. Maybe not. But the reviewer definitely did.

**Financial domain logic.** When I tried Sonnet builders on the invoicing phase (Phase 10), the quality dropped noticeably. Invoice numbering with per-tenant atomic counters, double-billing prevention, line item rounding — these required understanding the *intent* behind the spec, not just following patterns. I switched Phase 10 builders back to Opus.

The lesson: **Sonnet works for features that look like previous features. It struggles with novel domain logic where the brief can't capture every edge case.**

## The Economics

I don't have exact dollar figures to share (they depend on your usage pattern, API plan, and how chatty your agents are), but I can share the *relative* impact.

Before tiering (all Opus):
- Scout: 100% cost per session
- Builder: 100% cost per session
- Reviewer: 100% cost per session

After tiering (Sonnet scouts + builders):
- Scout: ~30% of original cost
- Builder: ~30% of original cost
- Reviewer: 100% (Opus, unchanged)

For a typical phase with 10-15 slices, each having a scout + builder + reviewer, that's roughly a **40-50% reduction in total API cost**. And the code quality, as measured by reviewer findings, stayed about the same — because the reviewer (the quality gate) didn't change.

## The Exception That Proves the Rule

I mentioned that Sonnet builders struggled with the invoicing phase. Here's why that matters:

Invoicing has domain rules that don't map to existing patterns:
- Invoice numbers must be sequential *per tenant* with no gaps (legal requirement)
- An `InvoiceCounter` entity tracks the next number per tenant using `SELECT ... FOR UPDATE` locking
- Time entries marked as billed can never be billed again (double-billing prevention)
- Line item amounts must round correctly and sum to the invoice total (penny-accurate)

The brief described all of this. But "describe" isn't the same as "convey the intent." The Sonnet builder implemented the counter correctly but missed the `FOR UPDATE` lock. It implemented the line items but used `float` instead of `BigDecimal`. These are the kind of errors that come from pattern-matching without understanding *why* the pattern exists.

For phases with novel financial or compliance logic, I now use Opus for builders too. The cost increase is worth the reduced review churn.

**My revert path**: changing `model: "sonnet"` to `model: "opus"` in the skill file. One line. If quality drops, I swap back. If it holds, I keep the savings.

## A Framework for Deciding

When deciding which model to use for a task, I ask three questions:

**1. Is the task constrained by a document?**

If yes → Sonnet is probably fine. The document provides guardrails. Scouts have briefs. Builders have briefs. Even if the model occasionally drifts, the guardrails pull it back.

If no → Opus. The task requires judgment that can't be constrained in advance.

**2. Does the task involve novel domain logic?**

"Novel" means: not similar to anything the codebase already does. A new CRUD entity? Not novel. An invoice numbering service with database-level locking? Novel.

Novel → Opus. The model needs to reason about *why*, not just *what*.

**3. Is the output reviewed before merging?**

If yes → more tolerance for Sonnet. The reviewer catches mistakes.

If no → Opus. When there's no safety net, you want the best model.

Most tasks in my pipeline hit (constrained: yes, novel: no, reviewed: yes) — that's the Sonnet sweet spot.

## Beyond My Pipeline

You don't need a scout-builder pipeline to apply model tiering. The principle works anywhere:

- **Code generation from specs?** Mid-tier model for generation, top-tier for review.
- **Refactoring?** Top-tier. Refactoring requires understanding what *shouldn't* change.
- **Test writing?** Mid-tier if patterns exist, top-tier for complex integration tests.
- **Documentation?** Mid-tier. Docs follow templates.
- **Architecture decisions?** Top-tier. Always.

The mistake I see people make: using the most expensive model for everything because they're afraid of quality drops. The reality is that most tasks don't need the most capable model. They need the most capable *review*.

The model that writes the code is less important than the model that reviews it. Invest accordingly.

---

*Next in this series: [Phase Execution: From Architecture Doc to 17 Merged PRs Without Touching the Keyboard](04-phase-execution.md)*

*Previous: [The Scout-Builder Pattern](02-scout-builder-pattern.md)*
