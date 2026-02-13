package io.b2mash.b2b.b2bstrawman.notification.template;

import io.b2mash.b2b.b2bstrawman.notification.Notification;

/**
 * Email template definitions for notification types. Templates use simple string formatting with
 * notification fields. When rich HTML emails are needed (future phase), these can be replaced with
 * Thymeleaf templates or a dedicated template engine.
 */
public enum EmailTemplate {
  TASK_ASSIGNED(
      "You've been assigned to a task",
      "Hi,\n\n%s\n\nView the task in DocTeams.\n\nBest,\nDocTeams"),
  TASK_CLAIMED(
      "A task you were working on was claimed",
      "Hi,\n\n%s\n\nView the task in DocTeams.\n\nBest,\nDocTeams"),
  TASK_UPDATED(
      "A task you're assigned to was updated",
      "Hi,\n\n%s\n\nView the task in DocTeams.\n\nBest,\nDocTeams"),
  COMMENT_ADDED(
      "New comment on your task", "Hi,\n\n%s\n\nView the comment in DocTeams.\n\nBest,\nDocTeams"),
  DOCUMENT_SHARED(
      "New document in your project",
      "Hi,\n\n%s\n\nView the document in DocTeams.\n\nBest,\nDocTeams"),
  MEMBER_INVITED(
      "You've been added to a project",
      "Hi,\n\n%s\n\nView the project in DocTeams.\n\nBest,\nDocTeams"),
  DEFAULT("DocTeams notification", "Hi,\n\n%s\n\nBest,\nDocTeams");

  private final String subjectTemplate;
  private final String bodyTemplate;

  EmailTemplate(String subjectTemplate, String bodyTemplate) {
    this.subjectTemplate = subjectTemplate;
    this.bodyTemplate = bodyTemplate;
  }

  public static EmailTemplate fromNotificationType(String type) {
    try {
      return valueOf(type);
    } catch (IllegalArgumentException e) {
      return DEFAULT;
    }
  }

  public String renderSubject(Notification notification) {
    return subjectTemplate;
  }

  public String renderBody(Notification notification) {
    return String.format(bodyTemplate, notification.getTitle());
  }
}
