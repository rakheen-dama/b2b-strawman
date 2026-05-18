package io.b2mash.b2b.b2bstrawman.integration.accounting.xero;

import java.util.UUID;

/** Published after a Xero connection is disconnected and tokens are revoked. */
public record XeroConnectionRevokedEvent(UUID connectionId, String xeroOrgName, UUID memberId) {}
