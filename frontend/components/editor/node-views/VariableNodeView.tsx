"use client";

import { NodeViewWrapper, type NodeViewProps } from "@tiptap/react";
import { AlertTriangle } from "lucide-react";
import { useMissingVariables } from "../MissingVariablesContext";
import { cn } from "@/lib/utils";

export function VariableNodeView({ node }: NodeViewProps) {
  const missingVars = useMissingVariables();
  const key = node.attrs.key as string;
  const isMissing = missingVars.has(key);

  return (
    <NodeViewWrapper
      as="span"
      className={cn(
        "inline-flex items-center rounded-md px-1.5 py-0.5 font-mono text-xs",
        isMissing
          ? "border border-amber-300 bg-amber-50 text-amber-700 dark:border-amber-700 dark:bg-amber-950 dark:text-amber-300"
          : "border border-teal-200 bg-teal-50 text-teal-700 dark:border-teal-800 dark:bg-teal-950 dark:text-teal-300"
      )}
      title={isMissing ? "This variable has no value for the selected entity" : undefined}
    >
      {"{"}
      {key}
      {"}"}
      {isMissing && <AlertTriangle className="ml-1 size-3" />}
    </NodeViewWrapper>
  );
}
