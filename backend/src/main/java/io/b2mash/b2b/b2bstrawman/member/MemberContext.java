package io.b2mash.b2b.b2bstrawman.member;

import java.util.UUID;

public final class MemberContext {

  private static final ThreadLocal<UUID> CURRENT_MEMBER_ID = new ThreadLocal<>();
  private static final ThreadLocal<String> CURRENT_ORG_ROLE = new ThreadLocal<>();

  private MemberContext() {}

  public static void setCurrentMemberId(UUID memberId) {
    CURRENT_MEMBER_ID.set(memberId);
  }

  public static UUID getCurrentMemberId() {
    return CURRENT_MEMBER_ID.get();
  }

  public static void setOrgRole(String orgRole) {
    CURRENT_ORG_ROLE.set(orgRole);
  }

  public static String getOrgRole() {
    return CURRENT_ORG_ROLE.get();
  }

  public static void clear() {
    CURRENT_MEMBER_ID.remove();
    CURRENT_ORG_ROLE.remove();
  }
}
