import { cn } from "@/lib/utils";

const PALETTES = [
  "bg-olive-200 text-olive-700 dark:bg-olive-700 dark:text-olive-200",
  "bg-indigo-100 text-indigo-700 dark:bg-indigo-800 dark:text-indigo-200",
  "bg-amber-100 text-amber-700 dark:bg-amber-800 dark:text-amber-200",
] as const;

function hashName(name: string): number {
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = (hash << 5) - hash + name.charCodeAt(i);
    hash |= 0;
  }
  return Math.abs(hash);
}

function getInitials(name: string): string {
  const parts = name.trim().split(/\s+/);
  if (parts.length >= 2) {
    return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
  }
  return (parts[0]?.[0] ?? "?").toUpperCase();
}

interface AvatarCircleProps {
  name: string;
  size?: number;
  className?: string;
}

export function AvatarCircle({ name, size = 32, className }: AvatarCircleProps) {
  const palette = PALETTES[hashName(name) % PALETTES.length];
  const initials = getInitials(name);
  const fontSize = Math.round(size * 0.4);

  return (
    <span
      className={cn(
        "inline-flex shrink-0 items-center justify-center rounded-full font-medium select-none",
        palette,
        className
      )}
      style={{ width: size, height: size, fontSize }}
      aria-hidden="true"
    >
      {initials}
    </span>
  );
}
