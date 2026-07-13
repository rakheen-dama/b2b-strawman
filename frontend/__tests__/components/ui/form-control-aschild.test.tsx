import { describe, it, expect, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useForm } from "react-hook-form";
import { Popover, PopoverTrigger, PopoverContent } from "@/components/ui/popover";
import {
  Form,
  FormField,
  FormItem,
  FormLabel,
  FormControl,
  FormDescription,
  FormMessage,
} from "@/components/ui/form";
import { Button } from "@b2mash/ui/button";
import { Input } from "@b2mash/ui/input";

/**
 * LZKC-027 regression: FormControl must forward props injected into it by a
 * parent `asChild` slot (e.g. Radix PopoverTrigger). The previous cloneElement
 * implementation read only `props.children` and discarded every injected prop
 * (onClick, ref, aria) — making any `<XTrigger asChild><FormControl>` combobox
 * inert (org-level CreateProposalDialog client picker).
 */

function ComboboxForm() {
  const form = useForm<{ customerId: string }>({ defaultValues: { customerId: "" } });
  return (
    <Form {...form}>
      <form>
        <FormField
          control={form.control}
          name="customerId"
          render={() => (
            <FormItem>
              <FormLabel>Customer</FormLabel>
              <Popover modal={false}>
                <PopoverTrigger asChild>
                  <FormControl>
                    <Button type="button" role="combobox" data-testid="combo-trigger">
                      Select a customer...
                    </Button>
                  </FormControl>
                </PopoverTrigger>
                <PopoverContent>
                  <div data-testid="combo-content">Customer list</div>
                </PopoverContent>
              </Popover>
              <FormMessage />
            </FormItem>
          )}
        />
      </form>
    </Form>
  );
}

function InputForm({ withError = false }: { withError?: boolean }) {
  const form = useForm<{ name: string }>({ defaultValues: { name: "" } });
  if (withError && !form.formState.errors.name) {
    form.setError("name", { type: "manual", message: "Name is required" });
  }
  return (
    <Form {...form}>
      <form>
        <FormField
          control={form.control}
          name="name"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Name</FormLabel>
              <FormControl>
                <Input {...field} />
              </FormControl>
              <FormDescription>Your full name</FormDescription>
              <FormMessage />
            </FormItem>
          )}
        />
      </form>
    </Form>
  );
}

describe("FormControl under PopoverTrigger asChild (LZKC-027)", () => {
  afterEach(() => cleanup());

  it("forwards injected trigger props so the popover opens on click", async () => {
    const user = userEvent.setup();
    render(<ComboboxForm />);

    const trigger = screen.getByTestId("combo-trigger");
    // Radix wires the trigger via injected props — data-state proves the ref/props arrived
    expect(trigger).toHaveAttribute("data-state", "closed");
    expect(trigger).toHaveAttribute("aria-haspopup", "dialog");
    expect(screen.queryByTestId("combo-content")).not.toBeInTheDocument();

    await user.click(trigger);

    expect(screen.getByTestId("combo-content")).toBeInTheDocument();
    expect(trigger).toHaveAttribute("data-state", "open");
    expect(trigger).toHaveAttribute("aria-expanded", "true");
  });

  it("still applies the form a11y wiring (id / aria-describedby) to the trigger", () => {
    render(<ComboboxForm />);
    const trigger = screen.getByTestId("combo-trigger");
    const label = screen.getByText("Customer");
    expect(trigger.id).toMatch(/-form-item$/);
    expect(label).toHaveAttribute("for", trigger.id);
    expect(trigger).toHaveAttribute("aria-invalid", "false");
    expect(trigger).toHaveAttribute("aria-describedby", `${trigger.id}-description`);
  });
});

describe("FormControl plain-input contract (unchanged behaviour)", () => {
  afterEach(() => cleanup());

  it("labels focus their input via the merged id", () => {
    render(<InputForm />);
    const input = screen.getByLabelText("Name");
    expect(input.id).toMatch(/-form-item$/);
    expect(input).toHaveAttribute("aria-invalid", "false");
    expect(input).toHaveAttribute("aria-describedby", `${input.id}-description`);
  });

  it("appends the message id to aria-describedby when the field has an error", () => {
    render(<InputForm withError />);
    const input = screen.getByLabelText("Name");
    expect(input).toHaveAttribute("aria-invalid", "true");
    expect(input).toHaveAttribute(
      "aria-describedby",
      `${input.id}-description ${input.id}-message`
    );
    expect(screen.getByRole("alert")).toHaveTextContent("Name is required");
  });
});
