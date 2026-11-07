// Comment helpers shared by the add form and the edit dialog (634E(3), 634C). Pure functions only:
// safe in client and server code.

/**
 * Matter (PROJECT) comments are the client thread and are always SHARED: the backend rejects any
 * other visibility for a PROJECT target, on create and on edit. The forms offer no choice for them.
 */
export function isVisibilityLocked(entityType: string): boolean {
  return entityType === "PROJECT";
}

/** A member who can be @-mentioned on a matter comment. */
export interface MentionCandidate {
  id: string;
  name: string;
}

/**
 * The @-token being typed just before the cursor. Mirrors the backend's MENTION_PATTERN
 * (NotificationService): an "@" at the start or after a character that is not a word character,
 * ".", "+" or "-" (so "a@b.com" is not a mention), followed by Unicode word characters.
 */
const MENTION_TOKEN = /(^|[^\p{L}\p{N}_.+-])@([\p{L}\p{N}_]*)$/u;

/** At most this many candidates are listed at once. */
export const MENTION_SUGGESTION_LIMIT = 8;

export interface MentionQuery {
  /** The text typed after "@" (may be empty). */
  query: string;
  /** Index of the "@" in the text. */
  start: number;
}

/** The mention being typed at `cursor`, or null when the cursor is not in an @-token. */
export function findMentionQuery(text: string, cursor: number): MentionQuery | null {
  const match = MENTION_TOKEN.exec(text.slice(0, cursor));
  if (!match) return null;
  const query = match[2];
  return { query, start: cursor - query.length - 1 };
}

/**
 * Candidates whose name, or any word of it, starts with `query` (case-insensitive), in their
 * given order, capped at `MENTION_SUGGESTION_LIMIT`.
 */
export function filterMentionCandidates(
  candidates: MentionCandidate[],
  query: string
): MentionCandidate[] {
  const q = query.toLocaleLowerCase();
  return candidates
    .filter((candidate) => {
      if (!q) return true;
      const name = candidate.name.toLocaleLowerCase();
      return name.startsWith(q) || name.split(/\s+/).some((word) => word.startsWith(q));
    })
    .slice(0, MENTION_SUGGESTION_LIMIT);
}

/**
 * Replaces the @-token from `start` to `cursor` with "@Full Name " and returns the new
 * text and the cursor position just after the inserted space.
 */
export function insertMention(
  text: string,
  start: number,
  cursor: number,
  name: string
): { text: string; cursor: number } {
  const inserted = `@${name} `;
  return {
    text: text.slice(0, start) + inserted + text.slice(cursor),
    cursor: start + inserted.length,
  };
}
