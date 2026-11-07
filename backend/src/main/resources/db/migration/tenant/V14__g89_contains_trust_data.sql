-- G89-F52 / founder decision D18: a stored document that prints trust money (balances, ledger
-- lines, references) may be downloaded only by a member holding VIEW_TRUST. The flag is set at
-- generation time (GeneratedDocumentService / StatementService) and copied onto the linked
-- documents row; the download paths check it (TrustDataVisibility.requireMayRead).
-- Idempotent: ADD COLUMN IF NOT EXISTS, and the back-fill only ever sets false -> true.

ALTER TABLE generated_documents
    ADD COLUMN IF NOT EXISTS contains_trust_data boolean NOT NULL DEFAULT false;

ALTER TABLE documents
    ADD COLUMN IF NOT EXISTS contains_trust_data boolean NOT NULL DEFAULT false;

-- Back-fill documents generated before the flag existed: every Statement of Account (pack key, the
-- base slug or a statement-of-account-* clone slug), Client Trust Statement and trust receipt,
-- unless its snapshot records the trust figure as withheld (the generator lacked VIEW_TRUST).
-- Over-flagging an older statement that printed no trust money is the safe default.
UPDATE generated_documents gd
SET contains_trust_data = true
FROM document_templates dt
WHERE gd.template_id = dt.id
  AND gd.contains_trust_data = false
  AND (dt.pack_template_key IN ('statement-of-account', 'client-trust-statement', 'trust-receipt')
       OR dt.slug = 'statement-of-account'
       OR dt.slug LIKE 'statement-of-account-%')
  AND COALESCE(gd.context_snapshot -> 'summary' ->> 'trust_withheld', 'false') <> 'true';

-- The linked documents row (saved to the matter / client documents) follows its generated document.
UPDATE documents d
SET contains_trust_data = true
FROM generated_documents gd
WHERE gd.document_id = d.id
  AND gd.contains_trust_data = true
  AND d.contains_trust_data = false;
