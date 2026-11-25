// FR-3 (founder ruling 2026-11-24, 653A F-2): one size limit for every file sent through a server
// action. next.config.ts sets `serverActions.bodySizeLimit` to SERVER_ACTION_BODY_LIMIT ("3mb" =
// 3 MiB = 3 145 728 bytes). The file limit is 3 MB = 3 000 000 bytes, so the multipart framing and
// the other form fields fit under the body limit. The browser refuses a larger file before the
// action is called: a body over the limit never reaches the action, and Next's own refusal is not
// readable. The backend repeats the check where it has one (take-on: TakeOnFileReader).

export const SERVER_ACTION_BODY_LIMIT = "3mb";

export const SERVER_ACTION_FILE_MAX_BYTES = 3_000_000;

export const SERVER_ACTION_FILE_MAX_LABEL = "3 MB";

/** The refusal for a file over the limit, or null. Sizes are decimal MB, rounded up. */
export function fileTooLargeError(size: number): string | null {
  if (size <= SERVER_ACTION_FILE_MAX_BYTES) {
    return null;
  }
  const mb = (Math.ceil(size / 100_000) / 10).toFixed(1);
  return `File exceeds the ${SERVER_ACTION_FILE_MAX_LABEL} limit (${mb} MB)`;
}
