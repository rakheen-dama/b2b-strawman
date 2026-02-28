"use client";

import type { ColumnDef } from "@tanstack/react-table";
import { MoreHorizontal } from "lucide-react";

import type { Customer } from "@/lib/types";
import { formatDate } from "@/lib/format";
import { StatusBadge } from "@/components/ui/status-badge";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

interface CustomerColumnsOptions {
  onEdit?: (customer: Customer) => void;
  onArchive?: (customer: Customer) => void;
  onUnarchive?: (customer: Customer) => void;
}

export function getCustomerColumns(
  options: CustomerColumnsOptions = {}
): ColumnDef<Customer, unknown>[] {
  return [
    {
      accessorKey: "name",
      header: "Name",
      cell: ({ row }) => (
        <div className="font-medium text-slate-900">{row.original.name}</div>
      ),
    },
    {
      accessorKey: "email",
      header: "Contact Email",
      cell: ({ row }) => (
        <span className="text-slate-600">{row.original.email}</span>
      ),
    },
    {
      accessorKey: "lifecycleStatus",
      header: "Status",
      cell: ({ row }) => {
        const status = row.original.lifecycleStatus ?? row.original.status;
        return <StatusBadge status={status} />;
      },
    },
    {
      accessorKey: "customerType",
      header: "Type",
      cell: ({ row }) => {
        const type = row.original.customerType;
        if (!type) return <span className="text-slate-400">--</span>;
        return (
          <span className="text-sm text-slate-600">
            {type.charAt(0) + type.slice(1).toLowerCase()}
          </span>
        );
      },
    },
    {
      accessorKey: "createdAt",
      header: "Created",
      cell: ({ row }) => (
        <span className="text-sm text-slate-500">
          {formatDate(row.original.createdAt)}
        </span>
      ),
    },
    {
      id: "actions",
      header: "",
      size: 48,
      enableSorting: false,
      cell: ({ row }) => {
        const customer = row.original;
        const isArchived = customer.status === "ARCHIVED";

        return (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button
                variant="ghost"
                size="icon-xs"
                onClick={(e) => e.stopPropagation()}
              >
                <MoreHorizontal className="size-4" />
                <span className="sr-only">Open menu</span>
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem
                onClick={(e) => {
                  e.stopPropagation();
                  options.onEdit?.(customer);
                }}
              >
                Edit
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              {isArchived ? (
                <DropdownMenuItem
                  onClick={(e) => {
                    e.stopPropagation();
                    options.onUnarchive?.(customer);
                  }}
                >
                  Unarchive
                </DropdownMenuItem>
              ) : (
                <DropdownMenuItem
                  className="text-red-600 focus:text-red-600"
                  onClick={(e) => {
                    e.stopPropagation();
                    options.onArchive?.(customer);
                  }}
                >
                  Archive
                </DropdownMenuItem>
              )}
            </DropdownMenuContent>
          </DropdownMenu>
        );
      },
    },
  ];
}
