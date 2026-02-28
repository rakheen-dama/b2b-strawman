"use client";

import { useState } from "react";
import { Plus, Trash2 } from "lucide-react";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

interface LoopTableColumn {
  header: string;
  key: string;
}

interface LoopTableConfigProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  dataSource: string;
  columns: LoopTableColumn[];
  onUpdate: (attrs: {
    dataSource: string;
    columns: LoopTableColumn[];
  }) => void;
}

function LoopTableConfigForm({
  initialDataSource,
  initialColumns,
  onUpdate,
}: {
  initialDataSource: string;
  initialColumns: LoopTableColumn[];
  onUpdate: LoopTableConfigProps["onUpdate"];
}) {
  const [dataSource, setDataSource] = useState(initialDataSource);
  const [columns, setColumns] = useState<LoopTableColumn[]>(
    initialColumns.length > 0 ? initialColumns : [],
  );

  const addColumn = () => {
    setColumns([...columns, { header: "", key: "" }]);
  };

  const removeColumn = (index: number) => {
    setColumns(columns.filter((_, i) => i !== index));
  };

  const updateColumn = (
    index: number,
    field: keyof LoopTableColumn,
    value: string,
  ) => {
    const updated = columns.map((col, i) =>
      i === index ? { ...col, [field]: value } : col,
    );
    setColumns(updated);
  };

  const handleApply = () => {
    if (!dataSource.trim()) return;
    const validColumns = columns.filter(
      (col) => col.header.trim() && col.key.trim(),
    );
    onUpdate({ dataSource: dataSource.trim(), columns: validColumns });
  };

  return (
    <div className="space-y-4">
      <div>
        <label
          htmlFor="loop-data-source"
          className="mb-1 block text-sm font-medium"
        >
          Data Source
        </label>
        <Input
          id="loop-data-source"
          value={dataSource}
          onChange={(e) => setDataSource(e.target.value)}
          placeholder="e.g. members, lines, tags"
        />
      </div>

      <div>
        <div className="mb-2 flex items-center justify-between">
          <span className="text-sm font-medium">Columns</span>
          <Button
            variant="ghost"
            size="xs"
            type="button"
            onClick={addColumn}
            aria-label="Add column"
          >
            <Plus className="size-3" />
            Add
          </Button>
        </div>

        <div className="space-y-2">
          {columns.map((col, index) => (
            <div key={index} className="flex items-center gap-2">
              <Input
                value={col.header}
                onChange={(e) =>
                  updateColumn(index, "header", e.target.value)
                }
                placeholder="Header"
                className="h-8 text-xs"
                aria-label={`Column ${index + 1} header`}
              />
              <Input
                value={col.key}
                onChange={(e) =>
                  updateColumn(index, "key", e.target.value)
                }
                placeholder="Data key"
                className="h-8 text-xs"
                aria-label={`Column ${index + 1} key`}
              />
              <Button
                variant="ghost"
                size="icon-xs"
                type="button"
                onClick={() => removeColumn(index)}
                aria-label={`Remove column ${index + 1}`}
              >
                <Trash2 className="size-3" />
              </Button>
            </div>
          ))}

          {columns.length === 0 && (
            <p className="text-xs text-muted-foreground">
              No columns configured. Click &quot;Add&quot; to add one.
            </p>
          )}
        </div>
      </div>

      <Button
        variant="default"
        size="sm"
        type="button"
        onClick={handleApply}
        className="w-full"
      >
        Apply
      </Button>
    </div>
  );
}

export function LoopTableConfig({
  open,
  onOpenChange,
  dataSource,
  columns,
  onUpdate,
}: LoopTableConfigProps) {
  return (
    <Popover open={open} onOpenChange={onOpenChange}>
      <PopoverTrigger asChild>
        <span />
      </PopoverTrigger>
      <PopoverContent className="w-96" align="start">
        {open && (
          <LoopTableConfigForm
            initialDataSource={dataSource}
            initialColumns={columns}
            onUpdate={onUpdate}
          />
        )}
      </PopoverContent>
    </Popover>
  );
}
