# Fix Spec: BUG-CYCLE26-01-02 — Team-invite form RHF binding broken

## Problem

QA Day-0 §0.28 / §0.29 (`qa_cycle/checkpoint-results/day-00.md`) showed two related failures on the team-invite form:

1. Selecting "Admin" via the Radix Select dropdown does not propagate; backend logged `Created invitation for email=bob@mathebula-test.local with role=member`.
2. Filling the email input via Playwright MCP `fill()` returns 200 from the Server Action with empty body and never reaches the backend (no Mailpit email, no `InvitationService.Created invitation` log entry).

Both were labeled "tooling-only" but the actual root cause is a fragile react-hook-form binding pattern that any input-event source (Playwright, browser autofill, password managers, paste-only-then-tab) can fail.

## Root Cause (verified)

File: `frontend/components/team/invite-member-form.tsx`

### Email field (lines 230–242)

```tsx
<Input
  id="invite-email"
  type="email"
  placeholder="colleague@company.com"
  data-testid="invite-email-input"
  {...form.register("emailAddress")}
  onChange={(e) => {
    form.setValue("emailAddress", e.target.value);
    form.clearErrors("emailAddress");
    setError(null);
    setSuccess(null);
  }}
/>
```

Problems:
- `register("emailAddress")` returns `{ onChange, onBlur, ref, name }` and is spread first.
- The component then defines its own `onChange={...}` AFTER the spread, which **overrides** the `onChange` returned by `register`. RHF therefore never sees synthetic onChange events through its registered handler.
- The custom handler manually calls `form.setValue("emailAddress", e.target.value)` to compensate. That works for ordinary keyboard input, but is brittle:
  - It relies on `e.target.value` being current at synthetic-onChange time. When Playwright `.fill()` sets value via the native setter and fires `input`+`change`, React's onChange wrapper picks up `e.target.value` correctly only when the synthetic event reaches this handler.
  - Some autofill / paste-event paths fire only `change` without bubbling through React's synthetic wrapper consistently in StrictMode/concurrent rendering.
- The `setValue("emailAddress", ...)` call is redundant if `register`'s onChange were not shadowed — leaving both is the smell.

### Role Select (lines 253–278)

```tsx
const [role, setRole] = useState<"org:member" | "org:admin">("org:member");
const [selectedRoleId, setSelectedRoleId] = useState<string | undefined>(undefined);
...
<Select value={getSelectValue()} onValueChange={handleRoleSelectChange}>
```

Problems:
- The role is held in **two parallel useState slots** (`role` + `selectedRoleId`) outside RHF. The submit handler reads them at call time:
  ```tsx
  const result = await inviteMember(
    values.emailAddress.trim(),
    role,
    selectedRoleId,
    ...
  );
  ```
- This works in steady state but means the role can NEVER appear in the FormData payload — it is captured by closure. If `handleRoleSelectChange` doesn't fire (Radix portal-rendered SelectItem clicks under Playwright MCP have known reliability issues), the closure keeps the default `"org:member"`.
- Putting role into RHF (with `Controller`) would give us a consistent submit-time value, FormData visibility, and dev-tools introspection — and also exposes a hidden form field for resilience.

### Why the two bugs are one fix

Both stem from the form mixing **`register()` shorthand for inputs** with **out-of-band useState for selects**, and then patching the email field with an extra-onChange that shadows register. The fix is to **wrap the form in `<Form>` and use `Controller` / `FormField` consistently** (per `frontend/CLAUDE.md` "Form Patterns" section). One PR, one form, both bugs gone.

## Fix

Refactor `InviteFormUI` in `frontend/components/team/invite-member-form.tsx` to use the project's standard RHF pattern documented in `frontend/CLAUDE.md`.

### Step 1 — Move role into RHF

Update the schema in `frontend/lib/schemas/invite-member.ts` to include role + orgRoleId:

```ts
export const inviteMemberSchema = z.object({
  emailAddress: z.string().email("Enter a valid email"),
  roleSelectValue: z.string().min(1, "Select a role"),  // holds "system:member" | "system:admin" | <orgRoleId>
});
export type InviteMemberFormData = z.infer<typeof inviteMemberSchema>;
```

(Keep existing email validation as-is — only add `roleSelectValue`.)

### Step 2 — Refactor `InviteFormUI` form body

Replace the form body (lines 222–283) with:

```tsx
import { Form, FormField, FormItem, FormLabel, FormControl, FormMessage } from "@/components/ui/form";

const SYSTEM_MEMBER_VALUE = "system:member";
const SYSTEM_ADMIN_VALUE = "system:admin";
const systemSelectValues = new Set([SYSTEM_MEMBER_VALUE, SYSTEM_ADMIN_VALUE]);

const form = useForm<InviteMemberFormData>({
  resolver: zodResolver(inviteMemberSchema),
  defaultValues: {
    emailAddress: "",
    roleSelectValue: SYSTEM_MEMBER_VALUE,
  },
});

const roleSelectValue = form.watch("roleSelectValue");

// Derive (role, selectedRoleId) from roleSelectValue
const isSystemRole = systemSelectValues.has(roleSelectValue);
const role: "org:member" | "org:admin" = isSystemRole
  ? (roleSelectValue === SYSTEM_ADMIN_VALUE ? "org:admin" : "org:member")
  : "org:member";
const selectedRoleId = isSystemRole ? undefined : roleSelectValue;
const selectedRole = selectedRoleId ? roles.find((r) => r.id === selectedRoleId) : undefined;
const isCustomRole = selectedRole && !selectedRole.isSystem;
const roleCapabilities = new Set(selectedRole?.capabilities ?? []);

// Reset overrides when the selected role changes
useEffect(() => {
  setOverrides([]);
  setCustomizeOpen(false);
  setError(null);
  setSuccess(null);
}, [roleSelectValue]);

const handleSubmit = async (values: InviteMemberFormData) => {
  setError(null);
  setSuccess(null);
  setIsSubmitting(true);
  try {
    const result = await inviteMember(
      values.emailAddress.trim(),
      role,
      selectedRoleId,
      overrides.length > 0 ? overrides : undefined
    );
    if (!result.success) {
      setError(result.error ?? "Failed to send invitation.");
      return;
    }
    onInviteSent();
    form.reset({ emailAddress: "", roleSelectValue: SYSTEM_MEMBER_VALUE });
    setSuccess(`Invitation sent to ${values.emailAddress.trim()}.`);
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : "Failed to send invitation.";
    setError(message);
  } finally {
    setIsSubmitting(false);
  }
};
```

