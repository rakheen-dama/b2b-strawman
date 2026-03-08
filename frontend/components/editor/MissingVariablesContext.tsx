"use client";

import { createContext, useContext } from "react";

/**
 * Provides a set of variable keys whose values are missing/empty
 * when previewing with real entity data. Used by VariableNodeView
 * to render amber warning styling on unresolved variables.
 */
const MissingVariablesContext = createContext<Set<string>>(new Set());

export function useMissingVariables() {
  return useContext(MissingVariablesContext);
}

export { MissingVariablesContext };
