/**
 * Test utilities for SWR. Import in test files that test components using SWR.
 *
 * Usage:
 *   import { SWRTestProvider } from "@/lib/swr/test-utils";
 *
 *   render(
 *     <SWRTestProvider>
 *       <MyComponent />
 *     </SWRTestProvider>
 *   );
 *
 * Each SWRTestProvider instance creates a fresh, empty cache — preventing
 * state leakage between tests.
 */

import React from "react";
import { SWRConfig } from "swr";

/**
 * Wraps children in an SWRConfig with a fresh Map cache and no deduplication.
 * Use this in tests to prevent SWR cache leakage between test cases.
 */
export function SWRTestProvider({ children }: { children: React.ReactNode }) {
  return React.createElement(
    SWRConfig,
    {
      value: {
        provider: () => new Map(),
        dedupingInterval: 0,
      },
    },
    children,
  );
}
