package io.b2mash.b2b.b2bstrawman.dashboard.dto;

import java.util.UUID;

/** Per-member hour breakdown within a project for the member-hours endpoint. */
public record MemberHoursEntry(
    UUID memberId, String memberName, double totalHours, double billableHours) {}
