package io.b2mash.b2b.b2bstrawman.member;

public record ProjectAccess(
    boolean canView,
    boolean canEdit,
    boolean canManageMembers,
    boolean canDelete,
    String projectRole) {

  public static final ProjectAccess DENIED = new ProjectAccess(false, false, false, false, null);
}
