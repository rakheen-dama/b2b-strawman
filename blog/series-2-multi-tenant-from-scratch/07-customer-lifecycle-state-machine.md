# The Customer Lifecycle State Machine: PROSPECT → ONBOARDING → ACTIVE

*Part 7 of "Multi-Tenant from Scratch" — a series on building production multi-tenant architecture with PostgreSQL, Spring Boot 4, and Hibernate 7.*

---

In most SaaS tutorials, a customer is a row in a table. You create it, maybe update it, maybe delete it. That's the lifecycle.

In professional services — accounting firms, law firms, consulting practices — a customer is a commitment. Before you can bill them, you need their tax details. Before you can manage their compliance, you need their FICA verification. Before you can create projects for them, they need to be "active" — meaning onboarding is complete, all regulatory checkboxes are ticked, and someone has approved them.

Modeling this in code means treating customer status not as a field but as a *state machine* with enforced transitions and gated actions.

## The State Machine

```
PROSPECT ──→ ONBOARDING ──→ ACTIVE ──→ DORMANT
                  │              │          │
                  │              │          │
                  └──→ OFFBOARDING ←───────┘
                           │
                           ▼
                       OFFBOARDED ──→ ANONYMIZED
                           │
                           └──→ ACTIVE (reactivation)
```

Seven states, each with specific allowed transitions:

```java
public enum LifecycleStatus {
    PROSPECT, ONBOARDING, ACTIVE, DORMANT, OFFBOARDING, OFFBOARDED, ANONYMIZED;

    private static final Map<LifecycleStatus, Set<LifecycleStatus>> ALLOWED_TRANSITIONS =
        Map.of(
            PROSPECT,    Set.of(ONBOARDING),
            ONBOARDING,  Set.of(ACTIVE, OFFBOARDING),
            ACTIVE,      Set.of(DORMANT, OFFBOARDING),
            DORMANT,     Set.of(ACTIVE, OFFBOARDING),
            OFFBOARDING, Set.of(OFFBOARDED),
            OFFBOARDED,  Set.of(ACTIVE, ANONYMIZED),
            ANONYMIZED,  Set.of()   // terminal state
        );

    public boolean canTransitionTo(LifecycleStatus target) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }
}
```

The transition map is the source of truth. No code outside this enum decides which transitions are valid.

## Enforced Transitions

The `Customer` entity validates transitions:

```java
public void transitionLifecycleStatus(LifecycleStatus target, UUID actorId) {
    if (!this.lifecycleStatus.canTransitionTo(target)) {
        throw new InvalidStateException(
            "Invalid lifecycle transition",
            "Cannot transition from " + this.lifecycleStatus + " to " + target);
    }
    this.lifecycleStatus = target;
    this.lifecycleStatusChangedAt = Instant.now();
    this.lifecycleStatusChangedBy = actorId;
    if (target == LifecycleStatus.OFFBOARDED) {
        this.offboardedAt = Instant.now();
    }
    this.updatedAt = Instant.now();
}
```

`InvalidStateException` extends the project's semantic exception hierarchy and renders as a 409 Conflict with an RFC 9457 ProblemDetail body. The frontend shows a meaningful error: "This customer is a prospect and cannot be directly activated. Start the onboarding process first."

## Action Gating

The state machine isn't just about transitions — it's about what you can *do* with a customer in each state. A PROSPECT shouldn't have projects. An OFFBOARDED customer shouldn't receive new invoices.

```java
@Component
public class CustomerLifecycleGuard {

    public void requireActionPermitted(Customer customer, LifecycleAction action) {
        var status = customer.getLifecycleStatus();

        switch (action) {
            case CREATE_PROJECT, CREATE_TASK, CREATE_TIME_ENTRY -> {
                if (status == LifecycleStatus.PROSPECT
                        || status == LifecycleStatus.OFFBOARDING
                        || status == LifecycleStatus.OFFBOARDED) {
                    throwBlocked(action, status);
                }
            }
            case CREATE_INVOICE -> {
                if (status != LifecycleStatus.ACTIVE
                        && status != LifecycleStatus.DORMANT) {
                    throwBlocked(action, status);
                }
            }
            case CREATE_DOCUMENT -> {
                if (status == LifecycleStatus.OFFBOARDED) {
                    throwBlocked(action, status);
                }
            }
        }
    }

    private void throwBlocked(LifecycleAction action, LifecycleStatus status) {
        throw new LifecycleActionBlockedException(action, status);
    }
}
```

Services call the guard before performing gated operations:

```java
@Service
public class ProjectService {

    @Transactional
    public Project create(UUID customerId, CreateProjectRequest request) {
        var customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

        // Guard: can't create projects for PROSPECT customers
        lifecycleGuard.requireActionPermitted(customer, LifecycleAction.CREATE_PROJECT);

        var project = new Project(request.name(), request.description(),
            RequestScopes.requireMemberId());
        project.setCustomerId(customerId);
        return projectRepository.save(project);
    }
}
```

This is enforced in the backend, not the frontend. The frontend can hide buttons for invalid states (better UX), but the guard catches any request that bypasses the UI — API calls, automated workflows, imported data.

## The Onboarding Engine: Checklists

ONBOARDING isn't just a status — it's a process. When a customer transitions from PROSPECT to ONBOARDING, the system creates a checklist instance from a template:

```
Checklist Template: "FICA/KYC Compliance" (accounting-za vertical)
├── Verify identity document
├── Verify proof of address (not older than 3 months)
├── Obtain tax clearance certificate
├── Verify company registration (CIPC)
├── Confirm VAT registration status
├── Collect banking details
└── Complete beneficial ownership declaration
```

Each item can be marked complete independently. When all items are complete, the customer auto-transitions from ONBOARDING to ACTIVE:

