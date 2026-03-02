"use client";

import { useRouter } from "next/navigation";
import { Users, AlertTriangle } from "lucide-react";
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
  CardFooter,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import type { AggregatedCompletenessResponse } from "@/lib/types";

interface IncompleteProfilesWidgetProps {
  data: AggregatedCompletenessResponse | null;
  orgSlug: string;
}

export function IncompleteProfilesWidget({
  data,
  orgSlug,
}: IncompleteProfilesWidgetProps) {
  const router = useRouter();

  if (data === null) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Users className="h-4 w-4" />
            Incomplete Profiles
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground italic">
            Unable to load profile completeness data.
          </p>
        </CardContent>
      </Card>
    );
  }

  if (data.incompleteCount === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Users className="h-4 w-4" />
            Incomplete Profiles
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-green-600 dark:text-green-400">
            All customer profiles are complete.
          </p>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Users className="h-4 w-4" />
          Incomplete Profiles
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="flex items-center gap-2">
          <AlertTriangle className="h-4 w-4 shrink-0 text-amber-500" />
          <p className="text-sm">
            <span className="font-mono font-semibold tabular-nums">
              {data.incompleteCount}
            </span>{" "}
            of{" "}
            <span className="font-mono tabular-nums">{data.totalCount}</span>{" "}
            customers have incomplete profiles
          </p>
        </div>

        {data.topMissingFields.length > 0 && (
          <div className="space-y-1.5">
            <p className="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
              Top Missing Fields
            </p>
            <ul className="space-y-1">
              {data.topMissingFields.map((field) => (
                <li key={field.fieldSlug}>
                  <button
                    type="button"
                    onClick={() =>
                      router.push(
                        `/org/${orgSlug}/customers?showIncomplete=true`,
                      )
                    }
                    className="flex w-full items-center justify-between rounded-md px-2 py-1 text-sm transition-colors hover:bg-slate-50 dark:hover:bg-slate-900"
                  >
                    <span className="truncate text-slate-700 dark:text-slate-300">
                      {field.fieldName}
                    </span>
                    <span className="ml-2 shrink-0 font-mono text-xs tabular-nums text-slate-500">
                      {field.customerCount}{" "}
                      {field.customerCount === 1 ? "customer" : "customers"}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          </div>
        )}
      </CardContent>
      <CardFooter>
        <Button
          variant="ghost"
          size="sm"
          className="text-muted-foreground"
          onClick={() => router.push(`/org/${orgSlug}/customers`)}
        >
          View all customers &rarr;
        </Button>
      </CardFooter>
    </Card>
  );
}
