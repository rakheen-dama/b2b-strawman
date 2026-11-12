import React from "react";

/**
 * RSC-boundary guard (Phase 90 OQ #19) for list-page tests that capture the props a Server
 * Component page hands its "use client" DataTable wrapper (`projects-page.test.tsx`,
 * `clients-page.test.tsx`). Pre-rendered ReactNode slots may hold elements (their `type` is a
 * component function, which React serialises as a client reference or server output); every other
 * prop must be plain data. Not a test file (no `.test.` suffix).
 */

/** Paths of values the RSC boundary cannot carry: functions, and elements outside a slot. */
export function unserializablePaths(value: unknown, path: string, inSlot: boolean): string[] {
  if (typeof value === "function") return [path];
  if (React.isValidElement(value)) return inSlot ? [] : [path];
  if (Array.isArray(value)) {
    return value.flatMap((item, i) => unserializablePaths(item, `${path}[${i}]`, inSlot));
  }
  if (typeof value === "object" && value !== null) {
    return Object.entries(value).flatMap(([key, item]) =>
      unserializablePaths(item, `${path}.${key}`, inSlot)
    );
  }
  return [];
}

/** Every unserializable path in `props`; `nodeSlots` names the props that may hold ReactNodes. */
export function unserializableProps(
  props: Record<string, unknown>,
  nodeSlots: ReadonlySet<string>
): string[] {
  return Object.entries(props).flatMap(([key, item]) =>
    unserializablePaths(item, key, nodeSlots.has(key))
  );
}
