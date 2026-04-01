// Court date types
export type CourtDateType =
  | "HEARING"
  | "TRIAL"
  | "MOTION"
  | "CONFERENCE"
  | "MEDIATION"
  | "ARBITRATION"
  | "MENTION"
  | "OTHER";

export type CourtDateStatus =
  | "SCHEDULED"
  | "POSTPONED"
  | "HEARD"
  | "CANCELLED";

export interface CourtDate {
  id: string;
  projectId: string;
  projectName: string;
  customerId: string;
  customerName: string;
  dateType: CourtDateType;
  scheduledDate: string; // ISO date "2026-03-15"
  scheduledTime: string | null; // "09:00"
  courtName: string;
  courtReference: string | null;
  judgeMagistrate: string | null;
  description: string | null;
  status: CourtDateStatus;
  outcome: string | null;
  reminderDays: number;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

// Prescription types
export type PrescriptionType =
  | "GENERAL_3Y"
  | "DEBT_6Y"
  | "MORTGAGE_30Y"
  | "DELICT_3Y"
  | "CONTRACT_3Y"
  | "CUSTOM";

export type PrescriptionStatus =
  | "RUNNING"
  | "WARNED"
  | "INTERRUPTED"
  | "EXPIRED";

export interface PrescriptionTracker {
  id: string;
  projectId: string;
  projectName: string;
  customerId: string;
  customerName: string;
  causeOfActionDate: string; // ISO date
  prescriptionType: PrescriptionType;
  customYears: number | null;
  prescriptionDate: string; // ISO date — computed by backend
  interruptionDate: string | null;
  interruptionReason: string | null;
  status: PrescriptionStatus;
  notes: string | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

// Conflict check types
export type ConflictCheckResult =
  | "NO_CONFLICT"
  | "CONFLICT_FOUND"
  | "POTENTIAL_CONFLICT";

export type ConflictCheckType =
  | "NEW_CLIENT"
  | "NEW_MATTER"
  | "PERIODIC_REVIEW";

export type ConflictResolution =
  | "PROCEED"
  | "DECLINED"
  | "WAIVER_OBTAINED"
  | "REFERRED";

export interface ConflictMatch {
  adversePartyId: string;
  adversePartyName: string;
  projectId: string;
  projectName: string;
  customerId: string;
  customerName: string;
  relationship: string;
  matchType: string; // NAME_SIMILARITY, ID_NUMBER_EXACT, REGISTRATION_NUMBER_EXACT
  similarityScore: number;
  explanation: string;
}

export interface ConflictCheck {
  id: string;
  checkedName: string;
  checkedIdNumber: string | null;
  checkedRegistrationNumber: string | null;
  checkType: ConflictCheckType;
  result: ConflictCheckResult;
  conflictsFound: ConflictMatch[] | null;
  resolution: ConflictResolution | null;
  resolutionNotes: string | null;
  waiverDocumentId: string | null;
  checkedBy: string;
  resolvedBy: string | null;
  checkedAt: string;
  resolvedAt: string | null;
  customerId: string | null;
  projectId: string | null;
}

// Adverse party types
export type AdversePartyType =
  | "NATURAL_PERSON"
  | "COMPANY"
  | "TRUST"
  | "CLOSE_CORPORATION"
  | "PARTNERSHIP"
  | "OTHER";

export interface AdverseParty {
  id: string;
  name: string;
  idNumber: string | null;
  registrationNumber: string | null;
  partyType: AdversePartyType;
  aliases: string | null;
  notes: string | null;
  linkedMatterCount: number;
  createdAt: string;
  updatedAt: string;
}

export type AdversePartyRelationship =
  | "OPPOSING_PARTY"
  | "WITNESS"
  | "CO_ACCUSED"
  | "RELATED_ENTITY"
  | "GUARANTOR";

export interface AdversePartyLink {
  id: string;
  adversePartyId: string;
  adversePartyName: string;
  projectId: string;
  projectName: string;
  customerId: string;
  customerName: string;
  relationship: AdversePartyRelationship;
  description: string | null;
  createdAt: string;
}

// Tariff types
export interface TariffSchedule {
  id: string;
  name: string;
  code: string;
  description: string | null;
  effectiveFrom: string;
  effectiveTo: string | null;
  active: boolean;
  itemCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface TariffItem {
  id: string;
  scheduleId: string;
  itemNumber: string;
  description: string;
  unit: string;
  rateInCents: number;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}
