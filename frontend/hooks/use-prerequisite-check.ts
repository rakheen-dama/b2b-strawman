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

  const runCheck = useCallback(async () => {
    setLoading(true);
    try {
      const result = await checkPrerequisitesAction(
        context,
        entityType,
        entityId,
      );
      setCheck(result);
    } catch {
      // Let caller handle errors via the check state
    } finally {
      setLoading(false);
    }
  }, [context, entityType, entityId]);

  const reset = useCallback(() => {
    setCheck(null);
  }, []);

  return { check, loading, runCheck, reset };
}
