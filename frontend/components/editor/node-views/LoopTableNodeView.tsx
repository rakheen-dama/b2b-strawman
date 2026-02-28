"use client";

import { useState } from "react";
import { NodeViewWrapper, type NodeViewProps } from "@tiptap/react";
import {
  Table,
  TableHeader,
  TableBody,
  TableHead,
  TableRow,
  TableCell,
} from "@/components/ui/table";
import { LoopTableConfig } from "../LoopTableConfig";

interface LoopTableColumn {
  header: string;
  key: string;
}

export function LoopTableNodeView({ node, updateAttributes }: NodeViewProps) {
  const [configOpen, setConfigOpen] = useState(false);
  const columns = (node.attrs.columns ?? []) as LoopTableColumn[];
  const dataSource = (node.attrs.dataSource ?? "") as string;

  return (
    <NodeViewWrapper className="my-4">
      <div
        className="cursor-pointer rounded-lg border border-slate-200 bg-white p-3 dark:border-slate-800 dark:bg-slate-950"
        onClick={() => setConfigOpen(true)}
        role="button"
        tabIndex={0}
        onKeyDown={(e) => {
          if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            setConfigOpen(true);
          }
        }}
      >
        {columns.length > 0 ? (
          <Table>
            <TableHeader>
              <TableRow>
                {columns.map((col, i) => (
                  <TableHead key={i}>{col.header}</TableHead>
                ))}
              </TableRow>
            </TableHeader>
            <TableBody>
              {[0, 1].map((rowIdx) => (
                <TableRow key={rowIdx}>
                  {columns.map((col, colIdx) => (
                    <TableCell
                      key={colIdx}
                      className="text-slate-400"
                    >
                      <span className="font-mono text-xs">
                        {"{"}
                        {col.key}
                        {"}"}
                      </span>
                    </TableCell>
                  ))}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        ) : (
          <div className="py-4 text-center text-sm text-slate-400">
            Click to configure loop table
          </div>
        )}

        {dataSource && (
          <div className="mt-2 text-xs text-muted-foreground">
            Data source: {dataSource}
          </div>
        )}
      </div>

      <LoopTableConfig
        open={configOpen}
        onOpenChange={setConfigOpen}
        dataSource={dataSource}
        columns={columns}
        onUpdate={(attrs) => {
          updateAttributes(attrs);
          setConfigOpen(false);
        }}
      />
    </NodeViewWrapper>
  );
}
