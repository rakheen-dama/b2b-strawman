"use client";

import { useState } from "react";
import {
  NodeViewWrapper,
  NodeViewContent,
  type NodeViewProps,
} from "@tiptap/react";
import { GitBranch, Settings2, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ConditionalBlockConfig } from "../ConditionalBlockConfig";

const OPERATOR_LABELS: Record<string, string> = {
  eq: "equals",
  neq: "does not equal",
  isEmpty: "is empty",
  isNotEmpty: "has a value",
  contains: "contains",
  in: "is one of",
};

export function ConditionalBlockNodeView({
  node,
  updateAttributes,
  deleteNode,
}: NodeViewProps) {
  const [configOpen, setConfigOpen] = useState(false);
  const fieldKey = (node.attrs.fieldKey ?? "") as string;
  const operator = (node.attrs.operator ?? "isNotEmpty") as string;
  const value = (node.attrs.value ?? "") as string;

  const isConfigured = fieldKey.trim() !== "";
  const operatorLabel = OPERATOR_LABELS[operator] ?? operator;
  const showValue = operator !== "isEmpty" && operator !== "isNotEmpty";

  return (
    <NodeViewWrapper className="my-4">
      <div className="rounded-lg border border-amber-300 bg-amber-50/50 dark:border-amber-700 dark:bg-amber-950/20">
        {/* Header */}
        <div className="flex items-center gap-2 border-b border-amber-200 px-3 py-1.5 dark:border-amber-800">
          <GitBranch className="h-3.5 w-3.5 text-amber-600 dark:text-amber-400" />

          <span className="flex-1 truncate text-xs text-amber-700 dark:text-amber-300">
            {isConfigured ? (
              <>
                Show if:{" "}
                <span className="font-mono font-medium">{fieldKey}</span>{" "}
                {operatorLabel}
                {showValue && value && (
                  <>
                    {" "}
                    <span className="font-medium">&ldquo;{value}&rdquo;</span>
                  </>
                )}
              </>
            ) : (
              "Unconfigured \u2014 click to set condition"
            )}
          </span>

          <Button
            variant="ghost"
            size="icon-xs"
            type="button"
            onClick={() => setConfigOpen(true)}
            aria-label="Configure condition"
          >
            <Settings2 className="h-3.5 w-3.5" />
          </Button>

          <Button
            variant="ghost"
            size="icon-xs"
            type="button"
            onClick={() => deleteNode()}
            className="text-red-500 hover:text-red-700 dark:text-red-400 dark:hover:text-red-300"
            aria-label="Remove conditional block"
          >
            <Trash2 className="h-3.5 w-3.5" />
          </Button>
        </div>

        {/* Editable content area */}
        <div className="px-3 py-2">
          <NodeViewContent />
        </div>
      </div>

      <ConditionalBlockConfig
        open={configOpen}
        onOpenChange={setConfigOpen}
        fieldKey={fieldKey}
        operator={operator}
        value={value}
        onUpdate={(attrs) => {
          updateAttributes(attrs);
          setConfigOpen(false);
        }}
      />
    </NodeViewWrapper>
  );
}
