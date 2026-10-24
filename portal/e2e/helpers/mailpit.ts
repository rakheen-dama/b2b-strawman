/**
 * Phase 88 624D — Mailpit helper for portal Playwright specs.
 *
 * A subset of `frontend/e2e/helpers/mailpit.ts`, ported so the portal suite
 * does not reach into the firm-side e2e tree. Defaults to the e2e stack's
 * Mailpit API (:8026), matching the portal helpers' `BACKEND_URL` default
 * (:8081). Override with MAILPIT_URL (the KC dev stack uses :8025).
 *
 * There is deliberately no `clearMailbox`: wiping the mailbox destroys other
 * runs' evidence. Callers take an id baseline with `listMessages` and then
 * wait for a message that is not in it.
 */
const MAILPIT_URL = process.env.MAILPIT_URL || "http://localhost:8026";

export interface MailpitMessage {
  ID: string;
  Subject: string;
  Created: string;
}

export interface MailpitMessageDetail {
  ID: string;
  Subject: string;
  Text: string;
  HTML: string;
  Created: string;
}

/** Every message addressed to `recipient` (Mailpit `to:` search). */
export async function listMessages(
  recipient: string,
): Promise<MailpitMessage[]> {
  const res = await fetch(
    `${MAILPIT_URL}/api/v1/search?query=${encodeURIComponent(`to:${recipient}`)}`,
  );
  if (!res.ok) {
    throw new Error(`Mailpit search failed: ${res.status}`);
  }
  const data: { messages?: MailpitMessage[] } = await res.json();
  return data.messages ?? [];
}

/** Ids of every message addressed to `recipient`. */
export async function listMessageIds(recipient: string): Promise<string[]> {
  return (await listMessages(recipient)).map((m) => m.ID);
}

async function getMessageDetail(id: string): Promise<MailpitMessageDetail> {
  const res = await fetch(`${MAILPIT_URL}/api/v1/message/${id}`);
  if (!res.ok) {
    throw new Error(`Mailpit get message failed: ${res.status}`);
  }
  return res.json();
}

/**
 * Polls until a message to `recipient` arrives whose id is not in `exclude`
 * (and whose subject contains `subject`, when given), then returns its detail.
 */
export async function waitForNewEmail(
  recipient: string,
  opts: {
    exclude: string[];
    subject?: string;
    timeout?: number;
    interval?: number;
  },
): Promise<MailpitMessageDetail> {
  const { exclude, subject, timeout = 30_000, interval = 1_000 } = opts;
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const match = (await listMessages(recipient)).find(
      (m) =>
        !exclude.includes(m.ID) && (!subject || m.Subject.includes(subject)),
    );
    if (match) {
      return getMessageDetail(match.ID);
    }
    await new Promise((r) => setTimeout(r, interval));
  }
  throw new Error(
    `Timed out waiting for a new email to ${recipient}` +
      `${subject ? ` ("${subject}")` : ""} (${timeout}ms)`,
  );
}

/**
 * Returns `pathname + search` of the `/auth/exchange` link in a portal
 * magic-link email, so it can be replayed against the Playwright baseURL.
 * The HTML href encodes `&` as `&amp;`; the Text part ends the link with `)`.
 */
export function extractExchangePath(mail: MailpitMessageDetail): string {
  const href =
    mail.HTML?.match(/href="([^"]*\/auth\/exchange\?[^"]+)"/)?.[1]?.replace(
      /&amp;/g,
      "&",
    ) ?? mail.Text?.match(/(https?:\/\/[^\s)]+\/auth\/exchange\?[^\s)]+)/)?.[1];
  if (!href) {
    throw new Error(`No /auth/exchange link in "${mail.Subject}"`);
  }
  const url = new URL(href);
  return url.pathname + url.search;
}
