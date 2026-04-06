"use client";

import { useRouter, usePathname, useSearchParams } from "next/navigation";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

interface InvestmentBasisFilterProps {
  currentValue?: string;
}

export function InvestmentBasisFilter({
  currentValue,
}: InvestmentBasisFilterProps) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  function handleValueChange(value: string) {
    const params = new URLSearchParams(searchParams.toString());
    if (value === "ALL") {
      params.delete("investmentBasis");
    } else {
      params.set("investmentBasis", value);
    }
    const qs = params.toString();
    router.push(qs ? `${pathname}?${qs}` : pathname);
  }

  return (
    <Select
      value={currentValue ?? "ALL"}
      onValueChange={handleValueChange}
    >
      <SelectTrigger data-testid="investment-basis-filter">
        <SelectValue placeholder="Filter by basis" />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value="ALL">All</SelectItem>
        <SelectItem value="FIRM_DISCRETION">Firm Discretion</SelectItem>
        <SelectItem value="CLIENT_INSTRUCTION">Client Instruction</SelectItem>
      </SelectContent>
    </Select>
  );
}
