package io.b2mash.b2b.b2bstrawman.multitenancy;

public class MemberContextNotBoundException extends RuntimeException {

  public MemberContextNotBoundException() {
    super("Member context not available — MEMBER_ID not bound by filter chain");
  }
}
