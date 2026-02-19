package io.b2mash.b2b.b2bstrawman.setupstatus;

public record ChecklistProgress(
    String checklistName, int completed, int total, int percentComplete) {}
