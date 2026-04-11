"use client";

import React, { Component, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { AlertTriangle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { createMessages } from "@/lib/messages";

// --- ErrorFallback ---

interface ErrorFallbackProps {
  onReset: () => void;
}

export function ErrorFallback({ onReset }: ErrorFallbackProps) {
  const router = useRouter();
  const { t } = createMessages("errors");

  return (
    <div className="flex flex-col items-center gap-4 py-24 text-center">
      <AlertTriangle className="size-12 text-red-500" />
      <h2 className="font-display text-xl text-slate-900 dark:text-slate-100">
        {t("boundary.heading")}
      </h2>
      <p className="max-w-md text-sm text-slate-600 dark:text-slate-400">
        {t("boundary.description")}
      </p>
      <div className="flex gap-2">
        <Button size="sm" variant="outline" onClick={onReset}>
          {t("boundary.tryAgain")}
        </Button>
        <Button size="sm" variant="outline" onClick={() => window.location.reload()}>
          {t("boundary.refreshPage")}
        </Button>
        <Button size="sm" variant="outline" onClick={() => router.back()}>
          {t("boundary.goBack")}
        </Button>
      </div>
    </div>
  );
}

// --- ErrorBoundary ---

interface ErrorBoundaryProps {
  children: ReactNode;
  fallback?: ReactNode;
}

interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    // Let Next.js handle its own internal errors (notFound, redirect)
    const digest = (error as { digest?: string }).digest;
    if (digest === "NEXT_NOT_FOUND" || digest?.startsWith("NEXT_REDIRECT")) {
      throw error;
    }
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo): void {
    if (process.env.NODE_ENV === "development") {
      console.error("[ErrorBoundary]", error, errorInfo);
    }
  }

  private handleReset = () => {
    this.setState({ hasError: false, error: null });
  };

  render(): ReactNode {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }
      return <ErrorFallback onReset={this.handleReset} />;
    }
    return this.props.children;
  }
}
