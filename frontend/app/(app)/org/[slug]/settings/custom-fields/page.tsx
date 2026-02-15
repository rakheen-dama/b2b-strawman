import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { auth } from "@clerk/nextjs/server";
import { api } from "@/lib/api";
import { CustomFieldsContent } from "./custom-fields-content";
import type {
  FieldDefinitionResponse,
  FieldGroupResponse,
  EntityType,
} from "@/lib/types";

const ENTITY_TYPES: EntityType[] = ["PROJECT", "TASK", "CUSTOMER"];

export default async function CustomFieldsSettingsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const { orgRole } = await auth();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  if (!isAdmin) {
    return (
      <div className="space-y-8">
        <Link
          href={`/org/${slug}/settings`}
          className="inline-flex items-center gap-1 text-sm text-olive-600 hover:text-olive-900 dark:text-olive-400 dark:hover:text-olive-100"
        >
          <ChevronLeft className="size-4" />
          Settings
        </Link>
        <h1 className="font-display text-3xl text-olive-950 dark:text-olive-50">
          Custom Fields
        </h1>
        <p className="text-olive-600 dark:text-olive-400">
          You do not have permission to manage custom fields. Only admins and
          owners can access this page.
        </p>
      </div>
    );
  }

  const fieldsByType: Record<EntityType, FieldDefinitionResponse[]> = {
    PROJECT: [],
    TASK: [],
    CUSTOMER: [],
  };
  const groupsByType: Record<EntityType, FieldGroupResponse[]> = {
    PROJECT: [],
    TASK: [],
    CUSTOMER: [],
  };

  try {
    const results = await Promise.all(
      ENTITY_TYPES.flatMap((et) => [
        api.get<FieldDefinitionResponse[]>(
          `/api/field-definitions?entityType=${et}`,
        ),
        api.get<FieldGroupResponse[]>(`/api/field-groups?entityType=${et}`),
      ]),
    );

    ENTITY_TYPES.forEach((et, i) => {
      fieldsByType[et] = (results[i * 2] as FieldDefinitionResponse[]) ?? [];
      groupsByType[et] = (results[i * 2 + 1] as FieldGroupResponse[]) ?? [];
    });
  } catch {
    // Non-fatal: show empty state
  }

  return (
    <div className="space-y-8">
      <Link
        href={`/org/${slug}/settings`}
        className="inline-flex items-center gap-1 text-sm text-olive-600 hover:text-olive-900 dark:text-olive-400 dark:hover:text-olive-100"
      >
        <ChevronLeft className="size-4" />
        Settings
      </Link>

      <div>
        <h1 className="font-display text-3xl text-olive-950 dark:text-olive-50">
          Custom Fields
        </h1>
        <p className="mt-1 text-sm text-olive-600 dark:text-olive-400">
          Define custom fields and groups for projects, tasks, and customers.
        </p>
      </div>

      <CustomFieldsContent
        slug={slug}
        fieldsByType={fieldsByType}
        groupsByType={groupsByType}
        canManage={isAdmin}
      />
    </div>
  );
}
