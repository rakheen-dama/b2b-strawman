/**
 * ADR-350 / 624E: the backend may skip a requested client email (no portal contact, or the
 * 5-minute dedup per client + matter + document type). Shown wherever "Email the client now"
 * is offered, so the copy never promises an email that may not be sent.
 */
export const CLIENT_EMAIL_CAVEAT =
  "No email is sent if the client has no portal contact, or if they were already emailed about this kind of document on this matter in the last 5 minutes.";
