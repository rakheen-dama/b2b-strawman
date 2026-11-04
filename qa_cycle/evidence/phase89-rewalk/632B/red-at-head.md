# 632B red at HEAD ec2906de4 (2026-11-04)

Command: `./mvnw test -Dtest="V10PaymentReceiptColumnsTest,PaymentReceiptIntegrationTest,PortalInvoiceReceiptIntegrationTest" -q`
Result: `Tests run: 3, Failures: 1, Errors: 2, Skipped: 0`

1. V10PaymentReceiptColumnsTest.migrateScratchSchemaFromV9:103
   `Flyway No migration with a target version 10 could be found. Ensure target is specified correctly and the migration exists.`

2. PaymentReceiptIntegrationTest.partPaymentGeneratesReceiptR1LinkedFromThePaymentEvent:117
   `BadSqlGrammar PreparedStatementCallback; bad SQL grammar [SELECT receipt_generated_document_id, receipt_number FROM "tenant_23cc7dbd5839".payment_events WHERE id = ?::uuid]`
   `Caused by: org.postgresql.util.PSQLException: ERROR: column "receipt_generated_document_id" does not exist`
   (POST /api/invoices/{id}/payment part payment 200 PARTIALLY_PAID, but no receipt column / no receipt document)

3. PortalInvoiceReceiptIntegrationTest.feeNoteDownloadServesTheFeeNoteEvenWhenANewerInvoiceDocumentExists:177
   GET /portal/invoices/{id}/download returned the NEWEST INVOICE document, not the fee note:
   Expecting actual:
     "http://test-storage/test-bucket/org/tenant_fd01c286ef65/generated/3adf93ef-bb3f-49bf-915a-e4f90049e830/firm-later-fee-note-document-inv-0001-2026-11-04.pdf?download=true"
   to contain:
     "org/tenant_fd01c286ef65/generated/1b01fd1b-73a2-4569-8df3-a9fcaf1fccc6/fee-note-inv-0001-2026-11-04.pdf"
