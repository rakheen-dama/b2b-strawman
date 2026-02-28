"use client";

import { NodeViewWrapper, type NodeViewProps } from "@tiptap/react";

export function VariableNodeView({ node }: NodeViewProps) {
  return (
    <NodeViewWrapper
      as="span"
      className="inline-flex items-center rounded-md border border-teal-200 bg-teal-50 px-1.5 py-0.5 font-mono text-xs text-teal-700 dark:border-teal-800 dark:bg-teal-950 dark:text-teal-300"
    >
      {"{"}
      {node.attrs.key as string}
      {"}"}
    </NodeViewWrapper>
  );
}
