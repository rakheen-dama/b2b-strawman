# Backend Contribution Checklist

Quick-reference checklist for backend PRs. Full conventions in `CLAUDE.md`.

## Controller Discipline
- [ ] Controller is a pure HTTP adapter — one service call per endpoint
- [ ] No repository injections in controller
- [ ] No `if/else`, `switch`, or business logic in controller
- [ ] No private helper methods in controller
- [ ] Returns `ResponseEntity` wrapping service result
- [ ] Uses `@RequiresCapability` for access control

## Error Handling
- [ ] Services throw semantic exceptions from `exception/` package
- [ ] No `IllegalArgumentException` thrown from controller-called code paths
- [ ] New exceptions extend `ErrorResponseException` and use `ProblemDetailFactory`
- [ ] No `ProblemDetail` constructed directly in controllers or services

## Testing
- [ ] Uses shared test utilities (`TestMemberHelper`, `TestJwtFactory`, `TestEntityHelper`)
- [ ] No private helper methods duplicating `testutil/` functionality
- [ ] Error paths assert ProblemDetail fields (status, title, detail)
- [ ] Uses `TestCustomerFactory.createActiveCustomer()` for customers needing ACTIVE status

## Architecture
- [ ] ArchUnit tests pass (`./mvnw test -pl backend -Dtest=LayerDependencyRulesTest`)
- [ ] No `@Autowired` field injection — use constructor injection
- [ ] Feature-organized packages (entity, repo, service, controller in same package)
