import { afterEach, describe, expect, it } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { Pencil, Share2 } from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSub,
  DropdownMenuSubTrigger,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { contrastRatio, loadThemes, tokenHex, type TokenMap } from "@/__tests__/theme/color-math";

// K-24 (663C): a focused menu/select item fills with --accent and its label turns
// --accent-foreground, but unclassed icons stayed --muted-foreground (1.40:1 on the firm-light
// teal fill). The recipes now also paint those icons --accent-foreground on :focus. Unit tests
// cannot compute :focus styles, so (1) pins the recipe token and (2) the token pair's contrast.

const FOCUSED_ICON_TOKEN = "focus:[&_svg:not([class*='text-'])]:text-accent-foreground";

afterEach(() => {
  cleanup();
});

describe("K-24 focused item icon recipe", () => {
  it("DropdownMenuItem and DropdownMenuSubTrigger paint unclassed icons --accent-foreground on focus", () => {
    render(
      <DropdownMenu defaultOpen modal={false}>
        <DropdownMenuTrigger>Open menu (K-24)</DropdownMenuTrigger>
        <DropdownMenuContent>
          <DropdownMenuItem>
            <Pencil />
            Edit (K-24)
          </DropdownMenuItem>
          <DropdownMenuSub>
            <DropdownMenuSubTrigger>
              <Share2 />
              Share (K-24)
            </DropdownMenuSubTrigger>
          </DropdownMenuSub>
        </DropdownMenuContent>
      </DropdownMenu>
    );

    expect(screen.getByRole("menuitem", { name: "Edit (K-24)" })).toHaveClass(FOCUSED_ICON_TOKEN);
    expect(screen.getByRole("menuitem", { name: "Share (K-24)" })).toHaveClass(FOCUSED_ICON_TOKEN);
  });

  it("SelectItem paints unclassed icons (incl. its check indicator) --accent-foreground on focus", () => {
    render(
      <Select open defaultValue="a">
        <SelectTrigger aria-label="Pick (K-24)">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="a">Alpha (K-24)</SelectItem>
          <SelectItem value="b">Beta (K-24)</SelectItem>
        </SelectContent>
      </Select>
    );

    expect(screen.getByRole("option", { name: "Beta (K-24)" })).toHaveClass(FOCUSED_ICON_TOKEN);
  });
});

describe("K-24 focused item icon contrast (WCAG 1.4.11, 3:1)", () => {
  const { dark, firmLight } = loadThemes();
  const ratio = (fg: string, bg: string, vars: TokenMap) =>
    contrastRatio(tokenHex(fg, vars), tokenHex(bg, vars));

  it("firm light: --accent-foreground on --accent ≥ 3:1; --muted-foreground (the defect) < 3:1", () => {
    expect(ratio("--accent-foreground", "--accent", firmLight)).toBeGreaterThanOrEqual(3);
    expect(ratio("--muted-foreground", "--accent", firmLight)).toBeLessThan(3);
  });

  it("in dark the icon now matches its label, which beats --muted-foreground (the fill is K-21)", () => {
    // Dark --accent (teal-500) under white is 2.25:1 for the label too — the dark accent retune is
    // K-21 (backlog, FD-1). K-24 only makes the icon match the label, never worse than before.
    expect(ratio("--accent-foreground", "--accent", dark)).toBeGreaterThan(
      ratio("--muted-foreground", "--accent", dark)
    );
  });
});
