const MAILPIT_URL = process.env.MAILPIT_URL || 'http://localhost:8025'

interface MailpitMessage {
  ID: string
  From: { Name: string; Address: string }
  To: { Name: string; Address: string }[]
  Subject: string
  Snippet: string
  Created: string
}

export interface MailpitMessageDetail {
  ID: string
  From: { Name: string; Address: string }
  To: { Name: string; Address: string }[]
  Subject: string
  Text: string
  HTML: string
  Created: string
}

interface MailpitSearchResponse {
  total: number
  messages: MailpitMessage[]
}

/**
 * Clear all messages in Mailpit. Call in test setup.
 */
export async function clearMailbox(): Promise<void> {
  await fetch(`${MAILPIT_URL}/api/v1/messages`, { method: 'DELETE' })
}

/**
 * Search for emails matching a query. Uses Mailpit's search syntax.
 */
async function searchMessages(query: string): Promise<MailpitMessage[]> {
  const res = await fetch(
    `${MAILPIT_URL}/api/v1/search?query=${encodeURIComponent(query)}`
  )
  if (!res.ok) {
    throw new Error(`Mailpit search failed: ${res.status}`)
  }
  const data: MailpitSearchResponse = await res.json()
  return data.messages ?? []
}

/**
 * Get the full message detail (including HTML/Text body) by ID.
 */
async function getMessageDetail(id: string): Promise<MailpitMessageDetail> {
  const res = await fetch(`${MAILPIT_URL}/api/v1/message/${id}`)
  if (!res.ok) {
    throw new Error(`Mailpit get message failed: ${res.status}`)
  }
  return res.json()
}

/**
 * Wait for an email to arrive for a specific recipient.
 * Polls Mailpit until a matching email is found or timeout is reached.
 */
export async function waitForEmail(
  recipient: string,
  opts: { subject?: string; timeout?: number; interval?: number } = {}
): Promise<MailpitMessageDetail> {
  const { subject, timeout = 30_000, interval = 2_000 } = opts
  const deadline = Date.now() + timeout

  while (Date.now() < deadline) {
    const query = `to:${recipient}`
    const messages = await searchMessages(query)

    const matches = subject
      ? messages.filter((m) =>
          m.Subject.toLowerCase().includes(subject.toLowerCase())
        )
      : messages

    if (matches.length > 0) {
      const latest = matches.sort(
        (a, b) =>
          new Date(b.Created).getTime() - new Date(a.Created).getTime()
      )[0]
      return getMessageDetail(latest.ID)
    }

    await new Promise((r) => setTimeout(r, interval))
  }

  throw new Error(
    `Timed out waiting for email to ${recipient}${subject ? ` with subject "${subject}"` : ''} (${timeout}ms)`
  )
}

/**
 * Extract a 6-digit OTP code from an email body.
 */
export function extractOtp(email: MailpitMessageDetail): string {
  const body = email.HTML || email.Text
  const match = body.match(/\b(\d{6})\b/)
  if (!match) {
    throw new Error(`No 6-digit OTP found in email "${email.Subject}"`)
  }
  return match[1]
}

/**
 * Extract an invitation/registration link from a Keycloak email.
 */
export function extractInviteLink(email: MailpitMessageDetail): string {
  const body = email.HTML || email.Text
  // Keycloak org-invite.ftl template wraps the link in <a href="...">
  const hrefMatch = body.match(
    /href="(https?:\/\/[^"]*(?:action-token|registration|orgs\/invite)[^"]*)"/i
  )
  if (hrefMatch) {
    return hrefMatch[1]
  }
  // Fallback: any Keycloak realm link
  const realmMatch = body.match(
    /(https?:\/\/[^\s"<]+realms\/[^\s"<]+)/i
  )
  if (realmMatch) {
    return realmMatch[1]
  }
  throw new Error(`No invitation link found in email "${email.Subject}"`)
}

/**
 * Get all emails for a recipient.
 */
export async function getEmails(
  recipient: string
): Promise<MailpitMessage[]> {
  return searchMessages(`to:${recipient}`)
}
