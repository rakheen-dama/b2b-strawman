import { describe, it } from "vitest";
import { RuleTester } from "eslint";
import rule from "../../eslint-rules/no-aschild-adjacency.mjs";

// ESLint's RuleTester defaults to mocha-style globals; bind to vitest so
// the test cases register with vitest's runner.
RuleTester.describe = describe;
RuleTester.it = it;
RuleTester.itOnly = it.only;

const ruleTester = new RuleTester({
  languageOptions: {
    ecmaVersion: "latest",
    sourceType: "module",
    parserOptions: {
      ecmaFeatures: { jsx: true },
    },
  },
});

ruleTester.run("no-aschild-adjacency", rule as never, {
  valid: [
    // Single Trigger asChild in a parent — no adjacency, fine.
    {
      code: `
        const x = (
          <Dialog>
            <DialogTrigger asChild>
              <Button>Open</Button>
            </DialogTrigger>
            <DialogContent>...</DialogContent>
          </Dialog>
        );
      `,
    },
    // Dialog-owns-button: no DialogTrigger at all, plain Button. The good pattern.
    {
      code: `
        const x = (
          <div className="flex gap-1">
            <Dialog>
              <Button onClick={() => {}}>Edit</Button>
              <DialogContent>...</DialogContent>
            </Dialog>
            <AlertDialog>
              <Button onClick={() => {}}>Delete</Button>
              <AlertDialogContent>...</AlertDialogContent>
            </AlertDialog>
          </div>
        );
      `,
    },
    // TooltipTrigger excluded by default — adjacency is fine for hover-only.
    {
      code: `
        const x = (
          <div>
            <TooltipTrigger asChild>
              <Button>Hover 1</Button>
            </TooltipTrigger>
            <TooltipTrigger asChild>
              <Button>Hover 2</Button>
            </TooltipTrigger>
          </div>
        );
      `,
    },
    // One Trigger asChild + one non-Slot button is fine — only one Slot in the row.
    {
      code: `
        const x = (
          <div className="flex">
            <Dialog>
              <DialogTrigger asChild>
                <Button>Open</Button>
              </DialogTrigger>
              <DialogContent>...</DialogContent>
            </Dialog>
            <Button onClick={() => {}}>Plain</Button>
          </div>
        );
      `,
    },
    // *Trigger without asChild — not a Slot composition, fine.
    {
      code: `
        const x = (
          <div>
            <DialogTrigger>Open</DialogTrigger>
            <AlertDialogTrigger>Delete</AlertDialogTrigger>
          </div>
        );
      `,
    },
    // Two triggers but in different parent JSX blocks — not adjacent.
    {
      code: `
        const x = (
          <div>
            <div>
              <DialogTrigger asChild>
                <Button>Edit</Button>
              </DialogTrigger>
            </div>
            <div>
              <AlertDialogTrigger asChild>
                <Button>Delete</Button>
              </AlertDialogTrigger>
            </div>
          </div>
        );
      `,
    },
  ],
  invalid: [
    // Canonical bad pattern: Dialog + AlertDialog adjacency in a flex row.
    {
      code: `
        const x = (
          <div className="flex justify-end gap-1">
            <DialogTrigger asChild>
              <Button>Edit</Button>
            </DialogTrigger>
            <AlertDialogTrigger asChild>
              <Button>Delete</Button>
            </AlertDialogTrigger>
          </div>
        );
      `,
      errors: [
        { messageId: "adjacency", data: { trigger: "DialogTrigger" } },
        { messageId: "adjacency", data: { trigger: "AlertDialogTrigger" } },
      ],
    },
    // Three Trigger asChild siblings — three diagnostics.
    {
      code: `
        const x = (
          <div>
            <DialogTrigger asChild><Button>A</Button></DialogTrigger>
            <DialogTrigger asChild><Button>B</Button></DialogTrigger>
            <PopoverTrigger asChild><Button>C</Button></PopoverTrigger>
          </div>
        );
      `,
      errors: [{ messageId: "adjacency" }, { messageId: "adjacency" }, { messageId: "adjacency" }],
    },
    // Inside a JSXFragment — same parent class as JSXElement, should fire.
    {
      code: `
        const x = (
          <>
            <DialogTrigger asChild><Button>A</Button></DialogTrigger>
            <AlertDialogTrigger asChild><Button>B</Button></AlertDialogTrigger>
          </>
        );
      `,
      errors: [
        { messageId: "adjacency", data: { trigger: "DialogTrigger" } },
        { messageId: "adjacency", data: { trigger: "AlertDialogTrigger" } },
      ],
    },
    // Conditional wrappers: {cond && <X asChild />} alongside another sibling.
    {
      code: `
        const x = (
          <div>
            {cond && (
              <DialogTrigger asChild><Button>Edit</Button></DialogTrigger>
            )}
            <AlertDialogTrigger asChild><Button>Delete</Button></AlertDialogTrigger>
          </div>
        );
      `,
      errors: [
        { messageId: "adjacency", data: { trigger: "DialogTrigger" } },
        { messageId: "adjacency", data: { trigger: "AlertDialogTrigger" } },
      ],
    },
    // Explicit asChild={true} (rather than shorthand) still matches.
    {
      code: `
        const x = (
          <div>
            <DialogTrigger asChild={true}><Button>A</Button></DialogTrigger>
            <AlertDialogTrigger asChild><Button>B</Button></AlertDialogTrigger>
          </div>
        );
      `,
      errors: [
        { messageId: "adjacency", data: { trigger: "DialogTrigger" } },
        { messageId: "adjacency", data: { trigger: "AlertDialogTrigger" } },
      ],
    },
    // excludeTriggers option: when caller drops TooltipTrigger from the
    // exclusion list, two adjacent TooltipTrigger asChild ARE flagged.
    {
      code: `
        const x = (
          <div>
            <TooltipTrigger asChild><Button>A</Button></TooltipTrigger>
            <TooltipTrigger asChild><Button>B</Button></TooltipTrigger>
          </div>
        );
      `,
      options: [{ excludeTriggers: [] }],
      errors: [
        { messageId: "adjacency", data: { trigger: "TooltipTrigger" } },
        { messageId: "adjacency", data: { trigger: "TooltipTrigger" } },
      ],
    },
  ],
});
