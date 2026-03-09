import emptyStates from "./en/empty-states.json";
import help from "./en/help.json";
import errors from "./en/errors.json";
import gettingStarted from "./en/getting-started.json";
import common from "./en/common.json";

export type MessageNamespace =
  | "empty-states"
  | "help"
  | "errors"
  | "getting-started"
  | "common";

export interface UseMessageReturn {
  t: (code: string, interpolations?: Record<string, string>) => string;
}

const namespaces: Record<MessageNamespace, Record<string, unknown>> = {
  "empty-states": emptyStates,
  help: help,
  errors: errors,
  "getting-started": gettingStarted,
  common: common,
};

function resolve(
  obj: Record<string, unknown>,
  path: string,
): string | undefined {
  const result = path.split(".").reduce<unknown>((acc, key) => {
    if (
      acc &&
      typeof acc === "object" &&
      key in (acc as Record<string, unknown>)
    ) {
      return (acc as Record<string, unknown>)[key];
    }
    return undefined;
  }, obj);

  return typeof result === "string" ? result : undefined;
}

export function useMessage(
  namespace: MessageNamespace,
  _locale?: string,
): UseMessageReturn {
  const messages = namespaces[namespace];

  const t = (
    code: string,
    interpolations?: Record<string, string>,
  ): string => {
    const value = resolve(messages, code);

    if (value === undefined) {
      if (process.env.NODE_ENV === "development") {
        console.warn(`[useMessage] Missing key: ${namespace}.${code}`);
      }
      return code;
    }

    if (!interpolations) {
      return value;
    }

    return value.replace(/\{\{(\w+)\}\}/g, (_, key: string) => {
      return interpolations[key] ?? `{{${key}}}`;
    });
  };

  return { t };
}
