"use client";

import { useState } from "react";
import { ChevronDown } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { CAPABILITY_META } from "@/lib/capabilities";

export function CapabilityReference() {
  const [isOpen, setIsOpen] = useState(false);

  return (
    <Collapsible open={isOpen} onOpenChange={setIsOpen}>
      <CollapsibleTrigger asChild>
        <Button variant="ghost" size="sm" className="gap-1.5">
          <ChevronDown className={`size-4 transition-transform ${isOpen ? "rotate-180" : ""}`} />
          Capability Reference
        </Button>
      </CollapsibleTrigger>
      <CollapsibleContent>
        <div className="mt-3 rounded-lg border">
          <Table>
            <TableHeader>
              <TableRow className="bg-muted/50 hover:bg-muted/50">
                <TableHead className="px-4">Capability</TableHead>
                <TableHead className="px-4">Description</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {CAPABILITY_META.map((cap) => (
                <TableRow key={cap.value}>
                  <TableCell className="text-foreground px-4 py-2.5 font-medium">
                    {cap.label}
                  </TableCell>
                  <TableCell className="text-muted-foreground px-4 py-2.5 whitespace-normal">
                    {cap.description}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      </CollapsibleContent>
    </Collapsible>
  );
}
