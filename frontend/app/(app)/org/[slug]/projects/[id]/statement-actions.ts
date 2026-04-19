"use server";

import { revalidatePath } from "next/cache";
import { ApiError } from "@/lib/api/client";
import {
  generateStatement,
  listStatements,
  type GenerateStatementRequest,
  type StatementResponse,
  type PaginatedStatementsResponse,
} from "@/lib/api/statement-of-account";

export type GenerateStatementResult =
  | { success: true; data: StatementResponse }
  | {
      success: false;
      kind: "forbidden" | "module_disabled" | "error";
      error: string;
    };

export type ListStatementsResult =
  | { success: true; data: PaginatedStatementsResponse }
  | { success: false; error: string };

function detectModuleDisabled(err: ApiError): boolean {
  const type =
    typeof err.detail?.type === "string" ? err.detail.type : undefined;
  return !!type && type.includes("module-not-enabled");
}

export async function generateStatementAction(
  slug: string,
  projectId: string,
  req: GenerateStatementRequest
): Promise<GenerateStatementResult> {
  try {
    const data = await generateStatement(projectId, req);
    revalidatePath(`/org/${slug}/projects/${projectId}`);
    return { success: true, data };
  } catch (err) {
    if (err instanceof ApiError && err.status === 403) {
      return {
        success: false,
        kind: detectModuleDisabled(err) ? "module_disabled" : "forbidden",
        error: err.message,
      };
    }
    return {
      success: false,
      kind: "error",
      error:
        err instanceof ApiError ? err.message : "Failed to generate statement",
    };
  }
}

// TODO: expose pagination params (page, size) once UI needs them.
export async function listStatementsAction(
  projectId: string
): Promise<ListStatementsResult> {
  try {
    const data = await listStatements(projectId);
    return { success: true, data };
  } catch (err) {
    return {
      success: false,
      error:
        err instanceof ApiError ? err.message : "Failed to list statements",
    };
  }
}
