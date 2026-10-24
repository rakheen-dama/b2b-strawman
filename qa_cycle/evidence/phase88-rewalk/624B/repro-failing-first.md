# 624B failing-first repro (pre-fix, base 13420b84b)

Command: ./mvnw test -Dtest="PortalAuthIntegrationTest,PortalTenantIsolationAuthTest,MagicLinkTokenIntegrationTest" -q

```
[ERROR]   MagicLinkTokenIntegrationTest.perContactCapIsSwallowedIntoTheGenericResponse Status expected:<200> but was:<401>
[ERROR]   MagicLinkTokenIntegrationTest.shouldResolveContactWithoutOrgId Status expected:<200> but was:<400>
[ERROR]   PortalAuthIntegrationTest.blankOrgIdIsTreatedAsEmailOnly Status expected:<200> but was:<400>
[ERROR]   PortalAuthIntegrationTest.emailOnlyMatchesAContactStoredInMixedCase Status expected:<200> but was:<400>
[ERROR]   PortalAuthIntegrationTest.emailOnlyOneFirmSendsOneBrandedLink Status expected:<200> but was:<400>
[ERROR]   PortalAuthIntegrationTest.emailOnlyTwoFirmsSendsOneBrandedLinkPerFirm Status expected:<200> but was:<400>
[ERROR]   PortalAuthIntegrationTest.emailOnlyUnknownEmailSendsNothing Status expected:<200> but was:<400>
[ERROR]   PortalAuthIntegrationTest.genericMessageIsIdenticalForZeroOneAndManyFirms Status expected:<200> but was:<400>
[ERROR]   PortalAuthIntegrationTest.perEmailCapAppliesAcrossFirmsAndRequestShapes Status expected:<200> but was:<400>
[ERROR]   PortalAuthIntegrationTest.perEmailCapTreatsUnknownEmailsTheSame Status expected:<200> but was:<400>
[ERROR]   PortalTenantIsolationAuthTest.directoryTenantSchemaIsNeverUsedForRouting:246 Status expected:<200> but was:<400>
[ERROR]   PortalTenantIsolationAuthTest.multiFirmTokensAreEachBoundToTheirOwnFirm:184 Status expected:<200> but was:<400>
[ERROR]   PortalTenantIsolationAuthTest.staleDirectoryRowIsANoOp:225 Status expected:<200> but was:<400>
[ERROR] Tests run: 42, Failures: 13, Errors: 0, Skipped: 0
```
