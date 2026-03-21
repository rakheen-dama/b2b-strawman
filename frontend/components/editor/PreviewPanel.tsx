"use client";

import { A4PreviewWrapper } from "@/components/documents/a4-preview-wrapper";

interface PreviewPanelProps {
  html: string;
  className?: string;
}

/**
 * Document preview panel that renders HTML in an A4-proportioned
 * scaled container with paper shadow and dark surround.
 */
export function PreviewPanel({ html, className }: PreviewPanelProps) {
  return <A4PreviewWrapper html={html} className={className} />;
}
