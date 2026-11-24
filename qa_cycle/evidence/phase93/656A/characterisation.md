# 656A — characterisation run (reproduce-before-fix)

The one existing behaviour 656A changes is "a matters batch cannot be committed, and its dry run checks no client" (main has no matters handler: the generic handler serves MATTERS and the claim is refused 409 `TAKE_ON_COMMIT_NOT_AVAILABLE`).

Run: `MattersDatasetHandler`'s `@Component` commented out (= main: no matters handler or committer bean), then `./mvnw test -Dspotless.check.skip=true -Dtest=MattersTakeOnIntegrationTest` on the 656A branch; the annotation was restored afterwards. Every case is red:

```
[ERROR]   MattersTakeOnIntegrationTest.aClientOffboardedBetweenValidationAndCommit_failsThatRowWithTheGuardsText:547->commit:559 » TakeOnProblem 409 CONFLICT, ProblemDetail[type='null', title='Commit not available yet', status=409
[ERROR]   MattersTakeOnIntegrationTest.aClientWhoseLifecycleRefusesAMatter_orAnAmbiguousIdNumber_isAnError:331->assertError:650 
[ERROR]   MattersTakeOnIntegrationTest.aCommitThatStopsMidBatch_resumesWhereItStopped_andCreatesEachMatterOnce:499 » TakeOnProblem 409 CONFLICT, ProblemDetail[type='null', title='Commit not available yet', status=409, detail='This
[ERROR]   MattersTakeOnIntegrationTest.aLeadWhoLeftBeforeTheCommit_isAWarningOnTheCreatedRow_notAFailure:467 
[ERROR]   MattersTakeOnIntegrationTest.anOrphanOrAMissingClientReference_isAnError_andTheValidRowsStillCommit:272 
[ERROR]   MattersTakeOnIntegrationTest.commit_createsOrdinaryMatters_leadClientLinkPortalAndAudit_withNoTemplate:200->commit:559 » TakeOnProblem 409 CONFLICT, ProblemDetail[type='null', title='Commit not available yet', status=409
[ERROR]   MattersTakeOnIntegrationTest.duplicates_byReferenceOrByNameOnTheClient_areSkipped_andAnInFileRepeatIsAnError:371 
[ERROR]   MattersTakeOnIntegrationTest.theClient_resolvesByEmailThenPreviousSystemIdOfAnEarlierBatchThenIdNumber:170 
[ERROR]   MattersTakeOnIntegrationTest.theResponsibleAttorney_becomesTheLead_andAnUnknownOneIsAWarning:422 
[ERROR] Tests run: 9, Failures: 6, Errors: 3, Skipped: 0
[ERROR] Tests run: 9, Failures: 6, Errors: 3, Skipped: 0, Time elapsed: 15.73 s <<< FAILURE! -- in io.b2mash.b2b.b2bstrawman.takeon.MattersTakeOnIntegrationTest
io.b2mash.b2b.b2bstrawman.takeon.TakeOnProblems$TakeOnProblemException: 409 CONFLICT, ProblemDetail[type='null', title='Commit not available yet', status=409, detail='This take-on cannot be committed yet.'
```

With the handler registered the same class is 9/9 green (Tier 2 run, see the PR body).
