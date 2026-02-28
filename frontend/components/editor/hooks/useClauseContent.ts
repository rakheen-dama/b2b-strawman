"use client";

import { useEffect, useState } from "react";
import { getClause } from "@/lib/actions/clause-actions";

interface ClauseContentState {
  body: Record<string, unknown> | null;
  title: string | null;
  isLoading: boolean;
}

interface ClauseContentResult {
  body: Record<string, unknown> | null;
  title: string | null;
  isLoading: boolean;
}

const clauseCache = new Map<string, { body: Record<string, unknown>; title: string }>();

export function useClauseContent(clauseId: string): ClauseContentResult {
  const [state, setState] = useState<ClauseContentState>(() =>
    resolveInitial(clauseId),
  );

  // Fetch clause content when clauseId changes and not cached
  useEffect(() => {
    if (!clauseId || clauseCache.has(clauseId)) return;

    let cancelled = false;

    getClause(clauseId)
      .then((clause) => {
        if (cancelled) return;
        if (clause) {
          clauseCache.set(clauseId, {
            body: clause.body,
            title: clause.title,
          });
          setState({
            body: clause.body,
            title: clause.title,
            isLoading: false,
          });
        } else {
          setState({ body: null, title: null, isLoading: false });
        }
      })
      .catch(() => {
        if (!cancelled) {
          setState({ body: null, title: null, isLoading: false });
        }
      });

    return () => {
      cancelled = true;
    };
  }, [clauseId]);

  // Derive current state from cache if available (handles clauseId changes
  // where data is already cached, without needing synchronous setState in effect)
  const cached = clauseId ? clauseCache.get(clauseId) : undefined;
  if (cached) {
    return { body: cached.body, title: cached.title, isLoading: false };
  }

  if (!clauseId) {
    return { body: null, title: null, isLoading: false };
  }

  return state;
}

function resolveInitial(clauseId: string): ClauseContentState {
  if (!clauseId) return { body: null, title: null, isLoading: false };
  const cached = clauseCache.get(clauseId);
  if (cached)
    return { body: cached.body, title: cached.title, isLoading: false };
  return { body: null, title: null, isLoading: true };
}
