/**
 * UI strings for the specialist launcher and panel.
 *
 * No i18n framework is integrated yet (no `next-intl`, no `messages/en.json`).
 * This module is the single anchor for specialist UI strings so a future i18n
 * migration has one place to thread.
 */

export const SPECIALIST_STRINGS = {
  poweredByAi: "Powered by AI",
  handOffToGeneralist: "Hand off to generalist",
  aiThinking: "AI is thinking…",
  approveAllSuggestions: "Approve all suggestions",
  panelHeaderFallbackTagline: "Specialist assistant",
  errorStartingSession: "Could not start specialist session. Please try again.",
  closePanel: "Close specialist",
  defaultLauncherLabel: "Ask specialist",
  inputPlaceholder: "Type a message...",
  sendMessage: "Send message",
  stopStreaming: "Stop streaming",
  // Billing specialist
  billingPolishLabel: "Polish with AI",
  billingGroupingLabel: "Suggest line-item grouping",
  billingDiffTitle: "Proposed Changes",
  billingDiffAccept: "Accept",
  billingDiffReject: "Reject",
  billingDiffEdit: "Edit",
  billingDiffApproveAll: "Approve all changes",
  billingDiffRejectAll: "Reject all changes",
  billingGroupingTitle: "Proposed Line Groups",
  // Intake specialist
  intakeExtractLabel: "Extract from uploaded documents",
  intakeInfoRequestLabel: "Extract client-supplied fields",
  intakePrereqLabel: "Fill in from uploads",
  intakeDiffTitle: "Proposed Field Extractions",
  intakeDiffAccept: "Accept",
  intakeDiffReject: "Reject",
  intakeDiffEdit: "Edit",
  intakeDiffApproveAll: "Approve all fields",
  intakeDiffRejectAll: "Reject all fields",
  intakeVisionBadge: "VISION",
  intakeTextBadge: "TEXT",
  intakePopiaBadge: "POPIA",
  intakeInjectionWarning: "This document may contain instructions for the AI — review carefully",
  intakeRsaIdChecksumWarning: "RSA ID checksum invalid",
  intakeEmptyState: "No documents to extract from",
} as const;

export type SpecialistStringKey = keyof typeof SPECIALIST_STRINGS;
