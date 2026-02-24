import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

/** Returns true if `url` uses http: or https: protocol. */
export function isSafeImageUrl(url: string): boolean {
  try {
    const parsed = new URL(url);
    return parsed.protocol === "http:" || parsed.protocol === "https:";
  } catch {
    return false;
  }
}

const HEX_COLOR_RE = /^#[0-9a-fA-F]{3,8}$/;

/** Returns true if `color` is a valid hex color string (#RGB, #RRGGBB, #RRGGBBAA). */
export function isValidHexColor(color: string): boolean {
  return HEX_COLOR_RE.test(color);
}
