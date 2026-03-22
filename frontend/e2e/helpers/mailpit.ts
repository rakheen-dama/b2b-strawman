export const MAILPIT_API = process.env.MAILPIT_API_URL || 'http://localhost:8025/api/v1'

export interface MailpitMessage {
  ID: string
  Subject: string
  From: { Address: string; Name: string }
  To: Array<{ Address: string; Name: string }>
  Text: string
  HTML: string
  Created: string
}

interface MailpitSearchResult {
  messages: MailpitMessage[]
  total: number
}

/**
 * Waits for an email to arrive at the given recipient address.
 * Polls Mailpit API until a matching message is found or timeout expires.
 */
export async function waitForEmail(
  recipient: string,
  options?: { subject?: string; timeout?: number }
): Promise<MailpitMessage> {
  const timeout = options?.timeout ?? 30_000
  const pollInterval = 2_000
  const start = Date.now()

  while (Date.now() - start < timeout) {
    const res = await fetch(
      `${MAILPIT_API}/search?query=${encodeURIComponent(`to:${recipient}`)}`
    )
    if (!res.ok) throw new Error(`Mailpit search failed: ${res.status}`)

    const data: MailpitSearchResult = await res.json()

    const match = data.messages.find((msg) => {
      if (options?.subject) {
        return msg.Subject.toLowerCase().includes(options.subject.toLowerCase())
      }
      return true
    })

    if (match) {
      // Search results lack message bodies — fetch the full message
      const fullRes = await fetch(`${MAILPIT_API}/message/${match.ID}`)
      if (!fullRes.ok) throw new Error(`Mailpit message fetch failed: ${fullRes.status}`)
      return (await fullRes.json()) as MailpitMessage
    }

    await new Promise((r) => setTimeout(r, pollInterval))
  }

  throw new Error(
    `No email for ${recipient} within ${timeout}ms` +
      (options?.subject ? ` (subject: "${options.subject}")` : '')
  )
}

/**
 * Extracts a 6-digit OTP code from email body text.
 */
export function extractOtp(email: MailpitMessage): string {
  // Match 6-digit code — adapt regex if OTP format changes
  const match = (email.Text || email.HTML).match(/\b(\d{6})\b/)
  if (!match) throw new Error(`No 6-digit OTP found in email: ${email.Subject}`)
  return match[1]
}

/**
 * Extracts a Keycloak invitation/registration link from email body.
 * Keycloak invite emails contain a link to the registration page.
 */
export function extractInviteLink(email: MailpitMessage): string {
  // Match Keycloak registration URLs
  const body = email.HTML || email.Text
  const match = body.match(
    /https?:\/\/[^\s"<]+\/realms\/[^\s"<]+\/protocol\/openid-connect\/auth[^\s"<]*/
  )
  if (!match) {
    // Fallback: look for any URL containing "registration" or "action-token"
    const fallback = body.match(
      /https?:\/\/[^\s"<]*(?:registration|action-token)[^\s"<]*/
    )
    if (!fallback) {
      throw new Error(`No invite link found in email: ${email.Subject}`)
    }
    return fallback[0]
  }
  return match[0]
}

/**
 * Deletes all emails in Mailpit. Call in test setup for a clean slate.
 */
export async function clearMailbox(): Promise<void> {
  const res = await fetch(`${MAILPIT_API}/messages`, { method: 'DELETE' })
  if (!res.ok) throw new Error(`Mailpit clearMailbox failed: ${res.status}`)
}

/**
 * Returns all emails for a given recipient.
 */
export async function getEmails(recipient: string): Promise<MailpitMessage[]> {
  const res = await fetch(
    `${MAILPIT_API}/search?query=${encodeURIComponent(`to:${recipient}`)}`
  )
  if (!res.ok) throw new Error(`Mailpit search failed: ${res.status}`)
  const data: MailpitSearchResult = await res.json()
  return data.messages
}
