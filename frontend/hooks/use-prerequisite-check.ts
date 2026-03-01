"use client";

import { useState, useCallback } from "react";
import { checkPrerequisitesAction } from "@/lib/actions/prerequisite-actions";
import type {
  PrerequisiteCheck,
  PrerequisiteContext,
} from "@/components/prerequisite/types";
import type { EntityType } from "@/lib/types";

export function usePrerequisiteCheck(
  context: PrerequisiteContext,
  entityType: EntityType,
  entityId: string,
) {
  const [check, setCheck] = useState<PrerequisiteCheck | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const runCheck = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await checkPrerequisitesAction(
        context,
        entityType,
        entityId,
      );
      setCheck(result);
    } catch (e) {
      setError(e instanceof Error ? e : new Error(String(e)));
    } finally {
      setLoading(false);
    }
  }, [context, entityType, entityId]);

  const reset = useCallback(() => {
    setCheck(null);
    setError(null);
  }, []);

  return { check, loading, error, runCheck, reset };
}