```java
@Service
public class ChecklistService {

    @Transactional
    public void completeItem(UUID checklistId, UUID itemId, UUID actorId) {
        var checklist = checklistRepository.findById(checklistId)
            .orElseThrow(() -> new ResourceNotFoundException("Checklist", checklistId));
        var item = checklist.findItem(itemId);

        item.markCompleted(actorId);

        // Check if all items are now complete
        if (checklist.allItemsCompleted()) {
            var customer = customerRepository.findById(checklist.getCustomerId())
                .orElseThrow();

            if (customer.getLifecycleStatus() == LifecycleStatus.ONBOARDING) {
                customer.transitionLifecycleStatus(LifecycleStatus.ACTIVE, actorId);
                customerRepository.save(customer);

                // Fire domain event for notifications, activity feed, audit
                eventPublisher.publishEvent(new CustomerActivatedEvent(customer, actorId));
            }
        }
    }
}
```

The auto-transition is deliberate: when all compliance requirements are met, the customer becomes active immediately. No manual approval step. For accounting firms dealing with FICA compliance, this reduces friction — the firm completes the checklist items as they receive documents from the client, and the system handles the rest.

## Vertical-Specific Checklists

Different industries have different compliance requirements. The checklist templates are seeded per vertical during tenant provisioning:

```
Generic pack:
  └── "Client Onboarding" (basic: contact info, service agreement)

Accounting-ZA pack:
  ├── "FICA/KYC Compliance" (7 items, entity-type-specific hints)
  ├── "Tax Registration Verification" (VAT, income tax, PAYE)
  └── "Annual Compliance Review" (recurring)

Law-ZA pack:
  ├── "Client Intake & Conflict Check" (6 items)
  ├── "Litigation Readiness" (discovery, court dates, statute checks)
  └── "LSSA Compliance" (trust account, billing standards)
```

The entity-type hint is important for accounting: a sole proprietor's FICA checklist differs from a company's. The checklist template includes conditional items:

```
If entity_type = "Company":
  ├── Obtain CIPC registration
  ├── Verify directors' IDs
  └── Complete beneficial ownership declaration
If entity_type = "Individual":
  ├── Verify SA ID document
  └── Obtain proof of residential address
```

## Why Not a Workflow Engine?

You might wonder why I didn't use a workflow engine (Camunda, Temporal, etc.) for the lifecycle.

The answer: the lifecycle is simple enough that a state machine enum + guard component handles it perfectly. A workflow engine would add:
- A new infrastructure dependency
- A DSL to learn and maintain
- State stored outside the database
- Operational complexity (worker processes, retry queues)

For 7 states and 8 transitions, that's massive overkill. The enum + guard pattern gives me:
- Compile-time visibility of all states and transitions
- Database as single source of truth
- Standard JPA transactions
- Testable with plain JUnit

If the lifecycle grew to 20+ states with complex parallel branches and timer-based transitions, I'd reconsider. For professional services customer management, the state machine is the right abstraction level.

## Testing the State Machine

Integration tests verify both valid transitions and blocked actions:

```java
@Test
void prospectCannotCreateProject() {
    ScopedValue.where(RequestScopes.TENANT_ID, testSchema).run(() -> {
        var customer = TestCustomerFactory.createProspect(customerRepository);

        assertThatThrownBy(() ->
            projectService.create(customer.getId(),
                new CreateProjectRequest("Test", "Desc")))
            .isInstanceOf(LifecycleActionBlockedException.class)
            .hasMessageContaining("PROSPECT");
    });
}

@Test
void activeCustomerCanCreateProject() {
    ScopedValue.where(RequestScopes.TENANT_ID, testSchema).run(() -> {
        var customer = TestCustomerFactory.createActiveCustomer(customerRepository);

        var project = projectService.create(customer.getId(),
            new CreateProjectRequest("Test", "Desc"));

        assertThat(project.getCustomerId()).isEqualTo(customer.getId());
    });
}
```

`TestCustomerFactory.createActiveCustomer()` is a shared test utility that creates a customer already in ACTIVE state. Tests that need ACTIVE customers use this helper — they don't manually transition through PROSPECT → ONBOARDING → ACTIVE (which would require completing all checklist items). This keeps tests focused on the behavior being tested, not on lifecycle ceremony.

For tests that verify the lifecycle *itself*, we transition explicitly:

```java
@Test
void completingAllChecklistItemsActivatesCustomer() {
    ScopedValue.where(RequestScopes.TENANT_ID, testSchema).run(() -> {
        var customer = TestCustomerFactory.createProspect(customerRepository);
        customer.transitionLifecycleStatus(LifecycleStatus.ONBOARDING, actorId);
        customerRepository.save(customer);

        // Create checklist and complete all items
        TestChecklistHelper.completeChecklistItems(mockMvc, customer.getId());

        // Verify auto-transition
        var refreshed = customerRepository.findById(customer.getId()).orElseThrow();
        assertThat(refreshed.getLifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);
    });
}
```

## The Design Principle

The customer lifecycle state machine embodies a broader principle: **business rules belong in the domain model, not in the UI or the API layer.**

The frontend can show/hide buttons based on status — that's UX optimization. But the enforcement is in `CustomerLifecycleGuard`, which runs regardless of how the request arrived. An API call from a mobile app, an automation trigger, a data import script — all go through the guard.

This means I can change the frontend completely — swap from Next.js to a mobile app — and the business rules remain intact. The domain model is the source of truth. The UI is just a view.

---

*Next in this series: [Neon Serverless Postgres for Multi-Tenant SaaS: What Works and What Doesn't](08-neon-serverless-postgres.md)*

*Previous: [Keycloak as Your Identity Layer](06-keycloak-identity-layer.md)*
