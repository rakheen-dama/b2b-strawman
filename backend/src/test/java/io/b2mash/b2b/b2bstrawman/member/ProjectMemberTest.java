package io.b2mash.b2b.b2bstrawman.member;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.security.Roles;
import io.b2mash.b2b.b2bstrawman.testutil.TestIds;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** project_members.project_role is stored only as Roles.PROJECT_LEAD / PROJECT_MEMBER (ADR-345). */
class ProjectMemberTest {

  private static final UUID PROJECT = UUID.randomUUID();
  private static final UUID MEMBER = UUID.randomUUID();

  @ParameterizedTest
  @CsvSource({
    "LEAD, lead",
    "lead, lead",
    "'  Lead ', lead",
    "MEMBER, member",
    "member, member",
    "CONTRIBUTOR, member",
    "owner, member"
  })
  void constructorNormalisesRole(String raw, String expected) {
    var pm = new ProjectMember(PROJECT, MEMBER, raw, null);
    assertThat(pm.getProjectRole()).isEqualTo(expected);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void nullOrBlankRoleBecomesMember(String raw) {
    assertThat(new ProjectMember(PROJECT, MEMBER, raw, null).getProjectRole())
        .isEqualTo(Roles.PROJECT_MEMBER);
  }

  @Test
  void setterNormalisesRole() {
    var pm = new ProjectMember(PROJECT, MEMBER, Roles.PROJECT_MEMBER, null);
    pm.setProjectRole("LEAD");
    assertThat(pm.getProjectRole()).isEqualTo(Roles.PROJECT_LEAD);
    pm.setProjectRole("CONTRIBUTOR");
    assertThat(pm.getProjectRole()).isEqualTo(Roles.PROJECT_MEMBER);
  }

  @Test
  void lifecycleGuardNormalisesAValueWrittenBehindTheSetter() {
    var pm = new ProjectMember(PROJECT, MEMBER, Roles.PROJECT_MEMBER, null);
    TestIds.withField(pm, "projectRole", "LEAD");
    pm.guardRole();
    assertThat(pm.getProjectRole()).isEqualTo(Roles.PROJECT_LEAD);

    TestIds.withField(pm, "projectRole", null);
    pm.guardRole();
    assertThat(pm.getProjectRole()).isEqualTo(Roles.PROJECT_MEMBER);
  }

  @Test
  void normaliserOnlyEverReturnsACanonicalValue() {
    for (String raw :
        new String[] {null, "", " ", "LEAD", "Lead", "lead", "MEMBER", "CONTRIBUTOR", "x"}) {
      assertThat(Roles.PROJECT_ROLES).contains(Roles.normaliseProjectRole(raw));
    }
  }
}
