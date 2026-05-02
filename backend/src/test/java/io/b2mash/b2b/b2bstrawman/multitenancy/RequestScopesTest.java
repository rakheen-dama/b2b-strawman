package io.b2mash.b2b.b2bstrawman.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

class RequestScopesTest {

  @Test
  void tenantIdBoundWithinScope() {
    ScopedValue.where(RequestScopes.TENANT_ID, "tenant_a1b2c3d4e5f6")
        .run(
            () -> {
              assertThat(RequestScopes.TENANT_ID.get()).isEqualTo("tenant_a1b2c3d4e5f6");
              assertThat(RequestScopes.TENANT_ID.isBound()).isTrue();
            });
  }

  @Test
  void tenantIdUnboundOutsideScope() {
    assertThat(RequestScopes.TENANT_ID.isBound()).isFalse();
    assertThatThrownBy(() -> RequestScopes.TENANT_ID.get())
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void tenantIdAutoUnboundsAfterScope() {
    ScopedValue.where(RequestScopes.TENANT_ID, "tenant_a1b2c3d4e5f6")
        .run(
            () -> {
              assertThat(RequestScopes.TENANT_ID.isBound()).isTrue();
            });
    assertThat(RequestScopes.TENANT_ID.isBound()).isFalse();
  }

  @Test
  void nestedScopeShadowsOuter() {
    ScopedValue.where(RequestScopes.TENANT_ID, "outer")
        .run(
            () -> {
              assertThat(RequestScopes.TENANT_ID.get()).isEqualTo("outer");

              ScopedValue.where(RequestScopes.TENANT_ID, "inner")
                  .run(
                      () -> {
                        assertThat(RequestScopes.TENANT_ID.get()).isEqualTo("inner");
                      });

              assertThat(RequestScopes.TENANT_ID.get()).isEqualTo("outer");
            });
  }

  @Test
  void memberIdBoundWithinScope() {
    UUID id = UUID.randomUUID();
    ScopedValue.where(RequestScopes.MEMBER_ID, id)
        .run(
            () -> {
              assertThat(RequestScopes.MEMBER_ID.get()).isEqualTo(id);
              assertThat(RequestScopes.MEMBER_ID.isBound()).isTrue();
            });
  }

  @Test
  void scopedValueAutoUnbindsOnException() {
    assertThatThrownBy(
            () ->
                ScopedValue.where(RequestScopes.TENANT_ID, "test")
                    .run(
                        () -> {
                          throw new RuntimeException("test");
                        }))
        .isInstanceOf(RuntimeException.class);
    assertThat(RequestScopes.TENANT_ID.isBound()).isFalse();
  }

  @Test
  void multipleBindingsWorkTogether() {
    UUID id = UUID.randomUUID();
    ScopedValue.where(RequestScopes.TENANT_ID, "tenant_abc")
        .where(RequestScopes.MEMBER_ID, id)
        .where(RequestScopes.ORG_ROLE, "admin")
        .run(
            () -> {
              assertThat(RequestScopes.TENANT_ID.get()).isEqualTo("tenant_abc");
              assertThat(RequestScopes.MEMBER_ID.get()).isEqualTo(id);
              assertThat(RequestScopes.ORG_ROLE.get()).isEqualTo("admin");
            });
  }

  @Test
  void requireMemberId_throwsWhenNotBound() {
    assertThatThrownBy(RequestScopes::requireMemberId)
        .isInstanceOf(MemberContextNotBoundException.class);
  }

  @Test
  void requireMemberId_returnsValueWhenBound() {
    UUID id = UUID.randomUUID();
    ScopedValue.where(RequestScopes.MEMBER_ID, id)
        .run(
            () -> {
              assertThat(RequestScopes.requireMemberId()).isEqualTo(id);
            });
  }

  @Test
  void getOrgRole_returnsNullWhenNotBound() {
    assertThat(RequestScopes.getOrgRole()).isNull();
  }

  @Test
  void getOrgRole_returnsValueWhenBound() {
    ScopedValue.where(RequestScopes.ORG_ROLE, "admin")
        .run(
            () -> {
              assertThat(RequestScopes.getOrgRole()).isEqualTo("admin");
            });
  }

  @Test
  void requireOwner_withOwnerRole_succeeds() {
    ScopedValue.where(RequestScopes.ORG_ROLE, "owner")
        .run(() -> assertThatCode(RequestScopes::requireOwner).doesNotThrowAnyException());
  }

  @Test
  void requireOwner_withAdminRole_throwsForbidden() {
    ScopedValue.where(RequestScopes.ORG_ROLE, "admin")
        .run(
            () ->
                assertThatThrownBy(RequestScopes::requireOwner)
                    .isInstanceOf(ForbiddenException.class));
  }

  @Test
  void requireOwner_withUnboundRole_throwsForbidden() {
    assertThatThrownBy(RequestScopes::requireOwner).isInstanceOf(ForbiddenException.class);
  }

  // ========================================================================
  // runForTenant / callForTenant — canonical static API for binding tenant
  // scope outside a request (replaces 14 duplicated handleInTenantScope
  // helpers in notification handlers). See ADR-T008.
  // ========================================================================

  @Test
  void runForTenant_bindsTenantId() {
    RequestScopes.runForTenant(
        "tenant_acme",
        null,
        () -> assertThat(RequestScopes.requireTenantId()).isEqualTo("tenant_acme"));
  }

  @Test
  void runForTenant_bindsOrgIdWhenProvided() {
    RequestScopes.runForTenant(
        "tenant_acme",
        "org_123",
        () -> {
          assertThat(RequestScopes.requireTenantId()).isEqualTo("tenant_acme");
          assertThat(RequestScopes.getOrgIdOrNull()).isEqualTo("org_123");
        });
  }

  @Test
  void runForTenant_omitsOrgIdWhenNull() {
    RequestScopes.runForTenant(
        "tenant_acme",
        null,
        () -> {
          assertThat(RequestScopes.requireTenantId()).isEqualTo("tenant_acme");
          assertThat(RequestScopes.getOrgIdOrNull()).isNull();
        });
  }

  @Test
  void runForTenant_omitsOrgIdWhenBlank() {
    RequestScopes.runForTenant(
        "tenant_acme", "   ", () -> assertThat(RequestScopes.getOrgIdOrNull()).isNull());
  }

  @Test
  void runForTenant_rejectsNullTenant() {
    Runnable action = () -> {};
    assertThatThrownBy(() -> RequestScopes.runForTenant(null, "org_123", action))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId");
  }

  @Test
  void runForTenant_rejectsBlankTenant() {
    Runnable action = () -> {};
    assertThatThrownBy(() -> RequestScopes.runForTenant("", "org_123", action))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RequestScopes.runForTenant("   ", "org_123", action))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void runForTenant_rejectsNullAction() {
    assertThatThrownBy(() -> RequestScopes.runForTenant("tenant_acme", null, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void runForTenant_propagatesRuntimeException() {
    assertThatThrownBy(
            () ->
                RequestScopes.runForTenant(
                    "tenant_acme",
                    null,
                    () -> {
                      throw new IllegalStateException("boom");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("boom");
  }

  @Test
  void runForTenant_unbindsAfterScope() {
    assertThat(RequestScopes.TENANT_ID.isBound()).isFalse();
    RequestScopes.runForTenant("tenant_acme", "org_123", () -> {});
    assertThat(RequestScopes.TENANT_ID.isBound()).isFalse();
    assertThat(RequestScopes.ORG_ID.isBound()).isFalse();
  }

  @Test
  void callForTenant_returnsResult() {
    String result =
        RequestScopes.callForTenant(
            "tenant_acme",
            "org_123",
            () -> RequestScopes.requireTenantId() + ":" + RequestScopes.getOrgIdOrNull());
    assertThat(result).isEqualTo("tenant_acme:org_123");
  }

  @Test
  void callForTenant_propagatesRuntimeException() {
    Callable<String> failing =
        () -> {
          throw new IllegalStateException("boom");
        };
    assertThatThrownBy(() -> RequestScopes.callForTenant("tenant_acme", null, failing))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("boom");
  }

  @Test
  void callForTenant_wrapsCheckedException() {
    Callable<String> failing =
        () -> {
          throw new java.io.IOException("disk full");
        };
    assertThatThrownBy(() -> RequestScopes.callForTenant("tenant_acme", null, failing))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(java.io.IOException.class);
  }

  @Test
  void callForTenant_rejectsNullTenant() {
    Callable<String> action = () -> "x";
    assertThatThrownBy(() -> RequestScopes.callForTenant(null, "org_123", action))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void callForTenant_rejectsBlankTenant() {
    Callable<String> action = () -> "x";
    assertThatThrownBy(() -> RequestScopes.callForTenant("", "org_123", action))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void callForTenant_rejectsNullAction() {
    assertThatThrownBy(() -> RequestScopes.callForTenant("tenant_acme", null, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void runForTenant_nestedCallRebindsTenantId() {
    RequestScopes.runForTenant(
        "outer_tenant",
        "org_outer",
        () -> {
          assertThat(RequestScopes.requireTenantId()).isEqualTo("outer_tenant");
          RequestScopes.runForTenant(
              "inner_tenant",
              "org_inner",
              () -> {
                // Both bindings rebind cleanly when the inner call provides both
                assertThat(RequestScopes.requireTenantId()).isEqualTo("inner_tenant");
                assertThat(RequestScopes.getOrgIdOrNull()).isEqualTo("org_inner");
              });
          // Outer scope restored on exit
          assertThat(RequestScopes.requireTenantId()).isEqualTo("outer_tenant");
          assertThat(RequestScopes.getOrgIdOrNull()).isEqualTo("org_outer");
        });
  }

  @Test
  void runForTenant_nestedCallWithNullOrgId_outerOrgIdRemainsVisible() {
    // Documents a known asymmetry in bindTenantScope: ORG_ID is only bound when non-null/blank,
    // so a nested runForTenant(t2, null, ...) inside an outer runForTenant(t1, "org_outer", ...)
    // leaves the outer ORG_ID visible to the inner action body. TENANT_ID rebinds correctly.
    //
    // No migrated AFTER_COMMIT handler exercises this path (every event consumed carries a
    // non-null orgId), so the leak is theoretical in PR #1's scope. Documented as a known
    // limitation in RequestScopes.runForTenant Javadoc; revisit if PR #2's broader migration
    // surfaces a use case where the asymmetry matters.
    RequestScopes.runForTenant(
        "outer_tenant",
        "org_outer",
        () -> {
          RequestScopes.runForTenant(
              "inner_tenant",
              null,
              () -> {
                assertThat(RequestScopes.requireTenantId()).isEqualTo("inner_tenant");
                // Asymmetry: outer ORG_ID remains visible because bindTenantScope didn't rebind it
                assertThat(RequestScopes.getOrgIdOrNull()).isEqualTo("org_outer");
              });
        });
  }

  // ========================================================================
  // runForTenantWithMember — 3-binding variant for system-actor backfills
  // (TENANT_ID + ORG_ID + MEMBER_ID = system actor sentinel UUID). Used by
  // portal read-model backfill helpers (RetainerPortalSyncService,
  // TrustLedgerPortalSyncService). See ADR-T008 Surface 2.
  // ========================================================================

  @Test
  void runForTenantWithMember_bindsTenantOrgAndMember() {
    UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    RequestScopes.runForTenantWithMember(
        "tenant_abc",
        "org_xyz",
        actorId,
        () -> {
          assertThat(RequestScopes.requireTenantId()).isEqualTo("tenant_abc");
          assertThat(RequestScopes.getOrgIdOrNull()).isEqualTo("org_xyz");
          assertThat(RequestScopes.requireMemberId()).isEqualTo(actorId);
        });
  }

  @Test
  void runForTenantWithMember_omitsOrgIdWhenNull() {
    UUID actorId = UUID.randomUUID();
    RequestScopes.runForTenantWithMember(
        "tenant_abc",
        null,
        actorId,
        () -> {
          assertThat(RequestScopes.requireTenantId()).isEqualTo("tenant_abc");
          assertThat(RequestScopes.getOrgIdOrNull()).isNull();
          assertThat(RequestScopes.requireMemberId()).isEqualTo(actorId);
        });
  }

  @Test
  void runForTenantWithMember_rejectsNullTenant() {
    UUID actorId = UUID.randomUUID();
    Runnable action = () -> {};
    assertThatThrownBy(() -> RequestScopes.runForTenantWithMember(null, "org_xyz", actorId, action))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void runForTenantWithMember_rejectsBlankTenant() {
    UUID actorId = UUID.randomUUID();
    Runnable action = () -> {};
    assertThatThrownBy(() -> RequestScopes.runForTenantWithMember("  ", "org_xyz", actorId, action))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void runForTenantWithMember_rejectsNullMember() {
    Runnable action = () -> {};
    assertThatThrownBy(
            () -> RequestScopes.runForTenantWithMember("tenant_abc", "org_xyz", null, action))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void runForTenantWithMember_rejectsNullAction() {
    UUID actorId = UUID.randomUUID();
    assertThatThrownBy(
            () -> RequestScopes.runForTenantWithMember("tenant_abc", "org_xyz", actorId, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void runForTenantWithMember_unbindsAfterScope() {
    UUID actorId = UUID.randomUUID();
    assertThat(RequestScopes.MEMBER_ID.isBound()).isFalse();
    RequestScopes.runForTenantWithMember("tenant_abc", "org_xyz", actorId, () -> {});
    assertThat(RequestScopes.TENANT_ID.isBound()).isFalse();
    assertThat(RequestScopes.ORG_ID.isBound()).isFalse();
    assertThat(RequestScopes.MEMBER_ID.isBound()).isFalse();
  }
}
