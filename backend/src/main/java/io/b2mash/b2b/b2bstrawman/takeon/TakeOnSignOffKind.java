package io.b2mash.b2b.b2bstrawman.takeon;

/**
 * The act of responsibility a {@link TakeOnSignOff} records (§17.2.4): the FICA attestation over a
 * clients batch, or the partner/director sign-off over a trust position.
 */
public enum TakeOnSignOffKind {
  FICA_ATTESTATION,
  TRUST_SIGN_OFF
}
