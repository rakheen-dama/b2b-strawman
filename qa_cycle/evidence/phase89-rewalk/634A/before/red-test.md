# 634A red evidence (G88-F3) — run at HEAD f52bf21e0, before any fix

## Backend — FieldGroupMembershipIntegrationTest (only the new test file added; no `$B` changes)

Command: `./mvnw test -Dtest="FieldGroupMembershipIntegrationTest" -q` → TARGETED TESTS FAILED

```
-------------------------------------------------------------------------------
Test set: io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupMembershipIntegrationTest
-------------------------------------------------------------------------------
Tests run: 8, Failures: 4, Errors: 4, Skipped: 0, Time elapsed: 12.10 s <<< FAILURE! -- in io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupMembershipIntegrationTest
io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupMembershipIntegrationTest.responseShape_everyProducerCarriesFieldDefinitionIds -- Time elapsed: 0.433 s <<< FAILURE!
org.opentest4j.AssertionFailedError: 

Expecting actual:
  []
to contain exactly (and in same order):
  ["72e6ecc2-66e6-49a8-b7b1-306afe69d770", "c57a73a5-fe0a-46d8-877e-c4a18d3050e4"]
but could not find the following elements:
  ["72e6ecc2-66e6-49a8-b7b1-306afe69d770", "c57a73a5-fe0a-46d8-877e-c4a18d3050e4"]


io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupMembershipIntegrationTest.put_auditsOneUpdatedRowWithCounts -- Time elapsed: 0.058 s <<< FAILURE!
java.lang.AssertionError: 

Expecting actual:
  ["null/null", "null/null"]
to contain exactly in any order:
  ["2/0", "1/1"]
elements not found:
  ["2/0", "1/1"]
and elements not expected:
  ["null/null", "null/null"]


io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupMembershipIntegrationTest.put_rejectsBadMembership_andRollsBack -- Time elapsed: 0.050 s <<< FAILURE!
java.lang.AssertionError: Status expected:<400> but was:<200>

io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupMembershipIntegrationTest.create_withFieldDefinitionIds_createsMembers -- Time elapsed: 0.026 s <<< ERROR!
com.jayway.jsonpath.PathNotFoundException: No results for path: $['fieldDefinitionIds']

io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupMembershipIntegrationTest.put_withoutField_leavesMembershipUnchanged -- Time elapsed: 0.038 s <<< ERROR!
com.jayway.jsonpath.PathNotFoundException: No results for path: $['fieldDefinitionIds']

io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupMembershipIntegrationTest.put_withAB_setsMembersAB -- Time elapsed: 0.033 s <<< ERROR!
com.jayway.jsonpath.PathNotFoundException: No results for path: $['fieldDefinitionIds']

io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupMembershipIntegrationTest.put_withBC_afterAB_replacesInOrder -- Time elapsed: 0.051 s <<< FAILURE!
org.opentest4j.AssertionFailedError: 

Expecting actual:
  []
to contain exactly (and in same order):
  ["3ee7858d-eb0e-412b-91ae-2f315ebe7afa", "57e57dfd-59c4-4784-b0bc-db155fd7ab53"]
but could not find the following elements:
  ["3ee7858d-eb0e-412b-91ae-2f315ebe7afa", "57e57dfd-59c4-4784-b0bc-db155fd7ab53"]


io.b2mash.b2b.b2bstrawman.fielddefinition.FieldGroupMembershipIntegrationTest.packSeededGroup_isEditable_andReconcileStaysAdditive -- Time elapsed: 0.018 s <<< ERROR!
com.jayway.jsonpath.PathNotFoundException: No results for path: $['fieldDefinitionIds']

```

Reading: the PUT returns 200 but membership stays `[]` (Jackson drops the unknown `fieldDefinitionIds`), the response has no `fieldDefinitionIds`, the `field_group.updated` audit has no `added`/`removed` counts, and a cross-entity-type / duplicate / null id list is silently accepted (200) instead of rejected.

## Frontend — vitest red cases (tests added, no production FE change)

Command: `pnpm vitest run __tests__/field-definitions/FieldGroupDialog.test.tsx __tests__/settings/custom-fields.test.tsx` → exit 1

```
     × lists member field labels on the group card in membership order 29ms
     × skips member ids with no active field and shows the empty copy 16ms
     × edit dialog opens with the group's fields pre-checked 81ms
⎯⎯⎯⎯⎯⎯⎯ Failed Tests 3 ⎯⎯⎯⎯⎯⎯⎯
 FAIL  __tests__/settings/custom-fields.test.tsx > CustomFieldsContent > lists member field labels on the group card in membership order
TestingLibraryElementError: Unable to find an accessible element with the role "list" and name "Fields in Financial Info"
 FAIL  __tests__/settings/custom-fields.test.tsx > CustomFieldsContent > skips member ids with no active field and shows the empty copy
TestingLibraryElementError: Unable to find an element with the text: No fields in this group yet.. This could be because the text is broken up by multiple elements. In this case, you can provide a function for your text matcher to make your matcher more flexible.
 FAIL  __tests__/settings/custom-fields.test.tsx > CustomFieldsContent > edit dialog opens with the group's fields pre-checked
TestingLibraryElementError: Unable to find an element with the text: 1 field selected. This could be because the text is broken up by multiple elements. In this case, you can provide a function for your text matcher to make your matcher more flexible.
 Test Files  1 failed | 1 passed (2)
      Tests  3 failed | 13 passed (16)
```

Reading: the settings card renders no member labels, and opening Edit from the card (`custom-fields-content.tsx:281-287`, no `initialFieldIds`) shows the "Edit Field Group" dialog with nothing pre-selected — saving it would send `fieldDefinitionIds: []`. `FieldGroupDialog` itself pre-checks correctly when given `initialFieldIds` (the dialog-level cases are green at HEAD), so the FE defect is the settings mount.
