import { toast } from "sonner";
import { createMessages, type MessageNamespace } from "@/lib/messages";

// --- Types ---

export type ErrorCategory =
  | "validation"
  | "forbidden"
  | "notFound"
  | "conflict"
  | "serverError"
  | "networkError"
  | "rateLimited";

export interface ClassifiedError {
  category: ErrorCategory;
  messageCode: string;
  retryable: boolean;
  action?: "retry" | "refresh" | "goBack" | "contactAdmin";
}

// --- classifyError ---

function getStatusCode(error: unknown): number | undefined {
  if (
    error &&
    typeof error === "object" &&
    "status" in error &&
    typeof (error as Record<string, unknown>).status === "number"
  ) {
    return (error as Record<string, unknown>).status as number;
  }

  if (
    error &&
    typeof error === "object" &&
    "response" in error &&
    (error as Record<string, unknown>).response &&
    typeof (error as Record<string, unknown>).response === "object"
  ) {
    const response = (error as Record<string, unknown>).response as Record<
      string,
      unknown
    >;
    if (typeof response.status === "number") {
      return response.status;
    }
  }

  return undefined;
}

export function classifyError(error: unknown): ClassifiedError {
  const status = getStatusCode(error);

  if (status === undefined) {
    return {
      category: "networkError",
      messageCode: "api.networkError",
      retryable: true,
      action: "retry",
    };
  }

  if (status === 400 || status === 422) {
    return {
      category: "validation",
      messageCode: "api.validation",
      retryable: false,
    };
  }

  if (status === 403) {
    return {
      category: "forbidden",
      messageCode: "api.forbidden",
      retryable: false,
      action: "contactAdmin",
    };
  }

  if (status === 404) {
    return {
      category: "notFound",
      messageCode: "api.notFound",
      retryable: false,
      action: "goBack",
    };
  }

  if (status === 409) {
    return {
      category: "conflict",
      messageCode: "api.conflict",
      retryable: true,
      action: "refresh",
    };
  }

  if (status === 429) {
    return {
      category: "rateLimited",
      messageCode: "api.rateLimited",
      retryable: true,
      action: "retry",
    };
  }

  if (status >= 500) {
    return {
      category: "serverError",
      messageCode: "api.serverError",
      retryable: true,
      action: "retry",
    };
  }

  // Unrecognized status — treat as network error
  return {
    category: "networkError",
    messageCode: "api.networkError",
    retryable: true,
    action: "retry",
  };
}

// --- showToast ---

const DURATION_MAP: Record<string, number> = {
  success: 4000,
  error: Infinity,
  warning: 6000,
  info: 4000,
};

export function showToast(
  type: "success" | "error" | "warning" | "info",
  messageCode: string,
  options?: {
    namespace?: MessageNamespace;
    interpolations?: Record<string, string>;
    onRetry?: () => void;
  },
): void {
  const { t } = createMessages(options?.namespace ?? "errors");
  const message = t(messageCode, options?.interpolations);
  const duration = DURATION_MAP[type];

  const toastOptions: Record<string, unknown> = { duration };

  if (type === "error" && options?.onRetry) {
    toastOptions.action = {
      label: "Try again",
      onClick: options.onRetry,
    };
  }

  toast[type](message, toastOptions);
}

// --- scrollToFirstError ---

export function scrollToFirstError(): void {
  const invalid = document.querySelector<HTMLElement>(
    '[aria-invalid="true"], .text-red-600',
  );
  if (invalid) {
    invalid.scrollIntoView({ behavior: "smooth", block: "center" });
    invalid.focus();
  }
}
