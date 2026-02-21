# ADR-084: Parameter Schema Design

**Status**: Accepted

**Context**:

Each `ReportDefinition` stores a `parameter_schema` JSONB field that describes what parameters the report accepts (date ranges, grouping options, entity filters). This schema serves two purposes: (1) the frontend reads it to dynamically render the parameter form (date pickers, dropdowns, comboboxes), and (2) the backend validates submitted parameters before executing the query.

The design must balance flexibility (different reports need different parameter types) with simplicity (the frontend must be able to render forms without complex schema interpretation logic). The three initial reports need: `date` (date picker), `enum` (dropdown with fixed options), and `uuid` with entity type (searchable combobox for projects/members/customers).

**Options Considered**:

1. **JSON Schema standard (draft-07)** -- Use the JSON Schema specification to define parameter validation rules. The frontend uses a JSON Schema form renderer library.
   - Pros: Industry standard. Rich validation vocabulary (min/max, patterns, conditionals). Large ecosystem of validators and form renderers. Extensible without custom code.
   - Cons: Heavyweight for the use case — JSON Schema's vocabulary is designed for arbitrary JSON validation, not for rendering domain-specific form fields (date pickers, entity search comboboxes). No standard way to express "this is a project UUID that should render as a project picker" — requires custom `x-` extensions, defeating the standardization benefit. JSON Schema form renderers (react-jsonschema-form, etc.) add significant bundle size and styling complexity with Shadcn. The schema syntax is verbose for simple parameter lists.

2. **Enum-based parameter types in Java** -- Define parameter types as a Java enum (`DATE`, `ENUM`, `UUID`). Store parameters as a Java list in the entity, serialized to JSONB. Validation logic lives in Java switch statements.
   - Pros: Type-safe in Java. Compile-time validation of parameter types. Clear, simple.
   - Cons: Adding a new parameter type requires a code change (new enum value + switch case). The schema is tied to Java — cannot be extended via seed data alone. Hibernate JSONB mapping of complex Java objects adds complexity.

3. **Custom JSON schema with typed parameter definitions (chosen)** -- A simple, purpose-built JSON structure: an array of parameter objects, each with `name`, `type`, `label`, `required`, and type-specific fields (`options` for enums, `default` for defaults, `entityType` for UUIDs).
   - Pros: Minimal and readable. Directly maps to UI rendering — each parameter type corresponds to exactly one UI component. Easy to validate server-side (small type set). No external library needed for form rendering. Extensible via new type strings without schema format changes. The frontend parameter form renderer is ~100 lines of TypeScript.
   - Cons: Not a recognized standard — custom format. Must document the type system. Adding a fundamentally new parameter paradigm (e.g., multi-select, nested parameters) requires updating both backend validation and frontend rendering.

**Decision**: Option 3 -- Custom JSON schema with typed parameter definitions.

**Rationale**:

The parameter schema exists at the intersection of two concerns: server-side validation and client-side form rendering. JSON Schema (Option 1) optimizes for validation but is a poor fit for rendering — it describes "what is valid JSON" but not "what UI component to use". The custom schema optimizes for both: each parameter type (`date`, `enum`, `uuid`) maps directly to a Shadcn component (`DatePicker`, `Select`, entity combobox), and the validation rules are implicit in the type (dates must be valid ISO dates, enums must be from the options list, UUIDs must be valid UUIDs of the specified entity type).

The three parameter types needed by the initial reports (`date`, `enum`, `uuid`) cover the vast majority of professional services reporting parameters. Future reports might add `boolean` (checkbox) or `decimal` (number input), which are trivial additions — a new case in the frontend switch and a new validation check in the backend. The schema format itself (`{parameters: [{name, type, label, ...}]}`) is stable regardless of how many types exist.

The custom schema also aligns with how seed data works in the codebase. Report definitions (including their parameter schemas) are seeded as JSON structures in Java constants. A simple, flat parameter array is easier to maintain in code than a verbose JSON Schema document. The parameter schema for the Timesheet Report is 12 lines of JSON — the equivalent JSON Schema would be 50+ lines.

**Backend impact**: `ReportDefinition.parameterSchema` is a `Map<String, Object>` mapped to JSONB via `@JdbcTypeCode(SqlTypes.JSON)`. `ReportExecutionService` validates submitted parameters against the schema before dispatching: iterates `parameters` list, checks required fields are present, validates enum values against `options`, and parses dates/UUIDs. Validation errors produce a 400 ProblemDetail with field-level error details. Frontend `ReportParameterForm` component switches on `parameter.type` to render the appropriate Shadcn input component.

**Consequences**:

- Parameter schema is a simple JSON array stored in JSONB. No JSON Schema library dependency.
- Frontend `ReportParameterForm` component switches on `parameter.type` to render the appropriate Shadcn component.
- Backend `ReportExecutionService` validates parameters against the schema before dispatching to the query: required fields present, types parseable, enum values in the options list.
- Supported types in this phase: `date`, `enum`, `uuid`. Adding `boolean`, `decimal`, or `string` types is a minimal change (one switch case each in frontend + backend).
- The `entityType` field on `uuid` parameters (`project`, `member`, `customer`) tells the frontend which search/combobox component to render. This is a domain concept that JSON Schema has no vocabulary for.
- Server-side validation rejects unknown parameter names (not in schema) and type mismatches, returning a 400 ProblemDetail with field-level errors.
