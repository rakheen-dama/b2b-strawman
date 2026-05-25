/**
 * Shared utility functions for compliance finding display components.
 */

export function getSeverityBadgeVariant(
  severity: string
): "destructive" | "warning" | "success" | "neutral" {
  switch (severity.toUpperCase()) {
    case "CRITICAL":
      return "destructive";
    case "HIGH":
      return "destructive";
    case "MEDIUM":
      return "warning";
    case "LOW":
      return "success";
    case "INFO":
      return "neutral";
    default:
      return "neutral";
  }
}

export function getStatusBadgeVariant(
  status: string
): "destructive" | "warning" | "success" | "neutral" {
  switch (status.toUpperCase()) {
    case "OPEN":
      return "destructive";
    case "ACKNOWLEDGED":
      return "warning";
    case "IN_PROGRESS":
      return "warning";
    case "RESOLVED":
      return "success";
    case "FALSE_POSITIVE":
      return "neutral";
    default:
      return "neutral";
  }
}

export function formatCategoryName(category: string): string {
  return category
    .replace(/_/g, " ")
    .toLowerCase()
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

export function formatStatusName(status: string): string {
  return status
    .replace(/_/g, " ")
    .toLowerCase()
    .replace(/\b\w/g, (c) => c.toUpperCase());
}
