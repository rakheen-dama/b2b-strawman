-- V39: Add warnings column to generated_documents for template validation results
ALTER TABLE generated_documents
    ADD COLUMN warnings JSONB;
