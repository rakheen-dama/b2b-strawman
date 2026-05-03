DSAR Audit Trail
================

This folder contains the full audit history of system events relating to
the data subject identified in the parent export pack.

FILES
-----

events.json  Machine-readable JSON array of audit events. Each element is
             a single event with fields: id, eventType, entityType,
             entityId, actorId, actorType, source, ipAddress, userAgent,
             details, occurredAt.

events.csv   Human-readable RFC 4180 CSV with one row per event. Columns:
             occurredAt, eventType, label, severity, entityType, entityId,
             actorId, actorDisplayName, actorType, source, ipAddress,
             userAgent, detailsJson. The first row is the header.

README.txt   This file.

SCOPE
-----

The audit trail includes events the system recorded in connection with the
data subject:
  * events whose entity is the data subject's customer record
  * events on the data subject's projects, invoices, proposals,
    information requests, documents, trust transactions, and acceptance
    requests
  * events that reference the data subject's customer ID in their
    metadata

POPIA Section 23
----------------

The audit trail is provided unsanitised, including internal notes and
operational metadata, in accordance with the Protection of Personal
Information Act, 2013 (POPIA), section 23, which entitles a data subject
to access personal information held by a responsible party.

Case-specific redactions (third-party privacy, legal privilege) are
handled at the DSAR fulfilment review stage, not as automatic policy.
