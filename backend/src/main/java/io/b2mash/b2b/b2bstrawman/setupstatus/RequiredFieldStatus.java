package io.b2mash.b2b.b2bstrawman.setupstatus;

import java.util.List;

public record RequiredFieldStatus(int filled, int total, List<FieldStatus> fields) {}
