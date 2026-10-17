/**
 * Shared billing utility functions used across billing components.
 */

export function computeDaysRemaining(dateString: string): number {
  return Math.max(0, Math.ceil((new Date(dateString).getTime() - Date.now()) / 86_400_000));
}

export function formatDate(isoString: string): string {
  return new Date(isoString).toLocaleDateString("en-ZA", {
    dateStyle: "long",
  });
}