Replace the JSX form body with:

```tsx
{!isAtLimit && (
  <Form {...form}>
    <form
      onSubmit={form.handleSubmit(handleSubmit)}
      className="flex flex-col gap-3 sm:flex-row sm:items-end"
    >
      <FormField
        control={form.control}
        name="emailAddress"
        render={({ field }) => (
          <FormItem className="flex-1 space-y-1.5">
            <FormLabel htmlFor="invite-email">Email address</FormLabel>
            <FormControl>
              <Input
                id="invite-email"
                type="email"
                placeholder="colleague@company.com"
                data-testid="invite-email-input"
                {...field}
              />
            </FormControl>
            <FormMessage />
          </FormItem>
        )}
      />
      <FormField
        control={form.control}
        name="roleSelectValue"
        render={({ field }) => (
          <FormItem className="space-y-1.5">
            <FormLabel htmlFor="invite-role">Role</FormLabel>
            <Select value={field.value} onValueChange={field.onChange}>
              <SelectTrigger
                className="h-9 w-full min-w-[140px]"
                id="invite-role"
                data-testid="role-select"
              >
                <SelectValue placeholder="Select a role..." />
              </SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  <SelectLabel>System</SelectLabel>
                  <SelectItem value={SYSTEM_MEMBER_VALUE}>Member</SelectItem>
                  <SelectItem value={SYSTEM_ADMIN_VALUE}>Admin</SelectItem>
                </SelectGroup>
                {customRoles.length > 0 && (
                  <SelectGroup>
                    <SelectLabel>Custom</SelectLabel>
                    {customRoles.map((r) => (
                      <SelectItem key={r.id} value={r.id}>
                        {r.name}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                )}
              </SelectContent>
            </Select>
            {/* Hidden input so the role value is also present in raw FormData,
                even if some Server Action middleware bypasses RHF state. */}
            <input
              type="hidden"
              name="roleSelectValue"
              value={field.value}
              data-testid="role-hidden"
            />
            <FormMessage />
          </FormItem>
        )}
      />
      <Button type="submit" disabled={isSubmitting} size="sm" data-testid="invite-member-btn">
        {isSubmitting ? "Sending..." : "Send Invite"}
      </Button>
    </form>
  </Form>
)}
```

### Step 3 — Remove dead code

Delete the obsolete bits in the same file:

- `const [role, setRole] = useState<"org:member" | "org:admin">("org:member");` (line 113)
- `const [selectedRoleId, setSelectedRoleId] = useState<string | undefined>(undefined);` (line 114)
- The `handleRoleSelectChange` function (lines 131–145) — its logic is now derived inline from `roleSelectValue`.
- The `getSelectValue` helper (lines 184–189).
- The `SYSTEM_MEMBER_VALUE` / `SYSTEM_ADMIN_VALUE` / `systemSelectValues` declarations at lines 180–182 — move them to module-scope (top of file) so the schema-driven default value can reference them too.

### Step 4 — Update tests

`frontend/components/team/invite-member-form.test.tsx` likely asserts old behaviour. After refactor:

- Tests that simulate role-change should call `screen.getByTestId("role-select")` then click items as before — `Form` + `Controller` works the same way for users.
- Tests that fire `change` events on the email input now must do so via `userEvent.type()` (the canonical RHF-friendly path) rather than `fireEvent.change`. Update any direct `fireEvent.change` usages.

Run `pnpm test invite-member-form` and adapt expectations.

## Scope

- Frontend only.
- Files to modify:
  - `frontend/components/team/invite-member-form.tsx`
  - `frontend/lib/schemas/invite-member.ts`
  - `frontend/components/team/invite-member-form.test.tsx` (test fixups)
- Files to create: none.
- Migration needed: no.

## Verification

1. **Re-run Day 0 §0.28**: invite Bob as Admin via real browser (or Playwright MCP). Backend log MUST show `role=admin` (or `role_name=admin`). Re-screenshot.
2. **Re-run Day 0 §0.29**: invite Carol via straight Playwright MCP `fill()` + `click("Send Invite")`. No workaround dispatch. Mailpit MUST show invitation email; backend MUST log invitation creation.
3. **Vitest**: `pnpm --dir frontend test invite-member-form` green.
4. **Lint**: `pnpm --dir frontend run lint` green.

If both checkpoints pass without the native-setter workaround documented in §0.29, the fix is verified.

## Estimated Effort

M (30 min – 2 hr) — straightforward refactor to a standard pattern, plus test fixups.
