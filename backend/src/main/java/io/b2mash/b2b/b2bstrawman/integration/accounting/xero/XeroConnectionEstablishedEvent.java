package io.b2mash.b2b.b2bstrawman.integration.accounting.xero;

import java.util.UUID;

/** Published after a Xero OAuth2 connection is established and tokens are stored. */
public record XeroConnectionEstablishedEvent(
    UUID connectionId, String xeroOrgName, UUID memberId) {}
