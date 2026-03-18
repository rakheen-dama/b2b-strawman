package io.b2mash.b2b.b2bstrawman.verticals;

/** Shared response record for stub module status endpoints. */
public record ModuleStatusResponse(String module, String status, String message) {}
